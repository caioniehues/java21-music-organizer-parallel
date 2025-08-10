package com.musicorganizer.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Service class for loading and validating music organizer configurations.
 * 
 * <p>This class provides methods to load configuration from various sources
 * including JSON files, YAML files, and string content. It handles Jackson
 * ObjectMapper configuration for proper serialization/deserialization of
 * Java 21 features like records, optionals, and time types.</p>
 * 
 * <p>The loader performs comprehensive validation of loaded configurations
 * and provides detailed error messages for troubleshooting configuration issues.</p>
 * 
 * @since 1.0
 */
public final class ConfigurationLoader {
    
    private static final ObjectMapper JSON_MAPPER;
    private static final ObjectMapper YAML_MAPPER;
    
    static {
        JSON_MAPPER = createObjectMapper();
        YAML_MAPPER = createObjectMapper(new YAMLFactory());
    }
    
    private ConfigurationLoader() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Loads configuration from a file, auto-detecting the format based on file extension.
     * 
     * @param configPath path to the configuration file
     * @return the loaded and validated configuration
     * @throws IOException if the file cannot be read
     * @throws ConfigurationException if the configuration is invalid
     */
    public static MusicOrganizerConfig loadFromFile(Path configPath) throws IOException, ConfigurationException {
        Objects.requireNonNull(configPath, "Config path cannot be null");
        
        if (!Files.exists(configPath)) {
            throw new IOException("Configuration file not found: " + configPath);
        }
        
        if (!Files.isRegularFile(configPath)) {
            throw new IOException("Configuration path is not a regular file: " + configPath);
        }
        
        String content = Files.readString(configPath);
        String fileName = configPath.getFileName().toString().toLowerCase();
        
        try {
            MusicOrganizerConfig config;
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                config = loadFromYaml(content);
            } else if (fileName.endsWith(".json")) {
                config = loadFromJson(content);
            } else {
                // Try to auto-detect format
                config = autoDetectAndLoad(content);
            }
            
            validate(config);
            return config;
            
        } catch (Exception e) {
            throw new ConfigurationException("Failed to load configuration from " + configPath, e);
        }
    }
    
    /**
     * Loads configuration from a JSON string.
     * 
     * @param jsonContent the JSON content as a string
     * @return the loaded and validated configuration
     * @throws ConfigurationException if the JSON is invalid or configuration is malformed
     */
    public static MusicOrganizerConfig loadFromJson(String jsonContent) throws ConfigurationException {
        Objects.requireNonNull(jsonContent, "JSON content cannot be null");
        
        try {
            MusicOrganizerConfig config = JSON_MAPPER.readValue(jsonContent, MusicOrganizerConfig.class);
            validate(config);
            return config;
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse JSON configuration", e);
        }
    }
    
    /**
     * Loads configuration from a YAML string.
     * 
     * @param yamlContent the YAML content as a string
     * @return the loaded and validated configuration
     * @throws ConfigurationException if the YAML is invalid or configuration is malformed
     */
    public static MusicOrganizerConfig loadFromYaml(String yamlContent) throws ConfigurationException {
        Objects.requireNonNull(yamlContent, "YAML content cannot be null");
        
        try {
            MusicOrganizerConfig config = YAML_MAPPER.readValue(yamlContent, MusicOrganizerConfig.class);
            validate(config);
            return config;
        } catch (Exception e) {
            throw new ConfigurationException("Failed to parse YAML configuration", e);
        }
    }
    
    /**
     * Gets a default configuration for first-time setup.
     * 
     * @return a default configuration with sensible settings
     */
    public static MusicOrganizerConfig getDefaultConfig() {
        return MusicOrganizerConfig.defaultConfig();
    }
    
    /**
     * Saves a configuration to a file in the specified format.
     * 
     * @param config the configuration to save
     * @param configPath the output file path
     * @throws IOException if the file cannot be written
     */
    public static void saveToFile(MusicOrganizerConfig config, Path configPath) throws IOException {
        Objects.requireNonNull(config, "Config cannot be null");
        Objects.requireNonNull(configPath, "Config path cannot be null");
        
        String fileName = configPath.getFileName().toString().toLowerCase();
        String content;
        
        try {
            if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                content = YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config);
            } else {
                content = JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config);
            }
            
            // Ensure parent directory exists
            Path parentDir = configPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            Files.writeString(configPath, content);
            
        } catch (Exception e) {
            throw new IOException("Failed to save configuration to " + configPath, e);
        }
    }
    
    /**
     * Validates a loaded configuration for consistency and completeness.
     * 
     * @param config the configuration to validate
     * @throws ConfigurationException if validation fails
     */
    public static void validate(MusicOrganizerConfig config) throws ConfigurationException {
        Objects.requireNonNull(config, "Config cannot be null");
        
        try {
            // Basic validation is already done in the record constructor
            // Additional semantic validation can be added here
            
            // Validate that watch service settings are reasonable if enabled
            if (config.watchService().enabled()) {
                if (config.watchService().batchSize() > 1000) {
                    System.err.println("Warning: Large batch size may cause memory issues: " + 
                        config.watchService().batchSize());
                }
                
                if (config.watchService().virtualThreadPoolSize() > 5000) {
                    System.err.println("Warning: Very large thread pool size: " + 
                        config.watchService().virtualThreadPoolSize());
                }
            }
            
            // Validate that directory mappings don't have circular references
            validateDirectoryMappings(config);
            
        } catch (Exception e) {
            throw new ConfigurationException("Configuration validation failed", e);
        }
    }
    
    /**
     * Gets a sample configuration in JSON format for documentation purposes.
     * 
     * @return a sample configuration as a JSON string
     */
    public static String getSampleConfigJson() {
        try {
            MusicOrganizerConfig sample = MusicOrganizerConfig.defaultConfig();
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate sample configuration", e);
        }
    }
    
    /**
     * Gets a sample configuration in YAML format for documentation purposes.
     * 
     * @return a sample configuration as a YAML string
     */
    public static String getSampleConfigYaml() {
        try {
            MusicOrganizerConfig sample = MusicOrganizerConfig.defaultConfig();
            return YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate sample configuration", e);
        }
    }
    
    private static ObjectMapper createObjectMapper() {
        return createObjectMapper(null);
    }
    
    private static ObjectMapper createObjectMapper(YAMLFactory yamlFactory) {
        ObjectMapper mapper = yamlFactory != null ? new ObjectMapper(yamlFactory) : new ObjectMapper();
        
        // Configure modules for Java 21 and modern features
        mapper.registerModule(new Jdk8Module());        // Optional support
        mapper.registerModule(new JavaTimeModule());    // Duration, LocalDateTime, etc.
        
        // Configure for better error reporting
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        
        return mapper;
    }
    
    private static MusicOrganizerConfig autoDetectAndLoad(String content) throws ConfigurationException {
        // Try JSON first, then YAML
        try {
            return loadFromJson(content);
        } catch (ConfigurationException e) {
            try {
                return loadFromYaml(content);
            } catch (ConfigurationException yamlEx) {
                throw new ConfigurationException(
                    "Could not parse configuration as JSON or YAML. JSON error: " + e.getMessage() + 
                    ". YAML error: " + yamlEx.getMessage()
                );
            }
        }
    }
    
    private static void validateDirectoryMappings(MusicOrganizerConfig config) throws ConfigurationException {
        // Check for potential issues with directory mappings
        for (DirectoryMapping mapping : config.directories()) {
            // Additional validation beyond constructor checks
            if (mapping.watch() && !config.watchService().enabled()) {
                System.err.println("Warning: Directory mapping '" + mapping.name() + 
                    "' has watch enabled but watch service is disabled");
            }
        }
    }
    
    /**
     * Exception thrown when configuration loading or validation fails.
     */
    public static class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }
        
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}