package com.musicorganizer.config.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Custom Jackson serializer for Path objects.
 * 
 * <p>Serializes Path objects as strings using their absolute path representation
 * with forward slashes for cross-platform compatibility.</p>
 * 
 * @since 1.0
 */
public class PathSerializer extends JsonSerializer<Path> {
    
    @Override
    public void serialize(Path path, JsonGenerator gen, SerializerProvider serializers) 
            throws IOException {
        if (path == null) {
            gen.writeNull();
        } else {
            // Convert to absolute path and normalize separators for cross-platform compatibility
            String pathString = path.toAbsolutePath().toString().replace('\\', '/');
            gen.writeString(pathString);
        }
    }
}