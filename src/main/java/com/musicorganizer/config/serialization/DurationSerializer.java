package com.musicorganizer.config.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.Duration;

/**
 * Custom Jackson serializer for Duration objects.
 * 
 * <p>Serializes Duration objects as human-readable strings in ISO-8601 format
 * (e.g., "PT30S" for 30 seconds, "PT1M30S" for 1 minute 30 seconds).</p>
 * 
 * @since 1.0
 */
public class DurationSerializer extends JsonSerializer<Duration> {
    
    @Override
    public void serialize(Duration duration, JsonGenerator gen, SerializerProvider serializers) 
            throws IOException {
        if (duration == null) {
            gen.writeNull();
        } else {
            gen.writeString(duration.toString());
        }
    }
}