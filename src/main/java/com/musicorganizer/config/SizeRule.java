package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.musicorganizer.model.AudioFile;
import java.util.Objects;

/**
 * File rule that matches audio files based on file size constraints.
 * 
 * <p>This rule evaluates audio files against minimum and maximum file size
 * thresholds, enabling organization based on file quality and storage
 * considerations (e.g., large lossless files vs. compressed mobile formats).</p>
 * 
 * @param minSize minimum file size in bytes (inclusive)
 * @param maxSize maximum file size in bytes (inclusive)
 * @param targetProfile the organization profile to use for matching files
 * 
 * @since 1.0
 */
public record SizeRule(
    @JsonProperty("min_size") long minSize,
    @JsonProperty("max_size") long maxSize,
    @JsonProperty("target_profile") String targetProfile
) implements FileRule {
    
    /**
     * Creates a SizeRule with validation.
     * 
     * @param minSize minimum file size in bytes
     * @param maxSize maximum file size in bytes
     * @param targetProfile target organization profile
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public SizeRule {
        if (minSize < 0) {
            throw new IllegalArgumentException("Min size cannot be negative: " + minSize);
        }
        
        if (maxSize < 0) {
            throw new IllegalArgumentException("Max size cannot be negative: " + maxSize);
        }
        
        if (minSize > maxSize) {
            throw new IllegalArgumentException(
                "Min size (" + minSize + ") cannot be greater than max size (" + maxSize + ")"
            );
        }
        
        Objects.requireNonNull(targetProfile, "Target profile cannot be null");
        if (targetProfile.trim().isEmpty()) {
            throw new IllegalArgumentException("Target profile cannot be empty");
        }
        
        targetProfile = targetProfile.trim();
    }
    
    @Override
    public boolean matches(AudioFile audioFile) {
        Objects.requireNonNull(audioFile, "Audio file cannot be null");
        
        long fileSize = audioFile.size();
        return fileSize >= minSize && fileSize <= maxSize;
    }
    
    @Override
    public String getTargetProfile() {
        return targetProfile;
    }
    
    @Override
    public String getDescription() {
        return String.format("Size %s - %s → %s", 
            formatBytes(minSize), 
            formatBytes(maxSize), 
            targetProfile);
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
    
    /**
     * Creates a rule for small compressed audio files (under 10MB).
     * 
     * @param targetProfile the profile for small files
     * @return a SizeRule for small audio files
     */
    public static SizeRule smallFiles(String targetProfile) {
        return new SizeRule(0, 10 * 1024 * 1024, targetProfile); // 10MB
    }
    
    /**
     * Creates a rule for medium-sized audio files (10MB to 50MB).
     * 
     * @param targetProfile the profile for medium files
     * @return a SizeRule for medium-sized audio files
     */
    public static SizeRule mediumFiles(String targetProfile) {
        return new SizeRule(10 * 1024 * 1024, 50 * 1024 * 1024, targetProfile); // 10-50MB
    }
    
    /**
     * Creates a rule for large lossless audio files (over 50MB).
     * 
     * @param targetProfile the profile for large files
     * @return a SizeRule for large audio files
     */
    public static SizeRule largeFiles(String targetProfile) {
        return new SizeRule(50 * 1024 * 1024, Long.MAX_VALUE, targetProfile); // 50MB+
    }
    
    /**
     * Creates a rule for high-quality files typical of lossless formats.
     * 
     * @param targetProfile the profile for high-quality files
     * @return a SizeRule for high-quality audio files
     */
    public static SizeRule highQuality(String targetProfile) {
        return new SizeRule(20 * 1024 * 1024, Long.MAX_VALUE, targetProfile); // 20MB+
    }
    
    /**
     * Creates a rule for mobile-optimized compressed files.
     * 
     * @param targetProfile the profile for mobile files
     * @return a SizeRule for mobile-optimized files
     */
    public static SizeRule mobileOptimized(String targetProfile) {
        return new SizeRule(0, 8 * 1024 * 1024, targetProfile); // Under 8MB
    }
}