package com.musicorganizer.service;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Interface for providing HTTP clients, enabling dependency injection and testing.
 */
public interface HttpClientProvider {
    
    /**
     * Creates a new HTTP client with standard configuration.
     */
    HttpClient createClient();
    
    /**
     * Default implementation for production use.
     */
    static HttpClientProvider defaultProvider() {
        return new DefaultHttpClientProvider();
    }
}

/**
 * Production implementation of HttpClientProvider.
 */
class DefaultHttpClientProvider implements HttpClientProvider {
    
    @Override
    public HttpClient createClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
}