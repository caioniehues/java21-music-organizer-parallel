package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of actions to take when duplicate files are encountered.
 * 
 * <p>This enum defines the possible strategies for handling duplicate audio files
 * during the organization process, providing flexibility in automated workflows.</p>
 * 
 * @since 1.0
 */
public enum DuplicateAction {
    
    /**
     * Skip processing the duplicate file, leaving the original in place.
     */
    SKIP("skip"),
    
    /**
     * Rename the duplicate file with a suffix to avoid conflicts.
     */
    RENAME("rename"),
    
    /**
     * Replace the existing file with the new duplicate.
     */
    REPLACE("replace"),
    
    /**
     * Prompt the user to decide what action to take.
     */
    ASK("ask");
    
    private final String value;
    
    DuplicateAction(String value) {
        this.value = value;
    }
    
    /**
     * Gets the string representation of this duplicate action.
     * 
     * @return the string value used in configuration files
     */
    @JsonValue
    public String getValue() {
        return value;
    }
    
    /**
     * Creates a DuplicateAction from its string representation.
     * 
     * @param value the string value from configuration
     * @return the corresponding DuplicateAction
     * @throws IllegalArgumentException if the value is not recognized
     */
    @JsonCreator
    public static DuplicateAction fromValue(String value) {
        for (DuplicateAction action : values()) {
            if (action.value.equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown duplicate action: " + value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}