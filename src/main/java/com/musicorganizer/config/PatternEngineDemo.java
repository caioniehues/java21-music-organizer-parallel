package com.musicorganizer.config;

import com.musicorganizer.model.TrackMetadata;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Demonstration of the PatternEngine capabilities.
 * Shows various pattern templates and their evaluation with sample metadata.
 */
public final class PatternEngineDemo {
    
    public static void main(String[] args) {
        PatternEngine engine = new PatternEngine();
        
        // Create sample metadata for demonstration
        TrackMetadata sample1 = createSampleMetadata();
        TrackMetadata sample2 = createClassicalMetadata();
        TrackMetadata sample3 = createCompilationMetadata();
        
        System.out.println("=== Pattern Engine Demonstration ===\n");
        
        // Demonstrate basic patterns
        demonstrateBasicPatterns(engine, sample1);
        
        // Demonstrate format specifiers
        demonstrateFormatSpecifiers(engine, sample1);
        
        // Demonstrate conditional patterns
        demonstrateConditionalPatterns(engine, sample1);
        
        // Demonstrate predefined templates
        demonstratePredefinedTemplates(engine, sample1, sample2, sample3);
        
        // Demonstrate pattern validation
        demonstratePatternValidation(engine);
        
        // Demonstrate custom patterns
        demonstrateCustomPatterns(engine, sample1);
    }
    
    private static void demonstrateBasicPatterns(PatternEngine engine, TrackMetadata metadata) {
        System.out.println("=== Basic Patterns ===");
        PatternContext context = PatternContext.forTrackMetadata(metadata);
        
        String[] basicPatterns = {
            "{artist}",
            "{album}",
            "{title}",
            "{artist}/{album}",
            "{artist} - {title}",
            "{year} - {artist} - {album}"
        };
        
        for (String pattern : basicPatterns) {
            String result = engine.evaluatePattern(pattern, context);
            System.out.printf("Pattern: %-30s Result: %s%n", pattern, result);
        }
        System.out.println();
    }
    
    private static void demonstrateFormatSpecifiers(PatternEngine engine, TrackMetadata metadata) {
        System.out.println("=== Format Specifiers ===");
        PatternContext context = PatternContext.forTrackMetadata(metadata);
        
        String[] formatPatterns = {
            "{track}",
            "{track:02d}",
            "{track:3d}",
            "{artist:upper}",
            "{artist:lower}",
            "{artist:title}",
            "{title:sanitize}",
            "{artist:upper,max:10}"
        };
        
        for (String pattern : formatPatterns) {
            String result = engine.evaluatePattern(pattern, context);
            System.out.printf("Pattern: %-25s Result: %s%n", pattern, result);
        }
        System.out.println();
    }
    
    private static void demonstrateConditionalPatterns(PatternEngine engine, TrackMetadata metadata) {
        System.out.println("=== Conditional Patterns ===");
        PatternContext context = PatternContext.forTrackMetadata(metadata);
        
        String[] conditionalPatterns = {
            "{year?[{year}] }{album}",
            "{disc?Disc {disc}/}{track:02d} - {title}",
            "{composer?Composed by {composer} - }{title}",
            "{albumartist?{albumartist}:{artist}} - {album}"
        };
        
        for (String pattern : conditionalPatterns) {
            String result = engine.evaluatePattern(pattern, context);
            System.out.printf("Pattern: %-45s%nResult:  %s%n%n", pattern, result);
        }
    }
    
    private static void demonstratePredefinedTemplates(PatternEngine engine, 
                                                       TrackMetadata rock, 
                                                       TrackMetadata classical,
                                                       TrackMetadata compilation) {
        System.out.println("=== Predefined Templates ===");
        
        demonstrateTemplate(engine, "STANDARD", PatternEngine.Templates.STANDARD, rock);
        demonstrateTemplate(engine, "WITH_YEAR", PatternEngine.Templates.WITH_YEAR, rock);
        demonstrateTemplate(engine, "CLASSICAL", PatternEngine.Templates.CLASSICAL, classical);
        demonstrateTemplate(engine, "GENRE_BASED", PatternEngine.Templates.GENRE_BASED, rock);
        demonstrateTemplate(engine, "COMPILATION", PatternEngine.Templates.COMPILATION, compilation);
        demonstrateTemplate(engine, "FLAT", PatternEngine.Templates.FLAT, rock);
        demonstrateTemplate(engine, "DETAILED", PatternEngine.Templates.DETAILED, rock);
    }
    
    private static void demonstrateTemplate(PatternEngine engine, String name, String template, TrackMetadata metadata) {
        PatternContext context = PatternContext.forTrackMetadata(metadata);
        String result = engine.evaluatePattern(template, context);
        
        System.out.printf("%-15s: %s%n", name, result);
        System.out.printf("%-15s  Pattern: %s%n%n", "", template);
    }
    
    private static void demonstratePatternValidation(PatternEngine engine) {
        System.out.println("=== Pattern Validation ===");
        
        String[] patterns = {
            "{artist}/{album}/{track:02d} - {title}",  // Valid
            "{artist}/{album/{track} - {title}",       // Invalid - unbalanced braces
            "{123invalid}",                            // Invalid - bad variable name
            "{track:invalid_format}",                  // Invalid - bad format
            "{artist?[{album}] - {title}",            // Valid - conditional
        };
        
        for (String pattern : patterns) {
            PatternEngine.ValidationResult result = engine.validatePattern(pattern);
            System.out.printf("Pattern: %s%n", pattern);
            System.out.printf("Valid:   %s%n", result.isValid());
            if (!result.isValid()) {
                System.out.printf("Errors:  %s%n", String.join(", ", result.errors()));
            }
            System.out.println();
        }
    }
    
    private static void demonstrateCustomPatterns(PatternEngine engine, TrackMetadata metadata) {
        System.out.println("=== Custom Pattern Examples ===");
        
        // Custom context with additional variables
        PatternContext customContext = new PatternContext.Builder()
            .withTrackMetadata(metadata)
            .addCustomProvider("custom_date", () -> java.util.Optional.of("2024-01"))
            .addCustomProvider("quality", () -> java.util.Optional.of("Lossless"))
            .build();
        
        String[] customPatterns = {
            "{artist}/{custom_date} - {album} [{quality}]/{track:02d} - {title}",
            "{genre:upper}/{artist:title}/{year?[{year}] }{album}/{track:02d} - {title:sanitize}"
        };
        
        for (String pattern : customPatterns) {
            String result = engine.evaluatePattern(pattern, customContext);
            System.out.printf("Pattern: %s%nResult:  %s%n%n", pattern, result);
        }
    }
    
    private static TrackMetadata createSampleMetadata() {
        return TrackMetadata.builder()
            .title("Bohemian Rhapsody")
            .artist("Queen")
            .album("A Night at the Opera")
            .albumArtist("Queen")
            .genre("Rock")
            .year(1975)
            .trackNumber(11)
            .totalTracks(12)
            .discNumber(1)
            .totalDiscs(1)
            .format("FLAC")
            .build();
    }
    
    private static TrackMetadata createClassicalMetadata() {
        return TrackMetadata.builder()
            .title("Symphony No. 9 in D minor, Op. 125 - I. Allegro ma non troppo")
            .artist("Vienna Philharmonic")
            .albumArtist("Vienna Philharmonic")
            .album("Beethoven: Complete Symphonies")
            .composer("Ludwig van Beethoven")
            .genre("Classical")
            .year(2020)
            .trackNumber(1)
            .totalTracks(36)
            .discNumber(5)
            .totalDiscs(9)
            .format("FLAC")
            .build();
    }
    
    private static TrackMetadata createCompilationMetadata() {
        return TrackMetadata.builder()
            .title("Sweet Child O' Mine")
            .artist("Guns N' Roses")
            .album("Greatest Hits of the 80s")
            .albumArtist("Various Artists")
            .genre("Rock")
            .year(2023)
            .trackNumber(15)
            .totalTracks(40)
            .discNumber(2)
            .totalDiscs(2)
            .format("MP3")
            .build();
    }
    
    /**
     * Demonstrate path generation
     */
    public static void demonstratePathGeneration() {
        System.out.println("\n=== Path Generation ===");
        
        PatternEngine engine = new PatternEngine();
        TrackMetadata metadata = createSampleMetadata();
        PatternContext context = PatternContext.forTrackMetadata(metadata);
        
        String[] patterns = {
            PatternEngine.Templates.STANDARD,
            PatternEngine.Templates.WITH_YEAR,
            PatternEngine.Templates.DETAILED
        };
        
        for (String pattern : patterns) {
            Path result = engine.evaluateToPath(pattern, context);
            System.out.printf("Pattern: %s%n", pattern);
            System.out.printf("Path:    %s%n", result);
            System.out.printf("Parts:   %s%n%n", java.util.Arrays.toString(
                java.util.stream.IntStream.range(0, result.getNameCount())
                    .mapToObj(result::getName)
                    .map(Path::toString)
                    .toArray(String[]::new)
            ));
        }
    }
}