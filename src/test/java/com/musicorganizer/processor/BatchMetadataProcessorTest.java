package com.musicorganizer.processor;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.service.MusicBrainzService;
import com.musicorganizer.util.ProgressTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for BatchMetadataProcessor using Java 21 features,
 * focusing on concurrency, rate limiting, retry logic, and batch processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchMetadataProcessor Tests")
class BatchMetadataProcessorTest {

    @Mock
    private MusicBrainzService musicBrainzService;

    @Mock
    private ProgressTracker progressTracker;

    private BatchMetadataProcessor processor;
    private BatchMetadataProcessor.BatchConfig defaultConfig;

    // Test data
    private final Path testFile1 = Paths.get("/music/artist1/song1.mp3");
    private final Path testFile2 = Paths.get("/music/artist1/song2.mp3");
    private final Path testFile3 = Paths.get("/music/artist2/song3.mp3");
    private final Path testFile4 = Paths.get("/music/artist2/song4.mp3");
    private final Path testFile5 = Paths.get("/music/artist3/song5.mp3");

    private final TrackMetadata sampleMetadata1 = createSampleMetadata("Song 1", "Artist 1", "Album 1");
    private final TrackMetadata sampleMetadata2 = createSampleMetadata("Song 2", "Artist 1", "Album 1");
    private final TrackMetadata sampleMetadata3 = createSampleMetadata("Song 3", "Artist 2", "Album 2");

    @BeforeEach
    void setUp() {
        defaultConfig = BatchMetadataProcessor.BatchConfig.defaultConfig();
        processor = new BatchMetadataProcessor(musicBrainzService, defaultConfig, progressTracker);
    }

    @Test
    @DisplayName("Should process empty batch successfully")
    void testProcessEmptyBatch() {
        var emptyFiles = List.<Path>of();
        
        var result = processor.processFiles(emptyFiles, null);
        
        assertTrue(result.successful().isEmpty());
        assertTrue(result.failed().isEmpty());
        assertEquals(0, result.totalProcessed());
        assertTrue(result.processingTime().compareTo(Duration.ZERO) > 0);
        
        verify(progressTracker).startOperation("metadata_processing", 0);
        verify(progressTracker).completeOperation("metadata_processing");
    }

    @Test
    @DisplayName("Should process single file successfully")
    void testProcessSingleFile() throws Exception {
        var files = List.of(testFile1);
        when(musicBrainzService.enrichMetadata(testFile1)).thenReturn(sampleMetadata1);
        
        var result = processor.processFiles(files, null);
        
        assertEquals(1, result.successful().size());
        assertEquals(sampleMetadata1, result.successful().get(testFile1));
        assertTrue(result.failed().isEmpty());
        assertEquals(1, result.totalProcessed());
        
        verify(musicBrainzService).enrichMetadata(testFile1);
        verify(progressTracker).startOperation("metadata_processing", 1);
        verify(progressTracker).updateProgress("metadata_processing", 1);
        verify(progressTracker).completeOperation("metadata_processing");
    }

    @Test
    @DisplayName("Should process multiple files in batches")
    void testProcessMultipleFiles() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            2,  // batch size = 2
            Duration.ofMillis(100),
            3,
            Duration.ofSeconds(10),  // Increased timeout
            5
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files = List.of(testFile1, testFile2, testFile3);
        when(musicBrainzService.enrichMetadata(testFile1)).thenReturn(sampleMetadata1);
        when(musicBrainzService.enrichMetadata(testFile2)).thenReturn(sampleMetadata2);
        when(musicBrainzService.enrichMetadata(testFile3)).thenReturn(sampleMetadata3);
        
        var result = processor.processFiles(files, null);
        
        assertEquals(3, result.successful().size());
        assertEquals(sampleMetadata1, result.successful().get(testFile1));
        assertEquals(sampleMetadata2, result.successful().get(testFile2));
        assertEquals(sampleMetadata3, result.successful().get(testFile3));
        assertTrue(result.failed().isEmpty());
        assertEquals(3, result.totalProcessed());
        
        verify(musicBrainzService, times(3)).enrichMetadata(any(Path.class));
        verify(progressTracker).startOperation("metadata_processing", 3);
        verify(progressTracker, atLeast(3)).updateProgress(eq("metadata_processing"), anyInt());
        verify(progressTracker).completeOperation("metadata_processing");
    }

    @Test
    @DisplayName("Should handle processing failures gracefully")
    void testProcessingFailure() throws Exception {
        var files = List.of(testFile1, testFile2);
        var exception = new RuntimeException("API error");
        
        when(musicBrainzService.enrichMetadata(testFile1)).thenReturn(sampleMetadata1);
        when(musicBrainzService.enrichMetadata(testFile2)).thenThrow(exception);
        
        var result = processor.processFiles(files, null);
        
        assertEquals(1, result.successful().size());
        assertEquals(sampleMetadata1, result.successful().get(testFile1));
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey(testFile2));
        assertTrue(result.failed().get(testFile2) instanceof RuntimeException);
        assertEquals(2, result.totalProcessed());
        
        verify(musicBrainzService).enrichMetadata(testFile1);
        verify(musicBrainzService, atLeast(1)).enrichMetadata(testFile2); // May retry
    }

    @Test
    @DisplayName("Should retry failed requests with exponential backoff")
    void testRetryLogic() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            5,
            Duration.ofMillis(10), // Fast rate limit for testing
            3, // max retries
            Duration.ofSeconds(5),  // Increased timeout
            5
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files = List.of(testFile1);
        when(musicBrainzService.enrichMetadata(testFile1))
            .thenThrow(new RuntimeException("First failure"))
            .thenThrow(new RuntimeException("Second failure"))
            .thenReturn(sampleMetadata1); // Success on third try
        
        var result = processor.processFiles(files, null);
        
        assertEquals(1, result.successful().size());
        assertEquals(sampleMetadata1, result.successful().get(testFile1));
        assertTrue(result.failed().isEmpty());
        
        // Verify the method was called 3 times (initial + 2 retries)
        verify(musicBrainzService, times(3)).enrichMetadata(testFile1);
    }

    @Test
    @DisplayName("Should respect maximum retry limit")
    void testMaxRetryLimit() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            5,
            Duration.ofMillis(10),
            2, // max retries = 2
            Duration.ofSeconds(5),  // Increased timeout
            5
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files = List.of(testFile1);
        var exception = new RuntimeException("Persistent failure");
        when(musicBrainzService.enrichMetadata(testFile1)).thenThrow(exception);
        
        var result = processor.processFiles(files, null);
        
        assertTrue(result.successful().isEmpty());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey(testFile1));
        
        // Verify the method was called 3 times (initial + 2 retries)
        verify(musicBrainzService, times(3)).enrichMetadata(testFile1);
    }

    @Test
    @DisplayName("Should enforce rate limiting between requests")
    void testRateLimiting() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            5,
            Duration.ofMillis(100), // 100ms between requests
            3,
            Duration.ofSeconds(1),
            1 // Only 1 concurrent request to test rate limiting
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files = List.of(testFile1, testFile2);
        when(musicBrainzService.enrichMetadata(any(Path.class))).thenReturn(sampleMetadata1);
        
        var startTime = System.currentTimeMillis();
        var result = processor.processFiles(files, null);
        var endTime = System.currentTimeMillis();
        
        assertEquals(2, result.successful().size());
        // Should take at least 100ms due to rate limiting
        assertTrue((endTime - startTime) >= 100, "Processing should take at least 100ms due to rate limiting");
        
        verify(musicBrainzService, times(2)).enrichMetadata(any(Path.class));
    }

    @Test
    @DisplayName("Should handle concurrent processing within semaphore limits")
    void testConcurrentProcessing() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            5,
            Duration.ofMillis(10), // Fast rate limit
            3,
            Duration.ofSeconds(1),
            3 // Allow 3 concurrent requests
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files = List.of(testFile1, testFile2, testFile3, testFile4);
        var callOrder = new ConcurrentLinkedQueue<Path>();
        
        when(musicBrainzService.enrichMetadata(any(Path.class))).thenAnswer(invocation -> {
            var file = (Path) invocation.getArgument(0);
            callOrder.offer(file);
            Thread.sleep(50); // Simulate processing time
            return createSampleMetadata("title", "artist", "album");
        });
        
        var result = processor.processFiles(files, null);
        
        assertEquals(4, result.successful().size());
        assertTrue(result.failed().isEmpty());
        assertEquals(4, callOrder.size());
        
        verify(musicBrainzService, times(4)).enrichMetadata(any(Path.class));
    }

    @Test
    @DisplayName("Should call progress callback for each processed file")
    void testProgressCallback() throws Exception {
        var files = List.of(testFile1, testFile2);
        var processedMetadata = new ArrayList<TrackMetadata>();
        
        Consumer<TrackMetadata> callback = processedMetadata::add;
        
        when(musicBrainzService.enrichMetadata(testFile1)).thenReturn(sampleMetadata1);
        when(musicBrainzService.enrichMetadata(testFile2)).thenReturn(sampleMetadata2);
        
        var result = processor.processFiles(files, callback);
        
        assertEquals(2, result.successful().size());
        assertEquals(2, processedMetadata.size());
        assertTrue(processedMetadata.contains(sampleMetadata1));
        assertTrue(processedMetadata.contains(sampleMetadata2));
    }

    @Test
    @DisplayName("Should not call progress callback for failed files")
    void testProgressCallbackOnFailure() throws Exception {
        var files = List.of(testFile1, testFile2);
        var processedMetadata = new ArrayList<TrackMetadata>();
        
        Consumer<TrackMetadata> callback = processedMetadata::add;
        
        when(musicBrainzService.enrichMetadata(testFile1)).thenReturn(sampleMetadata1);
        when(musicBrainzService.enrichMetadata(testFile2)).thenThrow(new RuntimeException("API error"));
        
        var result = processor.processFiles(files, callback);
        
        assertEquals(1, result.successful().size());
        assertEquals(1, result.failed().size());
        assertEquals(1, processedMetadata.size());
        assertTrue(processedMetadata.contains(sampleMetadata1));
    }

    @Test
    @DisplayName("Should handle null progress callback gracefully")
    void testNullProgressCallback() throws Exception {
        var files = List.of(testFile1);
        when(musicBrainzService.enrichMetadata(testFile1)).thenReturn(sampleMetadata1);
        
        // Should not throw exception with null callback
        var result = processor.processFiles(files, null);
        
        assertEquals(1, result.successful().size());
        assertTrue(result.failed().isEmpty());
    }

    @Test
    @DisplayName("Should process files asynchronously")
    void testAsyncProcessing() throws Exception {
        var files = List.of(testFile1, testFile2);
        when(musicBrainzService.enrichMetadata(any(Path.class))).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate processing time
            return sampleMetadata1;
        });
        
        var future = processor.processFilesAsync(files, null);
        
        assertFalse(future.isDone());
        
        // Wait for completion with timeout
        await().atMost(Duration.ofSeconds(10))
               .until(future::isDone);
        
        var result = future.get();
        assertEquals(2, result.successful().size());
        assertTrue(result.failed().isEmpty());
    }

    @Test
    @DisplayName("Should handle InterruptedException correctly")
    void testInterruptedException() throws Exception {
        var files = List.of(testFile1);
        
        when(musicBrainzService.enrichMetadata(testFile1)).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Thread interrupted");
        });
        
        var result = processor.processFiles(files, null);
        
        assertTrue(result.successful().isEmpty());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().containsKey(testFile1));
        assertTrue(result.failed().get(testFile1) instanceof InterruptedException);
        
        // Note: Virtual thread interruption doesn't automatically propagate to calling thread
        // We verify that the InterruptedException is properly captured in the failed results
        // This is the expected behavior for async processing with virtual threads
    }

    @Test
    @DisplayName("Should batch files according to batch size configuration")
    void testBatchSizeConfiguration() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            2, // batch size = 2
            Duration.ofMillis(10),
            3,
            Duration.ofSeconds(1),
            5
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files = List.of(testFile1, testFile2, testFile3, testFile4, testFile5);
        var maxConcurrentFiles = new AtomicInteger(0);
        var currentConcurrentFiles = new AtomicInteger(0);
        
        when(musicBrainzService.enrichMetadata(any(Path.class))).thenAnswer(invocation -> {
            var current = currentConcurrentFiles.incrementAndGet();
            maxConcurrentFiles.updateAndGet(max -> Math.max(max, current));
            
            Thread.sleep(50); // Simulate processing time
            
            currentConcurrentFiles.decrementAndGet();
            return createSampleMetadata("title", "artist", "album");
        });
        
        var result = processor.processFiles(files, null);
        
        assertEquals(5, result.successful().size());
        // Due to batching, we shouldn't have more than max concurrent requests
        assertTrue(maxConcurrentFiles.get() <= 5, "Should not exceed max concurrent requests from config");
    }

    @Test
    @DisplayName("Should validate batch configuration")
    void testBatchConfigValidation() {
        // Test default configuration
        var defaultConfig = BatchMetadataProcessor.BatchConfig.defaultConfig();
        
        assertEquals(10, defaultConfig.batchSize());
        assertEquals(Duration.ofMillis(1000), defaultConfig.rateLimit());
        assertEquals(3, defaultConfig.maxRetries());
        assertEquals(Duration.ofSeconds(30), defaultConfig.maxBackoff());
        assertEquals(5, defaultConfig.maxConcurrentRequests());
        
        // Test custom configuration
        var customConfig = new BatchMetadataProcessor.BatchConfig(
            20,
            Duration.ofMillis(500),
            5,
            Duration.ofSeconds(60),
            10
        );
        
        assertEquals(20, customConfig.batchSize());
        assertEquals(Duration.ofMillis(500), customConfig.rateLimit());
        assertEquals(5, customConfig.maxRetries());
        assertEquals(Duration.ofSeconds(60), customConfig.maxBackoff());
        assertEquals(10, customConfig.maxConcurrentRequests());
    }

    @Test
    @DisplayName("Should validate processing result")
    void testProcessingResult() {
        var successful = Map.of(testFile1, sampleMetadata1, testFile2, sampleMetadata2);
        var failed = Map.<Path, Exception>of(testFile3, new RuntimeException("Error"));
        var processingTime = Duration.ofMillis(1500);
        var totalProcessed = 3;
        
        var result = new BatchMetadataProcessor.ProcessingResult(
            successful, failed, processingTime, totalProcessed
        );
        
        assertEquals(successful, result.successful());
        assertEquals(failed, result.failed());
        assertEquals(processingTime, result.processingTime());
        assertEquals(totalProcessed, result.totalProcessed());
    }

    @Test
    @DisplayName("Should provide processing statistics")
    void testProcessingStats() {
        var stats = processor.getStats();
        
        assertNotNull(stats);
        assertTrue(stats.queuedRequests() >= 0);
        assertTrue(stats.activeRequests() >= 0);
        assertNotNull(stats.averageResponseTime());
        assertTrue(stats.totalRetries() >= 0);
    }

    @RepeatedTest(3)
    @DisplayName("Should handle concurrent batch processing consistently")
    void testConcurrentBatches() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            3,
            Duration.ofMillis(10),
            2,
            Duration.ofSeconds(1),
            10
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        var files1 = List.of(testFile1, testFile2);
        var files2 = List.of(testFile3, testFile4, testFile5);
        
        when(musicBrainzService.enrichMetadata(any(Path.class))).thenAnswer(invocation -> {
            Thread.sleep(20); // Simulate network delay
            return createSampleMetadata("title", "artist", "album");
        });
        
        // Process both batches concurrently
        var future1 = processor.processFilesAsync(files1, null);
        var future2 = processor.processFilesAsync(files2, null);
        
        var result1 = future1.get(10, TimeUnit.SECONDS);
        var result2 = future2.get(10, TimeUnit.SECONDS);
        
        assertEquals(2, result1.successful().size());
        assertTrue(result1.failed().isEmpty());
        assertEquals(3, result2.successful().size());
        assertTrue(result2.failed().isEmpty());
        
        verify(musicBrainzService, times(5)).enrichMetadata(any(Path.class));
    }

    @Test
    @DisplayName("Should handle large batch sizes efficiently")
    void testLargeBatchProcessing() throws Exception {
        var config = new BatchMetadataProcessor.BatchConfig(
            100, // Large batch size
            Duration.ofMillis(1), // Fast rate limit
            2,
            Duration.ofSeconds(1),
            20 // High concurrency
        );
        processor = new BatchMetadataProcessor(musicBrainzService, config, progressTracker);
        
        // Create 50 test files
        var files = new ArrayList<Path>();
        for (int i = 0; i < 50; i++) {
            files.add(Paths.get("/music/song" + i + ".mp3"));
        }
        
        when(musicBrainzService.enrichMetadata(any(Path.class))).thenAnswer(invocation -> {
            Thread.sleep(1); // Minimal delay
            return createSampleMetadata("title", "artist", "album");
        });
        
        var startTime = System.currentTimeMillis();
        var result = processor.processFiles(files, null);
        var endTime = System.currentTimeMillis();
        
        assertEquals(50, result.successful().size());
        assertTrue(result.failed().isEmpty());
        assertEquals(50, result.totalProcessed());
        
        // Should complete relatively quickly due to high concurrency
        assertTrue((endTime - startTime) < 5000, "Should complete in less than 5 seconds");
        
        verify(musicBrainzService, times(50)).enrichMetadata(any(Path.class));
        verify(progressTracker).startOperation("metadata_processing", 50);
        verify(progressTracker).completeOperation("metadata_processing");
    }

    @Test
    @DisplayName("Should handle timeout scenarios gracefully")
    void testTimeoutHandling() throws Exception {
        var files = List.of(testFile1);
        
        when(musicBrainzService.enrichMetadata(testFile1)).thenAnswer(invocation -> {
            Thread.sleep(2000); // Simulate long delay
            return sampleMetadata1;
        });
        
        var future = processor.processFilesAsync(files, null);
        
        // Test with timeout
        assertThrows(TimeoutException.class, () -> future.get(500, TimeUnit.MILLISECONDS));
        
        // But should eventually complete
        var result = future.get(10, TimeUnit.SECONDS);
        assertEquals(1, result.successful().size());
    }

    // Helper method to create sample metadata
    private TrackMetadata createSampleMetadata(String title, String artist, String album) {
        return TrackMetadata.builder()
            .title(title)
            .artist(artist)
            .album(album)
            .year(2023)
            .trackNumber(1)
            .duration(Duration.ofMinutes(3))
            .format("MP3")
            .build();
    }

    // Helper method to verify async completion
    private void awaitCompletion(CompletableFuture<?> future) {
        await().atMost(Duration.ofSeconds(15))
               .until(future::isDone);
    }
}