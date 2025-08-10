package com.musicorganizer.config;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.model.AudioMetadata;
import com.musicorganizer.model.AudioFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pattern Engine Tests")
class PatternEngineTest {
    
    private PatternEngine engine;
    private TrackMetadata sampleMetadata;
    private AudioMetadata sampleAudioMetadata;
    private AudioFile sampleAudioFile;
    
    @BeforeEach
    void setUp() {
        engine = new PatternEngine();
        
        // Create sample metadata for testing
        sampleMetadata = TrackMetadata.builder()
            .title("Bohemian Rhapsody")
            .artist("Queen")
            .album("A Night at the Opera")
            .genre("Rock")
            .year(1975)
            .trackNumber(11)
            .totalTracks(12)
            .discNumber(1)
            .totalDiscs(1)
            .albumArtist("Queen")
            .composer("Freddie Mercury")
            .format("FLAC")
            .build();
        
        sampleAudioMetadata = new AudioMetadata.Builder()
            .title("Bohemian Rhapsody")
            .artist("Queen")
            .album("A Night at the Opera")
            .genre("Rock")
            .year(1975)
            .trackNumber(11)
            .totalTracks(12)
            .duration(Duration.ofMinutes(5).plusSeconds(55))
            .bitrate(1411)
            .format("FLAC")
            .build();
        
        sampleAudioFile = new AudioFile(
            Paths.get("C:/Music/Queen/A Night at the Opera/11 - Bohemian Rhapsody.flac"),
            15_000_000L,
            Instant.now(),
            sampleAudioMetadata,
            "abc123def456"
        );
    }
    
    @Nested
    @DisplayName("Basic Pattern Evaluation")
    class BasicPatternEvaluation {
        
        @Test
        @DisplayName("Should evaluate simple variable patterns")
        void shouldEvaluateSimpleVariables() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            assertEquals("Queen", engine.evaluatePattern("{artist}", context));
            assertEquals("A Night at the Opera", engine.evaluatePattern("{album}", context));
            assertEquals("Bohemian Rhapsody", engine.evaluatePattern("{title}", context));
            assertEquals("1975", engine.evaluatePattern("{year}", context));
            assertEquals("11", engine.evaluatePattern("{track}", context));
        }
        
        @Test
        @DisplayName("Should handle literal text with variables")
        void shouldHandleLiteralTextWithVariables() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            String result = engine.evaluatePattern("Artist: {artist}, Album: {album}", context);
            assertEquals("Artist: Queen, Album: A Night at the Opera", result);
        }
        
        @Test
        @DisplayName("Should handle missing variables gracefully")
        void shouldHandleMissingVariablesGracefully() {
            TrackMetadata minimal = TrackMetadata.builder()
                .title("Test Track")
                .artist("Test Artist")
                .build();
            
            PatternContext context = PatternContext.forTrackMetadata(minimal);
            
            String result = engine.evaluatePattern("{artist} - {album}", context);
            assertEquals("Test Artist - Unknown", result);
        }
        
        @Test
        @DisplayName("Should evaluate predefined templates")
        void shouldEvaluatePredefinedTemplates() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            // Test STANDARD template
            String standard = engine.evaluatePattern(PatternEngine.Templates.STANDARD, context);
            assertEquals("Queen/A Night at the Opera/11 - Bohemian Rhapsody", standard);
            
            // Test WITH_YEAR template
            String withYear = engine.evaluatePattern(PatternEngine.Templates.WITH_YEAR, context);
            assertEquals("Queen/[1975] A Night at the Opera/11 - Bohemian Rhapsody", withYear);
        }
    }
    
    @Nested
    @DisplayName("Format Specifiers")
    class FormatSpecifiers {
        
        @Test
        @DisplayName("Should apply numeric formatting")
        void shouldApplyNumericFormatting() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            assertEquals("11", engine.evaluatePattern("{track:02d}", context));
            assertEquals("11", engine.evaluatePattern("{track:2d}", context));
            
            // Test with single digit track
            TrackMetadata singleDigit = TrackMetadata.from(sampleMetadata)
                .trackNumber(5)
                .build();
            PatternContext singleContext = PatternContext.forTrackMetadata(singleDigit);
            
            assertEquals("05", engine.evaluatePattern("{track:02d}", singleContext));
            assertEquals(" 5", engine.evaluatePattern("{track:2d}", singleContext));
        }
        
        @Test
        @DisplayName("Should apply string formatting")
        void shouldApplyStringFormatting() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            assertEquals("QUEEN", engine.evaluatePattern("{artist:upper}", context));
            assertEquals("queen", engine.evaluatePattern("{artist:lower}", context));
            assertEquals("Queen", engine.evaluatePattern("{artist:title}", context));
        }
        
        @Test
        @DisplayName("Should sanitize filenames")
        void shouldSanitizeFilenames() {
            TrackMetadata problematic = TrackMetadata.builder()
                .title("Track: With < Problematic > Characters?")
                .artist("Artist/With\\Slashes")
                .album("Album|With*Wildcards")
                .build();
            
            PatternContext context = PatternContext.forTrackMetadata(problematic);
            
            String result = engine.evaluatePattern("{artist:sanitize}/{album:sanitize}/{title:sanitize}", context);
            assertEquals("Artist_With_Slashes/Album_With_Wildcards/Track_ With _ Problematic _ Characters_", result);
        }
    }
    
    @Nested
    @DisplayName("Conditional Patterns")
    class ConditionalPatterns {
        
        @Test
        @DisplayName("Should include conditional content when variable exists")
        void shouldIncludeConditionalWhenExists() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            String result = engine.evaluatePattern("{year?[{year}] }{album}", context);
            assertEquals("[1975] A Night at the Opera", result);
        }
        
        @Test
        @DisplayName("Should skip conditional content when variable missing")
        void shouldSkipConditionalWhenMissing() {
            TrackMetadata noYear = TrackMetadata.builder()
                .title("Test Track")
                .artist("Test Artist")
                .album("Test Album")
                .build();
            
            PatternContext context = PatternContext.forTrackMetadata(noYear);
            
            String result = engine.evaluatePattern("{year?[{year}] }{album}", context);
            assertEquals("Test Album", result);
        }
        
        @Test
        @DisplayName("Should handle disc conditionals properly")
        void shouldHandleDiscConditionals() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            // With disc number
            String result = engine.evaluatePattern("{disc?Disc {disc}/}{track:02d} - {title}", context);
            assertEquals("Disc 1/11 - Bohemian Rhapsody", result);
            
            // Without disc number
            TrackMetadata noDisc = TrackMetadata.from(sampleMetadata)
                .discNumber(null)
                .build();
            PatternContext noDiscContext = PatternContext.forTrackMetadata(noDisc);
            
            String noDiscResult = engine.evaluatePattern("{disc?Disc {disc}/}{track:02d} - {title}", noDiscContext);
            assertEquals("11 - Bohemian Rhapsody", noDiscResult);
        }
    }
    
    @Nested
    @DisplayName("Path Generation")
    class PathGeneration {
        
        @Test
        @DisplayName("Should generate valid filesystem paths")
        void shouldGenerateValidPaths() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            Path result = engine.evaluateToPath(PatternEngine.Templates.STANDARD, context);
            
            assertEquals(3, result.getNameCount());
            assertEquals("Queen", result.getName(0).toString());
            assertEquals("A Night at the Opera", result.getName(1).toString());
            assertEquals("11 - Bohemian Rhapsody", result.getName(2).toString());
        }
        
        @Test
        @DisplayName("Should handle special characters in paths")
        void shouldHandleSpecialCharactersInPaths() {
            TrackMetadata special = TrackMetadata.builder()
                .title("Track: With \"Quotes\" & Symbols!")
                .artist("Artist/Band")
                .album("Album <Version>")
                .trackNumber(1)
                .build();
            
            PatternContext context = PatternContext.forTrackMetadata(special);
            
            Path result = engine.evaluateToPath("{artist:sanitize}/{album:sanitize}/{track:02d} - {title:sanitize}", context);
            
            // Verify path components are filesystem-safe
            for (int i = 0; i < result.getNameCount(); i++) {
                String component = result.getName(i).toString();
                assertFalse(component.contains("<"));
                assertFalse(component.contains(">"));
                assertFalse(component.contains("\""));
                assertFalse(component.contains("/"));
                assertFalse(component.contains("\\"));
            }
        }
    }
    
    @Nested
    @DisplayName("Pattern Validation")
    class PatternValidation {
        
        @Test
        @DisplayName("Should validate correct patterns")
        void shouldValidateCorrectPatterns() {
            PatternEngine.ValidationResult result = engine.validatePattern("{artist}/{album}/{track:02d} - {title}");
            
            assertTrue(result.isValid());
            assertTrue(result.errors().isEmpty());
        }
        
        @Test
        @DisplayName("Should detect unbalanced braces")
        void shouldDetectUnbalancedBraces() {
            PatternEngine.ValidationResult result = engine.validatePattern("{artist}/{album/{track} - {title}");
            
            assertFalse(result.isValid());
            assertFalse(result.errors().isEmpty());
            assertTrue(result.errors().get(0).contains("Unclosed braces"));
        }
        
        @Test
        @DisplayName("Should detect invalid variable names")
        void shouldDetectInvalidVariableNames() {
            PatternEngine.ValidationResult result = engine.validatePattern("{123invalid}/{-also-invalid}");
            
            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(err -> err.contains("Invalid variable name")));
        }
        
        @Test
        @DisplayName("Should detect invalid format specifiers")
        void shouldDetectInvalidFormatSpecifiers() {
            PatternEngine.ValidationResult result = engine.validatePattern("{track:invalid_format}");
            
            assertFalse(result.isValid());
            assertTrue(result.errors().stream().anyMatch(err -> err.contains("Invalid format specifier")));
        }
    }
    
    @Nested
    @DisplayName("Advanced Features")
    class AdvancedFeatures {
        
        @Test
        @DisplayName("Should work with AudioFile objects")
        void shouldWorkWithAudioFiles() {
            PatternContext context = PatternContext.forAudioFile(sampleAudioFile);
            
            String result = engine.evaluatePattern("{artist}/{album}/{track:02d} - {title}", context);
            assertEquals("Queen/A Night at the Opera/11 - Bohemian Rhapsody", result);
        }
        
        @Test
        @DisplayName("Should handle file-specific variables")
        void shouldHandleFileSpecificVariables() {
            Path filePath = Paths.get("/music/Queen/album/track.flac");
            PatternContext context = PatternContext.forPath(filePath);
            
            assertEquals("track", engine.evaluatePattern("{filename}", context));
            assertEquals("flac", engine.evaluatePattern("{extension}", context));
            assertEquals("album", engine.evaluatePattern("{directory}", context));
        }
        
        @Test
        @DisplayName("Should support custom variables")
        void shouldSupportCustomVariables() {
            PatternContext context = new PatternContext.Builder()
                .withTrackMetadata(sampleMetadata)
                .addCustomProvider("custom", () -> Optional.of("Custom Value"))
                .build();
            
            String result = engine.evaluatePattern("{artist} - {custom}", context);
            assertEquals("Queen - Custom Value", result);
        }
        
        @Test
        @DisplayName("Should cache compiled patterns for performance")
        void shouldCacheCompiledPatterns() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            String pattern = "{artist}/{album}/{track:02d} - {title}";
            
            int initialCacheSize = engine.getCacheSize();
            
            // First evaluation should compile and cache
            engine.evaluatePattern(pattern, context);
            assertEquals(initialCacheSize + 1, engine.getCacheSize());
            
            // Second evaluation should use cache
            engine.evaluatePattern(pattern, context);
            assertEquals(initialCacheSize + 1, engine.getCacheSize());
        }
    }
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("Should handle null patterns gracefully")
        void shouldHandleNullPatterns() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            assertEquals("", engine.evaluatePattern(null, context));
            assertEquals("", engine.evaluatePattern("", context));
            assertEquals("", engine.evaluatePattern("   ", context));
        }
        
        @Test
        @DisplayName("Should provide fallback in non-strict mode")
        void shouldProvideFallbackInNonStrictMode() {
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            // Intentionally malformed pattern
            String result = engine.evaluatePattern("{{{malformed", context);
            
            // Should return a fallback path instead of throwing
            assertNotNull(result);
            assertTrue(result.contains("Queen"));
            assertTrue(result.contains("A Night at the Opera"));
        }
        
        @Test
        @DisplayName("Should throw in strict mode for invalid patterns")
        void shouldThrowInStrictModeForInvalidPatterns() {
            PatternEngine strictEngine = new PatternEngine(PatternEngine.Configuration.strict());
            PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
            
            assertThrows(PatternEngine.PatternEvaluationException.class, () -> {
                strictEngine.evaluatePattern("{{{malformed", context);
            });
        }
    }
    
    @Test
    @DisplayName("Integration test with NIOFileOrganizer pattern")
    void integrationTestWithNIOFileOrganizerPattern() {
        PatternContext context = PatternContext.forTrackMetadata(sampleMetadata);
        
        // Test the pattern that might be used by NIOFileOrganizer
        String pattern = "{artist:sanitize}/{year?[{year}] }{album:sanitize}/{track:02d} - {title:sanitize}";
        String result = engine.evaluatePattern(pattern, context);
        
        assertEquals("Queen/[1975] A Night at the Opera/11 - Bohemian Rhapsody", result);
        
        // Verify it creates a valid path
        Path path = engine.evaluateToPath(pattern, context);
        assertEquals(3, path.getNameCount());
        assertDoesNotThrow(() -> path.toAbsolutePath());
    }
}