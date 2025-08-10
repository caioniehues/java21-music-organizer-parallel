package com.musicorganizer.processor;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.service.ExecutorServiceFactory;
import com.musicorganizer.service.MusicBrainzService;
import com.musicorganizer.util.ProgressTracker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Batch metadata processor using Virtual Threads and CompletableFuture
 * for efficient concurrent processing with MusicBrainz API rate limiting.
 */
public class BatchMetadataProcessor implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(BatchMetadataProcessor.class.getName());
    
    public record ProcessingResult(
        Map<Path, TrackMetadata> successful,
        Map<Path, Exception> failed,
        Duration processingTime,
        int totalProcessed
    ) {}
    
    public record BatchConfig(
        int batchSize,
        Duration rateLimit,
        int maxRetries,
        Duration maxBackoff,
        int maxConcurrentRequests
    ) {
        public static BatchConfig defaultConfig() {
            return new BatchConfig(
                10,              // batch size
                Duration.ofMillis(1000), // rate limit per request
                3,               // max retries
                Duration.ofSeconds(30),  // max backoff
                5                // max concurrent requests
            );
        }
    }
    
    private final MusicBrainzService musicBrainzService;
    private final BatchConfig config;
    private final ProgressTracker progressTracker;
    private final ExecutorServiceFactory executorServiceFactory;
    private final Semaphore rateLimiter;
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    
    public BatchMetadataProcessor(MusicBrainzService musicBrainzService, 
                                 BatchConfig config,
                                 ProgressTracker progressTracker) {
        this(musicBrainzService, config, progressTracker, 
             ExecutorServiceFactory.defaultFactory());
    }
    
    public BatchMetadataProcessor(MusicBrainzService musicBrainzService,
                                 BatchConfig config,
                                 ProgressTracker progressTracker,
                                 ExecutorServiceFactory executorServiceFactory) {
        this.musicBrainzService = musicBrainzService;
        this.config = config;
        this.progressTracker = progressTracker;
        this.executorServiceFactory = executorServiceFactory;
        this.rateLimiter = new Semaphore(config.maxConcurrentRequests());
    }
    
    /**
     * Process multiple audio files in batches using Virtual Threads
     */
    public CompletableFuture<ProcessingResult> processFilesAsync(List<Path> audioFiles,
                                                               Consumer<TrackMetadata> onMetadataProcessed) {
        return CompletableFuture.supplyAsync(() -> {
            var startTime = System.nanoTime();
            var successful = new ConcurrentHashMap<Path, TrackMetadata>();
            var failed = new ConcurrentHashMap<Path, Exception>();
            var processedCount = new AtomicInteger(0);
            
            try (var executor = executorServiceFactory.createVirtualThreadExecutor()) {
                var batches = createBatches(audioFiles);
                var batchFutures = new ArrayList<CompletableFuture<Void>>();
                
                progressTracker.startOperation("metadata_processing", audioFiles.size());
                
                for (var batch : batches) {
                    var batchFuture = CompletableFuture.runAsync(() -> {
                        processBatch(batch, successful, failed, processedCount, onMetadataProcessed);
                    }, executor);
                    batchFutures.add(batchFuture);
                }
                
                // Wait for all batches to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                
                progressTracker.completeOperation("metadata_processing");
                
                var endTime = System.nanoTime();
                var processingTime = Duration.ofNanos(endTime - startTime);
                
                return new ProcessingResult(
                    Map.copyOf(successful),
                    Map.copyOf(failed),
                    processingTime,
                    processedCount.get()
                );
            }
        }, executorServiceFactory.createVirtualThreadExecutor());
    }
    
    private void processBatch(List<Path> batch,
                            ConcurrentHashMap<Path, TrackMetadata> successful,
                            ConcurrentHashMap<Path, Exception> failed,
                            AtomicInteger processedCount,
                            Consumer<TrackMetadata> onMetadataProcessed) {
        
        var batchFutures = batch.stream()
            .map(file -> processFileWithRetry(file)
                .handle((metadata, throwable) -> {
                    if (throwable != null) {
                        // Properly unwrap exception for consistent handling
                        Exception actualException = unwrapException(throwable);
                        failed.put(file, actualException);
                    } else if (metadata != null) {
                        successful.put(file, metadata);
                        if (onMetadataProcessed != null) {
                            onMetadataProcessed.accept(metadata);
                        }
                    }
                    
                    int completed = processedCount.incrementAndGet();
                    progressTracker.updateProgress("metadata_processing", completed);
                    return null;
                }))
            .toArray(CompletableFuture[]::new);
        
        // Use join() to wait for all futures and handle potential exceptions
        try {
            CompletableFuture.allOf(batchFutures).join();
        } catch (CompletionException e) {
            // Log completion exception but don't rethrow as individual file
            // failures are already captured in the failed map
            LOGGER.log(Level.FINE, "Some files failed processing", e);
        }
    }
    
    private CompletableFuture<TrackMetadata> processFileWithRetry(Path file) {
        return processFileOnce(file)
            .handle((result, throwable) -> {
                if (throwable != null) {
                    return retryWithExponentialBackoff(file, 0, throwable);
                }
                return CompletableFuture.completedFuture(result);
            })
            .thenCompose(future -> future);
    }
    
    private CompletableFuture<TrackMetadata> retryWithExponentialBackoff(Path file, int attempt, Throwable lastError) {
        if (attempt >= config.maxRetries()) {
            return CompletableFuture.failedFuture(lastError);
        }
        
        var delay = calculateBackoffDelay(attempt);
        
        return CompletableFuture.supplyAsync(() -> null, 
                CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS))
            .thenCompose(v -> processFileOnce(file))
            .handle((result, throwable) -> {
                if (throwable != null) {
                    return retryWithExponentialBackoff(file, attempt + 1, throwable);
                }
                return CompletableFuture.completedFuture(result);
            })
            .thenCompose(future -> future);
    }
    
    private CompletableFuture<TrackMetadata> processFileOnce(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Rate limiting with semaphore
                rateLimiter.acquire();
                
                // Ensure minimum time between requests
                enforceRateLimit();
                
                // Process the file
                return musicBrainzService.enrichMetadata(file);
                
            } catch (InterruptedException e) {
                // Restore interrupt status and propagate as RuntimeException
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (Exception e) {
                // Don't wrap in CompletionException to avoid double wrapping
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            } finally {
                rateLimiter.release();
            }
        }, executorServiceFactory.createVirtualThreadExecutor());
    }
    
    private void enforceRateLimit() {
        var now = System.currentTimeMillis();
        var timeSinceLastRequest = now - lastRequestTime.get();
        var minInterval = config.rateLimit().toMillis();
        
        if (timeSinceLastRequest < minInterval) {
            try {
                Thread.sleep(minInterval - timeSinceLastRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        
        lastRequestTime.set(System.currentTimeMillis());
    }
    
    private long calculateBackoffDelay(int attempt) {
        var baseDelay = config.rateLimit().toMillis();
        var exponentialDelay = baseDelay * (1L << attempt); // 2^attempt
        var jitter = (long) (Math.random() * baseDelay * 0.1); // 10% jitter
        
        return Math.min(exponentialDelay + jitter, config.maxBackoff().toMillis());
    }
    
    private List<List<Path>> createBatches(List<Path> files) {
        var batches = new ArrayList<List<Path>>();
        var batchSize = config.batchSize();
        
        for (int i = 0; i < files.size(); i += batchSize) {
            var endIndex = Math.min(i + batchSize, files.size());
            batches.add(new ArrayList<>(files.subList(i, endIndex)));
        }
        
        return batches;
    }
    
    /**
     * Process files synchronously for simpler usage
     */
    public ProcessingResult processFiles(List<Path> audioFiles, 
                                       Consumer<TrackMetadata> onMetadataProcessed) {
        return processFilesAsync(audioFiles, onMetadataProcessed).join();
    }
    
    /**
     * Get processing statistics
     */
    public record ProcessingStats(
        int queuedRequests,
        int activeRequests,
        Duration averageResponseTime,
        int totalRetries
    ) {}
    
    public ProcessingStats getStats() {
        return new ProcessingStats(
            rateLimiter.getQueueLength(),
            config.maxConcurrentRequests() - rateLimiter.availablePermits(),
            config.rateLimit(),
            0 // This would need additional tracking for retries
        );
    }
    
    /**
     * Helper method to unwrap exceptions for consistent error handling.
     * This ensures tests and error handling logic receive the actual exception,
     * not the CompletionException wrapper.
     */
    private Exception unwrapException(Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() instanceof Exception) {
            // Recursively unwrap in case of nested CompletionExceptions
            return unwrapException(ce.getCause());
        } else if (throwable instanceof RuntimeException re && re.getCause() instanceof InterruptedException) {
            // Preserve InterruptedException type for specific test expectations
            return (InterruptedException) re.getCause();
        } else if (throwable instanceof Exception e) {
            return e;
        } else {
            // Wrap non-Exception Throwables
            return new RuntimeException(throwable);
        }
    }
    
    @Override
    public void close() {
        // The MusicBrainzService should be closed by its owner
        // This processor doesn't own the executor services created from factory
    }
}