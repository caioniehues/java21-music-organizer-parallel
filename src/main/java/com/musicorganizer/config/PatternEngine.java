package com.musicorganizer.config;

import com.musicorganizer.model.AudioFile;
import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.model.AudioMetadata;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main pattern processing engine for music file organization templates.
 * Supports variables, conditionals, formatting, and nested patterns using Java 21 features.
 * Thread-safe and optimized for concurrent pattern evaluation.
 */
public final class PatternEngine {
    
    // Compiled regex patterns for performance
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        """
        \\{                           # Opening brace
        ([a-zA-Z][a-zA-Z0-9_]*?)     # Variable name (group 1)
        (?::((?:[^?}])*?))?          # Optional format specifier (group 2) - non-greedy, no ? or }
        (?:\\?((?:[^}])*))?          # Optional conditional content (group 3) - anything until }
        }                            # Closing brace
        """, 
        Pattern.COMMENTS
    );
    
    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile(
        """
        \\[                          # Opening bracket
        ([^\\]]+)                    # Content inside brackets (group 1)
        ]                            # Closing bracket
        """,
        Pattern.COMMENTS
    );
    
    private static final Pattern ESCAPE_PATTERN = Pattern.compile(
        """
        \\\\(.)                      # Backslash followed by any character
        """,
        Pattern.COMMENTS
    );
    
    private static final Pattern NESTED_PATTERN = Pattern.compile(
        """
        \\{                          # Opening brace
        ([^{}]+                      # Content without braces
        (?:\\{[^{}]*}[^{}]*)*?)      # Optional nested braces
        }                            # Closing brace
        """,
        Pattern.COMMENTS
    );
    
    // Pattern compilation cache for performance
    private static final Map<String, CompiledPattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    
    // Default configuration
    private final PatternEngine.Configuration config;
    
    /**
     * Configuration for pattern engine behavior
     */
    public record Configuration(
        boolean enableCaching,
        boolean strictMode,
        int maxNestingDepth,
        String pathSeparator,
        PatternContext.Options defaultContextOptions
    ) {
        public static Configuration defaults() {
            return new Configuration(
                true,  // enableCaching
                false, // strictMode
                5,     // maxNestingDepth
                "/",   // pathSeparator
                PatternContext.Options.defaults()
            );
        }
        
        public static Configuration strict() {
            return new Configuration(
                true,  // enableCaching
                true,  // strictMode
                3,     // maxNestingDepth
                "/",   // pathSeparator
                PatternContext.Options.strict()
            );
        }
        
        public static Configuration permissive() {
            return new Configuration(
                true,  // enableCaching
                false, // strictMode
                10,    // maxNestingDepth
                "/",   // pathSeparator
                PatternContext.Options.permissive()
            );
        }
    }
    
    public PatternEngine(Configuration config) {
        this.config = config != null ? config : Configuration.defaults();
    }
    
    public PatternEngine() {
        this(Configuration.defaults());
    }
    
    /**
     * Evaluate a pattern template with the given context
     */
    public String evaluatePattern(String pattern, PatternContext context) throws PatternEvaluationException {
        if (pattern == null || pattern.isBlank()) {
            return "";
        }
        
        try {
            CompiledPattern compiled = compilePattern(pattern);
            return compiled.evaluate(context);
        } catch (Exception e) {
            if (config.strictMode()) {
                throw new PatternEvaluationException("Pattern evaluation failed: " + pattern, e);
            }
            // In non-strict mode, return a safe fallback
            return generateFallbackPath(context);
        }
    }
    
    /**
     * Evaluate pattern and return as filesystem path
     */
    public Path evaluateToPath(String pattern, PatternContext context) throws PatternEvaluationException {
        String evaluated = evaluatePattern(pattern, context);
        return Paths.get(evaluated);
    }
    
    /**
     * Compile pattern into optimized form with caching
     */
    private CompiledPattern compilePattern(String pattern) {
        if (config.enableCaching() && PATTERN_CACHE.containsKey(pattern)) {
            return PATTERN_CACHE.get(pattern);
        }
        
        CompiledPattern compiled = new CompiledPattern(pattern, parsePattern(pattern, 0));
        
        if (config.enableCaching()) {
            PATTERN_CACHE.put(pattern, compiled);
        }
        
        return compiled;
    }
    
    /**
     * Parse pattern into component tokens using simple character-by-character parsing
     */
    private List<PatternToken> parsePattern(String pattern, int nestingDepth) {
        if (nestingDepth > config.maxNestingDepth()) {
            throw new PatternEvaluationException("Maximum nesting depth exceeded: " + config.maxNestingDepth());
        }
        
        List<PatternToken> tokens = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            
            if (ch == '{') {
                // Add any accumulated literal text
                if (literal.length() > 0) {
                    tokens.add(new LiteralToken(unescapeText(literal.toString())));
                    literal.setLength(0);
                }
                
                // Find the matching closing brace
                int braceCount = 1;
                int start = i + 1;
                int end = start;
                
                for (int j = start; j < pattern.length() && braceCount > 0; j++) {
                    char c = pattern.charAt(j);
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                    end = j;
                }
                
                if (braceCount > 0) {
                    // Unmatched brace, treat as literal
                    literal.append(ch);
                    continue;
                }
                
                // Parse variable content
                String variableContent = pattern.substring(start, end);
                PatternToken token = parseVariableToken(variableContent, nestingDepth);
                if (token != null) {
                    tokens.add(token);
                }
                
                i = end; // Skip to after the closing brace
                
            } else {
                literal.append(ch);
            }
        }
        
        // Add any remaining literal text
        if (literal.length() > 0) {
            tokens.add(new LiteralToken(unescapeText(literal.toString())));
        }
        
        return tokens;
    }
    
    /**
     * Parse a variable token from its content (without braces)
     */
    private PatternToken parseVariableToken(String content, int nestingDepth) {
        // Check for conditional syntax: variable?conditional_content
        int questionIndex = content.indexOf('?');
        
        if (questionIndex == -1) {
            // Simple variable: variable or variable:format
            return parseSimpleVariable(content);
        } else {
            // Conditional variable: variable?conditional_content or variable:format?conditional_content
            String variablePart = content.substring(0, questionIndex);
            String conditionalPart = content.substring(questionIndex + 1);
            
            // Parse variable part
            String variableName;
            String formatSpec = null;
            int colonIndex = variablePart.indexOf(':');
            
            if (colonIndex == -1) {
                variableName = variablePart.trim();
            } else {
                variableName = variablePart.substring(0, colonIndex).trim();
                formatSpec = variablePart.substring(colonIndex + 1).trim();
            }
            
            // Validate variable name
            if (variableName.isEmpty() || !isValidVariableName(variableName)) {
                return new LiteralToken("{" + content + "}"); // Invalid variable, treat as literal
            }
            
            // Parse format specifier
            PatternFormatter.FormatSpecifier formatter = formatSpec != null && !formatSpec.isEmpty()
                ? PatternFormatter.parseFormatSpecifier(formatSpec)
                : PatternFormatter.parseFormatSpecifier("");
            
            // Parse conditional content
            List<PatternToken> conditionalTokens = parsePattern(conditionalPart, nestingDepth + 1);
            
            return new ConditionalVariableToken(variableName, formatter, conditionalTokens);
        }
    }
    
    /**
     * Parse a simple variable token (no conditionals)
     */
    private PatternToken parseSimpleVariable(String content) {
        String variableName;
        String formatSpec = null;
        
        int colonIndex = content.indexOf(':');
        if (colonIndex == -1) {
            variableName = content.trim();
        } else {
            variableName = content.substring(0, colonIndex).trim();
            formatSpec = content.substring(colonIndex + 1).trim();
        }
        
        // Validate variable name
        if (variableName.isEmpty() || !isValidVariableName(variableName)) {
            return new LiteralToken("{" + content + "}"); // Invalid variable, treat as literal
        }
        
        // Parse format specifier
        PatternFormatter.FormatSpecifier formatter = formatSpec != null && !formatSpec.isEmpty()
            ? PatternFormatter.parseFormatSpecifier(formatSpec)
            : PatternFormatter.parseFormatSpecifier("");
        
        return new VariableToken(variableName, formatter);
    }
    
    /**
     * Unescape text by processing backslash escapes
     */
    private String unescapeText(String text) {
        return ESCAPE_PATTERN.matcher(text).replaceAll("$1");
    }
    
    /**
     * Generate fallback path when pattern evaluation fails
     */
    private String generateFallbackPath(PatternContext context) {
        StringBuilder fallback = new StringBuilder();
        
        // Try to build basic path structure
        String artist = context.getVariable("artist")
            .map(String::valueOf)
            .orElse("Unknown Artist");
        String album = context.getVariable("album")
            .map(String::valueOf)
            .orElse("Unknown Album");
        String title = context.getVariable("title")
            .map(String::valueOf)
            .orElse("Unknown Track");
        
        // Sanitize components
        artist = PatternFormatter.sanitizeFilename(artist);
        album = PatternFormatter.sanitizeFilename(album);
        title = PatternFormatter.sanitizeFilename(title);
        
        fallback.append(artist)
               .append(config.pathSeparator())
               .append(album)
               .append(config.pathSeparator())
               .append(title);
        
        return fallback.toString();
    }
    
    /**
     * Validate pattern syntax without evaluation
     */
    public ValidationResult validatePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return new ValidationResult(false, List.of("Pattern cannot be empty"));
        }
        
        List<String> errors = new ArrayList<>();
        
        try {
            // Check for balanced braces
            int braceCount = 0;
            int bracketCount = 0;
            
            for (int i = 0; i < pattern.length(); i++) {
                char ch = pattern.charAt(i);
                
                switch (ch) {
                    case '{' -> braceCount++;
                    case '}' -> braceCount--;
                    case '[' -> bracketCount++;
                    case ']' -> bracketCount--;
                }
                
                if (braceCount < 0) {
                    errors.add("Unmatched closing brace '}' at position " + i);
                    break;
                }
                if (bracketCount < 0) {
                    errors.add("Unmatched closing bracket ']' at position " + i);
                    break;
                }
            }
            
            if (braceCount > 0) {
                errors.add("Unclosed braces: " + braceCount + " opening brace(s) without closing");
            }
            if (bracketCount > 0) {
                errors.add("Unclosed brackets: " + bracketCount + " opening bracket(s) without closing");
            }
            
            // Validate variable names and format specifiers
            Matcher matcher = VARIABLE_PATTERN.matcher(pattern);
            while (matcher.find()) {
                String variableName = matcher.group(1);
                String formatSpec = matcher.group(2);
                
                if (!isValidVariableName(variableName)) {
                    errors.add("Invalid variable name: " + variableName);
                }
                
                if (formatSpec != null && !isValidFormatSpecifier(formatSpec)) {
                    errors.add("Invalid format specifier for variable '" + variableName + "': " + formatSpec);
                }
            }
            
            // Try to compile pattern
            if (errors.isEmpty()) {
                try {
                    parsePattern(pattern, 0);
                } catch (Exception e) {
                    errors.add("Pattern compilation failed: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            errors.add("Pattern validation failed: " + e.getMessage());
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    private boolean isValidVariableName(String name) {
        return name != null && 
               name.matches("[a-zA-Z][a-zA-Z0-9_]*") && 
               name.length() <= 50;
    }
    
    private boolean isValidFormatSpecifier(String spec) {
        try {
            PatternFormatter.parseFormatSpecifier(spec);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get available variables for a given context type
     */
    public Set<String> getAvailableVariables(Class<?> contextType) {
        Set<String> variables = new HashSet<>();
        
        // Standard metadata variables
        variables.addAll(Set.of(
            "artist", "album", "title", "genre", "format", "year", 
            "track", "disc", "totaltracks", "totaldiscs"
        ));
        
        // Extended metadata variables
        if (contextType == TrackMetadata.class || contextType == AudioFile.class) {
            variables.addAll(Set.of(
                "albumartist", "composer", "publisher", "key", 
                "mood", "mbid", "language"
            ));
        }
        
        // File-specific variables
        variables.addAll(Set.of("filename", "extension", "ext", "directory", "dir"));
        
        return Collections.unmodifiableSet(variables);
    }
    
    /**
     * Clear pattern compilation cache
     */
    public void clearCache() {
        PATTERN_CACHE.clear();
    }
    
    /**
     * Get cache statistics
     */
    public record CacheStats(int size, int hits, int misses) {}
    
    public int getCacheSize() {
        return PATTERN_CACHE.size();
    }
    
    /**
     * Pattern validation result
     */
    public record ValidationResult(boolean isValid, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors != null ? errors : List.of());
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
     * Compiled pattern for efficient evaluation
     */
    private static class CompiledPattern {
        private final String originalPattern;
        private final List<PatternToken> tokens;
        
        CompiledPattern(String originalPattern, List<PatternToken> tokens) {
            this.originalPattern = originalPattern;
            this.tokens = List.copyOf(tokens);
        }
        
        String evaluate(PatternContext context) {
            StringBuilder result = new StringBuilder();
            
            for (PatternToken token : tokens) {
                String value = token.evaluate(context);
                if (value != null) {
                    result.append(value);
                }
            }
            
            return result.toString();
        }
        
        @Override
        public String toString() {
            return "CompiledPattern{" +
                   "pattern='" + originalPattern + '\'' +
                   ", tokens=" + tokens.size() +
                   '}';
        }
    }
    
    /**
     * Base interface for pattern tokens
     */
    private sealed interface PatternToken permits LiteralToken, VariableToken, ConditionalVariableToken {
        String evaluate(PatternContext context);
    }
    
    /**
     * Literal text token
     */
    private record LiteralToken(String text) implements PatternToken {
        @Override
        public String evaluate(PatternContext context) {
            return text;
        }
    }
    
    /**
     * Variable substitution token
     */
    private record VariableToken(
        String variableName,
        PatternFormatter.FormatSpecifier formatter
    ) implements PatternToken {
        
        @Override
        public String evaluate(PatternContext context) {
            return context.getFormattedVariable(variableName, formatter);
        }
    }
    
    /**
     * Conditional variable token (only renders if variable exists)
     */
    private record ConditionalVariableToken(
        String variableName,
        PatternFormatter.FormatSpecifier formatter,
        List<PatternToken> conditionalTokens
    ) implements PatternToken {
        
        @Override
        public String evaluate(PatternContext context) {
            if (!context.hasVariable(variableName)) {
                return "";
            }
            
            StringBuilder result = new StringBuilder();
            for (PatternToken token : conditionalTokens) {
                String value = token.evaluate(context);
                if (value != null) {
                    result.append(value);
                }
            }
            
            return result.toString();
        }
    }
    
    /**
     * Predefined pattern templates for common use cases
     */
    public static final class Templates {
        public static final String STANDARD = "{artist}/{album}/{track:02d} - {title}";
        public static final String WITH_YEAR = "{artist}/{year?[{year}] }{album}/{track:02d} - {title}";
        public static final String CLASSICAL = "Classical/{composer}/{year?{year} - }{album}/{track:02d} - {title}";
        public static final String GENRE_BASED = "{genre}/{artist}/{album}/{disc?Disc {disc}/}{track:02d} - {title}";
        public static final String FLAT = "{artist} - {album} - {track:02d} - {title}";
        public static final String ALBUM_ARTIST = "{albumartist?{albumartist}/{album}/{track:02d} - {title}:{artist}/{album}/{track:02d} - {title}}";
        public static final String DETAILED = "{artist}/{year?[{year}] }{album} {format?[{format:upper}]}/{disc?{disc:02d}-}{track:02d} - {title}";
        public static final String COMPILATION = "{albumartist?{albumartist}:Various Artists}/{year?[{year}] }{album}/{track:02d} - {artist} - {title}";
        
        private Templates() {} // Utility class
    }
}