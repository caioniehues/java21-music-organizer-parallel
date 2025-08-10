package com.musicorganizer.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Factory interface for creating ExecutorService instances optimized for the music organizer.
 * Enables dependency injection, testability, and consistent executor configuration across the application.
 * 
 * <p>This interface provides methods for creating both virtual thread executors (preferred for I/O-bound
 * tasks like file processing) and traditional thread pools (for CPU-bound operations).</p>
 * 
 * <p>Virtual threads are the preferred choice for most operations in this application as they enable
 * massive concurrency for file processing and network operations with minimal resource overhead.</p>
 * 
 * @since 1.0
 * @see ExecutorService
 * @see Executors#newVirtualThreadPerTaskExecutor()
 */
public interface ExecutorServiceFactory {
    
    /**
     * Creates a new virtual thread executor for highly concurrent I/O-bound operations.
     * 
     * <p>Virtual threads are ideal for the music organizer's file processing tasks as they:
     * <ul>
     *   <li>Allow thousands of concurrent file operations without resource exhaustion</li>
     *   <li>Automatically handle blocking I/O operations efficiently</li>
     *   <li>Reduce memory overhead compared to platform threads</li>
     *   <li>Simplify concurrent programming by eliminating thread pool sizing concerns</li>
     * </ul>
     * 
     * <p>The returned executor creates a new virtual thread for each submitted task,
     * making it perfect for parallel file scanning, metadata extraction, and duplicate detection.</p>
     * 
     * @return a new virtual thread executor service, never null
     */
    ExecutorService createVirtualThreadExecutor();
    
    /**
     * Creates a fixed thread pool executor with the specified number of platform threads.
     * 
     * <p>Fixed thread pools are suitable for CPU-intensive tasks where thread count
     * should be limited to available processor cores. Use this for operations like:</p>
     * <ul>
     *   <li>Audio file processing and transcoding</li>
     *   <li>Cryptographic hash calculations</li>
     *   <li>Complex metadata analysis</li>
     * </ul>
     * 
     * @param threads the number of threads in the pool; must be positive
     * @return a fixed thread pool executor service with the specified thread count
     * @throws IllegalArgumentException if threads is not positive
     */
    ExecutorService createFixedThreadPool(int threads);
    
    /**
     * Creates a default ExecutorServiceFactory with production-optimized settings.
     * 
     * <p>The default factory creates executors with:</p>
     * <ul>
     *   <li>Virtual thread executors for maximum I/O concurrency</li>
     *   <li>Named thread pools for easier debugging and monitoring</li>
     *   <li>Appropriate default configurations for the music organizer use case</li>
     * </ul>
     * 
     * @return a default ExecutorServiceFactory implementation
     */
    static ExecutorServiceFactory defaultFactory() {
        return new DefaultExecutorServiceFactory();
    }
    
    /**
     * Creates a factory that produces executors optimized for the given hardware configuration.
     * 
     * @param availableProcessors the number of available processors
     * @return an ExecutorServiceFactory tuned for the hardware
     */
    static ExecutorServiceFactory forHardware(int availableProcessors) {
        return new HardwareOptimizedExecutorServiceFactory(availableProcessors);
    }
}

/**
 * Default production implementation of ExecutorServiceFactory.
 * 
 * <p>This implementation creates executors optimized for the music organizer's workload,
 * emphasizing virtual threads for I/O operations and appropriately sized thread pools
 * for CPU-bound tasks.</p>
 */
final class DefaultExecutorServiceFactory implements ExecutorServiceFactory {
    
    @Override
    public ExecutorService createVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    @Override
    public ExecutorService createFixedThreadPool(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Thread count must be positive, got: " + threads);
        }
        
        // Create thread factory with meaningful names for debugging
        ThreadFactory threadFactory = Thread.ofPlatform()
            .name("music-organizer-worker-", 0)
            .factory();
        
        return Executors.newFixedThreadPool(threads, threadFactory);
    }
}

/**
 * Hardware-optimized implementation that adjusts executor configuration based on available processors.
 */
final class HardwareOptimizedExecutorServiceFactory implements ExecutorServiceFactory {
    
    private final int availableProcessors;
    
    HardwareOptimizedExecutorServiceFactory(int availableProcessors) {
        if (availableProcessors <= 0) {
            throw new IllegalArgumentException("Available processors must be positive");
        }
        this.availableProcessors = availableProcessors;
    }
    
    @Override
    public ExecutorService createVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    @Override
    public ExecutorService createFixedThreadPool(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Thread count must be positive, got: " + threads);
        }
        
        // Cap thread count to reasonable multiple of available processors
        int effectiveThreads = Math.min(threads, availableProcessors * 4);
        
        ThreadFactory threadFactory = Thread.ofPlatform()
            .name("music-organizer-cpu-worker-", 0)
            .factory();
        
        return Executors.newFixedThreadPool(effectiveThreads, threadFactory);
    }
}