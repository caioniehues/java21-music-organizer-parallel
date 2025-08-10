package com.musicorganizer.organizer;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.organizer.NIOFileOrganizer.OrganizationConfig;
import com.musicorganizer.util.ProgressTracker;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified unit tests for NIOFileOrganizer class focusing on core functionality.
 * Tests file organization patterns, atomic moves, and error handling.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimpleNIOFileOrganizerTest {

    private FileSystem fileSystem;
    private Path sourceDirectory;
    private Path targetDirectory;
    private NIOFileOrganizer organizer;
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
        
        // Mock progress tracker
        mockProgressTracker = Mockito.mock(ProgressTracker.class);
        
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
    @Order(2)
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
    @Order(3)
    @DisplayName("Should sanitize invalid characters in filenames")
    void shouldSanitizeInvalidCharacters() throws IOException {
        var sourceFile = createTestFile("invalid.mp3");
        
        var metadata = TrackMetadata.builder()
            .title("Song<>Title")
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
    @Order(4)
    @DisplayName("Should handle null and empty metadata fields")
    void shouldHandleNullAndEmptyMetadata() throws IOException {
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
    @Order(5)
    @DisplayName("Should organize multiple files correctly")
    void shouldOrganizeMultipleFiles() throws IOException {
        var files = new HashMap<Path, TrackMetadata>();
        
        // Create multiple test files
        for (int i = 1; i <= 3; i++) {
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
        
        assertEquals(3, result.successful().size());
        assertEquals(0, result.failed().size());
        assertEquals(3, result.totalProcessed());
        
        // Verify all files were organized correctly
        for (int i = 1; i <= 3; i++) {
            var expectedPath = targetDirectory
                .resolve("Artist " + i)
                .resolve("[" + (2020 + i) + "] Album " + i)
                .resolve(String.format("%02d Song %d.mp3", i, i));
            assertTrue(Files.exists(expectedPath));
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("Should handle file organization without year")
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
    @Order(7)
    @DisplayName("Should preserve different file extensions")
    void shouldPreserveFileExtensions() throws IOException {
        var extensions = List.of("mp3", "flac", "wav", "m4a");
        
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
    @Order(8)
    @DisplayName("Should handle async organization")
    void shouldHandleAsyncOrganization() throws IOException {
        var sourceFile = createTestFile("async.mp3");
        var metadata = TrackMetadata.builder()
            .title("Async Song")
            .artist("Async Artist")
            .album("Async Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        var processedFiles = new ArrayList<Path>();
        
        var future = organizer.organizeFilesAsync(fileMetadata, processedFiles::add);
        var result = future.join();
        
        assertNotNull(result);
        assertEquals(1, result.successful().size());
        assertTrue(result.processingTime().toMillis() >= 0);
    }
    
    @Test
    @Order(9)
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
    @Order(10)
    @DisplayName("Should call progress tracker correctly")
    void shouldCallProgressTracker() throws IOException {
        var sourceFile = createTestFile("progress.mp3");
        var metadata = TrackMetadata.builder()
            .title("Progress Song")
            .artist("Progress Artist")
            .album("Progress Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        organizer.organizeFiles(fileMetadata, null);
        
        // Verify progress tracker was called
        Mockito.verify(mockProgressTracker).startOperation(Mockito.eq("file_organization"), Mockito.eq(1));
        Mockito.verify(mockProgressTracker).updateProgress(Mockito.eq("file_organization"), Mockito.eq(1));
        Mockito.verify(mockProgressTracker).completeOperation(Mockito.eq("file_organization"));
    }
    
    @Test
    @Order(11)
    @DisplayName("Should handle organization with disabled directory creation")
    void shouldHandleDisabledDirectoryCreation() throws IOException {
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
    @Order(12)
    @DisplayName("Should create target directory on construction")
    void shouldCreateTargetDirectoryOnConstruction() throws IOException {
        var newTargetDir = fileSystem.getPath("/new_target");
        assertFalse(Files.exists(newTargetDir));
        
        var config = OrganizationConfig.defaultConfig(newTargetDir);
        new NIOFileOrganizer(config, mockProgressTracker);
        
        assertTrue(Files.exists(newTargetDir));
    }
    
    @Test
    @Order(13)
    @DisplayName("Should measure processing time")
    void shouldMeasureProcessingTime() throws IOException {
        var sourceFile = createTestFile("timing.mp3");
        var metadata = TrackMetadata.builder()
            .title("Timing Song")
            .artist("Timing Artist")
            .album("Timing Album")
            .build();
        
        var fileMetadata = Map.of(sourceFile, metadata);
        
        var result = organizer.organizeFiles(fileMetadata, null);
        
        assertNotNull(result.processingTime());
        assertTrue(result.processingTime().toNanos() > 0);
    }
    
    @Test
    @Order(14)
    @DisplayName("Should handle rollback operations")
    void shouldHandleRollbackOperations() throws IOException {
        // Test rollback functionality
        var rollbackFuture = organizer.rollbackAllAsync();
        var rollbackCount = rollbackFuture.join();
        
        assertTrue(rollbackCount >= 0);
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
     * Test configuration record
     */
    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {
        
        @Test
        @DisplayName("Should create default configuration")
        void shouldCreateDefaultConfiguration() {
            var config = OrganizationConfig.defaultConfig(targetDirectory);
            
            assertEquals(targetDirectory, config.targetDirectory());
            assertTrue(config.createDirectoryStructure());
            assertTrue(config.preserveOriginalOnError());
            assertTrue(config.enableRollback());
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
        }
    }
}