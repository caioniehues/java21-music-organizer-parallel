package com.musicorganizer.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sealed class representing the result of a music library scan.
 * Uses Java 21 sealed classes for type-safe result handling.
 */
public sealed interface ScanResult permits ScanResult.Success, ScanResult.Failure, ScanResult.Partial {
    
    /**
     * Returns the timestamp when the scan was performed.
     */
    Instant getScanTime();
    
    /**
     * Returns the duration of the scan operation.
     */
    Duration getScanDuration();
    
    /**
     * Successful scan result.
     */
    record Success(
        List<AudioFile> audioFiles,
        List<DuplicateInfo> duplicates,
        Map<String, Integer> fileTypeStats,
        ScanStatistics statistics,
        Instant scanTime,
        Duration scanDuration
    ) implements ScanResult {
        
        public Success {
            if (audioFiles == null) {
                throw new IllegalArgumentException("Audio files list cannot be null");
            }
            if (duplicates == null) {
                throw new IllegalArgumentException("Duplicates list cannot be null");
            }
            if (fileTypeStats == null) {
                throw new IllegalArgumentException("File type stats cannot be null");
            }
            if (statistics == null) {
                throw new IllegalArgumentException("Statistics cannot be null");
            }
            if (scanTime == null) {
                throw new IllegalArgumentException("Scan time cannot be null");
            }
            if (scanDuration == null || scanDuration.isNegative()) {
                throw new IllegalArgumentException("Scan duration must be positive");
            }
        }
        
        /**
         * Returns the total number of audio files found.
         */
        public int getTotalFiles() {
            return audioFiles.size();
        }
        
        /**
         * Returns the total number of duplicate files.
         */
        public int getTotalDuplicates() {
            return duplicates.stream()
                .mapToInt(DuplicateInfo::getDuplicateCount)
                .sum();
        }
        
        /**
         * Returns the total wasted space from duplicates.
         */
        public long getTotalWastedSpace() {
            return duplicates.stream()
                .mapToLong(DuplicateInfo::totalWastedSpace)
                .sum();
        }
        
        /**
         * Returns files by extension.
         */
        public Map<String, List<AudioFile>> getFilesByExtension() {
            return audioFiles.stream()
                .collect(java.util.stream.Collectors.groupingBy(AudioFile::getExtension));
        }
        
        @Override
        public Instant getScanTime() {
            return scanTime;
        }
        
        @Override
        public Duration getScanDuration() {
            return scanDuration;
        }
    }
    
    /**
     * Failed scan result.
     */
    record Failure(
        Exception cause,
        String errorMessage,
        List<String> failedPaths,
        Instant scanTime,
        Duration scanDuration
    ) implements ScanResult {
        
        public Failure {
            if (cause == null && (errorMessage == null || errorMessage.isBlank())) {
                throw new IllegalArgumentException("Must provide either cause or error message");
            }
            if (failedPaths == null) {
                throw new IllegalArgumentException("Failed paths list cannot be null");
            }
            if (scanTime == null) {
                throw new IllegalArgumentException("Scan time cannot be null");
            }
            if (scanDuration == null || scanDuration.isNegative()) {
                throw new IllegalArgumentException("Scan duration must be positive");
            }
        }
        
        /**
         * Creates a failure result from an exception.
         */
        public static Failure fromException(Exception ex, Instant scanTime, Duration scanDuration) {
            return new Failure(ex, ex.getMessage(), List.of(), scanTime, scanDuration);
        }
        
        /**
         * Creates a failure result with a custom message.
         */
        public static Failure withMessage(String message, Instant scanTime, Duration scanDuration) {
            return new Failure(null, message, List.of(), scanTime, scanDuration);
        }
        
        @Override
        public Instant getScanTime() {
            return scanTime;
        }
        
        @Override
        public Duration getScanDuration() {
            return scanDuration;
        }
    }
    
    /**
     * Partial scan result (some files processed successfully, others failed).
     */
    record Partial(
        List<AudioFile> audioFiles,
        List<DuplicateInfo> duplicates,
        Map<String, Integer> fileTypeStats,
        ScanStatistics statistics,
        List<String> failedPaths,
        List<Exception> errors,
        Instant scanTime,
        Duration scanDuration
    ) implements ScanResult {
        
        public Partial {
            if (audioFiles == null) {
                throw new IllegalArgumentException("Audio files list cannot be null");
            }
            if (duplicates == null) {
                throw new IllegalArgumentException("Duplicates list cannot be null");
            }
            if (fileTypeStats == null) {
                throw new IllegalArgumentException("File type stats cannot be null");
            }
            if (statistics == null) {
                throw new IllegalArgumentException("Statistics cannot be null");
            }
            if (failedPaths == null) {
                throw new IllegalArgumentException("Failed paths list cannot be null");
            }
            if (errors == null) {
                throw new IllegalArgumentException("Errors list cannot be null");
            }
            if (scanTime == null) {
                throw new IllegalArgumentException("Scan time cannot be null");
            }
            if (scanDuration == null || scanDuration.isNegative()) {
                throw new IllegalArgumentException("Scan duration must be positive");
            }
        }
        
        /**
         * Returns the success rate as a percentage.
         */
        public double getSuccessRate() {
            int total = audioFiles.size() + failedPaths.size();
            return total > 0 ? (double) audioFiles.size() / total * 100.0 : 0.0;
        }
        
        @Override
        public Instant getScanTime() {
            return scanTime;
        }
        
        @Override
        public Duration getScanDuration() {
            return scanDuration;
        }
    }
    
    /**
     * Statistics about the scan operation.
     */
    record ScanStatistics(
        int totalFilesScanned,
        int totalDirectoriesScanned,
        long totalSizeScanned,
        int threadCount,
        Map<String, Long> processingTimes,
        Set<String> supportedFormats
    ) {
        
        public ScanStatistics {
            if (totalFilesScanned < 0) {
                throw new IllegalArgumentException("Total files scanned cannot be negative");
            }
            if (totalDirectoriesScanned < 0) {
                throw new IllegalArgumentException("Total directories scanned cannot be negative");
            }
            if (totalSizeScanned < 0) {
                throw new IllegalArgumentException("Total size scanned cannot be negative");
            }
            if (threadCount <= 0) {
                throw new IllegalArgumentException("Thread count must be positive");
            }
            if (processingTimes == null) {
                throw new IllegalArgumentException("Processing times cannot be null");
            }
            if (supportedFormats == null) {
                throw new IllegalArgumentException("Supported formats cannot be null");
            }
        }
        
        /**
         * Returns formatted total size.
         */
        public String getFormattedTotalSize() {
            return formatFileSize(totalSizeScanned);
        }
        
        /**
         * Returns average processing time per file.
         */
        public Duration getAverageProcessingTime() {
            if (totalFilesScanned == 0) return Duration.ZERO;
            
            long totalMillis = processingTimes.values().stream()
                .mapToLong(Long::longValue)
                .sum();
                
            return Duration.ofMillis(totalMillis / totalFilesScanned);
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
    }
    
    /**
     * Pattern matching helper for handling results.
     */
    default <T> T match(
        java.util.function.Function<Success, T> onSuccess,
        java.util.function.Function<Failure, T> onFailure,
        java.util.function.Function<Partial, T> onPartial
    ) {
        return switch (this) {
            case Success s -> onSuccess.apply(s);
            case Failure f -> onFailure.apply(f);
            case Partial p -> onPartial.apply(p);
        };
    }
    
    /**
     * Returns a summary of the scan result.
     */
    default String getSummary() {
        return switch (this) {
            case Success(var files, var duplicates, var stats, var scanStats, var time, var duration) ->
                """
                Scan completed successfully in %s
                Files found: %d
                Duplicates: %d groups (%d files)
                Total size: %s
                Formats: %s
                """.formatted(
                    formatDuration(duration),
                    files.size(),
                    duplicates.size(),
                    duplicates.stream().mapToInt(DuplicateInfo::getDuplicateCount).sum(),
                    scanStats.getFormattedTotalSize(),
                    String.join(", ", stats.keySet())
                );
                
            case Failure(var cause, var message, var failedPaths, var time, var duration) ->
                """
                Scan failed after %s
                Error: %s
                Failed paths: %d
                """.formatted(
                    formatDuration(duration),
                    message != null ? message : cause.getMessage(),
                    failedPaths.size()
                );
                
            case Partial(var files, var duplicates, var stats, var scanStats, var failed, var errors, var time, var duration) ->
                """
                Scan partially completed in %s
                Success rate: %.1f%%
                Files found: %d
                Failed: %d
                Duplicates: %d groups
                Total size: %s
                """.formatted(
                    formatDuration(duration),
                    ((Partial) this).getSuccessRate(),
                    files.size(),
                    failed.size(),
                    duplicates.size(),
                    scanStats.getFormattedTotalSize()
                );
        };
    }
    
    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "m " + seconds + "s";
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + minutes + "m " + seconds + "s";
    }
}