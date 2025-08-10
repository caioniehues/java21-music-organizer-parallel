package com.musicorganizer.service;

import com.musicorganizer.model.TrackMetadata;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MusicBrainzService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MusicBrainzService.class.getName());
    private static final String BASE_URL = "https://musicbrainz.org/ws/2";
    private static final String USER_AGENT = "MusicOrganizer/1.0 (https://github.com/example/music-organizer)";
    private static final int RATE_LIMIT_PER_SECOND = 1;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final Semaphore rateLimiter;
    private final Map<String, CachedResult> cache;
    private final long cacheExpirationMs = TimeUnit.HOURS.toMillis(24);
    private final boolean ownExecutorService;
    
    /**
     * Default constructor that creates default dependencies.
     * Maintains backward compatibility.
     */
    public MusicBrainzService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.rateLimiter = new Semaphore(RATE_LIMIT_PER_SECOND);
        this.cache = new ConcurrentHashMap<>();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.ownExecutorService = true;
    }
    
    /**
     * Constructor with dependency injection support.
     * 
     * @param httpClient the HTTP client to use for requests
     * @param rateLimiter the semaphore for rate limiting API calls
     * @param cache the cache map for storing results
     */
    public MusicBrainzService(HttpClient httpClient, Semaphore rateLimiter, Map<String, CachedResult> cache) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient cannot be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "Semaphore cannot be null");
        this.cache = Objects.requireNonNull(cache, "Cache cannot be null");
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.ownExecutorService = true;
    }
    
    /**
     * Constructor with full dependency injection including custom executor service.
     * 
     * @param httpClient the HTTP client to use for requests
     * @param rateLimiter the semaphore for rate limiting API calls
     * @param cache the cache map for storing results
     * @param executorService the executor service for async operations
     */
    public MusicBrainzService(HttpClient httpClient, Semaphore rateLimiter, 
                             Map<String, CachedResult> cache, ExecutorService executorService) {
        this.httpClient = Objects.requireNonNull(httpClient, "HttpClient cannot be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "Semaphore cannot be null");
        this.cache = Objects.requireNonNull(cache, "Cache cannot be null");
        this.executorService = Objects.requireNonNull(executorService, "ExecutorService cannot be null");
        this.ownExecutorService = false;
    }
    
    /**
     * @deprecated Use the constructor with dependency injection instead.
     */
    @Deprecated(since = "1.1", forRemoval = false)
    public MusicBrainzService(HttpClientProvider httpClientProvider,
                             ExecutorServiceFactory executorFactory,
                             UrlEncoder urlEncoder) {
        this(httpClientProvider.createClient(),
             new Semaphore(RATE_LIMIT_PER_SECOND),
             new ConcurrentHashMap<>(),
             executorFactory.createVirtualThreadExecutor());
    }
    
    /**
     * Constructor for testing that accepts actual dependencies directly.
     */
    public MusicBrainzService(HttpClient httpClient, 
                             Semaphore rateLimiter, 
                             ConcurrentHashMap<String, CachedResult> cache) {
        this.httpClient = httpClient;
        this.executorService = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.ownExecutorService = true;
    }
    
    public CompletableFuture<Optional<TrackMetadata>> lookupTrack(String artist, String title) {
        return lookupTrack(artist, title, null);
    }
    
    public CompletableFuture<Optional<TrackMetadata>> lookupTrack(String artist, String title, String album) {
        String cacheKey = createCacheKey(artist, title, album);
        
        CachedResult cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.metadata);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.acquire();
                
                String query = buildQuery(artist, title, album);
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                URI uri = new URI(BASE_URL + "/recording/?query=" + encodedQuery + "&fmt=json");
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    Optional<TrackMetadata> metadata = parseResponse(response.body(), artist, title, album);
                    cache.put(cacheKey, new CachedResult(metadata));
                    return metadata;
                } else {
                    LOGGER.warning("MusicBrainz API returned status: " + response.statusCode());
                    return Optional.empty();
                }
                
            } catch (InterruptedException | IOException | URISyntaxException e) {
                LOGGER.log(Level.WARNING, "Error looking up track: " + artist + " - " + title, e);
                return Optional.empty();
            } finally {
                rateLimiter.release();
            }
        }, executorService);
    }
    
    public CompletableFuture<Optional<TrackMetadata>> lookupByMusicBrainzId(String mbid) {
        CachedResult cached = cache.get(mbid);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.metadata);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.acquire();
                
                URI uri = new URI(BASE_URL + "/recording/" + mbid + "?inc=artist-credits+releases&fmt=json");
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", USER_AGENT)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    Optional<TrackMetadata> metadata = parseRecordingResponse(response.body());
                    cache.put(mbid, new CachedResult(metadata));
                    return metadata;
                } else {
                    LOGGER.warning("MusicBrainz API returned status: " + response.statusCode());
                    return Optional.empty();
                }
                
            } catch (InterruptedException | IOException | URISyntaxException e) {
                LOGGER.log(Level.WARNING, "Error looking up MBID: " + mbid, e);
                return Optional.empty();
            } finally {
                rateLimiter.release();
            }
        }, executorService);
    }
    
    private String buildQuery(String artist, String title, String album) {
        StringBuilder query = new StringBuilder();
        
        if (artist != null && !artist.isBlank()) {
            query.append("artist:\"").append(escapeQueryValue(artist)).append("\" ");
        }
        
        if (title != null && !title.isBlank()) {
            query.append("recording:\"").append(escapeQueryValue(title)).append("\" ");
        }
        
        if (album != null && !album.isBlank()) {
            query.append("release:\"").append(escapeQueryValue(album)).append("\"");
        }
        
        return query.toString().trim();
    }
    
    /**
     * Escapes special characters for MusicBrainz query syntax.
     * Properly handles quotes and special characters without double-encoding.
     */
    private String escapeQueryValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // Escape quotes and backslashes for MusicBrainz query syntax
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"");
    }
    
    private String createCacheKey(String artist, String title, String album) {
        return String.format("%s|%s|%s", 
            artist != null ? artist.toLowerCase() : "",
            title != null ? title.toLowerCase() : "",
            album != null ? album.toLowerCase() : "");
    }
    
    private Optional<TrackMetadata> parseResponse(String json, String artist, String title, String album) {
        try {
            TrackMetadata.Builder builder = TrackMetadata.builder()
                .artist(artist)
                .title(title)
                .album(album);
            
            return Optional.of(builder.build());
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing MusicBrainz response", e);
            return Optional.empty();
        }
    }
    
    private Optional<TrackMetadata> parseRecordingResponse(String json) {
        try {
            TrackMetadata.Builder builder = TrackMetadata.builder();
            
            return Optional.of(builder.build());
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing MusicBrainz recording response", e);
            return Optional.empty();
        }
    }
    
    public void clearCache() {
        cache.clear();
    }
    
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Enriches metadata for a given audio file path by extracting basic info
     * and looking up additional metadata from MusicBrainz API.
     */
    public TrackMetadata enrichMetadata(java.nio.file.Path audioFile) throws Exception {
        // Extract filename to get basic info for lookup
        String filename = audioFile.getFileName().toString();
        String[] parts = filename.replaceAll("\\.[^.]+$", "").split(" - ");
        
        String artist = parts.length > 0 ? parts[0].trim() : "Unknown Artist";
        String title = parts.length > 1 ? parts[1].trim() : filename;
        String album = parts.length > 2 ? parts[2].trim() : null;
        
        // Try to lookup enhanced metadata
        Optional<TrackMetadata> enriched = lookupTrack(artist, title, album).get();
        
        // If lookup fails, return basic metadata
        return enriched.orElse(
            TrackMetadata.builder()
                .artist(artist)
                .title(title)
                .album(album)
                .format(getFileExtension(filename))
                .build()
        );
    }
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toUpperCase() : "UNKNOWN";
    }

    /**
     * Shuts down the executor service if it's owned by this instance.
     * If the executor service was injected, it won't be shut down.
     */
    public void shutdown() {
        if (ownExecutorService && executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }
    
    @Override
    public void close() {
        shutdown();
    }
    
    static class CachedResult {
        final Optional<TrackMetadata> metadata;
        final long timestamp;
        
        CachedResult(Optional<TrackMetadata> metadata) {
            this.metadata = metadata;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.HOURS.toMillis(24);
        }
        
        // Package-private constructor for testing
        CachedResult(Optional<TrackMetadata> metadata, long timestamp) {
            this.metadata = metadata;
            this.timestamp = timestamp;
        }
    }
}