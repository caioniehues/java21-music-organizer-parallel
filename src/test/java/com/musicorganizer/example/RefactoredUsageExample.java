package com.musicorganizer.example;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.processor.BatchMetadataProcessor;
import com.musicorganizer.scanner.ParallelMusicScanner;
import com.musicorganizer.service.*;
import com.musicorganizer.util.ProgressTracker;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive example demonstrating the refactored, testable architecture.
 * Shows how dependency injection improves separation of concerns and testability.
 */
public class RefactoredUsageExample {
    
    public static void main(String[] args) {
        demonstrateBasicUsage();
        demonstrateCustomConfiguration();
        demonstrateTestableConfiguration();
    }
    
    /**
     * Basic usage with default dependencies.
     */
    public static void demonstrateBasicUsage() {
        System.out.println("=== Basic Usage with Default Dependencies ===");
        
        // Use default configuration
        ServiceConfiguration config = ServiceConfiguration.create();
        
        try (MusicBrainzService service = config.createMusicBrainzService()) {
            // Service uses production dependencies internally
            Optional<TrackMetadata> result = service.lookupTrack("The Beatles", "Hey Jude")
                .get(30, TimeUnit.SECONDS);
            
            if (result.isPresent()) {
                System.out.println("Found: " + result.get().artist() + " - " + result.get().title());
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Custom configuration with production-ready settings.
     */
    public static void demonstrateCustomConfiguration() {
        System.out.println("\n=== Custom Production Configuration ===");
        
        // Custom HTTP client with specific timeout settings
        HttpClientProvider customHttpProvider = () -> 
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        
        // Custom executor factory with specific thread pool sizes
        ExecutorServiceFactory customExecutorFactory = new ExecutorServiceFactory() {
            @Override
            public ExecutorService createVirtualThreadExecutor() {
                return Executors.newVirtualThreadPerTaskExecutor();
            }
            
            @Override
            public ExecutorService createFixedThreadPool(int nThreads) {
                return Executors.newFixedThreadPool(Math.min(nThreads, 20)); // Limit max threads
            }
        };
        
        ServiceConfiguration config = ServiceConfiguration.create()
            .withHttpClientProvider(customHttpProvider)
            .withExecutorServiceFactory(customExecutorFactory);
        
        try (MusicBrainzService service = config.createMusicBrainzService()) {
            System.out.println("Service configured with custom dependencies");
            // Use service...
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Configuration optimized for testing scenarios.
     */
    public static void demonstrateTestableConfiguration() {
        System.out.println("\n=== Testable Configuration Example ===");
        
        // Mock-friendly HTTP client provider
        HttpClientProvider testHttpProvider = new HttpClientProvider() {
            @Override
            public HttpClient createClient() {
                System.out.println("Creating test HTTP client");
                return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .build();
            }
        };
        
        // Synchronous executor for predictable testing
        ExecutorServiceFactory testExecutorFactory = new ExecutorServiceFactory() {
            @Override
            public ExecutorService createVirtualThreadExecutor() {
                System.out.println("Creating synchronous executor for testing");
                return new SynchronousExecutorService();
            }
            
            @Override
            public ExecutorService createFixedThreadPool(int nThreads) {
                return createVirtualThreadExecutor();
            }
        };
        
        // Test-friendly URL encoder with logging
        UrlEncoder testUrlEncoder = new UrlEncoder() {
            private final UrlEncoder delegate = UrlEncoder.defaultEncoder();
            
            @Override
            public String encode(String value) {
                System.out.println("Encoding: " + value);
                return delegate.encode(value);
            }
            
            @Override
            public String escapeQueryValue(String value) {
                System.out.println("Escaping query: " + value);
                return delegate.escapeQueryValue(value);
            }
        };
        
        ServiceConfiguration config = ServiceConfiguration.create()
            .withHttpClientProvider(testHttpProvider)
            .withExecutorServiceFactory(testExecutorFactory)
            .withUrlEncoder(testUrlEncoder);
        
        try (MusicBrainzService service = config.createMusicBrainzService()) {
            System.out.println("Service configured for testing with logging dependencies");
            // This would be ideal for unit testing scenarios
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    /**
     * Example of a complete workflow using the refactored architecture.
     */
    public static void demonstrateCompleteWorkflow() {
        System.out.println("\n=== Complete Workflow Example ===");
        
        ServiceConfiguration config = ServiceConfiguration.create();
        
        // Create all services with injected dependencies
        try (MusicBrainzService musicBrainzService = config.createMusicBrainzService();
             ParallelMusicScanner scanner = config.createParallelMusicScanner(100, true, true)) {
            
            // Create batch processor
            ProgressTracker progressTracker = new ProgressTracker();
            BatchMetadataProcessor.BatchConfig batchConfig = 
                BatchMetadataProcessor.BatchConfig.defaultConfig();
            
            try (BatchMetadataProcessor processor = config.createBatchMetadataProcessor(
                    musicBrainzService, batchConfig, progressTracker)) {
                
                Path musicDir = Paths.get("E:/Music");
                if (musicDir.toFile().exists()) {
                    // Scan directory
                    var scanResult = scanner.scanDirectory(musicDir);
                    
                    // Process metadata in batches
                    scanResult.match(
                        success -> {
                            List<Path> audioPaths = success.audioFiles().stream()
                                .map(audioFile -> audioFile.path())
                                .limit(10) // Process first 10 files
                                .toList();
                            
                            try {
                                var processingResult = processor.processFiles(audioPaths, 
                                    metadata -> System.out.println("Processed: " + metadata.title()));
                                
                                System.out.println("Processing completed: " + 
                                    processingResult.successful().size() + " successful, " +
                                    processingResult.failed().size() + " failed");
                            } catch (Exception e) {
                                System.err.println("Processing error: " + e.getMessage());
                            }
                            return 0;
                        },
                        failure -> {
                            System.err.println("Scan failed: " + failure.errorMessage());
                            return 1;
                        },
                        partial -> {
                            System.out.println("Scan completed with warnings: " + 
                                partial.audioFiles().size() + " files processed");
                            return 0;
                        }
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Workflow error: " + e.getMessage());
        }
    }
    
    /**
     * Simple synchronous executor for testing.
     */
    private static class SynchronousExecutorService implements ExecutorService {
        private boolean shutdown = false;
        
        @Override
        public void shutdown() {
            shutdown = true;
        }
        
        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }
        
        @Override
        public boolean isShutdown() {
            return shutdown;
        }
        
        @Override
        public boolean isTerminated() {
            return shutdown;
        }
        
        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
        
        @Override
        public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
            try {
                T result = task.call();
                return java.util.concurrent.CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
        }
        
        @Override
        public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            task.run();
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        }
        
        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            task.run();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        
        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(
                java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            return tasks.stream()
                .map(this::submit)
                .toList();
        }
        
        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(
                java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
                long timeout, TimeUnit unit) {
            return invokeAll(tasks);
        }
        
        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) 
                throws java.util.concurrent.ExecutionException {
            if (tasks.isEmpty()) {
                throw new IllegalArgumentException("Empty task collection");
            }
            
            try {
                return tasks.iterator().next().call();
            } catch (Exception e) {
                throw new java.util.concurrent.ExecutionException(e);
            }
        }
        
        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
                long timeout, TimeUnit unit) 
                throws java.util.concurrent.ExecutionException {
            return invokeAny(tasks);
        }
        
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}