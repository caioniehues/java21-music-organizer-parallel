package com.musicorganizer.validator;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.util.ProgressTracker;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Collection validator using Virtual Threads for parallel validation,
 * file integrity checks, metadata completeness verification, and incomplete album detection.
 */
public class CollectionValidator {
    
    public record ValidationResult(
        List<ValidationIssue> issues,
        Map<String, AlbumCompleteness> albumCompleteness,
        Map<Path, FileIntegrity> fileIntegrityResults,
        ValidationSummary summary,
        Duration validationTime
    ) {}
    
    public record ValidationIssue(
        IssueType type,
        IssueSeverity severity,
        Path filePath,
        String message,
        String suggestion
    ) {}
    
    public record AlbumCompleteness(
        String artist,
        String album,
        int totalTracks,
        List<Integer> missingTracks,
        List<Path> presentTracks,
        boolean isComplete,
        Map<String, Object> metadata
    ) {}
    
    public record FileIntegrity(
        Path file,
        boolean isReadable,
        boolean hasValidMetadata,
        Optional<String> checksum,
        List<String> integrityIssues
    ) {}
    
    public record ValidationSummary(
        int totalFiles,
        int validFiles,
        int corruptFiles,
        int incompleteAlbums,
        int duplicateFiles,
        Map<IssueType, Integer> issueCounts
    ) {}
    
    public enum IssueType {
        MISSING_METADATA,
        CORRUPT_FILE,
        DUPLICATE_FILE,
        INCOMPLETE_ALBUM,
        INVALID_FILENAME,
        MISSING_COVER_ART,
        INCONSISTENT_METADATA,
        LOW_QUALITY_AUDIO
    }
    
    public enum IssueSeverity {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    public record ValidationConfig(
        boolean checkFileIntegrity,
        boolean validateMetadata,
        boolean detectIncompleteAlbums,
        boolean findDuplicates,
        boolean validateCoverArt,
        Set<String> supportedFormats,
        int minBitrate,
        boolean strictMetadataValidation
    ) {
        public static ValidationConfig defaultConfig() {
            return new ValidationConfig(
                true,  // check file integrity
                true,  // validate metadata
                true,  // detect incomplete albums
                true,  // find duplicates
                true,  // validate cover art
                Set.of("mp3", "flac", "m4a", "ogg", "wav"),
                128,   // min bitrate
                false  // strict metadata validation
            );
        }
    }
    
    private final ValidationConfig config;
    private final ProgressTracker progressTracker;
    
    public CollectionValidator(ValidationConfig config, ProgressTracker progressTracker) {
        this.config = config;
        this.progressTracker = progressTracker;
    }
    
    /**
     * Validate entire music collection using Virtual Threads
     */
    public CompletableFuture<ValidationResult> validateCollectionAsync(Map<Path, TrackMetadata> collection) {
        return CompletableFuture.supplyAsync(() -> {
            var startTime = System.nanoTime();
            var issues = new ConcurrentLinkedQueue<ValidationIssue>();
            var fileIntegrityResults = new ConcurrentHashMap<Path, FileIntegrity>();
            var processedCount = new AtomicInteger(0);
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                progressTracker.startOperation("collection_validation", collection.size());
                
                // Validate individual files in parallel
                var validationTasks = collection.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        var file = entry.getKey();
                        var metadata = entry.getValue();
                        
                        validateFile(file, metadata, issues, fileIntegrityResults);
                        
                        int completed = processedCount.incrementAndGet();
                        progressTracker.updateProgress("collection_validation", completed);
                        
                    }, executor))
                    .toArray(CompletableFuture[]::new);
                
                CompletableFuture.allOf(validationTasks).join();
                
                // Analyze album completeness
                var albumCompleteness = config.detectIncompleteAlbums() 
                    ? analyzeAlbumCompleteness(collection)
                    : Map.<String, AlbumCompleteness>of();
                
                // Find duplicates if enabled
                if (config.findDuplicates()) {
                    findDuplicates(collection, issues);
                }
                
                progressTracker.completeOperation("collection_validation");
                
                var endTime = System.nanoTime();
                var validationTime = Duration.ofNanos(endTime - startTime);
                
                var issuesList = new ArrayList<>(issues);
                var summary = createValidationSummary(issuesList, fileIntegrityResults, albumCompleteness);
                
                return new ValidationResult(
                    issuesList,
                    albumCompleteness,
                    Map.copyOf(fileIntegrityResults),
                    summary,
                    validationTime
                );
                
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Validate individual file
     */
    private void validateFile(Path file, TrackMetadata metadata,
                            ConcurrentLinkedQueue<ValidationIssue> issues,
                            ConcurrentHashMap<Path, FileIntegrity> integrityResults) {
        
        var integrityIssues = new ArrayList<String>();
        var isReadable = Files.isReadable(file);
        var hasValidMetadata = metadata != null;
        Optional<String> checksum = Optional.empty();
        
        // File integrity check
        if (config.checkFileIntegrity()) {
            if (!isReadable) {
                integrityIssues.add("File is not readable");
                issues.add(new ValidationIssue(
                    IssueType.CORRUPT_FILE,
                    IssueSeverity.CRITICAL,
                    file,
                    "File is not readable or accessible",
                    "Check file permissions and disk health"
                ));
            } else {
                checksum = calculateFileChecksum(file);
                if (checksum.isEmpty()) {
                    integrityIssues.add("Failed to calculate checksum");
                }
            }
        }
        
        // Metadata validation
        if (config.validateMetadata() && hasValidMetadata) {
            validateMetadata(file, metadata, issues);
        } else if (!hasValidMetadata) {
            issues.add(new ValidationIssue(
                IssueType.MISSING_METADATA,
                IssueSeverity.ERROR,
                file,
                "No metadata available for file",
                "Extract or add metadata to this file"
            ));
        }
        
        // Cover art validation
        if (config.validateCoverArt() && hasValidMetadata) {
            validateCoverArt(file, metadata, issues);
        }
        
        // Audio quality check
        if (hasValidMetadata && metadata.bitRate().isPresent()) {
            var bitrate = metadata.bitRate().get();
            if (bitrate < config.minBitrate()) {
                issues.add(new ValidationIssue(
                    IssueType.LOW_QUALITY_AUDIO,
                    IssueSeverity.WARNING,
                    file,
                    String.format("Low bitrate: %d kbps (minimum: %d kbps)", bitrate, config.minBitrate()),
                    "Consider re-encoding at higher quality"
                ));
            }
        }
        
        // File format validation
        var extension = getFileExtension(file).toLowerCase();
        if (!config.supportedFormats().contains(extension)) {
            issues.add(new ValidationIssue(
                IssueType.INVALID_FILENAME,
                IssueSeverity.WARNING,
                file,
                "Unsupported file format: " + extension,
                "Convert to supported format: " + config.supportedFormats()
            ));
        }
        
        integrityResults.put(file, new FileIntegrity(
            file,
            isReadable,
            hasValidMetadata,
            checksum,
            List.copyOf(integrityIssues)
        ));
    }
    
    /**
     * Validate metadata completeness and consistency
     */
    private void validateMetadata(Path file, TrackMetadata metadata, 
                                ConcurrentLinkedQueue<ValidationIssue> issues) {
        
        var requiredFields = List.of("title", "artist", "album");
        var missingFields = new ArrayList<String>();
        
        if (metadata.title() == null || metadata.title().isEmpty()) missingFields.add("title");
        if (metadata.artist() == null || metadata.artist().isEmpty()) missingFields.add("artist");
        if (metadata.album() == null || metadata.album().isEmpty()) missingFields.add("album");
        
        if (!missingFields.isEmpty()) {
            var severity = config.strictMetadataValidation() ? IssueSeverity.ERROR : IssueSeverity.WARNING;
            issues.add(new ValidationIssue(
                IssueType.MISSING_METADATA,
                severity,
                file,
                "Missing required metadata fields: " + String.join(", ", missingFields),
                "Add missing metadata using a tag editor"
            ));
        }
        
        // Optional but recommended fields
        var recommendedFields = new ArrayList<String>();
        if (metadata.trackNumber().isEmpty()) recommendedFields.add("track number");
        if (metadata.year().isEmpty()) recommendedFields.add("year");
        if (metadata.genre() == null || metadata.genre().isEmpty()) recommendedFields.add("genre");
        
        if (!recommendedFields.isEmpty()) {
            issues.add(new ValidationIssue(
                IssueType.MISSING_METADATA,
                IssueSeverity.INFO,
                file,
                "Missing recommended metadata fields: " + String.join(", ", recommendedFields),
                "Consider adding these fields for better organization"
            ));
        }
    }
    
    /**
     * Validate cover art presence and quality
     */
    private void validateCoverArt(Path file, TrackMetadata metadata,
                                ConcurrentLinkedQueue<ValidationIssue> issues) {
        
        // Check embedded cover art
        var hasEmbeddedCover = metadata.albumArt().isPresent();
        
        // Check for album folder cover art
        var albumDir = file.getParent();
        var hasFolderCover = findAlbumCoverInDirectory(albumDir);
        
        if (!hasEmbeddedCover && !hasFolderCover) {
            issues.add(new ValidationIssue(
                IssueType.MISSING_COVER_ART,
                IssueSeverity.WARNING,
                file,
                "No cover art found (neither embedded nor in album folder)",
                "Add cover art: embed in file or place cover.jpg/folder.jpg in album directory"
            ));
        }
        
        // Validate embedded cover art quality if present
        if (hasEmbeddedCover) {
            var coverArt = metadata.albumArt().get();
            if (coverArt.length < 10000) { // Less than ~10KB
                issues.add(new ValidationIssue(
                    IssueType.MISSING_COVER_ART,
                    IssueSeverity.INFO,
                    file,
                    "Embedded cover art appears to be low resolution",
                    "Consider using higher resolution cover art (at least 500x500 pixels)"
                ));
            }
        }
    }
    
    /**
     * Find album cover in directory
     */
    private boolean findAlbumCoverInDirectory(Path directory) {
        var coverNames = List.of("cover.jpg", "cover.png", "folder.jpg", "folder.png", 
                               "albumart.jpg", "albumart.png", "front.jpg", "front.png");
        
        return coverNames.stream()
            .anyMatch(name -> Files.exists(directory.resolve(name)));
    }
    
    /**
     * Analyze album completeness
     */
    private Map<String, AlbumCompleteness> analyzeAlbumCompleteness(Map<Path, TrackMetadata> collection) {
        return collection.entrySet().stream()
            .filter(entry -> entry.getValue() != null && 
                           entry.getValue().artist() != null && 
                           entry.getValue().album() != null)
            .collect(Collectors.groupingBy(
                entry -> entry.getValue().artist() + " - " + entry.getValue().album(),
                Collectors.toList()
            ))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> analyzeAlbum(entry.getValue())
            ));
    }
    
    /**
     * Analyze individual album completeness
     */
    private AlbumCompleteness analyzeAlbum(List<Map.Entry<Path, TrackMetadata>> albumTracks) {
        if (albumTracks.isEmpty()) {
            return new AlbumCompleteness("Unknown", "Unknown", 0, List.of(), List.of(), false, Map.of());
        }
        
        var firstTrack = albumTracks.get(0).getValue();
        var artist = firstTrack.artist() != null ? firstTrack.artist() : "Unknown";
        var album = firstTrack.album() != null ? firstTrack.album() : "Unknown";
        
        var trackNumbers = albumTracks.stream()
            .map(entry -> entry.getValue().trackNumber())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
        
        var presentTracks = albumTracks.stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Determine total tracks (use highest track number or total tracks metadata)
        var maxTrackNumber = trackNumbers.stream().mapToInt(Integer::intValue).max().orElse(0);
        var totalTracks = firstTrack.totalTracks().orElse(maxTrackNumber);
        
        // Find missing tracks
        var missingTracks = new ArrayList<Integer>();
        for (int i = 1; i <= totalTracks; i++) {
            if (!trackNumbers.contains(i)) {
                missingTracks.add(i);
            }
        }
        
        var isComplete = missingTracks.isEmpty() && !trackNumbers.isEmpty();
        
        // Collect additional metadata
        var metadata = new HashMap<String, Object>();
        firstTrack.year().ifPresent(year -> metadata.put("year", year));
        if (firstTrack.genre() != null) metadata.put("genre", firstTrack.genre());
        
        return new AlbumCompleteness(
            artist,
            album,
            totalTracks,
            List.copyOf(missingTracks),
            presentTracks,
            isComplete,
            Map.copyOf(metadata)
        );
    }
    
    /**
     * Find duplicate files based on metadata and content
     */
    private void findDuplicates(Map<Path, TrackMetadata> collection,
                              ConcurrentLinkedQueue<ValidationIssue> issues) {
        
        // Group by metadata signature
        var metadataGroups = collection.entrySet().stream()
            .collect(Collectors.groupingBy(
                entry -> createMetadataSignature(entry.getValue()),
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())
            ));
        
        metadataGroups.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .forEach(entry -> {
                var duplicates = entry.getValue();
                var signature = entry.getKey();
                
                for (var file : duplicates) {
                    issues.add(new ValidationIssue(
                        IssueType.DUPLICATE_FILE,
                        IssueSeverity.WARNING,
                        file,
                        String.format("Duplicate file found (signature: %s), duplicates: %s", 
                                    signature, duplicates.size()),
                        "Review and remove duplicate files to save space"
                    ));
                }
            });
    }
    
    /**
     * Create metadata signature for duplicate detection
     */
    private String createMetadataSignature(TrackMetadata metadata) {
        if (metadata == null) {
            return "null|||";
        }
        return String.format("%s|%s|%s|%s",
            metadata.artist() != null ? metadata.artist() : "",
            metadata.album() != null ? metadata.album() : "",
            metadata.title() != null ? metadata.title() : "",
            metadata.trackNumber().map(String::valueOf).orElse("")
        );
    }
    
    /**
     * Calculate file checksum for integrity verification
     */
    private Optional<String> calculateFileChecksum(Path file) {
        try {
            var digest = MessageDigest.getInstance("MD5");
            
            try (var inputStream = Files.newInputStream(file)) {
                var buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return Optional.of(HexFormat.of().formatHex(digest.digest()));
            
        } catch (IOException | NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Create validation summary
     */
    private ValidationSummary createValidationSummary(List<ValidationIssue> issues,
                                                    Map<Path, FileIntegrity> integrityResults,
                                                    Map<String, AlbumCompleteness> albumCompleteness) {
        
        var totalFiles = integrityResults.size();
        var validFiles = (int) integrityResults.values().stream()
            .filter(fi -> fi.isReadable() && fi.hasValidMetadata() && fi.integrityIssues().isEmpty())
            .count();
        var corruptFiles = (int) integrityResults.values().stream()
            .filter(fi -> !fi.isReadable() || !fi.integrityIssues().isEmpty())
            .count();
        var incompleteAlbums = (int) albumCompleteness.values().stream()
            .filter(ac -> !ac.isComplete())
            .count();
        var duplicateFiles = (int) issues.stream()
            .filter(issue -> issue.type() == IssueType.DUPLICATE_FILE)
            .count();
        
        var issueCounts = issues.stream()
            .collect(Collectors.groupingBy(
                ValidationIssue::type,
                Collectors.reducing(0, e -> 1, Integer::sum)
            ));
        
        return new ValidationSummary(
            totalFiles,
            validFiles,
            corruptFiles,
            incompleteAlbums,
            duplicateFiles,
            issueCounts
        );
    }
    
    /**
     * Get file extension
     */
    private String getFileExtension(Path file) {
        var filename = file.getFileName().toString();
        var lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }
    
    /**
     * Synchronous version for simpler usage
     */
    public ValidationResult validateCollection(Map<Path, TrackMetadata> collection) {
        return validateCollectionAsync(collection).join();
    }
    
    /**
     * Validate specific album completeness
     */
    public CompletableFuture<List<AlbumCompleteness>> findIncompleteAlbumsAsync(Map<Path, TrackMetadata> collection) {
        return CompletableFuture.supplyAsync(() -> {
            var albumCompleteness = analyzeAlbumCompleteness(collection);
            
            return albumCompleteness.values().stream()
                .filter(album -> !album.isComplete())
                .sorted(Comparator.comparing(album -> album.artist() + " - " + album.album()))
                .collect(Collectors.toList());
                
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}