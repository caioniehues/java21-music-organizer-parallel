package com.musicorganizer.service;

import com.musicorganizer.processor.BatchMetadataProcessor;

/**
 * Service configuration builder for dependency injection.
 * Provides a fluent API for configuring services with their dependencies.
 */
public class ServiceConfiguration {
    
    private HttpClientProvider httpClientProvider = HttpClientProvider.defaultProvider();
    private ExecutorServiceFactory executorServiceFactory = ExecutorServiceFactory.defaultFactory();
    private UrlEncoder urlEncoder = UrlEncoder.defaultEncoder();
    
    public static ServiceConfiguration create() {
        return new ServiceConfiguration();
    }
    
    public ServiceConfiguration withHttpClientProvider(HttpClientProvider provider) {
        this.httpClientProvider = provider;
        return this;
    }
    
    public ServiceConfiguration withExecutorServiceFactory(ExecutorServiceFactory factory) {
        this.executorServiceFactory = factory;
        return this;
    }
    
    public ServiceConfiguration withUrlEncoder(UrlEncoder encoder) {
        this.urlEncoder = encoder;
        return this;
    }
    
    /**
     * Creates a configured MusicBrainzService.
     */
    public MusicBrainzService createMusicBrainzService() {
        return new MusicBrainzService(httpClientProvider, executorServiceFactory, urlEncoder);
    }
    
    /**
     * Creates a configured BatchMetadataProcessor.
     */
    public BatchMetadataProcessor createBatchMetadataProcessor(
            MusicBrainzService musicBrainzService,
            BatchMetadataProcessor.BatchConfig config,
            com.musicorganizer.util.ProgressTracker progressTracker) {
        return new BatchMetadataProcessor(musicBrainzService, config, progressTracker, executorServiceFactory);
    }
    
    /**
     * Creates a configured ParallelMusicScanner.
     */
    public com.musicorganizer.scanner.ParallelMusicScanner createParallelMusicScanner(
            int maxConcurrentFiles, boolean calculateChecksums, boolean deepScan) {
        return new com.musicorganizer.scanner.ParallelMusicScanner(
            maxConcurrentFiles, calculateChecksums, deepScan, executorServiceFactory);
    }
    
    // Getters for testing
    public HttpClientProvider getHttpClientProvider() {
        return httpClientProvider;
    }
    
    public ExecutorServiceFactory getExecutorServiceFactory() {
        return executorServiceFactory;
    }
    
    public UrlEncoder getUrlEncoder() {
        return urlEncoder;
    }
}