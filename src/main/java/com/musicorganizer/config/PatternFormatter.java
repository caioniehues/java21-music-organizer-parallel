package com.musicorganizer.config;

import java.text.DecimalFormat;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Format specifier handling for pattern variables.
 * Provides thread-safe formatting operations with caching for performance.
 */
public final class PatternFormatter {
    
    private static final Map<String, String> FORMATTER_CACHE = new ConcurrentHashMap<>();
    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1f]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern LEADING_TRAILING_DOTS = Pattern.compile("^\\.+|\\.+$");
    
    /**
     * Sealed interface for different format specifier types
     */
    public sealed interface FormatSpecifier permits 
        NumericFormat, StringFormat, DateFormat, CustomFormat {
        
        /**
         * Apply formatting to the input value
         */
        String format(Object value);
        
        /**
         * Validate that the format specifier is well-formed
         */
        boolean isValid();
        
        /**
         * Get format description for documentation
         */
        String getDescription();
    }
    
    /**
     * Numeric formatting (padding, precision, etc.)
     */
    public record NumericFormat(
        int padWidth,
        char padChar,
        int precision,
        boolean leadingZeros
    ) implements FormatSpecifier {
        
        public NumericFormat {
            if (padWidth < 0 || padWidth > 10) {
                throw new IllegalArgumentException("Pad width must be between 0 and 10");
            }
            if (precision < 0 || precision > 10) {
                throw new IllegalArgumentException("Precision must be between 0 and 10");
            }
        }
        
        @Override
        public String format(Object value) {
            if (!(value instanceof Number number)) {
                return String.valueOf(value);
            }
            
            if (precision > 0) {
                DecimalFormat df = new DecimalFormat("0." + "0".repeat(precision));
                return df.format(number.doubleValue());
            }
            
            if (padWidth > 0) {
                if (leadingZeros) {
                    return String.format("%0" + padWidth + "d", number.intValue());
                } else {
                    return String.format("%" + padWidth + "d", number.intValue());
                }
            }
            
            return String.valueOf(number.intValue());
        }
        
        @Override
        public boolean isValid() {
            return padWidth >= 0 && padWidth <= 10 && 
                   precision >= 0 && precision <= 10;
        }
        
        @Override
        public String getDescription() {
            return String.format("Numeric format: pad=%d, precision=%d, leadingZeros=%s", 
                                padWidth, precision, leadingZeros);
        }
        
        /**
         * Parse numeric format specifiers like "02d", "3.2f", etc.
         */
        public static NumericFormat parse(String formatSpec) {
            // Handle formats like "02d", "3d", "2.1f"
            if (formatSpec.endsWith("d")) {
                String numberPart = formatSpec.substring(0, formatSpec.length() - 1);
                if (numberPart.isEmpty()) {
                    return new NumericFormat(0, '0', 0, false);
                }
                
                boolean leadingZeros = numberPart.startsWith("0") && numberPart.length() > 1;
                int padWidth = Integer.parseInt(numberPart);
                return new NumericFormat(padWidth, '0', 0, leadingZeros);
            }
            
            if (formatSpec.endsWith("f")) {
                String numberPart = formatSpec.substring(0, formatSpec.length() - 1);
                if (numberPart.contains(".")) {
                    String[] parts = numberPart.split("\\.");
                    int padWidth = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
                    int precision = Integer.parseInt(parts[1]);
                    return new NumericFormat(padWidth, ' ', precision, false);
                }
                int padWidth = numberPart.isEmpty() ? 0 : Integer.parseInt(numberPart);
                return new NumericFormat(padWidth, ' ', 0, false);
            }
            
            throw new IllegalArgumentException("Invalid numeric format: " + formatSpec);
        }
    }
    
    /**
     * String formatting (case conversion, sanitization, etc.)
     */
    public record StringFormat(
        CaseFormat caseFormat,
        boolean sanitize,
        int maxLength,
        String truncateSuffix
    ) implements FormatSpecifier {
        
        public StringFormat {
            if (maxLength <= 0) {
                throw new IllegalArgumentException("Max length must be positive");
            }
            if (truncateSuffix == null) {
                truncateSuffix = "...";
            }
        }
        
        @Override
        public String format(Object value) {
            String str = String.valueOf(value);
            
            // Apply case formatting
            str = switch (caseFormat) {
                case UPPER -> str.toUpperCase(Locale.ROOT);
                case LOWER -> str.toLowerCase(Locale.ROOT);
                case TITLE -> toTitleCase(str);
                case CAMEL -> toCamelCase(str);
                case SNAKE -> toSnakeCase(str);
                case KEBAB -> toKebabCase(str);
                case NONE -> str;
            };
            
            // Sanitize for filesystem if requested
            if (sanitize) {
                str = sanitizeFilename(str);
            }
            
            // Apply length limit
            if (str.length() > maxLength) {
                int cutPoint = maxLength - truncateSuffix.length();
                if (cutPoint > 0) {
                    str = str.substring(0, cutPoint) + truncateSuffix;
                } else {
                    str = str.substring(0, maxLength);
                }
            }
            
            return str;
        }
        
        @Override
        public boolean isValid() {
            return maxLength > 0 && caseFormat != null;
        }
        
        @Override
        public String getDescription() {
            return String.format("String format: case=%s, sanitize=%s, maxLength=%d", 
                                caseFormat, sanitize, maxLength);
        }
        
        private static String toTitleCase(String str) {
            return FORMATTER_CACHE.computeIfAbsent("title:" + str, key -> {
                StringBuilder result = new StringBuilder();
                boolean capitalizeNext = true;
                
                for (char ch : str.toCharArray()) {
                    if (Character.isLetterOrDigit(ch)) {
                        result.append(capitalizeNext ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                        capitalizeNext = false;
                    } else {
                        result.append(ch);
                        capitalizeNext = Character.isWhitespace(ch);
                    }
                }
                
                return result.toString();
            });
        }
        
        private static String toCamelCase(String str) {
            return FORMATTER_CACHE.computeIfAbsent("camel:" + str, key -> {
                StringBuilder result = new StringBuilder();
                boolean capitalizeNext = false;
                
                for (char ch : str.toCharArray()) {
                    if (Character.isLetterOrDigit(ch)) {
                        result.append(capitalizeNext ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                        capitalizeNext = false;
                    } else {
                        capitalizeNext = true;
                    }
                }
                
                return result.toString();
            });
        }
        
        private static String toSnakeCase(String str) {
            return str.toLowerCase(Locale.ROOT)
                     .replaceAll("\\s+", "_")
                     .replaceAll("[^a-z0-9_]", "_")
                     .replaceAll("_+", "_")
                     .replaceAll("^_|_$", "");
        }
        
        private static String toKebabCase(String str) {
            return str.toLowerCase(Locale.ROOT)
                     .replaceAll("\\s+", "-")
                     .replaceAll("[^a-z0-9-]", "-")
                     .replaceAll("-+", "-")
                     .replaceAll("^-|-$", "");
        }
        
        /**
         * Parse string format specifiers like "upper", "lower", "sanitize", "title", etc.
         */
        public static StringFormat parse(String formatSpec) {
            CaseFormat caseFormat = CaseFormat.NONE;
            boolean sanitize = false;
            int maxLength = Integer.MAX_VALUE;
            
            String[] parts = formatSpec.split(",");
            for (String part : parts) {
                part = part.trim();
                
                switch (part.toLowerCase()) {
                    case "upper", "uppercase" -> caseFormat = CaseFormat.UPPER;
                    case "lower", "lowercase" -> caseFormat = CaseFormat.LOWER;
                    case "title", "titlecase" -> caseFormat = CaseFormat.TITLE;
                    case "camel", "camelcase" -> caseFormat = CaseFormat.CAMEL;
                    case "snake", "snakecase" -> caseFormat = CaseFormat.SNAKE;
                    case "kebab", "kebabcase" -> caseFormat = CaseFormat.KEBAB;
                    case "sanitize", "safe" -> sanitize = true;
                    default -> {
                        if (part.startsWith("max:") || part.startsWith("length:")) {
                            String lengthStr = part.substring(part.indexOf(':') + 1);
                            maxLength = Integer.parseInt(lengthStr);
                        }
                    }
                }
            }
            
            return new StringFormat(caseFormat, sanitize, maxLength, "...");
        }
    }
    
    public enum CaseFormat {
        NONE, UPPER, LOWER, TITLE, CAMEL, SNAKE, KEBAB
    }
    
    /**
     * Date formatting (for year, release date, etc.)
     */
    public record DateFormat(
        String pattern,
        DateTimeFormatter formatter
    ) implements FormatSpecifier {
        
        public DateFormat(String pattern) {
            this(pattern, DateTimeFormatter.ofPattern(pattern));
        }
        
        @Override
        public String format(Object value) {
            return switch (value) {
                case Integer year -> Year.of(year).format(formatter);
                case java.time.LocalDate date -> date.format(formatter);
                case java.time.Year year -> year.format(formatter);
                default -> String.valueOf(value);
            };
        }
        
        @Override
        public boolean isValid() {
            return pattern != null && !pattern.isBlank() && formatter != null;
        }
        
        @Override
        public String getDescription() {
            return "Date format: " + pattern;
        }
        
        public static DateFormat parse(String formatSpec) {
            // Common date format shortcuts
            return switch (formatSpec.toLowerCase()) {
                case "year" -> new DateFormat("yyyy");
                case "short" -> new DateFormat("yy");
                case "full" -> new DateFormat("yyyy-MM-dd");
                default -> new DateFormat(formatSpec);
            };
        }
    }
    
    /**
     * Custom formatting for special cases
     */
    public record CustomFormat(
        String name,
        Function<Object, String> formatter,
        String description
    ) implements FormatSpecifier {
        
        @Override
        public String format(Object value) {
            return formatter.apply(value);
        }
        
        @Override
        public boolean isValid() {
            return name != null && formatter != null;
        }
        
        @Override
        public String getDescription() {
            return description != null ? description : "Custom format: " + name;
        }
    }
    
    /**
     * Parse format specifier from string
     */
    public static FormatSpecifier parseFormatSpecifier(String specifier) {
        if (specifier == null || specifier.isBlank()) {
            return new StringFormat(CaseFormat.NONE, false, Integer.MAX_VALUE, "...");
        }
        
        specifier = specifier.trim();
        
        // Check for numeric formats
        if (specifier.matches("\\d*\\.?\\d*[df]")) {
            return NumericFormat.parse(specifier);
        }
        
        // Check for date formats
        if (specifier.startsWith("date:")) {
            return DateFormat.parse(specifier.substring(5));
        }
        
        // Check for predefined custom formats
        return switch (specifier.toLowerCase()) {
            case "sanitize" -> new StringFormat(CaseFormat.NONE, true, Integer.MAX_VALUE, "...");
            case "filename" -> new StringFormat(CaseFormat.NONE, true, 200, "...");
            case "dirname" -> new StringFormat(CaseFormat.NONE, true, 100, "...");
            default -> StringFormat.parse(specifier);
        };
    }
    
    /**
     * Sanitize string for use as filename or directory name
     */
    public static String sanitizeFilename(String input) {
        if (input == null || input.isBlank()) {
            return "Unknown";
        }
        
        return FORMATTER_CACHE.computeIfAbsent("sanitize:" + input, key -> {
            String sanitized = input.trim();
            
            // Replace invalid characters with underscores
            sanitized = INVALID_FILENAME_CHARS.matcher(sanitized).replaceAll("_");
            
            // Normalize spaces
            sanitized = MULTIPLE_SPACES.matcher(sanitized).replaceAll(" ");
            
            // Remove leading/trailing dots (Windows limitation)
            sanitized = LEADING_TRAILING_DOTS.matcher(sanitized).replaceAll("");
            
            // Remove multiple consecutive underscores
            sanitized = sanitized.replaceAll("_+", "_");
            
            // Remove leading/trailing underscores
            sanitized = sanitized.replaceAll("^_|_$", "");
            
            // Ensure not empty and not too long
            if (sanitized.isEmpty()) {
                sanitized = "Unknown";
            } else if (sanitized.length() > 200) {
                sanitized = sanitized.substring(0, 197) + "...";
            }
            
            // Windows reserved names
            String upperSanitized = sanitized.toUpperCase();
            if (upperSanitized.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
                sanitized = sanitized + "_";
            }
            
            return sanitized;
        });
    }
    
    /**
     * Clear the formatter cache (useful for testing or memory management)
     */
    public static void clearCache() {
        FORMATTER_CACHE.clear();
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public static int getCacheSize() {
        return FORMATTER_CACHE.size();
    }
    
    private PatternFormatter() {} // Utility class
}