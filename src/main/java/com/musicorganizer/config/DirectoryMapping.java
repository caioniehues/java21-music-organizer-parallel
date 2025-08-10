package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.musicorganizer.config.serialization.PathDeserializer;
import com.musicorganizer.config.serialization.PathSerializer;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Record representing a mapping between source and target directories for music organization.
 * 
 * <p>A directory mapping defines how files from a source directory should be organized
 * into a target directory structure, along with configuration for file watching,
 * recursion, and filtering.</p>
 * 
 * @param name descriptive name for this mapping
 * @param source source directory to scan for music files
 * @param target target directory where organized files will be placed
 * @param profile name of the organization profile to use
 * @param watch whether to watch the source directory for changes
 * @param recursive whether to scan source directory recursively
 * @param filters file filtering criteria to apply
 * 
 * @since 1.0
 */
public record DirectoryMapping(
    @JsonProperty("name") String name,
    
    @JsonProperty("source")
    @JsonSerialize(using = PathSerializer.class)
    @JsonDeserialize(using = PathDeserializer.class)
    Path source,
    
    @JsonProperty("target")
    @JsonSerialize(using = PathSerializer.class)
    @JsonDeserialize(using = PathDeserializer.class)
    Path target,
    
    @JsonProperty("profile") String profile,
    @JsonProperty("watch") boolean watch,
    @JsonProperty("recursive") boolean recursive,
    @JsonProperty("filters") FileFilters filters
) {
    
    /**
     * Creates a DirectoryMapping with validation.
     * 
     * @param name descriptive name
     * @param source source directory path
     * @param target target directory path
     * @param profile organization profile name
     * @param watch enable file watching
     * @param recursive enable recursive scanning
     * @param filters file filtering criteria
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public DirectoryMapping {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(source, "Source path cannot be null");
        Objects.requireNonNull(target, "Target path cannot be null");
        Objects.requireNonNull(profile, "Profile cannot be null");
        Objects.requireNonNull(filters, "Filters cannot be null");
        
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        
        if (profile.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile cannot be empty");
        }
        
        name = name.trim();
        profile = profile.trim();
        
        // Convert paths to absolute paths for consistency
        source = source.toAbsolutePath().normalize();
        target = target.toAbsolutePath().normalize();
        
        // Validate that source and target are different
        if (source.equals(target)) {
            throw new IllegalArgumentException("Source and target paths cannot be the same: " + source);
        }
        
        // Validate that target is not a subdirectory of source (would cause infinite loops)
        if (target.startsWith(source)) {
            throw new IllegalArgumentException(
                "Target path cannot be a subdirectory of source path. Source: " + source + ", Target: " + target
            );
        }
    }
    
    /**
     * Creates a basic directory mapping with default settings.
     * 
     * @param name descriptive name
     * @param source source directory
     * @param target target directory
     * @param profile profile name
     * @return a DirectoryMapping with default settings
     */
    public static DirectoryMapping basic(String name, Path source, Path target, String profile) {
        return new DirectoryMapping(
            name,
            source,
            target,
            profile,
            false,
            true,
            FileFilters.defaultFilters()
        );
    }
    
    /**
     * Creates a watched directory mapping that monitors for changes.
     * 
     * @param name descriptive name
     * @param source source directory
     * @param target target directory
     * @param profile profile name
     * @return a DirectoryMapping with watching enabled
     */
    public static DirectoryMapping watched(String name, Path source, Path target, String profile) {
        return new DirectoryMapping(
            name,
            source,
            target,
            profile,
            true,
            true,
            FileFilters.defaultFilters()
        );
    }
    
    /**
     * Creates a flat directory mapping that doesn't scan recursively.
     * 
     * @param name descriptive name
     * @param source source directory
     * @param target target directory
     * @param profile profile name
     * @return a DirectoryMapping with recursive scanning disabled
     */
    public static DirectoryMapping flat(String name, Path source, Path target, String profile) {
        return new DirectoryMapping(
            name,
            source,
            target,
            profile,
            false,
            false,
            FileFilters.defaultFilters()
        );
    }
    
    /**
     * Creates a high-quality directory mapping for lossless files.
     * 
     * @param name descriptive name
     * @param source source directory
     * @param target target directory
     * @param profile profile name
     * @return a DirectoryMapping with high-quality file filters
     */
    public static DirectoryMapping highQuality(String name, Path source, Path target, String profile) {
        return new DirectoryMapping(
            name,
            source,
            target,
            profile,
            true,
            true,
            FileFilters.highQualityFilters()
        );
    }
    
    /**
     * Gets a description of this directory mapping for display purposes.
     * 
     * @return a human-readable description
     */
    public String getDescription() {
        return String.format(
            "%s: %s → %s (Profile: %s, Watch: %s, Recursive: %s)",
            name,
            source,
            target,
            profile,
            watch ? "Yes" : "No",
            recursive ? "Yes" : "No"
        );
    }
    
    /**
     * Creates a new DirectoryMapping with the specified watch setting.
     * 
     * @param newWatch the new watch setting
     * @return a new DirectoryMapping with the updated watch setting
     */
    public DirectoryMapping withWatch(boolean newWatch) {
        return new DirectoryMapping(name, source, target, profile, newWatch, recursive, filters);
    }
    
    /**
     * Creates a new DirectoryMapping with the specified recursive setting.
     * 
     * @param newRecursive the new recursive setting
     * @return a new DirectoryMapping with the updated recursive setting
     */
    public DirectoryMapping withRecursive(boolean newRecursive) {
        return new DirectoryMapping(name, source, target, profile, watch, newRecursive, filters);
    }
    
    /**
     * Creates a new DirectoryMapping with the specified filters.
     * 
     * @param newFilters the new file filters
     * @return a new DirectoryMapping with the updated filters
     */
    public DirectoryMapping withFilters(FileFilters newFilters) {
        return new DirectoryMapping(name, source, target, profile, watch, recursive, newFilters);
    }
}