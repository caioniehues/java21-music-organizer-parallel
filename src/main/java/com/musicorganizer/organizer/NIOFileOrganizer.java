package com.musicorganizer.organizer;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.util.ProgressTracker;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * NIO.2-based file organizer with Virtual Threads for parallel operations,
 * atomic moves with rollback support, and efficient directory structure creation.
 */
public class NIOFileOrganizer {
    
    public record OrganizationResult(
        Map<Path, Path> successful,
        Map<Path, Exception> failed,
        List<Path> rollbackOperations,
        Duration processingTime,
        int totalProcessed
    ) {}
    
    public record OrganizationConfig(
        Path targetDirectory,
        boolean createDirectoryStructure,
        boolean preserveOriginalOnError,
        boolean enableRollback,
        StandardCopyOption... copyOptions
    ) {
        public static OrganizationConfig defaultConfig(Path targetDirectory) {
            return new OrganizationConfig(
                targetDirectory,
                true,  // create directory structure
                true,  // preserve original on error
                true,  // enable rollback
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
    
    private final OrganizationConfig config;
    private final ProgressTracker progressTracker;
    private final Pattern invalidCharsPattern = Pattern.compile("[<>:\"/\\\\|?*]");
    private final Map<Path, Path> rollbackMap = new ConcurrentHashMap<>();
    
    public NIOFileOrganizer(OrganizationConfig config, ProgressTracker progressTracker) {
        this.config = config;
        this.progressTracker = progressTracker;
        
        // Ensure target directory exists
        try {
            Files.createDirectories(config.targetDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create target directory: " + config.targetDirectory(), e);
        }
    }
    
    /**
     * Organize files using their metadata with Virtual Threads
     */
    public CompletableFuture<OrganizationResult> organizeFilesAsync(Map<Path, TrackMetadata> fileMetadata,
                                                                   Consumer<Path> onFileProcessed) {
        return CompletableFuture.supplyAsync(() -> {
            var startTime = System.nanoTime();
            var successful = new ConcurrentHashMap<Path, Path>();
            var failed = new ConcurrentHashMap<Path, Exception>();
            var processedCount = new AtomicInteger(0);
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                if (progressTracker != null) {
                    progressTracker.startOperation("file_organization", fileMetadata.size());
                }
                
                var organizationTasks = fileMetadata.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        var sourcePath = entry.getKey();
                        var metadata = entry.getValue();
                        
                        try {
                            var targetPath = organizeFile(sourcePath, metadata);
                            successful.put(sourcePath, targetPath);
                            
                            if (onFileProcessed != null) {
                                onFileProcessed.accept(targetPath);
                            }
                        } catch (Exception e) {
                            failed.put(sourcePath, e);
                        }
                        
                        int completed = processedCount.incrementAndGet();
                        if (progressTracker != null) {
                            progressTracker.updateProgress("file_organization", completed);
                        }
                        
                    }, executor))
                    .toArray(CompletableFuture[]::new);
                
                CompletableFuture.allOf(organizationTasks).join();
                if (progressTracker != null) {
                    progressTracker.completeOperation("file_organization");
                }
                
                var endTime = System.nanoTime();
                var processingTime = Duration.ofNanos(endTime - startTime);
                
                return new OrganizationResult(
                    Map.copyOf(successful),
                    Map.copyOf(failed),
                    new ArrayList<>(rollbackMap.keySet()),
                    processingTime,
                    processedCount.get()
                );
                
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Organize a single file based on its metadata
     */
    private Path organizeFile(Path sourceFile, TrackMetadata metadata) throws IOException {
        // Generate target path based on metadata
        var targetPath = generateTargetPath(metadata, sourceFile);
        
        // Create directory structure if needed
        if (config.createDirectoryStructure()) {
            Files.createDirectories(targetPath.getParent());
        }
        
        // Perform atomic move with rollback support
        return performAtomicMove(sourceFile, targetPath);
    }
    
    /**
     * Generate target path: Artist/[Year] Album/## Track.ext
     */
    private Path generateTargetPath(TrackMetadata metadata, Path sourceFile) {
        var artist = sanitizeFilename(metadata.artist() != null ? metadata.artist() : "Unknown Artist");
        var album = sanitizeFilename(metadata.album() != null ? metadata.album() : "Unknown Album");
        var title = sanitizeFilename(metadata.title() != null && !metadata.title().isBlank() ? 
                                   metadata.title() : getFilenameWithoutExtension(sourceFile));
        var year = metadata.year().map(String::valueOf).orElse("");
        var track = metadata.trackNumber().map(num -> String.format("%02d", num)).orElse("00");
        var extension = getFileExtension(sourceFile);
        
        // Build directory structure: Artist/[Year] Album/
        var albumFolder = year.isEmpty() ? album : String.format("[%s] %s", year, album);
        var artistDir = config.targetDirectory().resolve(artist);
        var albumDir = artistDir.resolve(albumFolder);
        
        // Build filename: ## Track.ext
        var filename = String.format("%s %s.%s", track, title, extension);
        
        return albumDir.resolve(filename);
    }
    
    /**
     * Perform atomic move with rollback support
     */
    private Path performAtomicMove(Path source, Path target) throws IOException {
        Path backupPath = null;
        
        try {
            // If target exists and rollback is enabled, create backup
            if (Files.exists(target) && config.enableRollback()) {
                backupPath = createBackupPath(target);
                Files.move(target, backupPath, StandardCopyOption.ATOMIC_MOVE);
                rollbackMap.put(target, backupPath);
            }
            
            // Perform the main move operation
            Files.move(source, target, config.copyOptions());
            
            // Clean up backup if move was successful
            if (backupPath != null) {
                rollbackMap.remove(target);
                Files.deleteIfExists(backupPath);
            }
            
            return target;
            
        } catch (IOException e) {
            // Rollback on failure
            if (config.enableRollback() && backupPath != null && Files.exists(backupPath)) {
                try {
                    Files.move(backupPath, target, StandardCopyOption.ATOMIC_MOVE);
                    rollbackMap.remove(target);
                } catch (IOException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }
            throw e;
        }
    }
    
    /**
     * Create a unique backup path
     */
    private Path createBackupPath(Path originalPath) {
        var parent = originalPath.getParent();
        var filename = originalPath.getFileName().toString();
        var timestamp = LocalDateTime.now().toString().replaceAll("[:-]", "");
        
        return parent.resolve(filename + ".backup." + timestamp);
    }
    
    /**
     * Sanitize filename by removing invalid characters
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Unknown";
        }
        
        // Replace invalid characters with underscores
        var sanitized = invalidCharsPattern.matcher(filename.trim()).replaceAll("_");
        
        // Remove multiple consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        
        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_|_$", "");
        
        // Ensure filename is not empty and not too long
        if (sanitized.isEmpty()) {
            sanitized = "Unknown";
        } else if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        
        return sanitized;
    }
    
    /**
     * Get file extension
     */
    private String getFileExtension(Path file) {
        var filename = file.getFileName().toString();
        var lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1).toLowerCase() : "";
    }
    
    /**
     * Get filename without extension
     */
    private String getFilenameWithoutExtension(Path file) {
        var filename = file.getFileName().toString();
        var lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(0, lastDotIndex) : filename;
    }
    
    /**
     * Synchronous version for simpler usage
     */
    public OrganizationResult organizeFiles(Map<Path, TrackMetadata> fileMetadata,
                                          Consumer<Path> onFileProcessed) {
        return organizeFilesAsync(fileMetadata, onFileProcessed).join();
    }
    
    /**
     * Rollback all operations performed during the current session
     */
    public CompletableFuture<Integer> rollbackAllAsync() {
        return CompletableFuture.supplyAsync(() -> {
            var rolledBack = new AtomicInteger(0);
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var rollbackTasks = rollbackMap.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        try {
                            var target = entry.getKey();
                            var backup = entry.getValue();
                            
                            if (Files.exists(backup)) {
                                Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
                                rolledBack.incrementAndGet();
                            }
                        } catch (IOException e) {
                            // Log error but continue with other rollbacks
                            System.err.println("Failed to rollback: " + entry.getKey() + " -> " + e.getMessage());
                        }
                    }, executor))
                    .toArray(CompletableFuture[]::new);
                
                CompletableFuture.allOf(rollbackTasks).join();
            }
            
            rollbackMap.clear();
            return rolledBack.get();
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Scan directory for duplicate files
     */
    public CompletableFuture<Map<String, List<Path>>> findDuplicatesAsync(Path directory) {
        return CompletableFuture.supplyAsync(() -> {
            var duplicates = new ConcurrentHashMap<String, List<Path>>();
            var fileHashes = new ConcurrentHashMap<Path, String>();
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var hashingTasks = Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .map(file -> CompletableFuture.runAsync(() -> {
                        try {
                            var hash = calculateFileHash(file);
                            fileHashes.put(file, hash);
                        } catch (IOException e) {
                            // Skip files that can't be read
                        }
                    }, executor))
                    .toArray(CompletableFuture[]::new);
                
                // Wait for all hashing tasks to complete
                CompletableFuture.allOf(hashingTasks).join();
                
                // Group files by hash
                fileHashes.entrySet().stream()
                    .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                    ))
                    .entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .forEach(entry -> duplicates.put(entry.getKey(), entry.getValue()));
                
            } catch (IOException e) {
                throw new CompletionException(e);
            }
            
            return Map.copyOf(duplicates);
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    private String calculateFileHash(Path file) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("MD5");
            
            try (var inputStream = Files.newInputStream(file)) {
                var buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Get organization statistics
     */
    public record OrganizationStats(
        long totalFiles,
        long organizedFiles,
        long duplicateFiles,
        long totalSize,
        Map<String, Integer> artistCounts
    ) {}
    
    public CompletableFuture<OrganizationStats> getStatsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var totalFiles = new AtomicInteger(0);
                var totalSize = new AtomicInteger(0);
                var artistCounts = new ConcurrentHashMap<String, Integer>();
                
                Files.walk(config.targetDirectory())
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        totalFiles.incrementAndGet();
                        try {
                            totalSize.addAndGet((int) Files.size(file));
                            
                            // Extract artist from path structure
                            var relativePath = config.targetDirectory().relativize(file);
                            if (relativePath.getNameCount() >= 2) {
                                var artist = relativePath.getName(0).toString();
                                artistCounts.merge(artist, 1, Integer::sum);
                            }
                        } catch (IOException e) {
                            // Skip files with issues
                        }
                    });
                
                return new OrganizationStats(
                    totalFiles.get(),
                    totalFiles.get(),
                    0L, // Would need additional tracking for duplicates
                    totalSize.get(),
                    Map.copyOf(artistCounts)
                );
                
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}