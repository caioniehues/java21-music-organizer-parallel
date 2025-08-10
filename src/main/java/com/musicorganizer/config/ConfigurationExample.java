package com.musicorganizer.config;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Example class demonstrating usage of the configuration system.
 * 
 * <p>This class shows how to programmatically create and configure
 * music organizer configurations using the Java 21 record-based
 * configuration classes.</p>
 * 
 * @since 1.0
 */
public final class ConfigurationExample {
    
    private ConfigurationExample() {
        // Utility class
    }
    
    /**
     * Creates a sample configuration for a home music library setup.
     * 
     * @return a complete MusicOrganizerConfig for home use
     */
    public static MusicOrganizerConfig createHomeMusicLibraryConfig() {
        // Define custom organization profiles
        Map<String, OrganizationProfile> profiles = Map.of(
            "standard", OrganizationProfile.standard(),
            "vinyl_rips", new OrganizationProfile(
                "Vinyl Rips/{artist}/{album} ({year})/{track:02d} - {title}",
                DuplicateAction.ASK,
                true,
                true
            ),
            "compilations", new OrganizationProfile(
                "Compilations/{album}/{track:02d} - {artist} - {title}",
                DuplicateAction.RENAME,
                false,
                true
            )
        );
        
        // Define directory mappings
        List<DirectoryMapping> directories = List.of(
            DirectoryMapping.basic(
                "Music Downloads",
                Paths.get("/Users/music/Downloads"),
                Paths.get("/Users/music/Library"),
                "standard"
            ),
            DirectoryMapping.highQuality(
                "Vinyl Rips",
                Paths.get("/Users/music/VinylRips"),
                Paths.get("/Users/music/HighQuality"),
                "vinyl_rips"
            ).withWatch(true)
        );
        
        // Define processing rules
        List<FileRule> rules = List.of(
            // Lossless formats get detailed organization
            FormatRule.lossless("vinyl_rips"),
            
            // High bitrate gets standard treatment
            BitrateRule.highQuality("standard"),
            
            // Classical music gets special handling
            GenreRule.classical("vinyl_rips"),
            
            // Large files are assumed to be high quality
            SizeRule.largeFiles("vinyl_rips")
        );
        
        // Configure watch service for automated processing
        WatchConfig watchConfig = new WatchConfig(
            true,
            Duration.ofMinutes(2),  // Check every 2 minutes
            Duration.ofSeconds(45), // Wait 45 seconds for file stability
            30,                     // Process 30 files at a time
            500                     // Use up to 500 virtual threads
        );
        
        return new MusicOrganizerConfig(
            MusicOrganizerConfig.CURRENT_VERSION,
            "standard",
            profiles,
            directories,
            watchConfig,
            rules
        );
    }
    
    /**
     * Creates a configuration optimized for audiophile collections.
     * 
     * @return a MusicOrganizerConfig for high-quality audio collections
     */
    public static MusicOrganizerConfig createAudiophileConfig() {
        Map<String, OrganizationProfile> profiles = Map.of(
            "dsd", new OrganizationProfile(
                "DSD/{artist}/{album} - {year} - DSD{bitrate}/{track:02d} - {title}",
                DuplicateAction.ASK,
                true,
                true
            ),
            "flac", new OrganizationProfile(
                "FLAC/{bitrate}kbps/{artist}/{album} ({year})/{track:02d} - {title}",
                DuplicateAction.SKIP,
                true,
                true
            ),
            "mp3", OrganizationProfile.flat()
        );
        
        List<FileRule> rules = List.of(
            new FormatRule(List.of("dsd", "dsf", "dff"), "dsd"),
            FormatRule.lossless("flac"),
            FormatRule.lossy("mp3")
        );
        
        return new MusicOrganizerConfig(
            "1.0",
            "flac",
            profiles,
            List.of(),  // To be configured per user
            WatchConfig.lowResourceConfig(),
            rules
        );
    }
    
    /**
     * Demonstrates programmatic configuration building.
     */
    public static void demonstrateConfigurationBuilding() {
        // Start with a minimal configuration
        MusicOrganizerConfig config = MusicOrganizerConfig.minimal();
        
        // Add custom profiles
        config = config.withProfile("jazz", new OrganizationProfile(
            "Jazz/{artist}/{album} ({year})/{track:02d} - {title}",
            DuplicateAction.ASK,
            true,
            true
        ));
        
        // Add directory mappings
        config = config.withDirectory(DirectoryMapping.watched(
            "Jazz Collection",
            Paths.get("/music/jazz-incoming"),
            Paths.get("/music/jazz-library"),
            "jazz"
        ));
        
        // Add processing rules
        config = config.withRule(GenreRule.jazz("jazz"));
        
        System.out.println("Built configuration:");
        System.out.println(config.getSummary());
    }
    
    /**
     * Demonstrates file filter usage.
     */
    public static void demonstrateFileFilters() {
        // High-quality audio filters
        FileFilters highQuality = new FileFilters(
            List.of("flac", "wav", "dsd"),
            Optional.of(50_000_000L), // 50MB minimum
            Optional.empty(),
            List.of(".*\\.tmp$", ".*_temp_.*")
        );
        
        // Test file matching
        boolean matches = highQuality.matches("great_song.flac", 75_000_000L);
        System.out.println("FLAC file matches high-quality filter: " + matches);
        
        // Mobile-friendly filters
        FileFilters mobile = new FileFilters(
            List.of("mp3", "aac", "m4a"),
            Optional.empty(),
            Optional.of(10_000_000L), // 10MB maximum
            List.of(".*live.*", ".*demo.*")
        );
        
        matches = mobile.matches("song.mp3", 5_000_000L);
        System.out.println("MP3 file matches mobile filter: " + matches);
    }
}