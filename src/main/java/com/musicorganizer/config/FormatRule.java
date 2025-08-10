package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.musicorganizer.model.AudioFile;
import java.util.List;
import java.util.Objects;

/**
 * File rule that matches audio files based on their format/extension.
 * 
 * <p>This rule evaluates audio files against a list of supported formats,
 * enabling format-specific organization profiles (e.g., lossless formats
 * to archival storage, lossy formats to streaming libraries).</p>
 * 
 * @param formats list of file extensions to match (case-insensitive, without dots)
 * @param targetProfile the organization profile to use for matching files
 * 
 * @since 1.0
 */
public record FormatRule(
    @JsonProperty("formats") List<String> formats,
    @JsonProperty("target_profile") String targetProfile
) implements FileRule {
    
    /**
     * Creates a FormatRule with validation.
     * 
     * @param formats list of file extensions to match
     * @param targetProfile target organization profile
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public FormatRule {
        Objects.requireNonNull(formats, "Formats list cannot be null");
        Objects.requireNonNull(targetProfile, "Target profile cannot be null");
        
        if (formats.isEmpty()) {
            throw new IllegalArgumentException("Formats list cannot be empty");
        }
        
        if (targetProfile.trim().isEmpty()) {
            throw new IllegalArgumentException("Target profile cannot be empty");
        }
        
        // Normalize formats to lowercase without dots
        formats = formats.stream()
            .filter(Objects::nonNull)
            .map(format -> format.toLowerCase().replaceFirst("^\\.", ""))
            .filter(format -> !format.isEmpty())
            .distinct()
            .toList();
        
        if (formats.isEmpty()) {
            throw new IllegalArgumentException("No valid formats provided after normalization");
        }
        
        targetProfile = targetProfile.trim();
    }
    
    @Override
    public boolean matches(AudioFile audioFile) {
        Objects.requireNonNull(audioFile, "Audio file cannot be null");
        
        String extension = getFileExtension(audioFile.path().toString()).toLowerCase();
        return formats.contains(extension);
    }
    
    @Override
    public String getTargetProfile() {
        return targetProfile;
    }
    
    @Override
    public String getDescription() {
        return String.format("Format in [%s] → %s", String.join(", ", formats), targetProfile);
    }
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot + 1);
    }
    
    /**
     * Creates a rule for lossless audio formats.
     * 
     * @param targetProfile the profile for lossless formats
     * @return a FormatRule for lossless audio formats
     */
    public static FormatRule lossless(String targetProfile) {
        return new FormatRule(
            List.of("flac", "wav", "aiff", "alac", "ape", "wv", "dsd", "dsf", "dff"),
            targetProfile
        );
    }
    
    /**
     * Creates a rule for lossy compressed audio formats.
     * 
     * @param targetProfile the profile for lossy formats
     * @return a FormatRule for lossy audio formats
     */
    public static FormatRule lossy(String targetProfile) {
        return new FormatRule(
            List.of("mp3", "aac", "m4a", "ogg", "opus", "wma"),
            targetProfile
        );
    }
    
    /**
     * Creates a rule for high-resolution audio formats.
     * 
     * @param targetProfile the profile for high-resolution formats
     * @return a FormatRule for high-resolution audio formats
     */
    public static FormatRule highResolution(String targetProfile) {
        return new FormatRule(
            List.of("dsd", "dsf", "dff", "flac"),
            targetProfile
        );
    }
    
    /**
     * Creates a rule for mobile-friendly audio formats.
     * 
     * @param targetProfile the profile for mobile formats
     * @return a FormatRule for mobile-friendly audio formats
     */
    public static FormatRule mobileFriendly(String targetProfile) {
        return new FormatRule(
            List.of("mp3", "aac", "m4a", "opus"),
            targetProfile
        );
    }
    
    /**
     * Creates a rule for archival-quality formats.
     * 
     * @param targetProfile the profile for archival formats
     * @return a FormatRule for archival audio formats
     */
    public static FormatRule archival(String targetProfile) {
        return new FormatRule(
            List.of("flac", "wav", "aiff"),
            targetProfile
        );
    }
    
    /**
     * Creates a rule for streaming-optimized formats.
     * 
     * @param targetProfile the profile for streaming formats
     * @return a FormatRule for streaming-optimized formats
     */
    public static FormatRule streaming(String targetProfile) {
        return new FormatRule(
            List.of("mp3", "aac", "ogg", "opus"),
            targetProfile
        );
    }
}