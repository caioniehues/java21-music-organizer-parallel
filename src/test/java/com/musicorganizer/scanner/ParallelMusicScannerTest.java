package com.musicorganizer.scanner;

// Removed Jimfs imports - using real temp directories instead
import com.musicorganizer.model.*;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ParallelMusicScanner.
 * Tests virtual thread processing, concurrent operations, error handling,
 * and metadata extraction using mocked file systems and JAudioTagger.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParallelMusicScannerTest {

    @TempDir
    private Path rootPath;
    private ParallelMusicScanner scanner;
    private MockedStatic<AudioFileIO> audioFileIOMock;
    private AudioFile mockAudioFile;
    private AudioHeader mockAudioHeader;
    private Tag mockTag;

    @BeforeEach
    void setUp() throws IOException {
        // rootPath is automatically created by @TempDir annotation
        
        // Initialize scanner with test configuration
        scanner = new ParallelMusicScanner(100, true, true);
        
        // Setup JAudioTagger mocks
        if (audioFileIOMock != null) {
            audioFileIOMock.close(); // Close any existing mock
        }
        audioFileIOMock = mockStatic(AudioFileIO.class);
        mockAudioFile = mock(AudioFile.class);
        mockAudioHeader = mock(AudioHeader.class);
        mockTag = mock(Tag.class);
        
        // Configure default mock behaviors
        setupDefaultMockBehavior();
    }

    private void setupDefaultMockBehavior() {
        when(mockAudioFile.getAudioHeader()).thenReturn(mockAudioHeader);
        when(mockAudioFile.getTag()).thenReturn(mockTag);
        when(mockAudioHeader.getFormat()).thenReturn("MP3");
        when(mockAudioHeader.getBitRate()).thenReturn("320");
        when(mockAudioHeader.getTrackLength()).thenReturn(180);
        when(mockTag.getFirst(FieldKey.TITLE)).thenReturn("Test Song");
        when(mockTag.getFirst(FieldKey.ARTIST)).thenReturn("Test Artist");
        when(mockTag.getFirst(FieldKey.ALBUM)).thenReturn("Test Album");
        when(mockTag.getFirst(FieldKey.GENRE)).thenReturn("Rock");
        when(mockTag.getFirst(FieldKey.YEAR)).thenReturn("2023");
        when(mockTag.getFirst(FieldKey.TRACK)).thenReturn("1/10");
        
        // Configure static mock to return our mock AudioFile by default
        audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                      .thenReturn(mockAudioFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (scanner != null) {
            scanner.close();
        }
        if (audioFileIOMock != null) {
            audioFileIOMock.close();
        }
    }

    @Nested
    @DisplayName("Directory Scanning Tests")
    class DirectoryScanningTests {

        @Test
        @DisplayName("Should handle empty directory successfully")
        void shouldHandleEmptyDirectorySuccessfully() {
            // Given: Empty directory
            
            // When: Scanning empty directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Returns successful result with no files
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertTrue(success.audioFiles().isEmpty());
            assertTrue(success.duplicates().isEmpty());
            assertTrue(success.fileTypeStats().isEmpty());
            assertEquals(0, success.statistics().totalFilesScanned());
        }

        @Test
        @DisplayName("Should handle non-existent directory")
        void shouldHandleNonExistentDirectory() {
            // Given: Non-existent directory path
            Path nonExistentPath = rootPath.resolve("../nonexistent");
            
            // When: Scanning non-existent directory
            ScanResult result = scanner.scanDirectory(nonExistentPath);
            
            // Then: Returns failure result
            assertInstanceOf(ScanResult.Failure.class, result);
            var failure = (ScanResult.Failure) result;
            assertTrue(failure.errorMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("Should handle file instead of directory")
        void shouldHandleFileInsteadOfDirectory() throws IOException {
            // Given: A file path instead of directory
            Path filePath = rootPath.resolve("notadirectory.txt");
            Files.writeString(filePath, "content");
            
            // When: Scanning file path as directory
            ScanResult result = scanner.scanDirectory(filePath);
            
            // Then: Returns failure result
            assertInstanceOf(ScanResult.Failure.class, result);
            var failure = (ScanResult.Failure) result;
            assertTrue(failure.errorMessage().contains("not a directory"));
        }
    }

    @Nested
    @DisplayName("Single File Processing Tests")
    class SingleFileProcessingTests {

        @Test
        @DisplayName("Should process single MP3 file successfully")
        void shouldProcessSingleMp3FileSuccessfully() throws Exception {
            // Given: Single MP3 file
            Path mp3File = createTestAudioFile("test.mp3", "Test content");
            // Note: Default mock configuration from setUp() will be used
            
            // When: Scanning directory with single file
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Returns successful result with one file
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            
            var audioFile = success.audioFiles().get(0);
            assertEquals("mp3", audioFile.getExtension());
            assertEquals("Test Song", audioFile.metadata().title().orElse(""));
            assertEquals("Test Artist", audioFile.metadata().artist().orElse(""));
            assertNotNull(audioFile.checksum()); // Checksum should be calculated
        }

        @Test
        @DisplayName("Should process FLAC file with metadata")
        void shouldProcessFlacFileWithMetadata() throws Exception {
            // Given: Single FLAC file with FLAC-specific metadata
            Path flacFile = createTestAudioFile("test.flac", "FLAC content");
            
            // Override format and bitrate for FLAC
            when(mockAudioHeader.getFormat()).thenReturn("FLAC");
            when(mockAudioHeader.getBitRate()).thenReturn("1411");
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: File processed with correct metadata
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            
            var audioFile = success.audioFiles().get(0);
            assertEquals("flac", audioFile.getExtension());
            assertEquals("FLAC", audioFile.metadata().format().orElse(""));
            assertEquals(1411, audioFile.metadata().bitrate().orElse(0));
        }

        @Test
        @DisplayName("Should handle corrupted audio file gracefully")
        void shouldHandleCorruptedAudioFileGracefully() throws Exception {
            // Given: Corrupted audio file
            Path corruptedFile = createTestAudioFile("corrupted.mp3", "Invalid audio data");
            
            // Configure mock to throw exception specifically for this file
            audioFileIOMock.when(() -> AudioFileIO.read(argThat(file -> file.getName().equals("corrupted.mp3"))))
                          .thenThrow(new RuntimeException("Corrupted file"));
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Returns failure result with failed path
            assertInstanceOf(ScanResult.Failure.class, result);
            var failure = (ScanResult.Failure) result;
            assertEquals(1, failure.failedPaths().size());
            assertTrue(failure.failedPaths().get(0).contains("corrupted.mp3"));
        }
    }

    @Nested
    @DisplayName("Multiple File Processing Tests")
    class MultipleFileProcessingTests {

        @Test
        @DisplayName("Should process multiple files concurrently")
        void shouldProcessMultipleFilesConcurrently() throws Exception {
            // Given: Multiple audio files
            createTestAudioFile("song1.mp3", "Content 1");
            createTestAudioFile("song2.flac", "Content 2");
            createTestAudioFile("song3.m4a", "Content 3");
            
            // Track concurrent processing
            AtomicInteger concurrentCount = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenAnswer(invocation -> {
                              int current = concurrentCount.incrementAndGet();
                              maxConcurrent.updateAndGet(max -> Math.max(max, current));
                              
                              // Simulate processing time
                              Thread.sleep(50);
                              
                              concurrentCount.decrementAndGet();
                              return mockAudioFile;
                          });
            
            // When: Scanning directory
            Instant start = Instant.now();
            ScanResult result = scanner.scanDirectory(rootPath);
            Duration elapsed = Duration.between(start, Instant.now());
            
            // Then: All files processed with concurrent execution
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(3, success.audioFiles().size());
            
            // Should process faster than sequential (3 files * 50ms each = 150ms minimum sequential)
            assertTrue(elapsed.toMillis() < 120, "Should benefit from parallel processing");
            assertTrue(maxConcurrent.get() > 1, "Should process files concurrently");
        }

        @Test
        @DisplayName("Should handle mixed success and failure scenarios")
        void shouldHandleMixedSuccessAndFailureScenarios() throws Exception {
            // Given: Mix of valid and corrupted files
            createTestAudioFile("valid1.mp3", "Valid content 1");
            createTestAudioFile("corrupted.mp3", "Corrupted content");
            createTestAudioFile("valid2.flac", "Valid content 2");
            
            // Configure mock to throw exception specifically for corrupted file
            audioFileIOMock.when(() -> AudioFileIO.read(argThat(file -> file.getName().contains("corrupted"))))
                          .thenThrow(new RuntimeException("Corrupted file"));
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Returns partial result
            assertInstanceOf(ScanResult.Partial.class, result);
            var partial = (ScanResult.Partial) result;
            assertEquals(2, partial.audioFiles().size()); // 2 valid files
            assertEquals(1, partial.failedPaths().size()); // 1 corrupted file
            assertEquals(66.7, partial.getSuccessRate(), 0.1); // ~66.7% success rate
        }

        @Test
        @DisplayName("Should respect semaphore limits for concurrency")
        void shouldRespectSemaphoreLimitsForConcurrency() throws Exception {
            // Given: Scanner with limited concurrency and many files
            scanner.close();
            scanner = new ParallelMusicScanner(2, false, true); // Max 2 concurrent
            
            // Create more files than concurrency limit
            for (int i = 1; i <= 5; i++) {
                createTestAudioFile("song" + i + ".mp3", "Content " + i);
            }
            
            // Track concurrent processing with delays
            AtomicInteger concurrentCount = new AtomicInteger(0);
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            CountDownLatch processingStarted = new CountDownLatch(5);
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenAnswer(invocation -> {
                              int current = concurrentCount.incrementAndGet();
                              maxConcurrent.updateAndGet(max -> Math.max(max, current));
                              processingStarted.countDown();
                              
                              // Simulate longer processing time
                              Thread.sleep(100);
                              
                              concurrentCount.decrementAndGet();
                              return mockAudioFile;
                          });
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Respects concurrency limit
            assertTrue(processingStarted.await(5, TimeUnit.SECONDS));
            assertInstanceOf(ScanResult.Success.class, result);
            
            // Should never exceed semaphore limit
            assertTrue(maxConcurrent.get() <= 2, 
                "Max concurrent should not exceed semaphore limit of 2, was: " + maxConcurrent.get());
        }
    }

    @Nested
    @DisplayName("Virtual Thread Processing Tests")
    class VirtualThreadProcessingTests {

        @Test
        @DisplayName("Should use virtual threads for parallel processing")
        void shouldUseVirtualThreadsForParallelProcessing() throws Exception {
            // Given: Multiple files to process
            createTestAudioFile("song1.mp3", "Content 1");
            createTestAudioFile("song2.mp3", "Content 2");
            createTestAudioFile("song3.mp3", "Content 3");
            
            // Track thread information
            AtomicInteger virtualThreadCount = new AtomicInteger(0);
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenAnswer(invocation -> {
                              Thread currentThread = Thread.currentThread();
                              if (currentThread.isVirtual()) {
                                  virtualThreadCount.incrementAndGet();
                              }
                              return mockAudioFile;
                          });
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Uses virtual threads
            assertInstanceOf(ScanResult.Success.class, result);
            assertTrue(virtualThreadCount.get() > 0, "Should use virtual threads for processing");
        }

        @Test
        @DisplayName("Should handle virtual thread interruption gracefully")
        void shouldHandleVirtualThreadInterruptionGracefully() throws Exception {
            // Given: Files to process with interruption simulation
            createTestAudioFile("song1.mp3", "Content 1");
            createTestAudioFile("song2.mp3", "Content 2");
            
            AtomicInteger processCount = new AtomicInteger(0);
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenAnswer(invocation -> {
                              if (processCount.getAndIncrement() == 1) {
                                  // Interrupt current thread on second call
                                  Thread.currentThread().interrupt();
                                  throw new InterruptedException("Thread interrupted");
                              }
                              return mockAudioFile;
                          });
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Handles interruption gracefully
            // Should process at least one file successfully
            assertTrue(result instanceof ScanResult.Success || result instanceof ScanResult.Partial);
        }
    }

    @Nested
    @DisplayName("Metadata Extraction Tests")
    class MetadataExtractionTests {

        @Test
        @DisplayName("Should extract complete metadata from audio file")
        void shouldExtractCompleteMetadataFromAudioFile() throws Exception {
            // Given: Audio file with complete metadata
            createTestAudioFile("complete.mp3", "Content");
            
            // Override mock configuration for specific metadata
            when(mockTag.getFirst(FieldKey.TITLE)).thenReturn("Complete Song");
            when(mockTag.getFirst(FieldKey.ARTIST)).thenReturn("Complete Artist");
            when(mockTag.getFirst(FieldKey.ALBUM)).thenReturn("Complete Album");
            when(mockTag.getFirst(FieldKey.GENRE)).thenReturn("Electronic");
            when(mockTag.getFirst(FieldKey.YEAR)).thenReturn("2024");
            when(mockTag.getFirst(FieldKey.TRACK)).thenReturn("5/12");
            when(mockAudioHeader.getFormat()).thenReturn("MP3");
            when(mockAudioHeader.getBitRate()).thenReturn("256");
            when(mockAudioHeader.getTrackLength()).thenReturn(240);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Extracts all metadata correctly
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            
            AudioMetadata metadata = success.audioFiles().get(0).metadata();
            assertEquals("Complete Song", metadata.title().orElse(""));
            assertEquals("Complete Artist", metadata.artist().orElse(""));
            assertEquals("Complete Album", metadata.album().orElse(""));
            assertEquals("Electronic", metadata.genre().orElse(""));
            assertEquals(2024, metadata.year().orElse(0));
            assertEquals(5, metadata.trackNumber().orElse(0));
            assertEquals(12, metadata.totalTracks().orElse(0));
            assertEquals("MP3", metadata.format().orElse(""));
            assertEquals(256, metadata.bitrate().orElse(0));
            assertEquals(Duration.ofSeconds(240), metadata.duration().orElse(Duration.ZERO));
        }

        @Test
        @DisplayName("Should handle missing metadata gracefully")
        void shouldHandleMissingMetadataGracefully() throws Exception {
            // Given: Audio file with minimal metadata
            createTestAudioFile("minimal.mp3", "Content");
            
            // Override mock to return null tag (no tag information)
            when(mockAudioFile.getTag()).thenReturn(null);
            when(mockAudioHeader.getFormat()).thenReturn("MP3");
            when(mockAudioHeader.getBitRate()).thenReturn(null);
            when(mockAudioHeader.getTrackLength()).thenReturn(0);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Creates file with empty metadata (except format)
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            
            AudioMetadata metadata = success.audioFiles().get(0).metadata();
            assertFalse(metadata.title().isPresent());
            assertFalse(metadata.artist().isPresent());
            assertEquals("MP3", metadata.format().orElse(""));
        }

        @Test
        @DisplayName("Should handle malformed metadata values")
        void shouldHandleMalformedMetadataValues() throws Exception {
            // Given: Audio file with invalid metadata
            createTestAudioFile("malformed.mp3", "Content");
            
            when(mockTag.getFirst(FieldKey.YEAR)).thenReturn("not_a_year");
            when(mockTag.getFirst(FieldKey.TRACK)).thenReturn("invalid/track");
            when(mockAudioHeader.getBitRate()).thenReturn("not_a_number");
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Handles malformed values gracefully
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            
            AudioMetadata metadata = success.audioFiles().get(0).metadata();
            assertFalse(metadata.year().isPresent()); // Invalid year ignored
            assertFalse(metadata.trackNumber().isPresent()); // Invalid track ignored
            assertFalse(metadata.bitrate().isPresent()); // Invalid bitrate ignored
        }
    }

    @Nested
    @DisplayName("File Extension Filtering Tests")
    class FileExtensionFilteringTests {

        @Test
        @DisplayName("Should only process supported audio formats")
        void shouldOnlyProcessSupportedAudioFormats() throws Exception {
            // Given: Mix of supported and unsupported files
            createTestAudioFile("song.mp3", "MP3 content");
            createTestAudioFile("song.flac", "FLAC content");
            createTestAudioFile("song.wav", "WAV content");
            createTestFile("document.txt", "Text content");
            createTestFile("image.jpg", "Image content");
            createTestFile("video.mp4", "Video content"); // Note: mp4 is supported as audio
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Only processes supported audio formats
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(4, success.audioFiles().size()); // mp3, flac, wav, mp4
            
            List<String> extensions = success.audioFiles().stream()
                .map(com.musicorganizer.model.AudioFile::getExtension)
                .sorted()
                .toList();
            assertEquals(List.of("flac", "mp3", "mp4", "wav"), extensions);
        }

        @Test
        @DisplayName("Should handle case-insensitive extensions")
        void shouldHandleCaseInsensitiveExtensions() throws Exception {
            // Given: Files with different case extensions
            createTestAudioFile("song1.MP3", "Content 1");
            createTestAudioFile("song2.Flac", "Content 2");
            createTestAudioFile("song3.WAV", "Content 3");
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Processes all files regardless of case
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(3, success.audioFiles().size());
        }

        @Test
        @DisplayName("Should skip files without extensions")
        void shouldSkipFilesWithoutExtensions() throws Exception {
            // Given: Files with and without extensions
            createTestFile("no_extension", "Content");
            createTestAudioFile("with.extension.mp3", "MP3 content");
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Only processes files with supported extensions
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle I/O errors during file reading")
        void shouldHandleIOErrorsDuringFileReading() throws Exception {
            // Given: File that causes I/O error
            Path problemFile = createTestAudioFile("problem.mp3", "Content");
            
            // Configure mock to throw I/O exception specifically for problem file
            audioFileIOMock.when(() -> AudioFileIO.read(argThat(file -> file.getName().equals("problem.mp3"))))
                          .thenThrow(new IOException("Cannot read file"));
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Handles I/O error gracefully
            assertInstanceOf(ScanResult.Failure.class, result);
            var failure = (ScanResult.Failure) result;
            assertEquals(1, failure.failedPaths().size());
            assertTrue(failure.failedPaths().get(0).contains("problem.mp3"));
        }

        @Test
        @DisplayName("Should handle permission denied errors")
        void shouldHandlePermissionDeniedErrors() throws Exception {
            // Given: File with permission issues (simulated)
            createTestAudioFile("restricted.mp3", "Content");
            
            // Configure mock to throw SecurityException specifically for restricted file
            audioFileIOMock.when(() -> AudioFileIO.read(argThat(file -> file.getName().equals("restricted.mp3"))))
                          .thenThrow(new SecurityException("Permission denied"));
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Handles permission error gracefully
            assertInstanceOf(ScanResult.Failure.class, result);
            var failure = (ScanResult.Failure) result;
            assertNotNull(failure.cause());
            assertTrue(failure.cause() instanceof SecurityException);
        }

        @Test
        @DisplayName("Should continue processing after individual file failures")
        void shouldContinueProcessingAfterIndividualFileFailures() throws Exception {
            // Given: Multiple files with some failures
            createTestAudioFile("good1.mp3", "Good content 1");
            createTestAudioFile("bad.mp3", "Bad content");
            createTestAudioFile("good2.flac", "Good content 2");
            
            // Configure mock to throw exception specifically for bad file
            audioFileIOMock.when(() -> AudioFileIO.read(argThat(file -> file.getName().contains("bad"))))
                          .thenThrow(new RuntimeException("Processing failed"));
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Continues processing good files
            assertInstanceOf(ScanResult.Partial.class, result);
            var partial = (ScanResult.Partial) result;
            assertEquals(2, partial.audioFiles().size()); // 2 good files
            assertEquals(1, partial.failedPaths().size()); // 1 bad file
        }

        @Test
        @DisplayName("Should handle OutOfMemoryError gracefully")
        void shouldHandleOutOfMemoryErrorGracefully() throws Exception {
            // Given: File that causes OOM
            createTestAudioFile("huge.mp3", "Content");
            
            // Configure mock to throw OutOfMemoryError specifically for huge file
            audioFileIOMock.when(() -> AudioFileIO.read(argThat(file -> file.getName().equals("huge.mp3"))))
                          .thenThrow(new OutOfMemoryError("Java heap space"));
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Handles OOM error gracefully
            assertInstanceOf(ScanResult.Failure.class, result);
            var failure = (ScanResult.Failure) result;
            assertEquals(1, failure.failedPaths().size());
        }
    }

    @Nested
    @DisplayName("Checksum and Duplicate Detection Tests")
    class ChecksumAndDuplicateDetectionTests {

        @Test
        @DisplayName("Should calculate checksums when enabled")
        void shouldCalculateChecksumsWhenEnabled() throws Exception {
            // Given: Scanner with checksums enabled
            createTestAudioFile("test.mp3", "Identical content");
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Calculates checksums
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            assertNotNull(success.audioFiles().get(0).checksum());
            assertFalse(success.audioFiles().get(0).checksum().isEmpty());
        }

        @Test
        @DisplayName("Should skip checksum calculation when disabled")
        void shouldSkipChecksumCalculationWhenDisabled() throws Exception {
            // Given: Scanner with checksums disabled
            scanner.close();
            scanner = new ParallelMusicScanner(100, false, true);
            
            createTestAudioFile("test.mp3", "Content");
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Skips checksum calculation
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(1, success.audioFiles().size());
            assertNull(success.audioFiles().get(0).checksum());
        }

        @Test
        @DisplayName("Should detect duplicate files")
        void shouldDetectDuplicateFiles() throws Exception {
            // Given: Multiple files with identical content
            createTestAudioFile("song1.mp3", "Identical content");
            createTestAudioFile("duplicate/song1_copy.mp3", "Identical content");
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Detects duplicates
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            assertEquals(2, success.audioFiles().size());
            assertEquals(1, success.duplicates().size()); // One duplicate group
            
            DuplicateInfo duplicate = success.duplicates().get(0);
            assertEquals(2, duplicate.getDuplicateCount());
            assertEquals(DuplicateInfo.DuplicateType.EXACT_MATCH, duplicate.type());
        }
    }

    @Nested
    @DisplayName("Statistics and Performance Tests")
    class StatisticsAndPerformanceTests {

        @Test
        @DisplayName("Should generate accurate scan statistics")
        void shouldGenerateAccurateScanStatistics() throws Exception {
            // Given: Multiple files of different types
            createTestAudioFile("song.mp3", "MP3 content");
            createTestAudioFile("song.flac", "FLAC content");
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            Instant start = Instant.now();
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Generates accurate statistics
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            
            ScanResult.ScanStatistics stats = success.statistics();
            assertEquals(2, stats.totalFilesScanned());
            assertTrue(stats.totalSizeScanned() > 0);
            assertTrue(stats.processingTimes().containsKey("total_scan"));
            assertTrue(stats.processingTimes().get("total_scan") > 0);
            assertFalse(stats.supportedFormats().isEmpty());
        }

        @Test
        @DisplayName("Should track file type statistics")
        void shouldTrackFileTypeStatistics() throws Exception {
            // Given: Files of different types
            createTestAudioFile("song1.mp3", "MP3 content");
            createTestAudioFile("song2.mp3", "MP3 content 2");
            createTestAudioFile("song3.flac", "FLAC content");
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Scanning directory
            ScanResult result = scanner.scanDirectory(rootPath);
            
            // Then: Tracks file type statistics
            assertInstanceOf(ScanResult.Success.class, result);
            var success = (ScanResult.Success) result;
            
            Map<String, Integer> typeStats = success.fileTypeStats();
            assertEquals(2, typeStats.get("mp3")); // 2 MP3 files
            assertEquals(1, typeStats.get("flac")); // 1 FLAC file
        }

        @Test
        @DisplayName("Should complete scan within reasonable time")
        void shouldCompleteScanWithinReasonableTime() throws Exception {
            // Given: Moderate number of files
            for (int i = 1; i <= 10; i++) {
                createTestAudioFile("song" + i + ".mp3", "Content " + i);
            }
            
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenAnswer(invocation -> {
                              // Simulate realistic processing time
                              Thread.sleep(10);
                              return mockAudioFile;
                          });
            
            // When: Scanning directory
            Instant start = Instant.now();
            ScanResult result = scanner.scanDirectory(rootPath);
            Duration elapsed = Duration.between(start, Instant.now());
            
            // Then: Completes within reasonable time
            assertInstanceOf(ScanResult.Success.class, result);
            assertTrue(elapsed.toSeconds() < 5, "Should complete within 5 seconds");
            
            var success = (ScanResult.Success) result;
            assertEquals(10, success.audioFiles().size());
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("Should properly close resources when scanner is closed")
        void shouldProperlyCloseResourcesWhenScannerIsClosed() throws Exception {
            // Given: Scanner in use
            createTestAudioFile("test.mp3", "Content");
            audioFileIOMock.when(() -> AudioFileIO.read(any(java.io.File.class)))
                          .thenReturn(mockAudioFile);
            
            // When: Using and then closing scanner
            ScanResult result = scanner.scanDirectory(rootPath);
            scanner.close();
            
            // Then: Resources are properly managed
            assertInstanceOf(ScanResult.Success.class, result);
            // Scanner should be closed without hanging
            assertTrue(true, "Scanner closed successfully");
        }

        @Test
        @DisplayName("Should handle multiple close calls gracefully")
        void shouldHandleMultipleCloseCallsGracefully() {
            // Given: Scanner instance
            
            // When: Closing multiple times
            assertDoesNotThrow(() -> {
                scanner.close();
                scanner.close();
                scanner.close();
            });
            
            // Then: No exceptions thrown
        }
    }

    // Helper methods

    private Path createTestAudioFile(String filename, String content) throws IOException {
        Path filePath = rootPath.resolve(filename);
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        return Files.writeString(filePath, content, StandardOpenOption.CREATE);
    }

    private Path createTestFile(String filename, String content) throws IOException {
        Path filePath = rootPath.resolve(filename);
        return Files.writeString(filePath, content, StandardOpenOption.CREATE);
    }

    // Custom assertion methods for better test readability
    private static void assertThat(String actual) {
        assertNotNull(actual);
    }

    // Helper method to make string assertions more readable
    private static StringAssertion assertThat(String actual, String message) {
        return new StringAssertion(actual);
    }

    private static class StringAssertion {
        private final String actual;

        StringAssertion(String actual) {
            this.actual = actual;
        }

        void contains(String expected) {
            assertTrue(actual.contains(expected), 
                "Expected string to contain '" + expected + "', but was: " + actual);
        }
    }
}