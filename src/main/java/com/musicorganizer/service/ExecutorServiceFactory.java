package com.musicorganizer.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Factory interface for creating ExecutorService instances, enabling dependency injection.
 */
public interface ExecutorServiceFactory {
    
    /**
     * Creates a new virtual thread executor.
     */
    ExecutorService createVirtualThreadExecutor();
    
    /**
     * Creates a fixed thread pool executor.
     */
    ExecutorService createFixedThreadPool(int nThreads);
    
    /**
     * Default factory for production use.
     */
    static ExecutorServiceFactory defaultFactory() {
        return new DefaultExecutorServiceFactory();
    }
}

/**
 * Production implementation of ExecutorServiceFactory.
 */
class DefaultExecutorServiceFactory implements ExecutorServiceFactory {
    
    @Override
    public ExecutorService createVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    @Override
    public ExecutorService createFixedThreadPool(int nThreads) {
        return Executors.newFixedThreadPool(nThreads);
    }
}