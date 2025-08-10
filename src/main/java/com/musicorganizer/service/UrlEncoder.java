package com.musicorganizer.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Interface for URL encoding operations, enabling testability and proper encoding.
 */
public interface UrlEncoder {
    
    /**
     * Encodes a string for use in URL query parameters.
     */
    String encode(String value);
    
    /**
     * Escapes special characters for MusicBrainz query syntax.
     */
    String escapeQueryValue(String value);
    
    /**
     * Default implementation for production use.
     */
    static UrlEncoder defaultEncoder() {
        return new DefaultUrlEncoder();
    }
}

/**
 * Production implementation using proper URLEncoder.
 */
class DefaultUrlEncoder implements UrlEncoder {
    
    @Override
    public String encode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
    
    @Override
    public String escapeQueryValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // First escape quotes for MusicBrainz query syntax
        String escaped = value.replace("\"", "\\\"");
        
        // Then URL encode the entire string
        return encode(escaped);
    }
}