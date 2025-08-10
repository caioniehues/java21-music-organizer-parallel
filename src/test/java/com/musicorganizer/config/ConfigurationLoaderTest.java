package com.musicorganizer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ConfigurationLoader functionality.
 */
class ConfigurationLoaderTest {
    
    @Test
    void testLoadDefaultConfig() throws ConfigurationLoader.ConfigurationException {
        MusicOrganizerConfig config = ConfigurationLoader.getDefaultConfig();
        
        assertNotNull(config);
        assertEquals("1.0", config.version());
        assertEquals("standard", config.defaultProfile());
        assertFalse(config.profiles().isEmpty());
        assertTrue(config.profiles().containsKey("standard"));
    }
    
    @Test
    void testJsonSerialization(@TempDir Path tempDir) throws Exception {
        // Create a test configuration
        MusicOrganizerConfig original = MusicOrganizerConfig.defaultConfig();
        
        // Save to JSON file
        Path jsonFile = tempDir.resolve("test-config.json");
        ConfigurationLoader.saveToFile(original, jsonFile);
        
        // Load back from JSON
        MusicOrganizerConfig loaded = ConfigurationLoader.loadFromFile(jsonFile);
        
        // Verify key properties match
        assertEquals(original.version(), loaded.version());
        assertEquals(original.defaultProfile(), loaded.defaultProfile());
        assertEquals(original.profiles().size(), loaded.profiles().size());
    }
    
    @Test
    void testYamlSerialization(@TempDir Path tempDir) throws Exception {
        // Create a test configuration
        MusicOrganizerConfig original = MusicOrganizerConfig.highPerformance();
        
        // Save to YAML file
        Path yamlFile = tempDir.resolve("test-config.yaml");
        ConfigurationLoader.saveToFile(original, yamlFile);
        
        // Verify file was created
        assertTrue(Files.exists(yamlFile));
        
        // Load back from YAML
        MusicOrganizerConfig loaded = ConfigurationLoader.loadFromFile(yamlFile);
        
        // Verify key properties match
        assertEquals(original.version(), loaded.version());
        assertEquals(original.defaultProfile(), loaded.defaultProfile());
        assertEquals(original.watchService().enabled(), loaded.watchService().enabled());
    }
    
    @Test 
    void testFileRuleValidation() {
        // Test BitrateRule
        BitrateRule bitrateRule = BitrateRule.highQuality("standard");
        assertEquals("standard", bitrateRule.getTargetProfile());
        assertTrue(bitrateRule.getDescription().contains("320"));
        
        // Test GenreRule
        GenreRule genreRule = GenreRule.classical("classical");
        assertEquals("classical", genreRule.getTargetProfile());
        assertTrue(genreRule.getDescription().toLowerCase().contains("classical"));
        
        // Test FormatRule
        FormatRule formatRule = FormatRule.lossless("detailed");
        assertEquals("detailed", formatRule.getTargetProfile());
        assertTrue(formatRule.formats().contains("flac"));
        
        // Test SizeRule
        SizeRule sizeRule = SizeRule.largeFiles("high-quality");
        assertEquals("high-quality", sizeRule.getTargetProfile());
    }
    
    @Test
    void testFileFilters() {
        FileFilters filters = FileFilters.defaultFilters();
        
        // Test common audio file extensions
        assertTrue(filters.matches("song.mp3", 5000000));
        assertTrue(filters.matches("song.flac", 25000000));
        assertFalse(filters.matches("document.txt", 1000));
        
        // Test high-quality filters
        FileFilters highQuality = FileFilters.highQualityFilters();
        assertTrue(highQuality.matches("song.flac", 50000000));
        assertFalse(highQuality.matches("song.mp3", 5000000)); // Wrong format
        assertFalse(highQuality.matches("song.flac", 5000000)); // Too small
    }
    
    @Test
    void testWatchConfigValidation() {
        // Test default configuration
        WatchConfig defaultConfig = WatchConfig.defaultConfig();
        assertTrue(defaultConfig.enabled());
        assertTrue(defaultConfig.pollInterval().toSeconds() > 0);
        assertTrue(defaultConfig.batchSize() > 0);
        
        // Test high-performance configuration
        WatchConfig highPerf = WatchConfig.highPerformanceConfig();
        assertTrue(highPerf.enabled());
        assertTrue(highPerf.virtualThreadPoolSize() > defaultConfig.virtualThreadPoolSize());
    }
    
    @Test
    void testOrganizationProfiles() {
        OrganizationProfile standard = OrganizationProfile.standard();
        assertTrue(standard.pattern().contains("{artist}"));
        assertTrue(standard.pattern().contains("{album}"));
        assertTrue(standard.createArtistFolder());
        
        OrganizationProfile flat = OrganizationProfile.flat();
        assertFalse(flat.createArtistFolder());
        assertEquals(DuplicateAction.RENAME, flat.duplicateAction());
    }
    
    @Test
    void testDuplicateActionSerialization() {
        assertEquals("skip", DuplicateAction.SKIP.getValue());
        assertEquals("rename", DuplicateAction.RENAME.getValue());
        assertEquals("replace", DuplicateAction.REPLACE.getValue());
        assertEquals("ask", DuplicateAction.ASK.getValue());
        
        // Test deserialization
        assertEquals(DuplicateAction.SKIP, DuplicateAction.fromValue("skip"));
        assertEquals(DuplicateAction.RENAME, DuplicateAction.fromValue("RENAME"));
    }
    
    @Test
    void testInvalidConfiguration() {
        // Test invalid version compatibility
        assertThrows(IllegalArgumentException.class, () ->
            new MusicOrganizerConfig("", "standard", 
                Map.of("standard", OrganizationProfile.standard()),
                List.of(), WatchConfig.defaultConfig(), List.of())
        );
        
        // Test missing default profile
        assertThrows(IllegalArgumentException.class, () ->
            new MusicOrganizerConfig("1.0", "missing", 
                Map.of("standard", OrganizationProfile.standard()),
                List.of(), WatchConfig.defaultConfig(), List.of())
        );
        
        // Test empty profiles
        assertThrows(IllegalArgumentException.class, () ->
            new MusicOrganizerConfig("1.0", "standard", Map.of(),
                List.of(), WatchConfig.defaultConfig(), List.of())
        );
    }
}