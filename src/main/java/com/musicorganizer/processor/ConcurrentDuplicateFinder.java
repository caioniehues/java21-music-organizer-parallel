package com.musicorganizer.processor;

import com.musicorganizer.model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Concurrent duplicate finder using Java 21 features for high-performance duplicate detection.
 */
public class ConcurrentDuplicateFinder implements AutoCloseable {
    
    private final ExecutorService virtualThreadExecutor;
    private final boolean useMetadataComparison;
    private final double similarityThreshold;
    
    public ConcurrentDuplicateFinder() {
        this(true, 0.85);
    }
    
    public ConcurrentDuplicateFinder(boolean useMetadataComparison, double similarityThreshold) {
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.useMetadataComparison = useMetadataComparison;
        this.similarityThreshold = similarityThreshold;
    }
    
    /**
     * Finds all types of duplicates in the given audio files using parallel processing.
     */
    public CompletableFuture<DuplicateAnalysisResult> findDuplicatesAsync(List<AudioFile> audioFiles) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return findDuplicates(audioFiles);
            } catch (Exception e) {
                throw new RuntimeException("Failed to find duplicates", e);
            }
        }, virtualThreadExecutor);
    }
    
    /**
     * Synchronous version of duplicate finding.
     */
    public DuplicateAnalysisResult findDuplicates(List<AudioFile> audioFiles) {
        if (audioFiles.isEmpty()) {
            return new DuplicateAnalysisResult(List.of(), List.of(), List.of(), Map.of());
        }
        
        System.out.printf("Starting duplicate analysis of %d files...%n", audioFiles.size());
        
        // Run different duplicate detection strategies in parallel
        List<CompletableFuture<List<DuplicateInfo>>> futures = List.of(
            CompletableFuture.supplyAsync(() -> findExactDuplicates(audioFiles), virtualThreadExecutor),
            useMetadataComparison ? 
                CompletableFuture.supplyAsync(() -> findMetadataDuplicates(audioFiles), virtualThreadExecutor) :
                CompletableFuture.completedFuture(List.of()),
            CompletableFuture.supplyAsync(() -> findSizeDuplicates(audioFiles), virtualThreadExecutor)
        );
        
        // Wait for all strategies to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        allFutures.join();
        
        // Collect results
        List<DuplicateInfo> exactDuplicates = futures.get(0).join();
        List<DuplicateInfo> metadataDuplicates = futures.get(1).join();
        List<DuplicateInfo> sizeDuplicates = futures.get(2).join();
        
        // Combine and deduplicate results
        List<DuplicateInfo> allDuplicates = new ArrayList<>();
        allDuplicates.addAll(exactDuplicates);
        allDuplicates.addAll(metadataDuplicates);
        allDuplicates.addAll(sizeDuplicates);
        
        // Remove overlapping duplicates (prefer exact over metadata, metadata over size)
        List<DuplicateInfo> uniqueDuplicates = deduplicateResults(allDuplicates);
        
        // Generate statistics
        Map<String, Object> statistics = generateStatistics(uniqueDuplicates, audioFiles.size());
        
        System.out.printf("Duplicate analysis completed. Found %d duplicate groups%n", uniqueDuplicates.size());
        
        return new DuplicateAnalysisResult(
            exactDuplicates,
            metadataDuplicates,
            sizeDuplicates,
            statistics
        );
    }
    
    /**
     * Finds exact duplicates based on checksums.
     */
    private List<DuplicateInfo> findExactDuplicates(List<AudioFile> audioFiles) {
        System.out.println("Finding exact duplicates by checksum...");
        
        Map<String, List<AudioFile>> checksumGroups = audioFiles.parallelStream()
            .filter(file -> file.checksum() != null && !file.checksum().isEmpty())
            .collect(Collectors.groupingByConcurrent(AudioFile::checksum));
        
        List<DuplicateInfo> duplicates = checksumGroups.entrySet().parallelStream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> DuplicateInfo.exactDuplicate(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        
        System.out.printf("Found %d exact duplicate groups%n", duplicates.size());
        return duplicates;
    }
    
    /**
     * Finds duplicates based on metadata similarity.
     */
    private List<DuplicateInfo> findMetadataDuplicates(List<AudioFile> audioFiles) {
        System.out.println("Finding metadata-based duplicates...");
        
        // Group files by metadata signature
        Map<String, List<AudioFile>> metadataGroups = new ConcurrentHashMap<>();
        
        audioFiles.parallelStream().forEach(file -> {
            String metadataSignature = createMetadataSignature(file.metadata());
            if (metadataSignature != null && !metadataSignature.isEmpty()) {
                metadataGroups.computeIfAbsent(metadataSignature, k -> new ArrayList<>())
                    .add(file);
            }
        });
        
        List<DuplicateInfo> duplicates = metadataGroups.entrySet().parallelStream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> DuplicateInfo.metadataDuplicate(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        
        System.out.printf("Found %d metadata-based duplicate groups%n", duplicates.size());
        return duplicates;
    }
    
    /**
     * Finds duplicates based on file size (potential duplicates for further analysis).
     */
    private List<DuplicateInfo> findSizeDuplicates(List<AudioFile> audioFiles) {
        System.out.println("Finding size-based potential duplicates...");
        
        Map<Long, List<AudioFile>> sizeGroups = audioFiles.parallelStream()
            .collect(Collectors.groupingByConcurrent(AudioFile::size));
        
        List<DuplicateInfo> duplicates = sizeGroups.entrySet().parallelStream()
            .filter(entry -> entry.getValue().size() > 1)
            .filter(entry -> entry.getKey() > 1024 * 1024) // Only consider files > 1MB
            .map(entry -> DuplicateInfo.metadataDuplicate(
                "size:" + entry.getKey(), 
                entry.getValue()
            ))
            .collect(Collectors.toList());
        
        System.out.printf("Found %d size-based potential duplicate groups%n", duplicates.size());
        return duplicates;
    }
    
    /**
     * Creates a metadata signature for grouping similar files.
     */
    private String createMetadataSignature(AudioMetadata metadata) {
        if (!metadata.hasAnyData()) {
            return null;
        }
        
        StringBuilder signature = new StringBuilder();
        
        // Normalize and combine key metadata fields
        metadata.artist().ifPresent(artist -> 
            signature.append(normalizeText(artist)).append("|"));
        metadata.album().ifPresent(album -> 
            signature.append(normalizeText(album)).append("|"));
        metadata.title().ifPresent(title -> 
            signature.append(normalizeText(title)).append("|"));
        metadata.year().ifPresent(year -> 
            signature.append(year).append("|"));
        metadata.trackNumber().ifPresent(track -> 
            signature.append(track).append("|"));
        
        return signature.toString();
    }
    
    /**
     * Normalizes text for comparison (lowercase, remove extra spaces, etc.).
     */
    private String normalizeText(String text) {
        if (text == null) return "";
        
        return text.toLowerCase()
            .replaceAll("\\s+", " ")
            .replaceAll("[^a-z0-9\\s]", "")
            .trim();
    }
    
    /**
     * Checks if a group of files have similar metadata.
     */
    private boolean areMetadataSimilar(List<AudioFile> files) {
        if (files.size() < 2) return false;
        
        // Use the first file as reference
        AudioMetadata reference = files.get(0).metadata();
        
        return files.stream()
            .skip(1)
            .allMatch(file -> calculateMetadataSimilarity(reference, file.metadata()) >= similarityThreshold);
    }
    
    /**
     * Calculates similarity between two metadata objects.
     */
    private double calculateMetadataSimilarity(AudioMetadata meta1, AudioMetadata meta2) {
        int totalFields = 0;
        int matchingFields = 0;
        
        // Compare each field
        totalFields++;
        if (meta1.title().equals(meta2.title())) matchingFields++;
        
        totalFields++;
        if (meta1.artist().equals(meta2.artist())) matchingFields++;
        
        totalFields++;
        if (meta1.album().equals(meta2.album())) matchingFields++;
        
        totalFields++;
        if (meta1.year().equals(meta2.year())) matchingFields++;
        
        totalFields++;
        if (meta1.genre().equals(meta2.genre())) matchingFields++;
        
        return totalFields > 0 ? (double) matchingFields / totalFields : 0.0;
    }
    
    /**
     * Removes overlapping duplicate results, preferring higher-confidence matches.
     */
    private List<DuplicateInfo> deduplicateResults(List<DuplicateInfo> allDuplicates) {
        // Group duplicates by the files they contain
        Map<Set<AudioFile>, List<DuplicateInfo>> fileSetGroups = new HashMap<>();
        
        for (DuplicateInfo duplicate : allDuplicates) {
            Set<AudioFile> fileSet = new HashSet<>(duplicate.duplicates());
            fileSetGroups.computeIfAbsent(fileSet, k -> new ArrayList<>()).add(duplicate);
        }
        
        // For each group, keep only the highest priority duplicate info
        List<DuplicateInfo> uniqueDuplicates = new ArrayList<>();
        
        for (List<DuplicateInfo> group : fileSetGroups.values()) {
            // Sort by priority: EXACT_MATCH > METADATA_MATCH > PARTIAL_MATCH
            group.sort((d1, d2) -> {
                int priority1 = getDuplicateTypePriority(d1.type());
                int priority2 = getDuplicateTypePriority(d2.type());
                return Integer.compare(priority2, priority1); // Higher priority first
            });
            
            uniqueDuplicates.add(group.get(0)); // Keep highest priority
        }
        
        return uniqueDuplicates;
    }
    
    /**
     * Returns numeric priority for duplicate types.
     */
    private int getDuplicateTypePriority(DuplicateInfo.DuplicateType type) {
        return switch (type) {
            case EXACT_MATCH -> 3;
            case METADATA_MATCH -> 2;
            case PARTIAL_MATCH -> 1;
        };
    }
    
    /**
     * Generates statistics about the duplicate analysis.
     */
    private Map<String, Object> generateStatistics(List<DuplicateInfo> duplicates, int totalFiles) {
        Map<String, Object> stats = new HashMap<>();
        
        long totalDuplicateFiles = duplicates.stream()
            .mapToLong(d -> d.getDuplicateCount())
            .sum();
        
        long totalWastedSpace = duplicates.stream()
            .mapToLong(DuplicateInfo::totalWastedSpace)
            .sum();
        
        Map<DuplicateInfo.DuplicateType, Long> typeStats = duplicates.stream()
            .collect(Collectors.groupingBy(
                DuplicateInfo::type,
                Collectors.counting()
            ));
        
        stats.put("total_files_analyzed", totalFiles);
        stats.put("duplicate_groups_found", duplicates.size());
        stats.put("total_duplicate_files", totalDuplicateFiles);
        stats.put("total_wasted_space_bytes", totalWastedSpace);
        stats.put("duplicate_percentage", totalFiles > 0 ? (double) totalDuplicateFiles / totalFiles * 100.0 : 0.0);
        stats.put("type_statistics", typeStats);
        
        return stats;
    }
    
    /**
     * Shuts down the executor service.
     */
    public void close() {
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            virtualThreadExecutor.shutdownNow();
        }
    }
    
    /**
     * Result record for duplicate analysis.
     */
    public record DuplicateAnalysisResult(
        List<DuplicateInfo> exactDuplicates,
        List<DuplicateInfo> metadataDuplicates,
        List<DuplicateInfo> sizeDuplicates,
        Map<String, Object> statistics
    ) {
        
        /**
         * Returns all unique duplicates (deduplicated).
         */
        public List<DuplicateInfo> getAllDuplicates() {
            List<DuplicateInfo> all = new ArrayList<>();
            all.addAll(exactDuplicates);
            all.addAll(metadataDuplicates);
            all.addAll(sizeDuplicates);
            
            // Simple deduplication based on file sets
            return all.stream()
                .collect(Collectors.toMap(
                    d -> new HashSet<>(d.duplicates()),
                    d -> d,
                    (existing, replacement) -> existing // Keep first occurrence
                ))
                .values()
                .stream()
                .toList();
        }
        
        /**
         * Returns total number of duplicate files found.
         */
        public long getTotalDuplicateFiles() {
            return (Long) statistics.getOrDefault("total_duplicate_files", 0L);
        }
        
        /**
         * Returns total wasted space from duplicates.
         */
        public long getTotalWastedSpace() {
            return (Long) statistics.getOrDefault("total_wasted_space_bytes", 0L);
        }
        
        /**
         * Returns duplicate percentage.
         */
        public double getDuplicatePercentage() {
            return (Double) statistics.getOrDefault("duplicate_percentage", 0.0);
        }
        
        /**
         * Returns formatted summary of the analysis.
         */
        public String getSummary() {
            return """
                Duplicate Analysis Results:
                ========================
                Total Files Analyzed: %d
                Duplicate Groups Found: %d
                Total Duplicate Files: %d (%.1f%%)
                Total Wasted Space: %s
                
                Breakdown:
                - Exact Duplicates: %d groups
                - Metadata Duplicates: %d groups  
                - Size-based Potential: %d groups
                """.formatted(
                    (Integer) statistics.get("total_files_analyzed"),
                    exactDuplicates.size() + metadataDuplicates.size() + sizeDuplicates.size(),
                    getTotalDuplicateFiles(),
                    getDuplicatePercentage(),
                    formatFileSize(getTotalWastedSpace()),
                    exactDuplicates.size(),
                    metadataDuplicates.size(),
                    sizeDuplicates.size()
                );
        }
        
        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            
            double kb = bytes / 1024.0;
            if (kb < 1024) return String.format("%.1f KB", kb);
            
            double mb = kb / 1024.0;
            if (mb < 1024) return String.format("%.1f MB", mb);
            
            double gb = mb / 1024.0;
            return String.format("%.1f GB", gb);
        }
    }
}