package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.musicorganizer.model.AudioFile;
import java.util.Objects;

/**
 * File rule that matches audio files based on minimum bitrate requirements.
 * 
 * <p>This rule evaluates audio files against a minimum bitrate threshold,
 * allowing high-quality files to be organized into specialized profiles
 * (e.g., lossless collections, audiophile libraries).</p>
 * 
 * @param minBitrate the minimum bitrate in kbps for files to match this rule
 * @param targetProfile the organization profile to use for matching files
 * 
 * @since 1.0
 */
public record BitrateRule(
    @JsonProperty("min_bitrate") int minBitrate,
    @JsonProperty("target_profile") String targetProfile
) implements FileRule {
    
    /**
     * Creates a BitrateRule with validation.
     * 
     * @param minBitrate minimum bitrate in kbps
     * @param targetProfile target organization profile
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public BitrateRule {
        if (minBitrate <= 0) {
            throw new IllegalArgumentException("Min bitrate must be positive: " + minBitrate);
        }
        
        if (minBitrate > 9216) { // Max reasonable bitrate for audio
            throw new IllegalArgumentException("Min bitrate too high (max 9216 kbps): " + minBitrate);
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
        
        if (audioFile.metadata() == null) {
            return false;
        }
        
        return audioFile.metadata()
            .bitrate()
            .map(bitrate -> bitrate >= minBitrate)
            .orElse(false);
    }
    
    @Override
    public String getTargetProfile() {
        return targetProfile;
    }
    
    @Override
    public String getDescription() {
        return String.format("Bitrate >= %d kbps → %s", minBitrate, targetProfile);
    }
    
    /**
     * Creates a rule for high-quality audio files (320+ kbps).
     * 
     * @param targetProfile the profile for high-quality files
     * @return a BitrateRule for high-quality audio
     */
    public static BitrateRule highQuality(String targetProfile) {
        return new BitrateRule(320, targetProfile);
    }
    
    /**
     * Creates a rule for lossless audio files (typically 1000+ kbps).
     * 
     * @param targetProfile the profile for lossless files
     * @return a BitrateRule for lossless audio
     */
    public static BitrateRule lossless(String targetProfile) {
        return new BitrateRule(1000, targetProfile);
    }
    
    /**
     * Creates a rule for CD-quality audio files (typically 1411 kbps).
     * 
     * @param targetProfile the profile for CD-quality files
     * @return a BitrateRule for CD-quality audio
     */
    public static BitrateRule cdQuality(String targetProfile) {
        return new BitrateRule(1411, targetProfile);
    }
}