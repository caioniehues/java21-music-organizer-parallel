package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.musicorganizer.model.AudioFile;
import java.util.Objects;

/**
 * File rule that matches audio files based on genre classification.
 * 
 * <p>This rule evaluates audio files against genre metadata, enabling
 * genre-specific organization profiles (e.g., classical music with movement
 * information, jazz with detailed performer credits, electronic music with
 * remix variations).</p>
 * 
 * @param genre the genre string to match (case-insensitive)
 * @param targetProfile the organization profile to use for matching files
 * 
 * @since 1.0
 */
public record GenreRule(
    @JsonProperty("genre") String genre,
    @JsonProperty("target_profile") String targetProfile
) implements FileRule {
    
    /**
     * Creates a GenreRule with validation.
     * 
     * @param genre the genre to match
     * @param targetProfile target organization profile
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public GenreRule {
        Objects.requireNonNull(genre, "Genre cannot be null");
        Objects.requireNonNull(targetProfile, "Target profile cannot be null");
        
        if (genre.trim().isEmpty()) {
            throw new IllegalArgumentException("Genre cannot be empty");
        }
        
        if (targetProfile.trim().isEmpty()) {
            throw new IllegalArgumentException("Target profile cannot be empty");
        }
        
        genre = genre.trim();
        targetProfile = targetProfile.trim();
    }
    
    @Override
    public boolean matches(AudioFile audioFile) {
        Objects.requireNonNull(audioFile, "Audio file cannot be null");
        
        if (audioFile.metadata() == null) {
            return false;
        }
        
        return audioFile.metadata()
            .genre()
            .map(fileGenre -> fileGenre.toLowerCase().contains(genre.toLowerCase()))
            .orElse(false);
    }
    
    @Override
    public String getTargetProfile() {
        return targetProfile;
    }
    
    @Override
    public String getDescription() {
        return String.format("Genre contains '%s' → %s", genre, targetProfile);
    }
    
    /**
     * Creates a rule for classical music files.
     * 
     * @param targetProfile the profile for classical music
     * @return a GenreRule for classical music
     */
    public static GenreRule classical(String targetProfile) {
        return new GenreRule("classical", targetProfile);
    }
    
    /**
     * Creates a rule for jazz music files.
     * 
     * @param targetProfile the profile for jazz music
     * @return a GenreRule for jazz music
     */
    public static GenreRule jazz(String targetProfile) {
        return new GenreRule("jazz", targetProfile);
    }
    
    /**
     * Creates a rule for electronic music files.
     * 
     * @param targetProfile the profile for electronic music
     * @return a GenreRule for electronic music
     */
    public static GenreRule electronic(String targetProfile) {
        return new GenreRule("electronic", targetProfile);
    }
    
    /**
     * Creates a rule for rock music files.
     * 
     * @param targetProfile the profile for rock music
     * @return a GenreRule for rock music
     */
    public static GenreRule rock(String targetProfile) {
        return new GenreRule("rock", targetProfile);
    }
    
    /**
     * Creates a rule for pop music files.
     * 
     * @param targetProfile the profile for pop music
     * @return a GenreRule for pop music
     */
    public static GenreRule pop(String targetProfile) {
        return new GenreRule("pop", targetProfile);
    }
}