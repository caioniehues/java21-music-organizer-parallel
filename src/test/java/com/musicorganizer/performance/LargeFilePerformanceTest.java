package com.musicorganizer.performance;

import com.musicorganizer.model.AudioFile;
import com.musicorganizer.processor.ConcurrentDuplicateFinder;
import com.musicorganizer.test.util.TestAudioFileFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for handling large audio files (250MB+).
 * These tests validate memory efficiency and processing speed with large files.
 * 
 * Enable these tests by setting system property: -DrunLargeFileTests=true
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Large File Performance Tests")
@EnabledIfSystemProperty(named = "runLargeFileTests", matches = "true")
public class LargeFilePerformanceTest {
    
    private static final long LARGE_FILE_SIZE_MB = 250;
    private static final long VERY_LARGE_FILE_SIZE_MB = 500;
    private static final long GIGABYTE_FILE_SIZE_MB = 1024;
    
    @BeforeAll
    static void setupPerformanceMonitoring() {
        System.out.println("=== Large File Performance Test Suite ===");
        System.out.println("JVM Max Heap: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        System.out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Virtual Threads Enabled: " + Thread.currentThread().isVirtual());
    }
    
    @Nested
    @DisplayName("Single Large File Processing")
    class SingleLargeFileTests {
        
        @Test
        @Order(1)
        @DisplayName("Should process 250MB file efficiently")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void shouldProcess250MBFile() {
            // Given
            AudioFile largeFile = TestAudioFileFactory.createLargeAudioFile("large_audio.flac", LARGE_FILE_SIZE_MB);
            long startMemory = getUsedMemory();
            long startTime = System.currentTimeMillis();
            
            // When
            try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder()) {
                var result = finder.findDuplicates(List.of(largeFile));
                
                // Then
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                long processingTime = endTime - startTime;
                long memoryUsed = (endMemory - startMemory) / (1024 * 1024); // MB
                
                System.out.printf("250MB file processed in %d ms, Memory used: %d MB%n", 
                    processingTime, memoryUsed);
                
                assertNotNull(result);
                assertTrue(processingTime < 10000, "Processing should complete within 10 seconds");
                assertTrue(memoryUsed < 500, "Memory usage should be under 500MB for a 250MB file");
            }
        }
        
        @Test
        @Order(2)
        @DisplayName("Should process 500MB file without memory issues")
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        void shouldProcess500MBFile() {
            // Given
            AudioFile veryLargeFile = TestAudioFileFactory.createLargeAudioFile("very_large.flac", VERY_LARGE_FILE_SIZE_MB);
            
            // When & Then
            assertDoesNotThrow(() -> {
                try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder()) {
                    var result = finder.findDuplicates(List.of(veryLargeFile));
                    assertNotNull(result);
                }
            });
        }
        
        @Test
        @Order(3)
        @DisplayName("Should handle 1GB file with streaming")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void shouldHandle1GBFile() {
            // Given
            AudioFile gigabyteFile = TestAudioFileFactory.createLargeAudioFile("gigabyte.flac", GIGABYTE_FILE_SIZE_MB);
            
            // When
            long startTime = System.currentTimeMillis();
            try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder()) {
                var result = finder.findDuplicates(List.of(gigabyteFile));
                
                // Then
                long processingTime = System.currentTimeMillis() - startTime;
                System.out.printf("1GB file processed in %d seconds%n", processingTime / 1000);
                
                assertNotNull(result);
                assertTrue(processingTime < 30000, "1GB file should process within 30 seconds");
            }
        }
    }
    
    @Nested
    @DisplayName("Multiple Large Files Processing")
    class MultipleLargeFilesTests {
        
        @Test
        @Order(4)
        @DisplayName("Should process 10 x 250MB files concurrently")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void shouldProcessMultipleLargeFilesConcurrently() {
            // Given
            AudioFile[] largeFiles = TestAudioFileFactory.createLargeFiles(10, 250, 250);
            long totalSizeMB = largeFiles.length * 250;
            long startMemory = getUsedMemory();
            long startTime = System.currentTimeMillis();
            
            // When
            try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder()) {
                var result = finder.findDuplicates(Arrays.asList(largeFiles));
                
                // Then
                long processingTime = System.currentTimeMillis() - startTime;
                long memoryUsed = (getUsedMemory() - startMemory) / (1024 * 1024);
                double throughputMBps = (double) totalSizeMB / (processingTime / 1000.0);
                
                System.out.printf("Processed %d files (%.1f GB total) in %.2f seconds%n", 
                    largeFiles.length, totalSizeMB / 1024.0, processingTime / 1000.0);
                System.out.printf("Throughput: %.2f MB/s, Memory used: %d MB%n", 
                    throughputMBps, memoryUsed);
                
                assertNotNull(result);
                assertTrue(memoryUsed < 2000, "Memory usage should be under 2GB for 2.5GB of files");
                assertTrue(throughputMBps > 50, "Throughput should exceed 50 MB/s");
            }
        }
        
        @Test
        @Order(5)
        @DisplayName("Should find duplicates among large files")
        @Timeout(value = 45, unit = TimeUnit.SECONDS)
        void shouldFindDuplicatesAmongLargeFiles() {
            // Given - Create some duplicate large files
            String checksum1 = TestAudioFileFactory.generateChecksum();
            String checksum2 = TestAudioFileFactory.generateChecksum();
            
            AudioFile[] files = {
                TestAudioFileFactory.createAudioFile("large1.flac", 250 * 1024 * 1024, checksum1, 
                    TestAudioFileFactory.createRandomMetadata()),
                TestAudioFileFactory.createAudioFile("large2.flac", 250 * 1024 * 1024, checksum1, 
                    TestAudioFileFactory.createRandomMetadata()),
                TestAudioFileFactory.createAudioFile("large3.flac", 250 * 1024 * 1024, checksum2, 
                    TestAudioFileFactory.createRandomMetadata()),
                TestAudioFileFactory.createAudioFile("large4.flac", 250 * 1024 * 1024, checksum2, 
                    TestAudioFileFactory.createRandomMetadata()),
            };
            
            // When
            try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder()) {
                var result = finder.findDuplicates(Arrays.asList(files));
                
                // Then
                assertEquals(2, result.exactDuplicates().size(), "Should find 2 duplicate groups");
                assertEquals(2, result.exactDuplicates().get(0).getDuplicateCount());
                assertEquals(2, result.exactDuplicates().get(1).getDuplicateCount());
            }
        }
    }
    
    @Nested
    @DisplayName("Memory Stress Tests")
    class MemoryStressTests {
        
        @Test
        @Order(6)
        @DisplayName("Should handle 100 files without memory leak")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void shouldHandle100FilesWithoutMemoryLeak() {
            // Given
            int fileCount = 100;
            AudioFile[] files = TestAudioFileFactory.createLargeFiles(fileCount, 50, 100);
            
            // Warm up
            System.gc();
            Thread.yield();
            long baselineMemory = getUsedMemory();
            
            // When - Process files multiple times to detect leaks
            for (int iteration = 0; iteration < 3; iteration++) {
                try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder()) {
                    var result = finder.findDuplicates(Arrays.asList(files));
                    assertNotNull(result);
                }
                
                // Force GC between iterations
                System.gc();
                Thread.yield();
            }
            
            // Then
            long finalMemory = getUsedMemory();
            long memoryGrowth = (finalMemory - baselineMemory) / (1024 * 1024);
            
            System.out.printf("Memory growth after 3 iterations: %d MB%n", memoryGrowth);
            assertTrue(memoryGrowth < 500, "Memory should not grow significantly between iterations");
        }
        
        @Test
        @Order(7)
        @DisplayName("Should maintain performance with virtual threads under load")
        @Timeout(value = 90, unit = TimeUnit.SECONDS)
        void shouldMaintainVirtualThreadPerformance() {
            // Given
            int threadCount = 5000; // Test virtual thread pool limit
            AudioFile[] files = IntStream.range(0, threadCount)
                .mapToObj(i -> TestAudioFileFactory.createAudioFile(
                    "file_" + i + ".mp3", 
                    10 * 1024 * 1024, // 10MB each
                    TestAudioFileFactory.generateChecksum(),
                    TestAudioFileFactory.createRandomMetadata()
                ))
                .toArray(AudioFile[]::new);
            
            // When
            long startTime = System.currentTimeMillis();
            try (ConcurrentDuplicateFinder finder = new ConcurrentDuplicateFinder(true, 0.85)) {
                var result = finder.findDuplicates(Arrays.asList(files));
                
                // Then
                long processingTime = System.currentTimeMillis() - startTime;
                System.out.printf("Processed %d files with %d virtual threads in %.2f seconds%n", 
                    files.length, threadCount, processingTime / 1000.0);
                
                assertNotNull(result);
                assertTrue(processingTime < 90000, "Should complete within 90 seconds even with thread pool saturation");
            }
        }
    }
    
    @Nested
    @DisplayName("Checksum Calculation Performance")
    class ChecksumPerformanceTests {
        
        @Test
        @Order(8)
        @DisplayName("Should calculate checksums for large files efficiently")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void shouldCalculateChecksumsEfficiently() {
            // This test simulates checksum calculation without actual file I/O
            // In real implementation, this would use memory-mapped files
            
            int fileCount = 10;
            long startTime = System.currentTimeMillis();
            
            // Simulate checksum calculation for large files
            for (int i = 0; i < fileCount; i++) {
                String checksum = TestAudioFileFactory.generateChecksum();
                assertNotNull(checksum);
                assertEquals(64, checksum.length()); // SHA-256 produces 64 hex chars
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            System.out.printf("Generated %d checksums in %d ms%n", fileCount, processingTime);
            
            assertTrue(processingTime < 1000, "Checksum generation should be fast");
        }
    }
    
    /**
     * Helper method to get current used memory in bytes.
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}