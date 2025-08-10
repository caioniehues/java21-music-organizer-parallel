package com.musicorganizer.config.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Custom Jackson deserializer for Path objects.
 * 
 * <p>Deserializes Path objects from string representations, handling both
 * relative and absolute paths across different operating systems.</p>
 * 
 * @since 1.0
 */
public class PathDeserializer extends JsonDeserializer<Path> {
    
    @Override
    public Path deserialize(JsonParser parser, DeserializationContext context) 
            throws IOException {
        String value = parser.getText();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        value = value.trim();
        
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid path: '" + value + "'", e);
        }
    }
}