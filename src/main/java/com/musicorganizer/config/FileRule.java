package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.musicorganizer.model.AudioFile;

/**
 * Sealed interface for file processing rules that determine organization profiles.
 * 
 * <p>This sealed interface defines the contract for rules that can be applied to
 * audio files to determine which organization profile should be used. Implementations
 * provide specific criteria such as bitrate, genre, file size, or format.</p>
 * 
 * <p>The interface uses Jackson annotations for polymorphic JSON serialization,
 * allowing different rule types to be mixed in configuration files.</p>
 * 
 * @since 1.0
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BitrateRule.class, name = "bitrate"),
    @JsonSubTypes.Type(value = GenreRule.class, name = "genre"),
    @JsonSubTypes.Type(value = SizeRule.class, name = "size"),
    @JsonSubTypes.Type(value = FormatRule.class, name = "format")
})
public sealed interface FileRule 
    permits BitrateRule, GenreRule, SizeRule, FormatRule {
    
    /**
     * Evaluates whether this rule applies to the given audio file.
     * 
     * @param audioFile the audio file to evaluate
     * @return true if the rule matches the file, false otherwise
     */
    boolean matches(AudioFile audioFile);
    
    /**
     * Gets the target profile name for files matching this rule.
     * 
     * @return the name of the organization profile to use
     */
    String getTargetProfile();
    
    /**
     * Gets a human-readable description of this rule for logging and debugging.
     * 
     * @return a descriptive string explaining the rule criteria
     */
    String getDescription();
}