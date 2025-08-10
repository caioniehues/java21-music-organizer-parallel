package com.musicorganizer.scanner;

import com.musicorganizer.model.*;
import com.musicorganizer.service.ExecutorServiceFactory;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.Tag;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parallel music scanner using Java 21 Virtual Threads for high-performance scanning.
 */
public class ParallelMusicScanner implements AutoCloseable {
    
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "mp3", "flac", "m4a", "aac", "ogg", "wav", "wma", "mp4"
    );
    
    private final ExecutorServiceFactory executorServiceFactory;
    private final int maxConcurrentFiles;
    private final boolean calculateChecksums;
    private final boolean deepScan;
    private final ExecutorService virtualThreadExecutor;
    private final boolean ownsExecutor;
    
    /**
     * Default constructor for backward compatibility.
     */
    public ParallelMusicScanner() {
        this(1000, true, true);
    }
    
    /**
     * Constructor with settings but using default ExecutorServiceFactory.
     */
    public ParallelMusicScanner(int maxConcurrentFiles, boolean calculateChecksums, boolean deepScan) {
        this(maxConcurrentFiles, calculateChecksums, deepScan, 
             ExecutorServiceFactory.defaultFactory());
    }
    
    /**
     * Constructor with ExecutorServiceFactory for dependency injection.
     */
    public ParallelMusicScanner(int maxConcurrentFiles, 
                               boolean calculateChecksums, 
                               boolean deepScan,
                               ExecutorServiceFactory executorServiceFactory) {
        this.executorServiceFactory = executorServiceFactory;
        this.maxConcurrentFiles = maxConcurrentFiles;
        this.calculateChecksums = calculateChecksums;
        this.deepScan = deepScan;
        this.virtualThreadExecutor = null;
        this.ownsExecutor = true;
    }
    
    /**
     * Constructor accepting pre-configured ExecutorService for full dependency injection.
     */
    public ParallelMusicScanner(int maxConcurrentFiles,
                               boolean calculateChecksums,
                               boolean deepScan,
                               ExecutorService executorService) {
        this.executorServiceFactory = null;
        this.maxConcurrentFiles = maxConcurrentFiles;
        this.calculateChecksums = calculateChecksums;
        this.deepScan = deepScan;
        this.virtualThreadExecutor = Objects.requireNonNull(executorService, "ExecutorService cannot be null");
        this.ownsExecutor = false;
    }
    
    /**
     * Scans a directory for audio files using parallel processing.
     */
    public ScanResult scanDirectory(Path rootPath) {
        Instant scanStart = Instant.now();
        
        // Initialize executor if not already done
        ExecutorService executor = getOrCreateExecutor();
        
        try {
            if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
                return ScanResult.Failure.withMessage(
                    "Directory does not exist or is not a directory: " + rootPath,
                    scanStart,
                    Duration.between(scanStart, Instant.now())
                );
            }
            
            System.out.printf("Starting parallel scan of: %s%n", rootPath);
            
            // Find all audio files
            List<Path> audioPaths = findAudioFiles(rootPath);
            System.out.printf("Found %d audio files to process%n", audioPaths.size());
            
            if (audioPaths.isEmpty()) {
                return new ScanResult.Success(
                    List.of(),
                    List.of(),
                    Map.of(),
                    createEmptyStatistics(),
                    scanStart,
                    Duration.between(scanStart, Instant.now())
                );
            }
            
            // Process files in parallel with semaphore for controlled concurrency
            Semaphore semaphore = new Semaphore(maxConcurrentFiles);
            List<CompletableFuture<ProcessResult>> futures = new ArrayList<>();
            
            for (Path audioPath : audioPaths) {
                CompletableFuture<ProcessResult> future = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            semaphore.acquire();
                            return processAudioFile(audioPath);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return ProcessResult.failed(audioPath, e);
                        } finally {
                            semaphore.release();
                        }
                    }, executor);
                
                futures.add(future);
            }
            
            // Wait for all futures to complete
            System.out.println("Processing files with virtual threads...");
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            allFutures.join(); // Wait for completion
            
            // Collect results
            List<com.musicorganizer.model.AudioFile> audioFiles = new ArrayList<>();
            List<String> failedPaths = new ArrayList<>();
            List<Exception> errors = new ArrayList<>();
            Map<String, Integer> fileTypeStats = new HashMap<>();
            
            for (CompletableFuture<ProcessResult> future : futures) {
                ProcessResult result = future.join();
                
                switch (result.type()) {
                    case SUCCESS -> {
                        audioFiles.add(result.audioFile());
                        String extension = result.audioFile().getExtension();
                        fileTypeStats.merge(extension, 1, Integer::sum);
                    }
                    case FAILURE -> {
                        failedPaths.add(result.path().toString());
                        if (result.error() != null) {
                            errors.add(result.error());
                        }
                    }
                }
            }
            
            Duration scanDuration = Duration.between(scanStart, Instant.now());
            
            System.out.printf("Scan completed in %s%n", formatDuration(scanDuration));
            System.out.printf("Successfully processed: %d files%n", audioFiles.size());
            System.out.printf("Failed: %d files%n", failedPaths.size());
            
            // Find duplicates if checksums were calculated
            List<DuplicateInfo> duplicates = findDuplicates(audioFiles);
            
            // Create statistics
            ScanResult.ScanStatistics statistics = createStatistics(
                audioFiles, audioPaths.size(), scanDuration
            );
            
            // Return appropriate result based on success/failure ratio
            if (failedPaths.isEmpty()) {
                return new ScanResult.Success(
                    audioFiles, duplicates, fileTypeStats, statistics, scanStart, scanDuration
                );
            } else if (audioFiles.isEmpty()) {
                return new ScanResult.Failure(
                    !errors.isEmpty() ? errors.get(0) : null,
                    "All files failed to process",
                    failedPaths,
                    scanStart,
                    scanDuration
                );
            } else {
                return new ScanResult.Partial(
                    audioFiles, duplicates, fileTypeStats, statistics,
                    failedPaths, errors, scanStart, scanDuration
                );
            }
            
        } catch (Exception e) {
            Duration scanDuration = Duration.between(scanStart, Instant.now());
            return ScanResult.Failure.fromException(e, scanStart, scanDuration);
        }
    }
    
    /**
     * Finds all audio files in a directory tree.
     */
    private List<Path> findAudioFiles(Path rootPath) throws IOException {
        List<Path> audioPaths = new ArrayList<>();
        
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString().toLowerCase();
                String extension = getFileExtension(fileName);
                
                if (SUPPORTED_EXTENSIONS.contains(extension)) {
                    audioPaths.add(file);
                }
                
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Failed to access file: " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        
        return audioPaths;
    }
    
    /**
     * Processes a single audio file and extracts metadata.
     */
    private ProcessResult processAudioFile(Path audioPath) {
        try {
            // Get basic file attributes
            BasicFileAttributes attrs = Files.readAttributes(audioPath, BasicFileAttributes.class);
            
            // Extract metadata using JAudioTagger
            AudioMetadata metadata = extractMetadata(audioPath);
            
            // Calculate checksum if enabled
            String checksum = calculateChecksums ? calculateChecksum(audioPath) : null;
            
            com.musicorganizer.model.AudioFile audioFile = new com.musicorganizer.model.AudioFile(
                audioPath,
                attrs.size(),
                attrs.lastModifiedTime().toInstant(),
                metadata,
                checksum
            );
            
            return ProcessResult.success(audioFile);
            
        } catch (Exception e) {
            return ProcessResult.failed(audioPath, e);
        }
    }
    
    /**
     * Extracts metadata from an audio file using JAudioTagger.
     */
    private AudioMetadata extractMetadata(Path audioPath) {
        try {
            AudioFile audioFile = AudioFileIO.read(audioPath.toFile());
            AudioHeader header = audioFile.getAudioHeader();
            Tag tag = audioFile.getTag();
            
            AudioMetadata.Builder builder = new AudioMetadata.Builder()
                .format(header.getFormat());
            
            if (header.getBitRate() != null) {
                builder.bitrate(Integer.parseInt(header.getBitRate()));
            }
            
            if (header.getTrackLength() > 0) {
                builder.duration(Duration.ofSeconds(header.getTrackLength()));
            }
            
            if (tag != null) {
                builder
                    .title(tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE))
                    .artist(tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST))
                    .album(tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM))
                    .genre(tag.getFirst(org.jaudiotagger.tag.FieldKey.GENRE));
                
                String yearStr = tag.getFirst(org.jaudiotagger.tag.FieldKey.YEAR);
                if (yearStr != null && !yearStr.isEmpty()) {
                    try {
                        builder.year(Integer.parseInt(yearStr.trim()));
                    } catch (NumberFormatException ignored) {
                        // Invalid year format, skip
                    }
                }
                
                String trackStr = tag.getFirst(org.jaudiotagger.tag.FieldKey.TRACK);
                if (trackStr != null && !trackStr.isEmpty()) {
                    try {
                        // Handle "track/total" format
                        String[] parts = trackStr.split("/");
                        builder.trackNumber(Integer.parseInt(parts[0].trim()));
                        if (parts.length > 1) {
                            builder.totalTracks(Integer.parseInt(parts[1].trim()));
                        }
                    } catch (NumberFormatException ignored) {
                        // Invalid track format, skip
                    }
                }
            }
            
            return builder.build();
            
        } catch (Exception e) {
            System.err.println("Failed to extract metadata from: " + audioPath + " - " + e.getMessage());
            return AudioMetadata.empty();
        }
    }
    
    /**
     * Calculates SHA-256 checksum of a file for duplicate detection.
     */
    private String calculateChecksum(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        try (var inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    /**
     * Finds duplicate files based on checksums.
     */
    private List<DuplicateInfo> findDuplicates(List<com.musicorganizer.model.AudioFile> audioFiles) {
        if (!calculateChecksums) {
            return List.of();
        }
        
        Map<String, List<com.musicorganizer.model.AudioFile>> checksumGroups = audioFiles.stream()
            .filter(file -> file.checksum() != null)
            .collect(Collectors.groupingBy(com.musicorganizer.model.AudioFile::checksum));
        
        return checksumGroups.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> DuplicateInfo.exactDuplicate(entry.getKey(), entry.getValue()))
            .toList();
    }
    
    /**
     * Creates scan statistics.
     */
    private ScanResult.ScanStatistics createStatistics(
        List<com.musicorganizer.model.AudioFile> audioFiles, 
        int totalFiles, 
        Duration scanDuration) {
        
        long totalSize = audioFiles.stream().mapToLong(com.musicorganizer.model.AudioFile::size).sum();
        
        Map<String, Long> processingTimes = Map.of(
            "total_scan", scanDuration.toMillis(),
            "avg_per_file", totalFiles > 0 ? scanDuration.toMillis() / totalFiles : 0L
        );
        
        return new ScanResult.ScanStatistics(
            audioFiles.size(),
            1, // For now, assume single directory
            totalSize,
            Runtime.getRuntime().availableProcessors(),
            processingTimes,
            SUPPORTED_EXTENSIONS
        );
    }
    
    private ScanResult.ScanStatistics createEmptyStatistics() {
        return new ScanResult.ScanStatistics(
            0, 0, 0, 
            Runtime.getRuntime().availableProcessors(),
            Map.of(),
            SUPPORTED_EXTENSIONS
        );
    }
    
    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }
    
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%dm %02ds", minutes, seconds);
    }
    
    /**
     * Gets or creates the executor service based on the configuration.
     */
    private ExecutorService getOrCreateExecutor() {
        if (virtualThreadExecutor != null) {
            return virtualThreadExecutor;
        }
        return executorServiceFactory.createVirtualThreadExecutor();
    }
    
    /**
     * Factory method to create scanner with default settings.
     */
    public static ParallelMusicScanner createDefault() {
        return new ParallelMusicScanner();
    }
    
    /**
     * Factory method to create scanner with custom executor.
     */
    public static ParallelMusicScanner createWithExecutor(ExecutorService executorService) {
        return new ParallelMusicScanner(1000, true, true, executorService);
    }
    
    /**
     * Factory method to create scanner with custom executor factory.
     */
    public static ParallelMusicScanner createWithFactory(ExecutorServiceFactory factory) {
        return new ParallelMusicScanner(1000, true, true, factory);
    }
    
    /**
     * Closes the scanner and releases resources.
     */
    public void close() {
        // Only close executor if we own it (created through factory)
        if (ownsExecutor && virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
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
    }
    
    /**
     * Internal record for processing results.
     */
    private record ProcessResult(
        ProcessType type,
        Path path,
        com.musicorganizer.model.AudioFile audioFile,
        Exception error
    ) {
        enum ProcessType { SUCCESS, FAILURE }
        
        static ProcessResult success(com.musicorganizer.model.AudioFile audioFile) {
            return new ProcessResult(ProcessType.SUCCESS, audioFile.path(), audioFile, null);
        }
        
        static ProcessResult failed(Path path, Exception error) {
            return new ProcessResult(ProcessType.FAILURE, path, null, error);
        }
    }
}