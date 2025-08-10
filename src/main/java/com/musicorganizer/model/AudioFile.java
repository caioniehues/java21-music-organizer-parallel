package com.musicorganizer.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Record representing an audio file with its metadata.
 * Uses Java 21 record syntax for immutable data containers.
 */
public record AudioFile(
    Path path,
    long size,
    Instant lastModified,
    AudioMetadata metadata,
    String checksum
) {
    
    public AudioFile {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        if (lastModified == null) {
            throw new IllegalArgumentException("Last modified time cannot be null");
        }
    }
    
    /**
     * Creates a new AudioFile with updated metadata.
     */
    public AudioFile withMetadata(AudioMetadata newMetadata) {
        return new AudioFile(path, size, lastModified, newMetadata, checksum);
    }
    
    /**
     * Creates a new AudioFile with updated checksum.
     */
    public AudioFile withChecksum(String newChecksum) {
        return new AudioFile(path, size, lastModified, metadata, newChecksum);
    }
    
    /**
     * Returns the file extension in lowercase.
     */
    public String getExtension() {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }
    
    /**
     * Checks if this audio file is a duplicate based on checksum.
     */
    public boolean isDuplicateOf(AudioFile other) {
        return checksum != null && checksum.equals(other.checksum());
    }
    
    /**
     * Returns a human-readable string representation.
     */
    @Override
    public String toString() {
        return """
            AudioFile {
                path: %s
                size: %d bytes
                extension: %s
                metadata: %s
            }
            """.formatted(path, size, getExtension(), metadata);
    }
}