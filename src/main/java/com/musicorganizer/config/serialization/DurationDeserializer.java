package com.musicorganizer.config.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom Jackson deserializer for Duration objects.
 * 
 * <p>Deserializes Duration objects from various string formats:
 * <ul>
 *   <li>ISO-8601 format: "PT30S", "PT1M30S", "PT1H30M"</li>
 *   <li>Simple format: "30s", "1m30s", "1h30m"</li>
 *   <li>Numeric format: "30" (interpreted as seconds)</li>
 * </ul>
 * 
 * @since 1.0
 */
public class DurationDeserializer extends JsonDeserializer<Duration> {
    
    private static final Pattern SIMPLE_DURATION_PATTERN = 
        Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);
    
    @Override
    public Duration deserialize(JsonParser parser, DeserializationContext context) 
            throws IOException {
        String value = parser.getText();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        value = value.trim();
        
        // Try ISO-8601 format first
        if (value.startsWith("PT") || value.startsWith("P")) {
            try {
                return Duration.parse(value);
            } catch (Exception e) {
                // Fall through to other formats
            }
        }
        
        // Try simple numeric format (assume seconds)
        if (value.matches("\\d+")) {
            try {
                long seconds = Long.parseLong(value);
                return Duration.ofSeconds(seconds);
            } catch (NumberFormatException e) {
                // Fall through
            }
        }
        
        // Try simple format like "1h30m45s"
        Matcher matcher = SIMPLE_DURATION_PATTERN.matcher(value);
        if (matcher.matches()) {
            long totalSeconds = 0;
            
            String hours = matcher.group(1);
            String minutes = matcher.group(2);
            String seconds = matcher.group(3);
            
            if (hours != null) {
                totalSeconds += Long.parseLong(hours) * 3600;
            }
            if (minutes != null) {
                totalSeconds += Long.parseLong(minutes) * 60;
            }
            if (seconds != null) {
                totalSeconds += Long.parseLong(seconds);
            }
            
            if (totalSeconds > 0) {
                return Duration.ofSeconds(totalSeconds);
            }
        }
        
        throw new IllegalArgumentException(
            "Cannot parse duration: '" + value + "'. " +
            "Expected formats: ISO-8601 (PT30S), simple (30s, 1m30s), or numeric seconds (30)"
        );
    }
}