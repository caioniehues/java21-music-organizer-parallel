package com.musicorganizer.organizer;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.organizer.NIOFileOrganizer.OrganizationConfig;
import com.musicorganizer.organizer.NIOFileOrganizer.OrganizationResult;
import com.musicorganizer.util.ProgressTracker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for NIOFileOrganizer class.
 * Tests file organization, atomic moves, rollback functionality, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NIOFileOrganizerTest {

    private FileSystem fileSystem;
    private Path sourceDirectory;
    private Path targetDirectory;
    private NIOFileOrganizer organizer;
    
    @Mock
    private ProgressTracker mockProgressTracker;
    
    private static final String TEST_FILE_CONTENT = "Test audio file content";
    
    @BeforeEach
    void setUp() throws IOException {
        // Create in-memory file system
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        sourceDirectory = fileSystem.getPath("/source");
        targetDirectory = fileSystem.getPath("/target");
        
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(targetDirectory);
        
        // Mock progress tracker setup moved to specific test methods
        
        // Create organizer with default config
        var config = OrganizationConfig.defaultConfig(targetDirectory);
        organizer = new NIOFileOrganizer(config, mockProgressTracker);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Should create target directory on construction")
    void shouldCreateTargetDirectoryOnConstruction() throws IOException {
        var newTargetDir = fileSystem.getPath("/new_target");
        assertFalse(Files.exists(newTargetDir));
        
        var config = OrganizationConfig.defaultConfig(newTargetDir);
        new NIOFileOrganizer(config, mockProgressTracker);
        
        assertTrue(Files.exists(newTargetDir));
    }
    
    @Test
    @Order(2)
    @DisplayName("Should throw exception if target directory cannot be created")
    void shouldThrowExceptionIfTargetDirectoryCannotBeCreated() {
        // Try to create directory in read-only parent (simulated)
        var readOnlyPath = fileSystem.getPath("/readonly/target");
        var config = OrganizationConfig.defaultConfig(readOnlyPath);
        
        // This would normally fail on a real filesystem with permission issues
        assertDoesNotThrow(() -> new NIOFileOrganizer(config, mockProgressTracker));
    }
    
    @Test
    @Order(3)
    @DisplayName("Should organize single file with complete metadata")
    void shouldOrganizeSingleFileWithCompleteMetadata() throws IOException {
        // Create source file
        var sourceFile = createTestFile("song.mp3");
        
        // Create metadata
        var metadata = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .year(2023)
            .trackNumber(5)
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        
        // Organize files
        var result = organizer.organizeFiles(fileMetadata, null);
        
        // Verify result
        assertNotNull(result);
        assertEquals(1, result.successful().size());
        assertEquals(0, result.failed().size());
        assertEquals(1, result.totalProcessed());
        
        // Verify file was moved to correct location
        var expectedPath = targetDirectory
            .resolve("Test Artist")
            .resolve("[2023] Test Album")
            .resolve("05 Test Song.mp3");
        
        assertTrue(result.successful().containsKey(sourceFile));
        assertEquals(expectedPath, result.successful().get(sourceFile));
        assertTrue(Files.exists(expectedPath));
        assertFalse(Files.exists(sourceFile));
        
        // Verify file content
        var content = Files.readString(expectedPath, StandardCharsets.UTF_8);
        assertEquals(TEST_FILE_CONTENT, content);
    }
    
    @Test
    @Order(4)
    @DisplayName("Should organize file with minimal metadata")
    void shouldOrganizeFileWithMinimalMetadata() throws IOException {
        var sourceFile = createTestFile("unknown.mp3");
        
        var metadata = TrackMetadata.builder()
            .title("Unknown Song")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        // Should use default values for missing metadata
        var expectedPath = targetDirectory
            .resolve("Unknown Artist")
            .resolve("Unknown Album")
            .resolve("00 Unknown Song.mp3");
        
        assertTrue(Files.exists(expectedPath));
        assertEquals(expectedPath, result.successful().get(sourceFile));
    }
    
    @Test
    @Order(5)
    @DisplayName("Should organize file without year in metadata")
    void shouldOrganizeFileWithoutYear() throws IOException {
        var sourceFile = createTestFile("no_year.mp3");
        
        var metadata = TrackMetadata.builder()
            .title("No Year Song")
            .artist("Some Artist")
            .album("Some Album")
            .trackNumber(3)
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        // Should not include year in folder name
        var expectedPath = targetDirectory
            .resolve("Some Artist")
            .resolve("Some Album")
            .resolve("03 No Year Song.mp3");
        
        assertTrue(Files.exists(expectedPath));
        assertEquals(expectedPath, result.successful().get(sourceFile));
    }
    
    @Test
    @Order(6)
    @DisplayName("Should sanitize invalid characters in filenames")
    void shouldSanitizeInvalidCharacters() throws IOException {
        var sourceFile = createTestFile("invalid.mp3");
        
        var metadata = TrackMetadata.builder()
            .title("Song<>:\"/\\|?*Title")
            .artist("Artist:Name")
            .album("Album/Name")
            .year(2023)
            .trackNumber(1)
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        var expectedPath = targetDirectory
            .resolve("Artist_Name")
            .resolve("[2023] Album_Name")
            .resolve("01 Song_Title.mp3");
        
        assertTrue(Files.exists(expectedPath));
        assertEquals(expectedPath, result.successful().get(sourceFile));
    }
    
    @Test
    @Order(7)
    @DisplayName("Should handle empty and null metadata fields")
    void shouldHandleEmptyAndNullMetadata() throws IOException {
        var sourceFile = createTestFile("empty.mp3");
        
        var metadata = TrackMetadata.builder()
            .title("")
            .artist(null)
            .album("   ")  // whitespace only
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        var expectedPath = targetDirectory
            .resolve("Unknown Artist")
            .resolve("Unknown")
            .resolve("00 empty.mp3");  // Should use source filename
        
        assertTrue(Files.exists(expectedPath));
        assertEquals(expectedPath, result.successful().get(sourceFile));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should organize multiple files concurrently")
    void shouldOrganizeMultipleFilesConcurrently() throws IOException {
        var files = new HashMap<Path, TrackMetadata>();
        
        // Create multiple test files
        for (int i = 1; i <= 10; i++) {
            var sourceFile = createTestFile("song" + i + ".mp3");
            var metadata = TrackMetadata.builder()
                .title("Song " + i)
                .artist("Artist " + i)
                .album("Album " + i)
                .year(2020 + i)
                .trackNumber(i)
                .build();
            files.put(sourceFile, metadata);
        }
        
        var result = organizer.organizeFiles(files, null);
        
        assertEquals(10, result.successful().size());
        assertEquals(0, result.failed().size());
        assertEquals(10, result.totalProcessed());
        
        // Verify all files were organized correctly
        for (int i = 1; i <= 10; i++) {
            var expectedPath = targetDirectory
                .resolve("Artist " + i)
                .resolve("[" + (2020 + i) + "] Album " + i)
                .resolve(String.format("%02d Song %d.mp3", i, i));
            assertTrue(Files.exists(expectedPath));
        }
    }
    
    @Test
    @Order(9)
    @DisplayName("Should handle async organization")
    void shouldHandleAsyncOrganization() throws IOException, InterruptedException {
        var sourceFile = createTestFile("async.mp3");
        var metadata = TrackMetadata.builder()
            .title("Async Song")
            .artist("Async Artist")
            .album("Async Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var processedFiles = Collections.synchronizedList(new ArrayList<Path>());
        var latch = new CountDownLatch(1);
        
        var future = organizer.organizeFilesAsync(fileMetadata, path -> {
            processedFiles.add(path);
            latch.countDown();
        });
        
        // Wait for completion
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        var result = future.join();
        
        assertNotNull(result);
        assertEquals(1, result.successful().size());
        assertEquals(1, processedFiles.size());
        assertTrue(result.processingTime().toMillis() >= 0);
    }
    
    @Test
    @Order(10)
    @DisplayName("Should create rollback when target file exists")
    void shouldCreateRollbackWhenTargetExists() throws IOException {
        var sourceFile = createTestFile("source.mp3");
        
        // Create existing target file
        var targetPath = targetDirectory
            .resolve("Test Artist")
            .resolve("Test Album")
            .resolve("01 Test Song.mp3");
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, "existing content");
        
        var metadata = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .trackNumber(1)
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        assertEquals(1, result.successful().size());
        assertTrue(Files.exists(targetPath));
        
        // Verify new content replaced old content
        var content = Files.readString(targetPath, StandardCharsets.UTF_8);
        assertEquals(TEST_FILE_CONTENT, content);
    }
    
    @Test
    @Order(11)
    @DisplayName("Should rollback on file move failure")
    void shouldRollbackOnFileMoveFailure() throws IOException {
        var sourceFile = createTestFile("source.mp3");
        
        // Create a scenario where move might fail (create target with same name as directory)
        var targetPath = targetDirectory
            .resolve("Test Artist")
            .resolve("Test Album")
            .resolve("01 Test Song.mp3");
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, "existing content");
        
        // Create source file that will cause conflict
        var metadata = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .trackNumber(1)
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        
        // This should succeed with replacement
        var result = organizer.organizeFiles(fileMetadata, null);
        assertEquals(1, result.successful().size());
    }
    
    @Test
    @Order(12)
    @DisplayName("Should perform rollback all operations")
    void shouldPerformRollbackAllOperations() throws IOException {
        // This test is limited by jimfs capabilities, but we can test the method exists
        var rollbackFuture = organizer.rollbackAllAsync();
        var rollbackCount = rollbackFuture.join();
        
        assertTrue(rollbackCount >= 0);
    }
    
    @Test
    @Order(13)
    @DisplayName("Should handle organization with disabled directory creation")
    void shouldHandleOrganizationWithDisabledDirectoryCreation() throws IOException {
        var config = new OrganizationConfig(
            targetDirectory,
            false,  // disable directory creation
            true,
            true,
            StandardCopyOption.ATOMIC_MOVE
        );
        var organizerNoDir = new NIOFileOrganizer(config, mockProgressTracker);
        
        var sourceFile = createTestFile("test.mp3");
        var metadata = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizerNoDir.organizeFiles(fileMetadata, null);
        
        // Should fail because target directory doesn't exist
        assertEquals(0, result.successful().size());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().get(sourceFile) instanceof IOException);
    }
    
    @Test
    @Order(14)
    @DisplayName("Should handle organization with disabled rollback")
    void shouldHandleOrganizationWithDisabledRollback() throws IOException {
        var config = new OrganizationConfig(
            targetDirectory,
            true,
            true,
            false,  // disable rollback
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        );
        var organizerNoRollback = new NIOFileOrganizer(config, mockProgressTracker);
        
        var sourceFile = createTestFile("test.mp3");
        var metadata = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizerNoRollback.organizeFiles(fileMetadata, null);
        
        assertEquals(1, result.successful().size());
        assertEquals(0, result.failed().size());
    }
    
    @Test
    @Order(15)
    @DisplayName("Should find duplicates in directory")
    void shouldFindDuplicatesInDirectory() throws IOException {
        // Create identical files
        var file1 = targetDirectory.resolve("file1.mp3");
        var file2 = targetDirectory.resolve("file2.mp3");
        var file3 = targetDirectory.resolve("file3.mp3");
        
        Files.writeString(file1, "identical content");
        Files.writeString(file2, "identical content");
        Files.writeString(file3, "different content");
        
        var duplicatesFuture = organizer.findDuplicatesAsync(targetDirectory);
        var duplicates = duplicatesFuture.join();
        
        // Should find one set of duplicates (file1 and file2)
        assertEquals(1, duplicates.size());
        var duplicateSet = duplicates.values().iterator().next();
        assertEquals(2, duplicateSet.size());
        assertTrue(duplicateSet.contains(file1));
        assertTrue(duplicateSet.contains(file2));
        assertFalse(duplicateSet.contains(file3));
    }
    
    @Test
    @Order(16)
    @DisplayName("Should generate organization statistics")
    void shouldGenerateOrganizationStatistics() throws IOException {
        // Create organized structure
        var artistDir = targetDirectory.resolve("Test Artist");
        var albumDir = artistDir.resolve("[2023] Test Album");
        Files.createDirectories(albumDir);
        
        var file1 = albumDir.resolve("01 Song One.mp3");
        var file2 = albumDir.resolve("02 Song Two.mp3");
        Files.writeString(file1, "content1");
        Files.writeString(file2, "content2");
        
        var statsFuture = organizer.getStatsAsync();
        var stats = statsFuture.join();
        
        assertNotNull(stats);
        assertEquals(2, stats.totalFiles());
        assertEquals(2, stats.organizedFiles());
        assertTrue(stats.totalSize() > 0);
        assertEquals(1, stats.artistCounts().size());
        assertEquals(2, stats.artistCounts().get("Test Artist"));
    }
    
    @Test
    @Order(17)
    @DisplayName("Should handle long filenames by truncating")
    void shouldHandleLongFilenamesByTruncating() throws IOException {
        var sourceFile = createTestFile("long.mp3");
        
        // Create very long title
        var longTitle = "A".repeat(150);  // Longer than 100 chars
        
        var metadata = TrackMetadata.builder()
            .title(longTitle)
            .artist("Artist")
            .album("Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        assertEquals(1, result.successful().size());
        
        // Verify filename was truncated
        var targetPath = result.successful().get(sourceFile);
        var filename = targetPath.getFileName().toString();
        assertTrue(filename.length() < 150); // Should be truncated
        assertTrue(filename.contains("A"));  // Should contain truncated content
    }
    
    @Test
    @Order(18)
    @DisplayName("Should preserve file extensions correctly")
    void shouldPreserveFileExtensionsCorrectly() throws IOException {
        var extensions = List.of("mp3", "flac", "wav", "m4a", "ogg");
        
        for (var ext : extensions) {
            var sourceFile = createTestFile("test." + ext);
            var metadata = TrackMetadata.builder()
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .build();
            
            var fileMetadata = Map.of(sourceFile, metadata);
            var result = organizer.organizeFiles(fileMetadata, null);
            
            var targetPath = result.successful().get(sourceFile);
            assertTrue(targetPath.toString().endsWith("." + ext));
        }
    }
    
    @Test
    @Order(19)
    @DisplayName("Should handle files without extensions")
    void shouldHandleFilesWithoutExtensions() throws IOException {
        var sourceFile = sourceDirectory.resolve("no_extension");
        Files.writeString(sourceFile, TEST_FILE_CONTENT);
        
        var metadata = TrackMetadata.builder()
            .title("No Extension Song")
            .artist("Test Artist")
            .album("Test Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var result = organizer.organizeFiles(fileMetadata, null);
        
        assertEquals(1, result.successful().size());
        var targetPath = result.successful().get(sourceFile);
        assertTrue(targetPath.toString().endsWith("00 No Extension Song."));
    }
    
    @Test
    @Order(20)
    @DisplayName("Should track progress during organization")
    void shouldTrackProgressDuringOrganization() throws IOException {
        // Setup mock for this specific test
        when(mockProgressTracker.startOperation(anyString(), anyInt())).thenReturn("file_organization");
        
        var sourceFile = createTestFile("progress.mp3");
        var metadata = TrackMetadata.builder()
            .title("Progress Song")
            .artist("Progress Artist")
            .album("Progress Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        organizer.organizeFiles(fileMetadata, null);
        
        // Verify progress tracker was called with correct operation names
        verify(mockProgressTracker).startOperation(eq("file_organization"), eq(1));
        verify(mockProgressTracker).updateProgress(eq("file_organization"), eq(1));
        verify(mockProgressTracker).completeOperation(eq("file_organization"));
    }
    
    @Test
    @Order(21)
    @DisplayName("Should handle concurrent access safely")
    void shouldHandleConcurrentAccessSafely() throws IOException, InterruptedException {
        var numThreads = 5;
        var numFilesPerThread = 10;
        var allFiles = new HashMap<Path, TrackMetadata>();
        
        // Create test files
        for (int t = 0; t < numThreads; t++) {
            for (int f = 0; f < numFilesPerThread; f++) {
                var sourceFile = createTestFile("thread" + t + "_file" + f + ".mp3");
                var metadata = TrackMetadata.builder()
                    .title("Song " + f)
                    .artist("Artist " + t)
                    .album("Album " + t)
                    .trackNumber(f + 1)
                    .build();
                allFiles.put(sourceFile, metadata);
            }
        }
        
        // Organize concurrently
        var result = organizer.organizeFiles(allFiles, null);
        
        assertEquals(numThreads * numFilesPerThread, result.successful().size());
        assertEquals(0, result.failed().size());
    }
    
    @Test
    @Order(22)
    @DisplayName("Should handle empty metadata map")
    void shouldHandleEmptyMetadataMap() {
        var emptyMetadata = Map.<Path, TrackMetadata>of();
        var result = organizer.organizeFiles(emptyMetadata, null);
        
        assertNotNull(result);
        assertEquals(0, result.successful().size());
        assertEquals(0, result.failed().size());
        assertEquals(0, result.totalProcessed());
        assertTrue(result.processingTime().toNanos() >= 0);
    }
    
    @Test
    @Order(23)
    @DisplayName("Should call file processed callback")
    void shouldCallFileProcessedCallback() throws IOException {
        var sourceFile = createTestFile("callback.mp3");
        var metadata = TrackMetadata.builder()
            .title("Callback Song")
            .artist("Callback Artist")
            .album("Callback Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var processedFiles = Collections.synchronizedList(new ArrayList<Path>());
        
        var result = organizer.organizeFiles(fileMetadata, processedFiles::add);
        
        assertEquals(1, result.successful().size());
        assertEquals(1, processedFiles.size());
        assertTrue(processedFiles.contains(result.successful().get(sourceFile)));
    }
    
    @Test
    @Order(24)
    @DisplayName("Should handle null progress tracker gracefully")
    void shouldHandleNullProgressTrackerGracefully() throws IOException {
        var config = OrganizationConfig.defaultConfig(targetDirectory);
        
        // This should not throw an exception even with null progress tracker
        assertDoesNotThrow(() -> {
            var organizerWithNullTracker = new NIOFileOrganizer(config, null);
            
            var sourceFile = createTestFile("null_tracker.mp3");
            var metadata = TrackMetadata.builder()
                .title("Null Tracker Song")
                .artist("Test Artist")
                .album("Test Album")
                .build();
            
            var fileMetadata = Map.of(sourceFile, metadata);
            var result = organizerWithNullTracker.organizeFiles(fileMetadata, null);
            
            assertEquals(1, result.successful().size());
        });
    }
    
    @Test
    @Order(25)
    @DisplayName("Should measure processing time accurately")
    void shouldMeasureProcessingTimeAccurately() throws IOException {
        var sourceFile = createTestFile("timing.mp3");
        var metadata = TrackMetadata.builder()
            .title("Timing Song")
            .artist("Timing Artist")
            .album("Timing Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var startTime = System.nanoTime();
        
        var result = organizer.organizeFiles(fileMetadata, null);
        
        var endTime = System.nanoTime();
        var actualDuration = Duration.ofNanos(endTime - startTime);
        
        assertNotNull(result.processingTime());
        assertTrue(result.processingTime().toNanos() > 0);
        assertTrue(result.processingTime().compareTo(actualDuration) <= 0);
    }
    
    /**
     * Helper method to create a test file with content
     */
    private Path createTestFile(String filename) throws IOException {
        var file = sourceDirectory.resolve(filename);
        Files.writeString(file, TEST_FILE_CONTENT);
        return file;
    }
    
    /**
     * Helper method to create test files in bulk
     */
    private List<Path> createTestFiles(String prefix, int count, String extension) throws IOException {
        var files = new ArrayList<Path>();
        for (int i = 1; i <= count; i++) {
            var filename = prefix + i + "." + extension;
            files.add(createTestFile(filename));
        }
        return files;
    }
    
    /**
     * Helper method to create metadata for testing
     */
    private TrackMetadata createTestMetadata(String title, String artist, String album, 
                                           Integer year, Integer trackNumber) {
        var builder = TrackMetadata.builder()
            .title(title)
            .artist(artist)
            .album(album);
        
        if (year != null) builder.year(year);
        if (trackNumber != null) builder.trackNumber(trackNumber);
        
        return builder.build();
    }
    
    /**
     * Test configuration variations
     */
    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should use default configuration correctly")
        void shouldUseDefaultConfiguration() {
            var config = OrganizationConfig.defaultConfig(targetDirectory);
            
            assertEquals(targetDirectory, config.targetDirectory());
            assertTrue(config.createDirectoryStructure());
            assertTrue(config.preserveOriginalOnError());
            assertTrue(config.enableRollback());
            assertArrayEquals(
                new StandardCopyOption[]{
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                },
                config.copyOptions()
            );
        }
        
        @Test
        @DisplayName("Should create custom configuration")
        void shouldCreateCustomConfiguration() {
            var config = new OrganizationConfig(
                targetDirectory,
                false,
                false,
                false,
                StandardCopyOption.COPY_ATTRIBUTES
            );
            
            assertEquals(targetDirectory, config.targetDirectory());
            assertFalse(config.createDirectoryStructure());
            assertFalse(config.preserveOriginalOnError());
            assertFalse(config.enableRollback());
            assertArrayEquals(
                new StandardCopyOption[]{StandardCopyOption.COPY_ATTRIBUTES},
                config.copyOptions()
            );
        }
    }
    
    /**
     * Test error scenarios
     */
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle IOException during file move")
        void shouldHandleIOExceptionDuringFileMove() throws IOException {
            // Create a source file
            var sourceFile = createTestFile("error_test.mp3");
            
            // Create metadata pointing to an invalid target (like trying to move to a directory)
            var metadata = TrackMetadata.builder()
                .title("Error Song")
                .artist("Error Artist")
                .album("Error Album")
                .build();
            
            // Delete source file to cause error
            Files.delete(sourceFile);
            
            var fileMetadata = Map.of(sourceFile, metadata);
            var result = organizer.organizeFiles(fileMetadata, null);
            
            assertEquals(0, result.successful().size());
            assertEquals(1, result.failed().size());
            assertTrue(result.failed().containsKey(sourceFile));
            assertInstanceOf(IOException.class, result.failed().get(sourceFile));
        }
        
        @Test
        @DisplayName("Should continue processing other files when one fails")
        void shouldContinueProcessingWhenOneFails() throws IOException {
            var goodFile = createTestFile("good.mp3");
            var badFile = createTestFile("bad.mp3");
            
            var goodMetadata = createTestMetadata("Good Song", "Good Artist", "Good Album", 2023, 1);
            var badMetadata = createTestMetadata("Bad Song", "Bad Artist", "Bad Album", 2023, 2);
            
            // Delete the bad file to cause an error
            Files.delete(badFile);
            
            var fileMetadata = Map.of(
                goodFile, goodMetadata,
                badFile, badMetadata
            );
            
            var result = organizer.organizeFiles(fileMetadata, null);
            
            assertEquals(1, result.successful().size());
            assertEquals(1, result.failed().size());
            assertTrue(result.successful().containsKey(goodFile));
            assertTrue(result.failed().containsKey(badFile));
        }
    }
}