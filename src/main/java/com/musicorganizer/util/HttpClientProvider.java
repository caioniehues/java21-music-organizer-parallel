package com.musicorganizer.util;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Interface for providing HttpClient instances with configurable timeouts.
 * Enables dependency injection, testability, and flexible HTTP client configuration.
 * 
 * <p>This interface follows Java 21 best practices with default implementations
 * and factory methods for common use cases.</p>
 * 
 * @since 1.0
 * @see HttpClient
 */
@FunctionalInterface
public interface HttpClientProvider {
    
    /**
     * Creates a new HttpClient with the specified connection timeout.
     * 
     * <p>The returned HttpClient should be configured with appropriate settings
     * for the music organizer application, including proper timeout handling,
     * connection pooling, and error handling.</p>
     * 
     * @param connectTimeout the maximum time to wait for a connection to be established;
     *                       must not be null or negative
     * @return a configured HttpClient instance, never null
     * @throws IllegalArgumentException if connectTimeout is null or negative
     */
    HttpClient createHttpClient(Duration connectTimeout);
    
    /**
     * Creates a default HttpClientProvider with standard production settings.
     * 
     * <p>The default implementation creates HttpClient instances with:</p>
     * <ul>
     *   <li>HTTP/2 support with fallback to HTTP/1.1</li>
     *   <li>Automatic redirect following</li>
     *   <li>Configurable connection timeout</li>
     *   <li>Default cookie and authentication handling</li>
     * </ul>
     * 
     * @return a default HttpClientProvider implementation
     */
    static HttpClientProvider defaultProvider() {
        return new DefaultHttpClientProvider();
    }
    
    /**
     * Creates an HttpClientProvider that always uses the given timeout.
     * 
     * @param fixedTimeout the timeout to use for all connections
     * @return an HttpClientProvider that ignores the timeout parameter
     */
    static HttpClientProvider withFixedTimeout(Duration fixedTimeout) {
        return connectTimeout -> createStandardClient(fixedTimeout);
    }
    
    /**
     * Creates a standard HttpClient with the given timeout.
     * 
     * @param timeout the connection timeout
     * @return a configured HttpClient
     */
    private static HttpClient createStandardClient(Duration timeout) {
        return HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();
    }
}

/**
 * Default production implementation of HttpClientProvider.
 * 
 * <p>This implementation creates HttpClient instances optimized for the music
 * organizer's needs, particularly for MusicBrainz API calls and metadata fetching.</p>
 */
final class DefaultHttpClientProvider implements HttpClientProvider {
    
    @Override
    public HttpClient createHttpClient(Duration connectTimeout) {
        if (connectTimeout == null) {
            throw new IllegalArgumentException("Connect timeout cannot be null");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }
        
        return HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();
    }
}