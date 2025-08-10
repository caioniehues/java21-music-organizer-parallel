package com.musicorganizer.config;

import com.musicorganizer.model.AudioFile;
import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.model.AudioMetadata;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Context for pattern evaluation providing metadata access, variable caching,
 * and custom variable providers. Thread-safe and optimized for concurrent use.
 */
public final class PatternContext {
    
    private final Map<String, Object> variableCache;
    private final Map<String, Supplier<Optional<Object>>> customProviders;
    private final PatternContext.Options options;
    
    // Source data (only one should be non-null)
    private final TrackMetadata trackMetadata;
    private final AudioMetadata audioMetadata;
    private final AudioFile audioFile;
    private final Path filePath;
    
    private PatternContext(Builder builder) {
        this.variableCache = new ConcurrentHashMap<>(builder.variableCache);
        this.customProviders = Map.copyOf(builder.customProviders);
        this.options = builder.options;
        this.trackMetadata = builder.trackMetadata;
        this.audioMetadata = builder.audioMetadata;
        this.audioFile = builder.audioFile;
        this.filePath = builder.filePath;
    }
    
    /**
     * Configuration options for pattern evaluation
     */
    public record Options(
        boolean useCache,
        boolean strictValidation,
        boolean failOnMissingVariable,
        String missingVariablePlaceholder,
        boolean sanitizeValues,
        int maxVariableLength
    ) {
        public static Options defaults() {
            return new Options(
                true,  // useCache
                false, // strictValidation
                false, // failOnMissingVariable
                "Unknown", // missingVariablePlaceholder
                true,  // sanitizeValues
                200    // maxVariableLength
            );
        }
        
        public static Options strict() {
            return new Options(
                true,  // useCache
                true,  // strictValidation
                true,  // failOnMissingVariable
                null,  // missingVariablePlaceholder (will throw)
                true,  // sanitizeValues
                200    // maxVariableLength
            );
        }
        
        public static Options permissive() {
            return new Options(
                true,  // useCache
                false, // strictValidation
                false, // failOnMissingVariable
                "Unknown", // missingVariablePlaceholder
                true,  // sanitizeValues
                500    // maxVariableLength
            );
        }
    }
    
    /**
     * Get variable value with caching and custom provider support
     */
    public Optional<Object> getVariable(String variableName) {
        if (variableName == null || variableName.isBlank()) {
            return Optional.empty();
        }
        
        // Check cache first if enabled
        if (options.useCache() && variableCache.containsKey(variableName)) {
            return Optional.ofNullable(variableCache.get(variableName));
        }
        
        // Check custom providers
        if (customProviders.containsKey(variableName)) {
            Optional<Object> customValue = customProviders.get(variableName).get();
            if (customValue.isPresent() && options.useCache()) {
                variableCache.put(variableName, customValue.get());
            }
            return customValue;
        }
        
        // Get from predefined variables
        Optional<Object> value = getStandardVariable(variableName);
        
        // Cache the result if enabled (don't cache null values)
        if (options.useCache() && value.isPresent()) {
            variableCache.put(variableName, value.get());
        }
        
        return value;
    }
    
    /**
     * Get standard variable value from metadata
     */
    private Optional<Object> getStandardVariable(String variableName) {
        PatternVariable variable = getPatternVariable(variableName);
        if (variable == null) {
            return Optional.empty();
        }
        
        Optional<Object> value = Optional.empty();
        
        // Try to extract from available sources in order of preference
        if (trackMetadata != null) {
            value = variable.extractFromTrackMetadata(trackMetadata);
        } else if (audioFile != null) {
            value = variable.extractFromAudioFile(audioFile);
        } else if (audioMetadata != null) {
            value = variable.extractFromAudioMetadata(audioMetadata);
        }
        
        // Handle file-specific variables
        if (value.isEmpty() && filePath != null) {
            value = getFilePathVariable(variableName, filePath);
        }
        
        // Validate the value if strict validation is enabled
        if (value.isPresent() && options.strictValidation() && !variable.isValid(value.get())) {
            if (options.failOnMissingVariable()) {
                throw new PatternEvaluationException("Invalid value for variable '" + variableName + "': " + value.get());
            }
            return Optional.empty();
        }
        
        return value;
    }
    
    /**
     * Get pattern variable definition by name
     */
    private PatternVariable getPatternVariable(String variableName) {
        return switch (variableName.toLowerCase()) {
            case "artist" -> PatternVariable.Variables.ARTIST;
            case "album" -> PatternVariable.Variables.ALBUM;
            case "title" -> PatternVariable.Variables.TITLE;
            case "genre" -> PatternVariable.Variables.GENRE;
            case "format" -> PatternVariable.Variables.FORMAT;
            case "year" -> PatternVariable.Variables.YEAR;
            case "track" -> PatternVariable.Variables.TRACK;
            case "disc" -> PatternVariable.Variables.DISC;
            case "totaltracks" -> PatternVariable.Variables.TOTAL_TRACKS;
            case "totaldiscs" -> PatternVariable.Variables.TOTAL_DISCS;
            case "albumartist" -> PatternVariable.Variables.ALBUM_ARTIST;
            case "composer" -> PatternVariable.Variables.COMPOSER;
            case "publisher" -> PatternVariable.Variables.PUBLISHER;
            case "key" -> PatternVariable.Variables.KEY;
            case "mood" -> PatternVariable.Variables.MOOD;
            case "mbid" -> PatternVariable.Variables.MUSICBRAINZ_ID;
            case "language" -> PatternVariable.Variables.LANGUAGE;
            default -> null;
        };
    }
    
    /**
     * Extract file-specific variables (filename, extension, etc.)
     */
    private Optional<Object> getFilePathVariable(String variableName, Path path) {
        return switch (variableName.toLowerCase()) {
            case "filename" -> Optional.of(getFilenameWithoutExtension(path));
            case "extension", "ext" -> Optional.of(getFileExtension(path));
            case "directory", "dir" -> Optional.of(path.getParent().getFileName().toString());
            default -> Optional.empty();
        };
    }
    
    private String getFilenameWithoutExtension(Path path) {
        String filename = path.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
    
    private String getFileExtension(Path path) {
        String filename = path.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }
    
    /**
     * Check if a variable exists and has a non-empty value
     */
    public boolean hasVariable(String variableName) {
        return getVariable(variableName).isPresent();
    }
    
    /**
     * Get variable value with default fallback
     */
    public Object getVariableOrDefault(String variableName, Object defaultValue) {
        return getVariable(variableName).orElse(defaultValue);
    }
    
    /**
     * Get variable value as string with formatting and default fallback
     */
    public String getFormattedVariable(String variableName, PatternFormatter.FormatSpecifier formatter) {
        Optional<Object> value = getVariable(variableName);
        if (value.isEmpty()) {
            if (options.failOnMissingVariable()) {
                throw new PatternEvaluationException("Required variable '" + variableName + "' is missing");
            }
            return options.missingVariablePlaceholder();
        }
        
        String formatted = formatter.format(value.get());
        
        // Apply global sanitization if enabled
        if (options.sanitizeValues()) {
            formatted = PatternFormatter.sanitizeFilename(formatted);
        }
        
        // Apply length limit
        if (formatted.length() > options.maxVariableLength()) {
            formatted = formatted.substring(0, options.maxVariableLength() - 3) + "...";
        }
        
        return formatted;
    }
    
    /**
     * Clear variable cache
     */
    public void clearCache() {
        variableCache.clear();
    }
    
    /**
     * Get all available variables (for debugging/introspection)
     */
    public Set<String> getAvailableVariables() {
        Set<String> available = new HashSet<>();
        
        // Add standard variables that have values
        for (String varName : List.of("artist", "album", "title", "genre", "format", "year", 
                                     "track", "disc", "totaltracks", "totaldiscs", "albumartist", 
                                     "composer", "publisher", "key", "mood", "mbid", "language")) {
            if (hasVariable(varName)) {
                available.add(varName);
            }
        }
        
        // Add file-specific variables if file path is available
        if (filePath != null) {
            available.addAll(Set.of("filename", "extension", "ext", "directory", "dir"));
        }
        
        // Add custom variables
        available.addAll(customProviders.keySet());
        
        return Collections.unmodifiableSet(available);
    }
    
    /**
     * Get context statistics for monitoring
     */
    public record ContextStats(
        int cacheSize,
        int customProviders,
        int availableVariables,
        boolean hasTrackMetadata,
        boolean hasAudioMetadata,
        boolean hasAudioFile,
        boolean hasFilePath
    ) {}
    
    public ContextStats getStats() {
        return new ContextStats(
            variableCache.size(),
            customProviders.size(),
            getAvailableVariables().size(),
            trackMetadata != null,
            audioMetadata != null,
            audioFile != null,
            filePath != null
        );
    }
    
    /**
     * Builder for creating PatternContext instances
     */
    public static class Builder {
        private final Map<String, Object> variableCache = new HashMap<>();
        private final Map<String, Supplier<Optional<Object>>> customProviders = new HashMap<>();
        private Options options = Options.defaults();
        private TrackMetadata trackMetadata;
        private AudioMetadata audioMetadata;
        private AudioFile audioFile;
        private Path filePath;
        
        public Builder withTrackMetadata(TrackMetadata metadata) {
            this.trackMetadata = metadata;
            return this;
        }
        
        public Builder withAudioMetadata(AudioMetadata metadata) {
            this.audioMetadata = metadata;
            return this;
        }
        
        public Builder withAudioFile(AudioFile audioFile) {
            this.audioFile = audioFile;
            return this;
        }
        
        public Builder withFilePath(Path filePath) {
            this.filePath = filePath;
            return this;
        }
        
        public Builder withOptions(Options options) {
            this.options = options != null ? options : Options.defaults();
            return this;
        }
        
        public Builder addCustomProvider(String variableName, Supplier<Optional<Object>> provider) {
            if (variableName != null && !variableName.isBlank() && provider != null) {
                this.customProviders.put(variableName, provider);
            }
            return this;
        }
        
        public Builder cacheVariable(String variableName, Object value) {
            if (variableName != null && !variableName.isBlank()) {
                this.variableCache.put(variableName, value);
            }
            return this;
        }
        
        public PatternContext build() {
            // Validation
            int sourceCount = 0;
            if (trackMetadata != null) sourceCount++;
            if (audioMetadata != null) sourceCount++;
            if (audioFile != null) sourceCount++;
            if (filePath != null) sourceCount++;
            
            if (sourceCount == 0) {
                throw new IllegalStateException("At least one metadata source must be provided");
            }
            
            return new PatternContext(this);
        }
    }
    
    /**
     * Exception thrown during pattern evaluation
     */
    public static class PatternEvaluationException extends RuntimeException {
        public PatternEvaluationException(String message) {
            super(message);
        }
        
        public PatternEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Static factory methods for common use cases
     */
    public static PatternContext forTrackMetadata(TrackMetadata metadata) {
        return new Builder().withTrackMetadata(metadata).build();
    }
    
    public static PatternContext forAudioFile(AudioFile audioFile) {
        return new Builder().withAudioFile(audioFile).build();
    }
    
    public static PatternContext forPath(Path filePath) {
        return new Builder().withFilePath(filePath).build();
    }
}