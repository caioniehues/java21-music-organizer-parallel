package com.musicorganizer.config;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.model.AudioFile;
import com.musicorganizer.organizer.NIOFileOrganizer;
import com.musicorganizer.util.ProgressTracker;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Pattern-based file organizer that integrates the PatternEngine with NIOFileOrganizer.
 * Provides flexible template-based organization using Java 21 virtual threads.
 */
public final class PatternBasedOrganizer {
    
    private final PatternEngine patternEngine;
    private final ProgressTracker progressTracker;
    private final OrganizationConfig config;
    
    /**
     * Configuration for pattern-based organization
     */
    public record OrganizationConfig(
        Path targetDirectory,
        String organizationPattern,
        boolean createDirectoryStructure,
        boolean preserveOriginalOnError,
        boolean enableRollback,
        PatternEngine.Configuration patternEngineConfig,
        PatternContext.Options contextOptions,
        StandardCopyOption... copyOptions
    ) {
        public static OrganizationConfig defaults(Path targetDirectory) {
            return new OrganizationConfig(
                targetDirectory,
                PatternEngine.Templates.STANDARD,
                true,  // create directory structure
                true,  // preserve original on error
                true,  // enable rollback
                PatternEngine.Configuration.defaults(),
                PatternContext.Options.defaults(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
        
        public static OrganizationConfig withPattern(Path targetDirectory, String pattern) {
            return new OrganizationConfig(
                targetDirectory,
                pattern,
                true,
                true,
                true,
                PatternEngine.Configuration.defaults(),
                PatternContext.Options.defaults(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        }
        
        public static OrganizationConfig classical(Path targetDirectory) {
            return withPattern(targetDirectory, PatternEngine.Templates.CLASSICAL);
        }
        
        public static OrganizationConfig genreBased(Path targetDirectory) {
            return withPattern(targetDirectory, PatternEngine.Templates.GENRE_BASED);
        }
        
        public static OrganizationConfig compilation(Path targetDirectory) {
            return withPattern(targetDirectory, PatternEngine.Templates.COMPILATION);
        }
    }
    
    /**
     * Result of pattern-based organization
     */
    public record OrganizationResult(
        Map<Path, Path> successful,
        Map<Path, Exception> failed,
        Map<String, PatternEngine.ValidationResult> patternValidations,
        List<Path> rollbackOperations,
        PatternEngine.CacheStats cacheStats,
        int totalProcessed,
        long processingTimeMillis
    ) {}
    
    public PatternBasedOrganizer(OrganizationConfig config, ProgressTracker progressTracker) {
        this.config = config;
        this.progressTracker = progressTracker;
        this.patternEngine = new PatternEngine(config.patternEngineConfig());
        
        // Validate organization pattern
        PatternEngine.ValidationResult validation = patternEngine.validatePattern(config.organizationPattern());
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Invalid organization pattern: " + 
                String.join(", ", validation.errors()));
        }
        
        // Ensure target directory exists
        try {
            Files.createDirectories(config.targetDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create target directory: " + config.targetDirectory(), e);
        }
    }
    
    /**
     * Organize files using pattern-based templates with virtual threads
     */
    public CompletableFuture<OrganizationResult> organizeFilesAsync(Map<Path, TrackMetadata> fileMetadata,
                                                                    Consumer<OrganizationEvent> onEvent) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            var successful = new ConcurrentHashMap<Path, Path>();
            var failed = new ConcurrentHashMap<Path, Exception>();
            var patternValidations = new ConcurrentHashMap<String, PatternEngine.ValidationResult>();
            var rollbackOperations = Collections.synchronizedList(new ArrayList<Path>());
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                
                if (progressTracker != null) {
                    progressTracker.startOperation("pattern_organization", fileMetadata.size());
                }
                
                // Process files concurrently
                var organizationTasks = fileMetadata.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        Path sourcePath = entry.getKey();
                        TrackMetadata metadata = entry.getValue();
                        
                        try {
                            // Generate target path using pattern
                            PatternContext context = new PatternContext.Builder()
                                .withTrackMetadata(metadata)
                                .withFilePath(sourcePath)
                                .withOptions(config.contextOptions())
                                .build();
                            
                            Path relativePath = patternEngine.evaluateToPath(config.organizationPattern(), context);
                            Path targetPath = config.targetDirectory().resolve(relativePath);
                            
                            // Notify about pattern evaluation
                            if (onEvent != null) {
                                onEvent.accept(new PatternEvaluationEvent(sourcePath, targetPath, config.organizationPattern()));
                            }
                            
                            // Perform file organization
                            Path actualTarget = organizeFile(sourcePath, targetPath, metadata);
                            successful.put(sourcePath, actualTarget);
                            
                            if (onEvent != null) {
                                onEvent.accept(new FileOrganizedEvent(sourcePath, actualTarget));
                            }
                            
                        } catch (Exception e) {
                            failed.put(sourcePath, e);
                            
                            if (onEvent != null) {
                                onEvent.accept(new OrganizationErrorEvent(sourcePath, e));
                            }
                        } finally {
                            if (progressTracker != null) {
                                progressTracker.updateProgress("pattern_organization", 
                                    successful.size() + failed.size());
                            }
                        }
                    }, executor))
                    .toArray(CompletableFuture[]::new);
                
                // Wait for all tasks to complete
                CompletableFuture.allOf(organizationTasks).join();
                
                if (progressTracker != null) {
                    progressTracker.completeOperation("pattern_organization");
                }
                
                // Validate patterns used
                patternValidations.put(config.organizationPattern(), 
                    patternEngine.validatePattern(config.organizationPattern()));
                
                return new OrganizationResult(
                    Map.copyOf(successful),
                    Map.copyOf(failed),
                    Map.copyOf(patternValidations),
                    List.copyOf(rollbackOperations),
                    new PatternEngine.CacheStats(patternEngine.getCacheSize(), 0, 0), // Simplified
                    fileMetadata.size(),
                    System.currentTimeMillis() - startTime
                );
                
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
    
    /**
     * Organize files synchronously
     */
    public OrganizationResult organizeFiles(Map<Path, TrackMetadata> fileMetadata,
                                          Consumer<OrganizationEvent> onEvent) {
        return organizeFilesAsync(fileMetadata, onEvent).join();
    }
    
    /**
     * Organize files from AudioFile objects
     */
    public CompletableFuture<OrganizationResult> organizeAudioFilesAsync(Collection<AudioFile> audioFiles,
                                                                         Consumer<OrganizationEvent> onEvent) {
        // Convert AudioFiles to metadata map
        Map<Path, TrackMetadata> metadataMap = audioFiles.stream()
            .filter(af -> af.metadata() != null)
            .collect(HashMap::new, (map, audioFile) -> {
                // Convert AudioMetadata to TrackMetadata for pattern processing
                TrackMetadata metadata = convertToTrackMetadata(audioFile);
                if (metadata != null) {
                    map.put(audioFile.path(), metadata);
                }
            }, HashMap::putAll);
        
        return organizeFilesAsync(metadataMap, onEvent);
    }
    
    /**
     * Convert AudioMetadata to TrackMetadata for pattern processing
     */
    private TrackMetadata convertToTrackMetadata(AudioFile audioFile) {
        if (audioFile.metadata() == null) {
            return null;
        }
        
        var metadata = audioFile.metadata();
        return TrackMetadata.builder()
            .title(metadata.title().orElse(null))
            .artist(metadata.artist().orElse(null))
            .album(metadata.album().orElse(null))
            .genre(metadata.genre().orElse(null))
            .year(metadata.year().orElse(null))
            .trackNumber(metadata.trackNumber().orElse(null))
            .totalTracks(metadata.totalTracks().orElse(null))
            .format(metadata.format().orElse(null))
            .build();
    }
    
    /**
     * Perform atomic file organization
     */
    private Path organizeFile(Path sourceFile, Path targetPath, TrackMetadata metadata) throws IOException {
        // Create directory structure if needed
        if (config.createDirectoryStructure()) {
            Files.createDirectories(targetPath.getParent());
        }
        
        // Perform atomic move operation
        try {
            Files.move(sourceFile, targetPath, config.copyOptions());
            return targetPath;
        } catch (IOException e) {
            if (config.preserveOriginalOnError()) {
                // Log error but don't delete original
                throw new IOException("Failed to organize file: " + sourceFile + " -> " + targetPath, e);
            }
            throw e;
        }
    }
    
    /**
     * Preview organization without actually moving files
     */
    public Map<Path, Path> previewOrganization(Map<Path, TrackMetadata> fileMetadata) {
        Map<Path, Path> preview = new HashMap<>();
        
        for (Map.Entry<Path, TrackMetadata> entry : fileMetadata.entrySet()) {
            Path sourcePath = entry.getKey();
            TrackMetadata metadata = entry.getValue();
            
            try {
                PatternContext context = new PatternContext.Builder()
                    .withTrackMetadata(metadata)
                    .withFilePath(sourcePath)
                    .withOptions(config.contextOptions())
                    .build();
                
                Path relativePath = patternEngine.evaluateToPath(config.organizationPattern(), context);
                Path targetPath = config.targetDirectory().resolve(relativePath);
                
                preview.put(sourcePath, targetPath);
            } catch (Exception e) {
                // Skip files that can't be processed
                preview.put(sourcePath, sourcePath); // No change
            }
        }
        
        return preview;
    }
    
    /**
     * Test pattern with sample metadata
     */
    public String testPattern(String pattern, TrackMetadata sampleMetadata) {
        try {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            return patternEngine.evaluatePattern(pattern, context);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Get available variables for pattern building
     */
    public Set<String> getAvailableVariables() {
        return patternEngine.getAvailableVariables(TrackMetadata.class);
    }
    
    /**
     * Validate a pattern without using it
     */
    public PatternEngine.ValidationResult validatePattern(String pattern) {
        return patternEngine.validatePattern(pattern);
    }
    
    /**
     * Base class for organization events
     */
    public sealed interface OrganizationEvent permits 
        PatternEvaluationEvent, FileOrganizedEvent, OrganizationErrorEvent {
    }
    
    /**
     * Event fired when pattern is evaluated for a file
     */
    public record PatternEvaluationEvent(
        Path sourceFile,
        Path targetPath,
        String pattern
    ) implements OrganizationEvent {}
    
    /**
     * Event fired when file is successfully organized
     */
    public record FileOrganizedEvent(
        Path sourceFile,
        Path targetPath
    ) implements OrganizationEvent {}
    
    /**
     * Event fired when organization fails
     */
    public record OrganizationErrorEvent(
        Path sourceFile,
        Exception error
    ) implements OrganizationEvent {}
    
    /**
     * Predefined organization configurations for common use cases
     */
    public static final class Presets {
        
        /**
         * Standard organization: Artist/Album/## - Track
         */
        public static OrganizationConfig standard(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.STANDARD);
        }
        
        /**
         * Organization with year: Artist/[Year] Album/## - Track
         */
        public static OrganizationConfig withYear(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.WITH_YEAR);
        }
        
        /**
         * Classical music organization: Classical/Composer/Year - Album/## - Track
         */
        public static OrganizationConfig classical(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.CLASSICAL);
        }
        
        /**
         * Genre-based organization: Genre/Artist/Album/## - Track
         */
        public static OrganizationConfig genreBased(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.GENRE_BASED);
        }
        
        /**
         * Flat organization: Artist - Album - ## - Track
         */
        public static OrganizationConfig flat(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.FLAT);
        }
        
        /**
         * Detailed organization with format info: Artist/[Year] Album [FORMAT]/##-## - Track
         */
        public static OrganizationConfig detailed(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.DETAILED);
        }
        
        /**
         * Compilation-friendly: Album Artist/[Year] Album/## - Artist - Track
         */
        public static OrganizationConfig compilation(Path targetDirectory) {
            return OrganizationConfig.withPattern(targetDirectory, PatternEngine.Templates.COMPILATION);
        }
        
        private Presets() {} // Utility class
    }
}