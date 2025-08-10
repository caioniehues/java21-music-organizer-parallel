package com.musicorganizer.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Record representing an organization profile that defines how music files should be structured.
 * 
 * <p>An organization profile specifies the directory structure and file naming pattern
 * for organizing music files. It uses template variables that are replaced with actual
 * metadata values during file organization.</p>
 * 
 * <p>Supported template variables:
 * <ul>
 *   <li><code>{artist}</code> - Artist name</li>
 *   <li><code>{album}</code> - Album title</li>
 *   <li><code>{title}</code> - Track title</li>
 *   <li><code>{track}</code> - Track number</li>
 *   <li><code>{track:02d}</code> - Track number zero-padded to 2 digits</li>
 *   <li><code>{year}</code> - Release year</li>
 *   <li><code>{genre}</code> - Genre</li>
 *   <li><code>{disc}</code> - Disc number (for multi-disc albums)</li>
 * </ul>
 * 
 * @param pattern template pattern for file organization (e.g., "{artist}/{album}/{track:02d} - {title}")
 * @param duplicateAction action to take when duplicate files are encountered
 * @param createArtistFolder whether to create artist-level directories
 * @param sanitizeFilenames whether to sanitize filenames for filesystem compatibility
 * 
 * @since 1.0
 */
public record OrganizationProfile(
    @JsonProperty("pattern") String pattern,
    @JsonProperty("duplicate_action") DuplicateAction duplicateAction,
    @JsonProperty("create_artist_folder") boolean createArtistFolder,
    @JsonProperty("sanitize_filenames") boolean sanitizeFilenames
) {
    
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = 
        Pattern.compile("\\{(artist|album|title|track(?::\\d+d)?|year|genre|disc)\\}");
    
    /**
     * Creates an OrganizationProfile with validation.
     * 
     * @param pattern the template pattern
     * @param duplicateAction action for duplicate files
     * @param createArtistFolder whether to create artist folders
     * @param sanitizeFilenames whether to sanitize filenames
     * @throws IllegalArgumentException if validation fails
     */
    @JsonCreator
    public OrganizationProfile {
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        Objects.requireNonNull(duplicateAction, "Duplicate action cannot be null");
        
        if (pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be empty");
        }
        
        pattern = pattern.trim();
        
        // Validate pattern contains at least one template variable
        if (!TEMPLATE_VARIABLE_PATTERN.matcher(pattern).find()) {
            throw new IllegalArgumentException(
                "Pattern must contain at least one template variable: " + pattern
            );
        }
        
        // Validate pattern doesn't contain invalid characters for filenames
        // Note: forward slashes are allowed as directory separators
        // Colons are allowed inside template variables like {track:02d}
        String patternWithoutTemplates = pattern.replaceAll("\\{[^}]*\\}", "");
        if (patternWithoutTemplates.matches(".*[<>:\"|?*\\\\].*")) {
            throw new IllegalArgumentException(
                "Pattern contains invalid filename characters outside template variables: " + pattern
            );
        }
    }
    
    /**
     * Creates a standard organization profile suitable for most music collections.
     * 
     * @return an OrganizationProfile with standard settings
     */
    public static OrganizationProfile standard() {
        return new OrganizationProfile(
            "{artist}/{album}/{track:02d} - {title}",
            DuplicateAction.SKIP,
            true,
            true
        );
    }
    
    /**
     * Creates a flat organization profile that doesn't create nested directories.
     * 
     * @return an OrganizationProfile for flat file organization
     */
    public static OrganizationProfile flat() {
        return new OrganizationProfile(
            "{artist} - {album} - {track:02d} - {title}",
            DuplicateAction.RENAME,
            false,
            true
        );
    }
    
    /**
     * Creates a detailed organization profile with genre and year information.
     * 
     * @return an OrganizationProfile with detailed metadata
     */
    public static OrganizationProfile detailed() {
        return new OrganizationProfile(
            "{genre}/{artist} ({year})/{album}/{track:02d} - {title}",
            DuplicateAction.ASK,
            true,
            true
        );
    }
    
    /**
     * Creates a minimalist organization profile with basic structure.
     * 
     * @return an OrganizationProfile with minimal structure
     */
    public static OrganizationProfile minimalist() {
        return new OrganizationProfile(
            "{artist}/{album}/{track:02d} {title}",
            DuplicateAction.SKIP,
            true,
            false
        );
    }
    
    /**
     * Creates a classical music organization profile with composer information.
     * 
     * @return an OrganizationProfile optimized for classical music
     */
    public static OrganizationProfile classical() {
        return new OrganizationProfile(
            "{artist}/{album}/{disc:02d}-{track:02d} - {title}",
            DuplicateAction.ASK,
            true,
            true
        );
    }
    
    /**
     * Creates a compilation-friendly organization profile.
     * 
     * @return an OrganizationProfile suitable for compilation albums
     */
    public static OrganizationProfile compilation() {
        return new OrganizationProfile(
            "Compilations/{album}/{track:02d} - {artist} - {title}",
            DuplicateAction.RENAME,
            false,
            true
        );
    }
    
    /**
     * Gets a description of this organization profile for display purposes.
     * 
     * @return a human-readable description
     */
    public String getDescription() {
        return String.format(
            "Pattern: %s, Duplicates: %s, Artist folders: %s, Sanitize: %s",
            pattern,
            duplicateAction.getValue(),
            createArtistFolder ? "Yes" : "No",
            sanitizeFilenames ? "Yes" : "No"
        );
    }
    
    /**
     * Creates a new OrganizationProfile with the specified pattern.
     * 
     * @param newPattern the new pattern to use
     * @return a new OrganizationProfile with the updated pattern
     */
    public OrganizationProfile withPattern(String newPattern) {
        return new OrganizationProfile(newPattern, duplicateAction, createArtistFolder, sanitizeFilenames);
    }
    
    /**
     * Creates a new OrganizationProfile with the specified duplicate action.
     * 
     * @param newDuplicateAction the new duplicate action
     * @return a new OrganizationProfile with the updated duplicate action
     */
    public OrganizationProfile withDuplicateAction(DuplicateAction newDuplicateAction) {
        return new OrganizationProfile(pattern, newDuplicateAction, createArtistFolder, sanitizeFilenames);
    }
}