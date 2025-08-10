package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main configuration record for the Music Organizer application.
 * 
 * <p>This record represents the complete configuration structure, including
 * organization profiles, directory mappings, file processing rules, and
 * system-wide settings. It serves as the root configuration object that
 * can be serialized to/from JSON or YAML configuration files.</p>
 * 
 * <p>The configuration follows a hierarchical structure:
 * <ul>
 *   <li>Global settings (version, default profile)</li>
 *   <li>Organization profiles define file naming patterns</li>
 *   <li>Directory mappings connect sources to targets with profiles</li>
 *   <li>File rules provide conditional profile selection</li>
 *   <li>Watch service configuration for automated processing</li>
 * </ul>
 * 
 * @param version configuration schema version for compatibility
 * @param defaultProfile name of the default organization profile
 * @param profiles map of profile names to organization profiles
 * @param directories list of directory mappings for file organization
 * @param watchService configuration for file system watching
 * @param rules list of conditional file processing rules
 * 
 * @since 1.0
 */
public record MusicOrganizerConfig(
    @JsonProperty("version") String version,
    @JsonProperty("default_profile") String defaultProfile,
    @JsonProperty("profiles") Map<String, OrganizationProfile> profiles,
    @JsonProperty("directories") List<DirectoryMapping> directories,
    @JsonProperty("watch_service") WatchConfig watchService,
    @JsonProperty("rules") List<FileRule> rules
) {
    
    /**
     * Current configuration schema version.
     */
    public static final String CURRENT_VERSION = "1.0";
    
    /**
     * Creates a MusicOrganizerConfig with comprehensive validation.
     * 
     * @param version configuration version
     * @param defaultProfile default profile name
     * @param profiles map of available profiles
     * @param directories list of directory mappings
     * @param watchService watch service configuration
     * @param rules list of file processing rules
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public MusicOrganizerConfig {
        Objects.requireNonNull(version, "Version cannot be null");
        Objects.requireNonNull(defaultProfile, "Default profile cannot be null");
        Objects.requireNonNull(profiles, "Profiles map cannot be null");
        Objects.requireNonNull(directories, "Directories list cannot be null");
        Objects.requireNonNull(watchService, "Watch service config cannot be null");
        Objects.requireNonNull(rules, "Rules list cannot be null");
        
        if (version.trim().isEmpty()) {
            throw new IllegalArgumentException("Version cannot be empty");
        }
        
        if (defaultProfile.trim().isEmpty()) {
            throw new IllegalArgumentException("Default profile cannot be empty");
        }
        
        version = version.trim();
        defaultProfile = defaultProfile.trim();
        
        // Validate that profiles map is not empty
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("At least one organization profile must be defined");
        }
        
        // Validate that default profile exists
        if (!profiles.containsKey(defaultProfile)) {
            throw new IllegalArgumentException(
                "Default profile '" + defaultProfile + "' not found in profiles: " + profiles.keySet()
            );
        }
        
        // Validate that all directory mappings reference valid profiles
        for (DirectoryMapping mapping : directories) {
            if (!profiles.containsKey(mapping.profile())) {
                throw new IllegalArgumentException(
                    "Directory mapping '" + mapping.name() + "' references unknown profile: " + mapping.profile()
                );
            }
        }
        
        // Validate that all file rules reference valid profiles
        for (FileRule rule : rules) {
            if (!profiles.containsKey(rule.getTargetProfile())) {
                throw new IllegalArgumentException(
                    "File rule references unknown profile: " + rule.getTargetProfile()
                );
            }
        }
        
        // Check for duplicate directory mapping names
        long uniqueNames = directories.stream()
            .map(DirectoryMapping::name)
            .distinct()
            .count();
        
        if (uniqueNames != directories.size()) {
            throw new IllegalArgumentException("Directory mapping names must be unique");
        }
        
        // Validate version compatibility
        if (!isVersionCompatible(version)) {
            System.err.println("Warning: Configuration version " + version + 
                " may not be compatible with current version " + CURRENT_VERSION);
        }
    }
    
    /**
     * Creates a default configuration suitable for most use cases.
     * 
     * @return a MusicOrganizerConfig with sensible defaults
     */
    public static MusicOrganizerConfig defaultConfig() {
        Map<String, OrganizationProfile> defaultProfiles = Map.of(
            "standard", OrganizationProfile.standard(),
            "flat", OrganizationProfile.flat(),
            "detailed", OrganizationProfile.detailed(),
            "classical", OrganizationProfile.classical()
        );
        
        List<FileRule> defaultRules = List.of(
            FormatRule.lossless("detailed"),
            BitrateRule.highQuality("standard"),
            GenreRule.classical("classical")
        );
        
        return new MusicOrganizerConfig(
            CURRENT_VERSION,
            "standard",
            defaultProfiles,
            List.of(), // Empty directories - to be configured by user
            WatchConfig.defaultConfig(),
            defaultRules
        );
    }
    
    /**
     * Creates a minimal configuration for simple setups.
     * 
     * @return a MusicOrganizerConfig with minimal settings
     */
    public static MusicOrganizerConfig minimal() {
        Map<String, OrganizationProfile> minimalProfiles = Map.of(
            "simple", OrganizationProfile.standard()
        );
        
        return new MusicOrganizerConfig(
            CURRENT_VERSION,
            "simple",
            minimalProfiles,
            List.of(),
            WatchConfig.disabledConfig(),
            List.of()
        );
    }
    
    /**
     * Creates a high-performance configuration for large music libraries.
     * 
     * @return a MusicOrganizerConfig optimized for performance
     */
    public static MusicOrganizerConfig highPerformance() {
        Map<String, OrganizationProfile> performanceProfiles = Map.of(
            "lossless", OrganizationProfile.detailed(),
            "compressed", OrganizationProfile.standard(),
            "mobile", OrganizationProfile.flat()
        );
        
        List<FileRule> performanceRules = List.of(
            FormatRule.lossless("lossless"),
            FormatRule.lossy("compressed"),
            SizeRule.smallFiles("mobile")
        );
        
        return new MusicOrganizerConfig(
            CURRENT_VERSION,
            "lossless",
            performanceProfiles,
            List.of(),
            WatchConfig.highPerformanceConfig(),
            performanceRules
        );
    }
    
    /**
     * Checks if the given version is compatible with the current implementation.
     * 
     * @param configVersion the version to check
     * @return true if compatible, false otherwise
     */
    private static boolean isVersionCompatible(String configVersion) {
        // For now, only check major version compatibility
        String currentMajor = CURRENT_VERSION.split("\\.")[0];
        String configMajor = configVersion.split("\\.")[0];
        return currentMajor.equals(configMajor);
    }
    
    /**
     * Gets the organization profile for the given name.
     * 
     * @param profileName the profile name
     * @return the organization profile, or the default profile if not found
     */
    public OrganizationProfile getProfile(String profileName) {
        return profiles.getOrDefault(profileName, profiles.get(defaultProfile));
    }
    
    /**
     * Gets the default organization profile.
     * 
     * @return the default organization profile
     */
    public OrganizationProfile getDefaultProfile() {
        return profiles.get(defaultProfile);
    }
    
    /**
     * Creates a new configuration with an additional profile.
     * 
     * @param name profile name
     * @param profile organization profile
     * @return new configuration with the added profile
     */
    public MusicOrganizerConfig withProfile(String name, OrganizationProfile profile) {
        Map<String, OrganizationProfile> newProfiles = new java.util.HashMap<>(profiles);
        newProfiles.put(name, profile);
        return new MusicOrganizerConfig(version, defaultProfile, Map.copyOf(newProfiles), directories, watchService, rules);
    }
    
    /**
     * Creates a new configuration with an additional directory mapping.
     * 
     * @param mapping directory mapping to add
     * @return new configuration with the added mapping
     */
    public MusicOrganizerConfig withDirectory(DirectoryMapping mapping) {
        List<DirectoryMapping> newDirectories = new java.util.ArrayList<>(directories);
        newDirectories.add(mapping);
        return new MusicOrganizerConfig(version, defaultProfile, profiles, List.copyOf(newDirectories), watchService, rules);
    }
    
    /**
     * Creates a new configuration with an additional file rule.
     * 
     * @param rule file rule to add
     * @return new configuration with the added rule
     */
    public MusicOrganizerConfig withRule(FileRule rule) {
        List<FileRule> newRules = new java.util.ArrayList<>(rules);
        newRules.add(rule);
        return new MusicOrganizerConfig(version, defaultProfile, profiles, directories, watchService, List.copyOf(newRules));
    }
    
    /**
     * Gets a summary of this configuration for display purposes.
     * 
     * @return a human-readable configuration summary
     */
    public String getSummary() {
        return String.format("""
            Music Organizer Configuration (v%s)
            Default Profile: %s
            Profiles: %d (%s)
            Directory Mappings: %d
            File Rules: %d
            Watch Service: %s
            """,
            version,
            defaultProfile,
            profiles.size(),
            String.join(", ", profiles.keySet()),
            directories.size(),
            rules.size(),
            watchService.enabled() ? "Enabled" : "Disabled"
        );
    }
}