# Pattern Engine for Music Organization

A comprehensive pattern template engine that provides flexible, customizable file organization using Java 21 features including records, sealed classes, pattern matching, and virtual threads.

## Overview

The Pattern Engine allows you to organize music files using flexible template patterns like:

```
{artist}/{year?[{year}] }{album}/{track:02d} - {title}
```

This would organize files as:
```
Queen/[1975] A Night at the Opera/11 - Bohemian Rhapsody.flac
```

## Core Components

### 1. PatternEngine (`com.musicorganizer.config.PatternEngine`)
- Main engine for parsing and evaluating pattern templates
- Thread-safe with pattern compilation caching
- Supports nested patterns and complex conditionals

### 2. PatternVariable (`com.musicorganizer.config.PatternVariable`)
- Sealed interface defining available variables
- Supports string, numeric, and optional variables
- Extracts values from `TrackMetadata`, `AudioMetadata`, or `AudioFile`

### 3. PatternFormatter (`com.musicorganizer.config.PatternFormatter`)
- Handles format specifiers for variables
- Supports numeric formatting, string case conversion, and sanitization
- Thread-safe with caching for performance

### 4. PatternContext (`com.musicorganizer.config.PatternContext`)
- Provides context for pattern evaluation
- Supports custom variable providers
- Caching and validation options

### 5. PatternBasedOrganizer (`com.musicorganizer.config.PatternBasedOrganizer`)
- Integration with the file organization system
- Provides predefined organization presets
- Supports preview mode and batch operations

## Available Variables

### Basic Metadata
- `{artist}` - Primary artist/performer
- `{album}` - Album name
- `{title}` - Track title
- `{genre}` - Musical genre
- `{year}` - Release year
- `{track}` - Track number
- `{disc}` - Disc number
- `{format}` - Audio format (MP3, FLAC, etc.)

### Extended Metadata
- `{albumartist}` - Album artist (for compilations)
- `{composer}` - Composer/songwriter
- `{publisher}` - Record label
- `{totaltracks}` - Total tracks on album
- `{totaldiscs}` - Total discs in album

### File-Specific Variables
- `{filename}` - Original filename (without extension)
- `{extension}` - File extension
- `{directory}` - Parent directory name

## Format Specifiers

### Numeric Formatting
- `{track:02d}` - Zero-padded to 2 digits (01, 02, 11)
- `{year:4d}` - Padded to 4 digits with spaces
- `{track:3.1f}` - Decimal with precision

### String Formatting
- `{artist:upper}` - UPPERCASE
- `{artist:lower}` - lowercase  
- `{artist:title}` - Title Case
- `{artist:sanitize}` - Safe for filenames
- `{title:max:50}` - Truncate to 50 characters

### Combined Formatting
- `{artist:upper,sanitize,max:20}` - Multiple formatters

## Conditional Patterns

Use `?` to include content only if a variable exists:

```java
// Include year in brackets only if present
"{artist}/{year?[{year}] }{album}"
// Result: "Queen/[1975] A Night at the Opera" or "Queen/Unknown Album"

// Include disc folder only if multi-disc
"{artist}/{album}/{disc?Disc {disc}/}{track:02d} - {title}"
// Result: "Queen/Album/Disc 1/01 - Track" or "Queen/Album/01 - Track"
```

## Predefined Templates

The engine includes common organization patterns:

```java
// Standard: Artist/Album/## - Track
PatternEngine.Templates.STANDARD
// "Queen/A Night at the Opera/11 - Bohemian Rhapsody"

// With Year: Artist/[Year] Album/## - Track  
PatternEngine.Templates.WITH_YEAR
// "Queen/[1975] A Night at the Opera/11 - Bohemian Rhapsody"

// Classical: Classical/Composer/Year - Album/## - Track
PatternEngine.Templates.CLASSICAL  
// "Classical/Mozart/1787 - Don Giovanni/01 - Overture"

// Genre-based: Genre/Artist/Album/## - Track
PatternEngine.Templates.GENRE_BASED
// "Rock/Queen/A Night at the Opera/11 - Bohemian Rhapsody"

// Flat: Artist - Album - ## - Track
PatternEngine.Templates.FLAT
// "Queen - A Night at the Opera - 11 - Bohemian Rhapsody"
```

## Usage Examples

### Basic Pattern Evaluation

```java
PatternEngine engine = new PatternEngine();
TrackMetadata metadata = // ... load metadata
PatternContext context = PatternContext.forTrackMetadata(metadata);

String result = engine.evaluatePattern("{artist}/{album}/{track:02d} - {title}", context);
// Result: "Queen/A Night at the Opera/11 - Bohemian Rhapsody"
```

### File Organization

```java
Path targetDirectory = Paths.get("/music/organized");
PatternBasedOrganizer organizer = new PatternBasedOrganizer(
    PatternBasedOrganizer.Presets.withYear(targetDirectory),
    progressTracker
);

Map<Path, TrackMetadata> files = // ... load files and metadata
PatternBasedOrganizer.OrganizationResult result = organizer.organizeFiles(files, event -> {
    System.out.println("Organized: " + event);
});
```

### Custom Pattern with Preview

```java
String customPattern = "{genre:upper}/{artist:title}/{year?[{year}] }{album:sanitize}/{track:02d} - {title:sanitize}";

// Validate pattern first
PatternEngine.ValidationResult validation = engine.validatePattern(customPattern);
if (!validation.isValid()) {
    System.err.println("Invalid pattern: " + validation.errors());
    return;
}

// Preview organization (no actual file moves)
Map<Path, Path> preview = organizer.previewOrganization(fileMetadata);
preview.forEach((source, target) -> 
    System.out.println(source + " -> " + target)
);
```

### Custom Variables

```java
PatternContext context = new PatternContext.Builder()
    .withTrackMetadata(metadata)
    .addCustomProvider("bitrate_quality", () -> {
        int bitrate = metadata.bitRate().orElse(0);
        if (bitrate >= 1000) return Optional.of("Lossless");
        if (bitrate >= 320) return Optional.of("High");
        if (bitrate >= 192) return Optional.of("Medium");
        return Optional.of("Low");
    })
    .build();

String result = engine.evaluatePattern("{artist} - {title} [{bitrate_quality}]", context);
```

## Performance Features

### Caching
- Pattern compilation results are cached for reuse
- Variable values are cached per context
- Format operations use concurrent hash maps

### Virtual Threads
- File organization uses virtual threads for I/O operations
- Concurrent processing of thousands of files
- Automatic resource management with try-with-resources

### Memory Efficiency
- Immutable data structures prevent accidental modification
- String interning for common values
- Lazy evaluation of expensive operations

## Integration with NIOFileOrganizer

The pattern engine integrates seamlessly with the existing file organization system:

```java
// Create pattern-based organizer
OrganizationConfig config = OrganizationConfig.withPattern(
    targetDirectory, 
    "{artist:sanitize}/{year?[{year}] }{album:sanitize}/{track:02d} - {title:sanitize}"
);

PatternBasedOrganizer organizer = new PatternBasedOrganizer(config, progressTracker);

// Organize using AudioFile objects  
CompletableFuture<OrganizationResult> future = organizer.organizeAudioFilesAsync(
    audioFiles, 
    event -> System.out.println("Event: " + event)
);

OrganizationResult result = future.join();
System.out.println("Organized " + result.totalProcessed() + " files in " + result.processingTimeMillis() + "ms");
```

## Error Handling

The engine provides robust error handling:

### Pattern Validation
```java
PatternEngine.ValidationResult validation = engine.validatePattern(pattern);
if (!validation.isValid()) {
    validation.errors().forEach(System.err::println);
}
```

### Runtime Error Handling
- Invalid patterns fall back to safe defaults in non-strict mode
- Missing variables use configurable placeholders
- File operation failures are captured with detailed error information

### Strict vs Permissive Modes
```java
// Strict mode - throws exceptions for invalid patterns/variables
PatternEngine strictEngine = new PatternEngine(PatternEngine.Configuration.strict());

// Permissive mode - provides fallbacks and defaults
PatternEngine permissiveEngine = new PatternEngine(PatternEngine.Configuration.permissive());
```

## Best Practices

1. **Validate patterns** before using them in production
2. **Use sanitization** for filesystem safety: `{variable:sanitize}`
3. **Test with preview mode** before organizing large collections
4. **Use conditionals** for optional metadata: `{year?[{year}] }`
5. **Cache contexts** when processing many files with similar metadata
6. **Handle missing metadata** gracefully with defaults

## Testing

The pattern engine includes comprehensive tests demonstrating all features:

```bash
mvn test -Dtest=PatternEngineTest
```

Run the demo to see examples:

```bash
java -cp target/classes com.musicorganizer.config.PatternEngineDemo
```

This pattern engine provides a powerful, flexible foundation for organizing music collections while maintaining type safety, performance, and extensibility through Java 21's modern features.