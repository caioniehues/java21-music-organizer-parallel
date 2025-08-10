package com.musicorganizer.processor;

import com.musicorganizer.model.*;
import com.musicorganizer.processor.ConcurrentDuplicateFinder.DuplicateAnalysisResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive test suite for ConcurrentDuplicateFinder.
 * Tests concurrent processing, virtual threads, and various duplicate detection scenarios.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentDuplicateFinderTest {

    private ConcurrentDuplicateFinder finder;
    private List<AudioFile> testAudioFiles;

    @BeforeEach
    void setUp() {
        finder = new ConcurrentDuplicateFinder();
        testAudioFiles = new ArrayList<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (finder != null) {
            finder.close();
        }
    }

    @Nested
    @DisplayName("Constructor and Configuration Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create finder with default configuration")
        void shouldCreateFinderWithDefaults() {
            try (var defaultFinder = new ConcurrentDuplicateFinder()) {
                assertNotNull(defaultFinder);
            }
        }

        @Test
        @DisplayName("Should create finder with custom configuration")
        void shouldCreateFinderWithCustomConfig() {
            try (var customFinder = new ConcurrentDuplicateFinder(false, 0.9)) {
                assertNotNull(customFinder);
            }
        }

        @Test
        @DisplayName("Should handle edge case similarity thresholds")
        void shouldHandleEdgeCaseThresholds() {
            try (var finder1 = new ConcurrentDuplicateFinder(true, 0.0);
                 var finder2 = new ConcurrentDuplicateFinder(true, 1.0)) {
                assertNotNull(finder1);
                assertNotNull(finder2);
            }
        }
    }

    @Nested
    @DisplayName("Empty and Null Input Tests")
    class EmptyInputTests {

        @Test
        @DisplayName("Should handle empty audio file list")
        void shouldHandleEmptyList() {
            DuplicateAnalysisResult result = finder.findDuplicates(Collections.emptyList());

            assertNotNull(result);
            assertTrue(result.exactDuplicates().isEmpty());
            assertTrue(result.metadataDuplicates().isEmpty());
            assertTrue(result.sizeDuplicates().isEmpty());
            assertEquals(0, result.getTotalDuplicateFiles());
        }

        @Test
        @DisplayName("Should handle single audio file")
        void shouldHandleSingleFile() {
            AudioFile singleFile = createTestAudioFile("song1.mp3", 1024L, "checksum1", 
                createMetadata("Song 1", "Artist 1", "Album 1"));
            
            DuplicateAnalysisResult result = finder.findDuplicates(List.of(singleFile));

            assertNotNull(result);
            assertTrue(result.exactDuplicates().isEmpty());
            assertTrue(result.metadataDuplicates().isEmpty());
            assertTrue(result.sizeDuplicates().isEmpty());
            assertEquals(0, result.getTotalDuplicateFiles());
        }

        @Test
        @DisplayName("Should handle null checksums gracefully")
        void shouldHandleNullChecksums() {
            List<AudioFile> filesWithNullChecksums = List.of(
                createTestAudioFile("song1.mp3", 1024L, null, createMetadata("Song", "Artist", "Album")),
                createTestAudioFile("song2.mp3", 1024L, null, createMetadata("Song", "Artist", "Album"))
            );

            DuplicateAnalysisResult result = finder.findDuplicates(filesWithNullChecksums);

            assertNotNull(result);
            assertTrue(result.exactDuplicates().isEmpty()); // Can't find exact duplicates without checksums
        }
    }

    @Nested
    @DisplayName("Exact Duplicate Detection Tests")
    class ExactDuplicateTests {

        @Test
        @DisplayName("Should find exact duplicates by checksum")
        void shouldFindExactDuplicatesByChecksum() {
            AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "abc123", 
                createMetadata("Song", "Artist", "Album"));
            AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "abc123", 
                createMetadata("Different", "Different", "Different"));
            AudioFile file3 = createTestAudioFile("song3.mp3", 1024L, "xyz789", 
                createMetadata("Song", "Artist", "Album"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2, file3));

            assertEquals(1, result.exactDuplicates().size());
            DuplicateInfo duplicate = result.exactDuplicates().get(0);
            assertEquals("abc123", duplicate.checksum());
            assertEquals(2, duplicate.getDuplicateCount());
            assertEquals(DuplicateInfo.DuplicateType.EXACT_MATCH, duplicate.type());
            assertTrue(duplicate.duplicates().contains(file1));
            assertTrue(duplicate.duplicates().contains(file2));
        }

        @Test
        @DisplayName("Should find multiple exact duplicate groups")
        void shouldFindMultipleExactDuplicateGroups() {
            AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "abc123", createMetadata("A", "B", "C"));
            AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "abc123", createMetadata("A", "B", "C"));
            AudioFile file3 = createTestAudioFile("song3.mp3", 2048L, "xyz789", createMetadata("X", "Y", "Z"));
            AudioFile file4 = createTestAudioFile("song4.mp3", 2048L, "xyz789", createMetadata("X", "Y", "Z"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2, file3, file4));

            assertEquals(2, result.exactDuplicates().size());
            
            Map<String, DuplicateInfo> duplicatesByChecksum = new HashMap<>();
            for (DuplicateInfo duplicate : result.exactDuplicates()) {
                duplicatesByChecksum.put(duplicate.checksum(), duplicate);
            }

            assertTrue(duplicatesByChecksum.containsKey("abc123"));
            assertTrue(duplicatesByChecksum.containsKey("xyz789"));
            assertEquals(2, duplicatesByChecksum.get("abc123").getDuplicateCount());
            assertEquals(2, duplicatesByChecksum.get("xyz789").getDuplicateCount());
        }

        @Test
        @DisplayName("Should ignore files with empty checksums")
        void shouldIgnoreEmptyChecksums() {
            AudioFile fileWithChecksum = createTestAudioFile("song1.mp3", 1024L, "abc123", 
                createMetadata("Song", "Artist", "Album"));
            AudioFile fileWithEmptyChecksum = createTestAudioFile("song2.mp3", 1024L, "", 
                createMetadata("Song", "Artist", "Album"));
            AudioFile fileWithNullChecksum = createTestAudioFile("song3.mp3", 1024L, null, 
                createMetadata("Song", "Artist", "Album"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(fileWithChecksum, fileWithEmptyChecksum, fileWithNullChecksum));

            assertTrue(result.exactDuplicates().isEmpty());
        }
    }

    @Nested
    @DisplayName("Metadata Duplicate Detection Tests")
    class MetadataDuplicateTests {

        @Test
        @DisplayName("Should find metadata duplicates with similar metadata")
        void shouldFindMetadataDuplicates() {
            try (var metadataFinder = new ConcurrentDuplicateFinder(true, 0.8)) {
                AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "checksum1", 
                    createMetadata("Hotel California", "Eagles", "Hotel California", 2005, 1));
                AudioFile file2 = createTestAudioFile("song2.mp3", 2048L, "checksum2", 
                    createMetadata("Hotel California", "Eagles", "Hotel California", 2005, 1));

                DuplicateAnalysisResult result = metadataFinder.findDuplicates(List.of(file1, file2));

                assertFalse(result.metadataDuplicates().isEmpty());
                DuplicateInfo duplicate = result.metadataDuplicates().get(0);
                assertEquals(2, duplicate.getDuplicateCount());
                assertEquals(DuplicateInfo.DuplicateType.METADATA_MATCH, duplicate.type());
            }
        }

        @Test
        @DisplayName("Should not find metadata duplicates when disabled")
        void shouldNotFindMetadataDuplicatesWhenDisabled() {
            try (var noMetadataFinder = new ConcurrentDuplicateFinder(false, 0.8)) {
                AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "checksum1", 
                    createMetadata("Same Song", "Same Artist", "Same Album"));
                AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "checksum2", 
                    createMetadata("Same Song", "Same Artist", "Same Album"));

                DuplicateAnalysisResult result = noMetadataFinder.findDuplicates(List.of(file1, file2));

                assertTrue(result.metadataDuplicates().isEmpty());
            }
        }

        @Test
        @DisplayName("Should handle files with no metadata")
        void shouldHandleFilesWithNoMetadata() {
            AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "checksum1", AudioMetadata.empty());
            AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "checksum2", AudioMetadata.empty());

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));

            assertTrue(result.metadataDuplicates().isEmpty());
        }

        @Test
        @DisplayName("Should respect similarity threshold")
        void shouldRespectSimilarityThreshold() {
            try (var strictFinder = new ConcurrentDuplicateFinder(true, 0.9)) {
                // Files with 60% similarity (3 out of 5 fields match)
                AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "checksum1", 
                    createMetadata("Same Title", "Same Artist", "Same Album", 2020, 1));
                AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "checksum2", 
                    createMetadata("Same Title", "Same Artist", "Different Album", 2021, 2));

                DuplicateAnalysisResult result = strictFinder.findDuplicates(List.of(file1, file2));

                // Should not find duplicates with 0.9 threshold but only 0.6 similarity
                assertTrue(result.metadataDuplicates().isEmpty());
            }
        }

        @Test
        @DisplayName("Should normalize metadata for comparison")
        void shouldNormalizeMetadataForComparison() {
            AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "checksum1", 
                createMetadata("Hotel California", "The Eagles", "Hotel California"));
            AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "checksum2", 
                createMetadata("HOTEL CALIFORNIA", "the eagles", "hotel california"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));

            assertFalse(result.metadataDuplicates().isEmpty());
        }
    }

    @Nested
    @DisplayName("Size-based Duplicate Detection Tests")
    class SizeDuplicateTests {

        @Test
        @DisplayName("Should find size-based duplicates")
        void shouldFindSizeDuplicates() {
            long largeSize = 2 * 1024 * 1024; // 2MB
            AudioFile file1 = createTestAudioFile("song1.mp3", largeSize, "checksum1", 
                createMetadata("Song 1", "Artist 1", "Album 1"));
            AudioFile file2 = createTestAudioFile("song2.mp3", largeSize, "checksum2", 
                createMetadata("Song 2", "Artist 2", "Album 2"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));

            assertEquals(1, result.sizeDuplicates().size());
            DuplicateInfo duplicate = result.sizeDuplicates().get(0);
            assertEquals(2, duplicate.getDuplicateCount());
            assertEquals(DuplicateInfo.DuplicateType.METADATA_MATCH, duplicate.type()); // Size duplicates use METADATA_MATCH type
            assertTrue(duplicate.checksum().startsWith("size:"));
        }

        @Test
        @DisplayName("Should ignore small files for size duplicates")
        void shouldIgnoreSmallFiles() {
            long smallSize = 1024; // 1KB
            AudioFile file1 = createTestAudioFile("song1.mp3", smallSize, "checksum1", createMetadata("A", "B", "C"));
            AudioFile file2 = createTestAudioFile("song2.mp3", smallSize, "checksum2", createMetadata("X", "Y", "Z"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));

            assertTrue(result.sizeDuplicates().isEmpty());
        }

        @Test
        @DisplayName("Should find multiple size duplicate groups")
        void shouldFindMultipleSizeDuplicateGroups() {
            long size1 = 2 * 1024 * 1024; // 2MB
            long size2 = 3 * 1024 * 1024; // 3MB
            
            AudioFile file1 = createTestAudioFile("song1.mp3", size1, "checksum1", createMetadata("A", "B", "C"));
            AudioFile file2 = createTestAudioFile("song2.mp3", size1, "checksum2", createMetadata("D", "E", "F"));
            AudioFile file3 = createTestAudioFile("song3.mp3", size2, "checksum3", createMetadata("G", "H", "I"));
            AudioFile file4 = createTestAudioFile("song4.mp3", size2, "checksum4", createMetadata("J", "K", "L"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2, file3, file4));

            assertEquals(2, result.sizeDuplicates().size());
        }
    }

    @Nested
    @DisplayName("Concurrent Processing Tests")
    class ConcurrentProcessingTests {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        @DisplayName("Should process files concurrently with virtual threads")
        void shouldProcessConcurrentlyWithVirtualThreads() {
            // Create a large number of files to test concurrent processing
            List<AudioFile> manyFiles = IntStream.range(0, 100)
                .mapToObj(i -> createTestAudioFile("song" + i + ".mp3", 1024L, "checksum" + i, 
                    createMetadata("Song " + i, "Artist " + i, "Album " + i)))
                .toList();

            long startTime = System.currentTimeMillis();
            DuplicateAnalysisResult result = finder.findDuplicates(manyFiles);
            long endTime = System.currentTimeMillis();

            assertNotNull(result);
            assertTrue(endTime - startTime < 5000); // Should complete in reasonable time
        }

        @Test
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        @DisplayName("Should handle concurrent async operations")
        void shouldHandleConcurrentAsyncOperations() {
            List<AudioFile> files = List.of(
                createTestAudioFile("song1.mp3", 1024L, "abc123", createMetadata("Song", "Artist", "Album")),
                createTestAudioFile("song2.mp3", 1024L, "abc123", createMetadata("Song", "Artist", "Album"))
            );

            CompletableFuture<DuplicateAnalysisResult> future = finder.findDuplicatesAsync(files);
            
            assertNotNull(future);
            DuplicateAnalysisResult result = future.join();
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should handle multiple concurrent finders")
        void shouldHandleMultipleConcurrentFinders() throws Exception {
            List<AudioFile> files = List.of(
                createTestAudioFile("song1.mp3", 1024L, "abc123", createMetadata("Song", "Artist", "Album")),
                createTestAudioFile("song2.mp3", 1024L, "abc123", createMetadata("Song", "Artist", "Album"))
            );

            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(5);
            List<CompletableFuture<DuplicateAnalysisResult>> futures = new ArrayList<>();

            try {
                // Create multiple finders running concurrently
                for (int i = 0; i < 5; i++) {
                    CompletableFuture<DuplicateAnalysisResult> future = CompletableFuture.supplyAsync(() -> {
                        try (var concurrentFinder = new ConcurrentDuplicateFinder()) {
                            latch.countDown();
                            return concurrentFinder.findDuplicates(files);
                        }
                    }, executor);
                    futures.add(future);
                }

                // Wait for all to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // Verify all results
                for (CompletableFuture<DuplicateAnalysisResult> future : futures) {
                    DuplicateAnalysisResult result = future.get();
                    assertNotNull(result);
                    assertEquals(1, result.exactDuplicates().size());
                }

            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }

        @Test
        @DisplayName("Should handle exception in async processing")
        void shouldHandleAsyncException() {
            // Create a scenario that could cause an exception
            List<AudioFile> files = null;

            CompletableFuture<DuplicateAnalysisResult> future = finder.findDuplicatesAsync(files);
            
            assertThrows(RuntimeException.class, future::join);
        }
    }

    @Nested
    @DisplayName("Deduplication and Priority Tests")
    class DeduplicationTests {

        @Test
        @DisplayName("Should prioritize exact duplicates over metadata duplicates")
        void shouldPrioritizeExactOverMetadata() {
            AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "abc123", 
                createMetadata("Same Song", "Same Artist", "Same Album"));
            AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "abc123", 
                createMetadata("Same Song", "Same Artist", "Same Album"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));

            // Should find both exact and metadata duplicates, but deduplication should prefer exact
            assertFalse(result.exactDuplicates().isEmpty());
            
            List<DuplicateInfo> allDuplicates = result.getAllDuplicates();
            assertEquals(1, allDuplicates.size()); // Should be deduplicated to one group
            assertEquals(DuplicateInfo.DuplicateType.EXACT_MATCH, allDuplicates.get(0).type());
        }

        @Test
        @DisplayName("Should prioritize metadata duplicates over size duplicates")
        void shouldPrioritizeMetadataOverSize() {
            long largeSize = 2 * 1024 * 1024; // 2MB
            AudioFile file1 = createTestAudioFile("song1.mp3", largeSize, "checksum1", 
                createMetadata("Same Song", "Same Artist", "Same Album"));
            AudioFile file2 = createTestAudioFile("song2.mp3", largeSize, "checksum2", 
                createMetadata("Same Song", "Same Artist", "Same Album"));

            DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));

            // Should find both metadata and size duplicates
            assertFalse(result.metadataDuplicates().isEmpty());
            assertFalse(result.sizeDuplicates().isEmpty());

            List<DuplicateInfo> allDuplicates = result.getAllDuplicates();
            // After deduplication, should prefer metadata match
            assertEquals(1, allDuplicates.size());
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should generate accurate statistics")
        void shouldGenerateAccurateStatistics() {
            List<AudioFile> files = List.of(
                createTestAudioFile("song1.mp3", 1024L, "abc123", createMetadata("A", "B", "C")),
                createTestAudioFile("song2.mp3", 1024L, "abc123", createMetadata("A", "B", "C")),
                createTestAudioFile("song3.mp3", 2048L, "xyz789", createMetadata("X", "Y", "Z")),
                createTestAudioFile("song4.mp3", 4096L, "unique1", createMetadata("U", "V", "W"))
            );

            DuplicateAnalysisResult result = finder.findDuplicates(files);

            Map<String, Object> stats = result.statistics();
            assertEquals(4, stats.get("total_files_analyzed"));
            assertEquals(2L, result.getTotalDuplicateFiles()); // 2 files are duplicates
            assertEquals(50.0, result.getDuplicatePercentage()); // 2 out of 4 files
            assertEquals(1024L, result.getTotalWastedSpace()); // One duplicate of 1024 bytes
        }

        @Test
        @DisplayName("Should handle zero duplicates in statistics")
        void shouldHandleZeroDuplicatesInStats() {
            List<AudioFile> uniqueFiles = List.of(
                createTestAudioFile("song1.mp3", 1024L, "unique1", createMetadata("A", "B", "C")),
                createTestAudioFile("song2.mp3", 2048L, "unique2", createMetadata("X", "Y", "Z"))
            );

            DuplicateAnalysisResult result = finder.findDuplicates(uniqueFiles);

            assertEquals(0, result.getTotalDuplicateFiles());
            assertEquals(0.0, result.getDuplicatePercentage());
            assertEquals(0L, result.getTotalWastedSpace());
        }

        @Test
        @DisplayName("Should provide meaningful summary")
        void shouldProvideMeaningfulSummary() {
            List<AudioFile> files = List.of(
                createTestAudioFile("song1.mp3", 1024L, "abc123", createMetadata("A", "B", "C")),
                createTestAudioFile("song2.mp3", 1024L, "abc123", createMetadata("A", "B", "C"))
            );

            DuplicateAnalysisResult result = finder.findDuplicates(files);
            String summary = result.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("Total Files Analyzed"));
            assertTrue(summary.contains("Duplicate Groups Found"));
            assertTrue(summary.contains("Exact Duplicates"));
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("Should properly close executor service")
        void shouldProperlyCloseExecutorService() throws Exception {
            ConcurrentDuplicateFinder testFinder = new ConcurrentDuplicateFinder();
            
            // Use the finder
            testFinder.findDuplicates(Collections.emptyList());
            
            // Close and verify it doesn't throw
            assertDoesNotThrow(testFinder::close);
            
            // Closing again should be safe
            assertDoesNotThrow(testFinder::close);
        }

        @Test
        @DisplayName("Should handle concurrent close operations")
        void shouldHandleConcurrentCloseOperations() throws Exception {
            ConcurrentDuplicateFinder testFinder = new ConcurrentDuplicateFinder();
            
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch latch = new CountDownLatch(5);
            
            try {
                // Have multiple threads try to close simultaneously
                for (int i = 0; i < 5; i++) {
                    executor.execute(() -> {
                        try {
                            testFinder.close();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                assertTrue(latch.await(5, TimeUnit.SECONDS));
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very large file collections")
        void shouldHandleVeryLargeCollections() {
            List<AudioFile> largeCollection = IntStream.range(0, 1000)
                .mapToObj(i -> createTestAudioFile("song" + i + ".mp3", 1024L, "checksum" + (i % 10), 
                    createMetadata("Song " + i, "Artist " + (i % 50), "Album " + (i % 25))))
                .toList();

            assertDoesNotThrow(() -> {
                DuplicateAnalysisResult result = finder.findDuplicates(largeCollection);
                assertNotNull(result);
            });
        }

        @Test
        @DisplayName("Should handle files with identical metadata but different checksums")
        void shouldHandleIdenticalMetadataDifferentChecksums() {
            AudioMetadata identicalMetadata = createMetadata("Same Song", "Same Artist", "Same Album");
            
            List<AudioFile> files = List.of(
                createTestAudioFile("song1.mp3", 1024L, "checksum1", identicalMetadata),
                createTestAudioFile("song2.mp3", 1024L, "checksum2", identicalMetadata)
            );

            DuplicateAnalysisResult result = finder.findDuplicates(files);

            assertTrue(result.exactDuplicates().isEmpty()); // Different checksums
            assertFalse(result.metadataDuplicates().isEmpty()); // Same metadata
        }

        @Test
        @DisplayName("Should handle mixed file sizes")
        void shouldHandleMixedFileSizes() {
            List<AudioFile> mixedSizeFiles = List.of(
                createTestAudioFile("small.mp3", 100L, "checksum1", createMetadata("A", "B", "C")),
                createTestAudioFile("medium.mp3", 1024 * 1024L, "checksum2", createMetadata("X", "Y", "Z")),
                createTestAudioFile("large.mp3", 100 * 1024 * 1024L, "checksum3", createMetadata("P", "Q", "R")),
                createTestAudioFile("huge.mp3", 1000 * 1024 * 1024L, "checksum4", createMetadata("U", "V", "W"))
            );

            assertDoesNotThrow(() -> {
                DuplicateAnalysisResult result = finder.findDuplicates(mixedSizeFiles);
                assertNotNull(result);
            });
        }

        @Test
        @DisplayName("Should handle special characters in metadata")
        void shouldHandleSpecialCharactersInMetadata() {
            AudioFile file1 = createTestAudioFile("song1.mp3", 1024L, "checksum1", 
                createMetadata("Café Münü", "Björk & Sigur Rós", "Ólafur Arnalds"));
            AudioFile file2 = createTestAudioFile("song2.mp3", 1024L, "checksum2", 
                createMetadata("café münü", "björk & sigur rós", "ólafur arnalds"));

            assertDoesNotThrow(() -> {
                DuplicateAnalysisResult result = finder.findDuplicates(List.of(file1, file2));
                assertNotNull(result);
            });
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        @DisplayName("Should complete duplicate detection within reasonable time")
        void shouldCompleteWithinReasonableTime() {
            // Create a reasonably sized collection
            List<AudioFile> files = IntStream.range(0, 500)
                .mapToObj(i -> createTestAudioFile("song" + i + ".mp3", 1024L + i, "checksum" + (i % 100), 
                    createMetadata("Song " + i, "Artist " + (i % 20), "Album " + (i % 10))))
                .toList();

            long startTime = System.nanoTime();
            DuplicateAnalysisResult result = finder.findDuplicates(files);
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;
            System.out.printf("Processed %d files in %d ms%n", files.size(), durationMs);

            assertNotNull(result);
            assertTrue(durationMs < 10_000); // Should complete within 10 seconds
        }

        @Test
        @DisplayName("Should handle memory efficiently with large collections")
        void shouldHandleMemoryEfficientlyWithLargeCollections() {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            List<AudioFile> largeCollection = IntStream.range(0, 2000)
                .mapToObj(i -> createTestAudioFile("song" + i + ".mp3", 1024L + i, "checksum" + i, 
                    createMetadata("Song " + i, "Artist " + i, "Album " + i)))
                .toList();

            DuplicateAnalysisResult result = finder.findDuplicates(largeCollection);
            
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = finalMemory - initialMemory;

            assertNotNull(result);
            // Memory usage should be reasonable (less than 100MB for this test)
            assertTrue(memoryUsed < 100 * 1024 * 1024, 
                String.format("Memory usage too high: %d bytes", memoryUsed));
        }
    }

    // Helper methods for creating test data

    private AudioFile createTestAudioFile(String filename, long size, String checksum, AudioMetadata metadata) {
        Path path = Paths.get("/test/music/" + filename);
        Instant lastModified = Instant.now();
        return new AudioFile(path, size, lastModified, metadata, checksum);
    }

    private AudioMetadata createMetadata(String title, String artist, String album) {
        return new AudioMetadata.Builder()
            .title(title)
            .artist(artist)
            .album(album)
            .build();
    }

    private AudioMetadata createMetadata(String title, String artist, String album, Integer year, Integer trackNumber) {
        return new AudioMetadata.Builder()
            .title(title)
            .artist(artist)
            .album(album)
            .year(year)
            .trackNumber(trackNumber)
            .build();
    }
}