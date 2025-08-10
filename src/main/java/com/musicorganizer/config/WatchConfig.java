package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.musicorganizer.config.serialization.DurationDeserializer;
import com.musicorganizer.config.serialization.DurationSerializer;

import java.time.Duration;
import java.util.Objects;

/**
 * Record representing file system watch service configuration.
 * 
 * <p>This record configures the behavior of the file system watcher that monitors
 * source directories for new music files, including polling intervals, batch processing
 * settings, and virtual thread pool configuration.</p>
 * 
 * @param enabled whether the watch service is active
 * @param pollInterval how often to check for new files
 * @param stabilityDelay time to wait before processing new files to ensure they're complete
 * @param batchSize number of files to process in a single batch
 * @param virtualThreadPoolSize maximum number of virtual threads for concurrent processing
 * 
 * @since 1.0
 */
public record WatchConfig(
    @JsonProperty("enabled") boolean enabled,
    
    @JsonProperty("poll_interval")
    @JsonSerialize(using = DurationSerializer.class)
    @JsonDeserialize(using = DurationDeserializer.class)
    Duration pollInterval,
    
    @JsonProperty("stability_delay")
    @JsonSerialize(using = DurationSerializer.class)
    @JsonDeserialize(using = DurationDeserializer.class)
    Duration stabilityDelay,
    
    @JsonProperty("batch_size") int batchSize,
    @JsonProperty("virtual_thread_pool_size") int virtualThreadPoolSize
) {
    
    /**
     * Creates a WatchConfig record with validation.
     * 
     * @param enabled whether watching is enabled
     * @param pollInterval how often to poll for changes
     * @param stabilityDelay delay before processing new files
     * @param batchSize files per batch
     * @param virtualThreadPoolSize thread pool size
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public WatchConfig {
        Objects.requireNonNull(pollInterval, "Poll interval cannot be null");
        Objects.requireNonNull(stabilityDelay, "Stability delay cannot be null");
        
        if (!pollInterval.isPositive()) {
            throw new IllegalArgumentException("Poll interval must be positive: " + pollInterval);
        }
        
        if (!stabilityDelay.isPositive()) {
            throw new IllegalArgumentException("Stability delay must be positive: " + stabilityDelay);
        }
        
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive: " + batchSize);
        }
        
        if (virtualThreadPoolSize <= 0) {
            throw new IllegalArgumentException("Virtual thread pool size must be positive: " + virtualThreadPoolSize);
        }
        
        if (virtualThreadPoolSize > 10000) {
            throw new IllegalArgumentException("Virtual thread pool size too large (max 10000): " + virtualThreadPoolSize);
        }
        
        // Warn if poll interval is too frequent
        if (pollInterval.toMillis() < 1000) {
            System.err.println("Warning: Poll interval less than 1 second may cause high CPU usage");
        }
        
        // Ensure stability delay is reasonable relative to poll interval
        if (stabilityDelay.compareTo(pollInterval.multipliedBy(2)) < 0) {
            System.err.println("Warning: Stability delay should be at least 2x poll interval to prevent incomplete file processing");
        }
    }
    
    /**
     * Creates a default WatchConfig with sensible values for most use cases.
     * 
     * @return a WatchConfig with default settings
     */
    public static WatchConfig defaultConfig() {
        return new WatchConfig(
            true,
            Duration.ofMinutes(1),  // Check every minute
            Duration.ofSeconds(30), // Wait 30 seconds for file stability
            50,                     // Process 50 files per batch
            1000                    // Use up to 1000 virtual threads
        );
    }
    
    /**
     * Creates a WatchConfig optimized for high-performance scenarios.
     * 
     * @return a WatchConfig with aggressive settings for maximum throughput
     */
    public static WatchConfig highPerformanceConfig() {
        return new WatchConfig(
            true,
            Duration.ofSeconds(10), // Check every 10 seconds
            Duration.ofSeconds(15), // Shorter stability delay
            200,                    // Larger batches
            2000                    // More virtual threads
        );
    }
    
    /**
     * Creates a WatchConfig for low-resource environments.
     * 
     * @return a WatchConfig with conservative settings
     */
    public static WatchConfig lowResourceConfig() {
        return new WatchConfig(
            true,
            Duration.ofMinutes(5),  // Less frequent polling
            Duration.ofMinutes(1),  // Longer stability delay
            20,                     // Smaller batches
            100                     // Fewer threads
        );
    }
    
    /**
     * Creates a disabled WatchConfig.
     * 
     * @return a WatchConfig with watching disabled
     */
    public static WatchConfig disabledConfig() {
        return new WatchConfig(
            false,
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            50,
            1000
        );
    }
    
    /**
     * Creates a new WatchConfig with the specified enabled state.
     * 
     * @param enabled the new enabled state
     * @return a new WatchConfig with updated enabled state
     */
    public WatchConfig withEnabled(boolean enabled) {
        return new WatchConfig(enabled, pollInterval, stabilityDelay, batchSize, virtualThreadPoolSize);
    }
    
    /**
     * Creates a new WatchConfig with the specified batch size.
     * 
     * @param batchSize the new batch size
     * @return a new WatchConfig with updated batch size
     */
    public WatchConfig withBatchSize(int batchSize) {
        return new WatchConfig(enabled, pollInterval, stabilityDelay, batchSize, virtualThreadPoolSize);
    }
}