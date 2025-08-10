package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Record representing file filtering criteria for music file processing.
 * 
 * <p>This record encapsulates various filters that can be applied to determine
 * which files should be processed, including file extensions, size limits,
 * and exclusion patterns.</p>
 * 
 * @param extensions list of allowed file extensions (e.g., "mp3", "flac")
 * @param minSize minimum file size in bytes (optional)
 * @param maxSize maximum file size in bytes (optional)
 * @param excludePatterns list of regex patterns for files to exclude
 * 
 * @since 1.0
 */
public record FileFilters(
    @JsonProperty("extensions") List<String> extensions,
    @JsonProperty("min_size") Optional<Long> minSize,
    @JsonProperty("max_size") Optional<Long> maxSize,
    @JsonProperty("exclude_patterns") List<String> excludePatterns
) {
    
    /**
     * Creates a FileFilters record with validation.
     * 
     * @param extensions list of allowed file extensions
     * @param minSize minimum file size (optional)
     * @param maxSize maximum file size (optional)
     * @param excludePatterns list of exclusion patterns
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public FileFilters {
        Objects.requireNonNull(extensions, "Extensions list cannot be null");
        Objects.requireNonNull(excludePatterns, "Exclude patterns list cannot be null");
        Objects.requireNonNull(minSize, "Min size optional cannot be null");
        Objects.requireNonNull(maxSize, "Max size optional cannot be null");
        
        // Validate size constraints
        if (minSize.isPresent() && minSize.get() < 0) {
            throw new IllegalArgumentException("Min size cannot be negative: " + minSize.get());
        }
        
        if (maxSize.isPresent() && maxSize.get() < 0) {
            throw new IllegalArgumentException("Max size cannot be negative: " + maxSize.get());
        }
        
        if (minSize.isPresent() && maxSize.isPresent() && minSize.get() > maxSize.get()) {
            throw new IllegalArgumentException(
                "Min size (" + minSize.get() + ") cannot be greater than max size (" + maxSize.get() + ")"
            );
        }
        
        // Normalize extensions to lowercase without dots
        extensions = extensions.stream()
            .map(ext -> ext.toLowerCase().replaceFirst("^\\.", ""))
            .distinct()
            .toList();
        
        // Validate exclude patterns compile as regex
        for (String pattern : excludePatterns) {
            try {
                java.util.regex.Pattern.compile(pattern);
            } catch (java.util.regex.PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex pattern: " + pattern, e);
            }
        }
    }
    
    /**
     * Creates a default FileFilters with common audio extensions.
     * 
     * @return a FileFilters instance with default settings
     */
    public static FileFilters defaultFilters() {
        return new FileFilters(
            List.of("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma"),
            Optional.empty(),
            Optional.empty(),
            List.of()
        );
    }
    
    /**
     * Creates a FileFilters for high-quality audio only.
     * 
     * @return a FileFilters instance for lossless and high-bitrate formats
     */
    public static FileFilters highQualityFilters() {
        return new FileFilters(
            List.of("flac", "wav", "dsd", "dsf"),
            Optional.of(10_000_000L), // 10MB minimum
            Optional.empty(),
            List.of(".*\\.tmp$", ".*\\.temp$")
        );
    }
    
    /**
     * Checks if a filename matches these filters.
     * 
     * @param filename the filename to check
     * @param fileSize the file size in bytes
     * @return true if the file matches all filter criteria
     */
    public boolean matches(String filename, long fileSize) {
        // Check extension
        String extension = getFileExtension(filename).toLowerCase();
        if (!extensions.contains(extension)) {
            return false;
        }
        
        // Check size constraints
        if (minSize.isPresent() && fileSize < minSize.get()) {
            return false;
        }
        
        if (maxSize.isPresent() && fileSize > maxSize.get()) {
            return false;
        }
        
        // Check exclusion patterns
        for (String pattern : excludePatterns) {
            if (filename.matches(pattern)) {
                return false;
            }
        }
        
        return true;
    }
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot + 1);
    }
}