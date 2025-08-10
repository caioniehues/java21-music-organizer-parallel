package com.musicorganizer.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Record representing information about duplicate audio files.
 * Uses Java 21 record features for immutable duplicate tracking.
 */
public record DuplicateInfo(
    String checksum,
    long fileSize,
    List<AudioFile> duplicates,
    DuplicateType type,
    long totalWastedSpace
) {
    
    public DuplicateInfo {
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("Checksum cannot be null or blank");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        if (duplicates == null || duplicates.size() < 2) {
            throw new IllegalArgumentException("Must have at least 2 duplicate files");
        }
        
        // Calculate wasted space (all duplicates except one original)
        totalWastedSpace = fileSize * (duplicates.size() - 1);
    }
    
    /**
     * Enum for different types of duplicates.
     */
    public enum DuplicateType {
        EXACT_MATCH,        // Identical files
        METADATA_MATCH,     // Same metadata, different files
        PARTIAL_MATCH       // Similar but not identical
    }
    
    /**
     * Factory method for creating exact duplicate info.
     */
    public static DuplicateInfo exactDuplicate(String checksum, List<AudioFile> files) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be empty");
        }
        
        long size = files.get(0).size();
        return new DuplicateInfo(checksum, size, files, DuplicateType.EXACT_MATCH, 0);
    }
    
    /**
     * Factory method for creating metadata-based duplicates.
     */
    public static DuplicateInfo metadataDuplicate(String identifier, List<AudioFile> files) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Files list cannot be empty");
        }
        
        long totalSize = files.stream().mapToLong(AudioFile::size).sum();
        return new DuplicateInfo(identifier, totalSize, files, DuplicateType.METADATA_MATCH, 0);
    }
    
    /**
     * Returns the number of duplicate files.
     */
    public int getDuplicateCount() {
        return duplicates.size();
    }
    
    /**
     * Returns all file paths as a set.
     */
    public Set<Path> getAllPaths() {
        return duplicates.stream()
            .map(AudioFile::path)
            .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Returns the suggested file to keep (usually the one with the best path).
     */
    public AudioFile getSuggestedKeeper() {
        return duplicates.stream()
            .min((f1, f2) -> {
                // Prefer files with better metadata
                boolean f1HasMetadata = f1.metadata().hasAnyData();
                boolean f2HasMetadata = f2.metadata().hasAnyData();
                
                if (f1HasMetadata && !f2HasMetadata) return -1;
                if (!f1HasMetadata && f2HasMetadata) return 1;
                
                // Prefer shorter paths (likely better organized)
                String path1 = f1.path().toString();
                String path2 = f2.path().toString();
                
                int pathComparison = Integer.compare(path1.length(), path2.length());
                if (pathComparison != 0) return pathComparison;
                
                // Fallback to alphabetical order
                return path1.compareToIgnoreCase(path2);
            })
            .orElse(duplicates.get(0));
    }
    
    /**
     * Returns files that should be deleted (all except the keeper).
     */
    public List<AudioFile> getFilesToDelete() {
        AudioFile keeper = getSuggestedKeeper();
        return duplicates.stream()
            .filter(file -> !file.equals(keeper))
            .toList();
    }
    
    /**
     * Returns formatted size information.
     */
    public String getFormattedSize() {
        return formatFileSize(fileSize);
    }
    
    /**
     * Returns formatted wasted space information.
     */
    public String getFormattedWastedSpace() {
        return formatFileSize(totalWastedSpace);
    }
    
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }
    
    /**
     * Returns a summary of this duplicate group.
     */
    public String getSummary() {
        return switch (type) {
            case EXACT_MATCH -> """
                Exact Duplicates (%d files):
                Checksum: %s
                Size: %s each
                Wasted Space: %s
                Keeper: %s
                """.formatted(
                    getDuplicateCount(),
                    checksum.substring(0, Math.min(8, checksum.length())),
                    getFormattedSize(),
                    getFormattedWastedSpace(),
                    getSuggestedKeeper().path().getFileName()
                );
                
            case METADATA_MATCH -> """
                Metadata Duplicates (%d files):
                Identifier: %s
                Total Size: %s
                Type: Similar metadata
                Keeper: %s
                """.formatted(
                    getDuplicateCount(),
                    checksum.substring(0, Math.min(20, checksum.length())),
                    getFormattedSize(),
                    getSuggestedKeeper().path().getFileName()
                );
                
            case PARTIAL_MATCH -> """
                Partial Duplicates (%d files):
                Similarity: %s
                Total Size: %s
                Type: Partial match
                Keeper: %s
                """.formatted(
                    getDuplicateCount(),
                    checksum.substring(0, Math.min(20, checksum.length())),
                    getFormattedSize(),
                    getSuggestedKeeper().path().getFileName()
                );
        };
    }
    
    @Override
    public String toString() {
        return """
            DuplicateInfo {
                type: %s
                count: %d
                size: %s
                wasted: %s
                files: %s
            }
            """.formatted(
                type,
                getDuplicateCount(),
                getFormattedSize(),
                getFormattedWastedSpace(),
                getAllPaths().stream()
                    .map(Path::getFileName)
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "))
            );
    }
}