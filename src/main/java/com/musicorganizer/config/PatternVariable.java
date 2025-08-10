package com.musicorganizer.config;

import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.model.AudioFile;
import com.musicorganizer.model.AudioMetadata;

import java.util.Optional;
import java.util.function.Function;

/**
 * Sealed interface representing pattern variables available for file organization templates.
 * Each variable defines how to extract metadata values and provides validation rules.
 */
public sealed interface PatternVariable permits 
    PatternVariable.StringVariable, 
    PatternVariable.NumericVariable, 
    PatternVariable.OptionalVariable {
    
    /**
     * Variable name used in patterns (e.g., "artist", "album")
     */
    String name();
    
    /**
     * Human-readable description
     */
    String description();
    
    /**
     * Default value when metadata is missing
     */
    String defaultValue();
    
    /**
     * Extract value from TrackMetadata
     */
    Optional<Object> extractFromTrackMetadata(TrackMetadata metadata);
    
    /**
     * Extract value from AudioMetadata
     */
    Optional<Object> extractFromAudioMetadata(AudioMetadata metadata);
    
    /**
     * Extract value from AudioFile
     */
    default Optional<Object> extractFromAudioFile(AudioFile audioFile) {
        if (audioFile.metadata() != null) {
            return extractFromAudioMetadata(audioFile.metadata());
        }
        return Optional.empty();
    }
    
    /**
     * Validate the extracted value
     */
    boolean isValid(Object value);
    
    /**
     * String-based variables (artist, album, title, etc.)
     */
    record StringVariable(
        String name,
        String description,
        String defaultValue,
        Function<TrackMetadata, String> trackExtractor,
        Function<AudioMetadata, Optional<String>> audioExtractor,
        int maxLength
    ) implements PatternVariable {
        
        public StringVariable {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Variable name cannot be null or blank");
            }
            if (maxLength <= 0) {
                throw new IllegalArgumentException("Max length must be positive");
            }
        }
        
        @Override
        public Optional<Object> extractFromTrackMetadata(TrackMetadata metadata) {
            if (metadata == null) return Optional.empty();
            String value = trackExtractor.apply(metadata);
            return Optional.ofNullable(value).filter(v -> !v.isBlank()).map(v -> (Object) v);
        }
        
        @Override
        public Optional<Object> extractFromAudioMetadata(AudioMetadata metadata) {
            if (metadata == null) return Optional.empty();
            return audioExtractor.apply(metadata).map(s -> (Object) s);
        }
        
        @Override
        public boolean isValid(Object value) {
            return value instanceof String str && 
                   !str.isBlank() && 
                   str.length() <= maxLength;
        }
    }
    
    /**
     * Numeric variables (year, track, disc, etc.)
     */
    record NumericVariable(
        String name,
        String description,
        String defaultValue,
        Function<TrackMetadata, Optional<Integer>> trackExtractor,
        Function<AudioMetadata, Optional<Integer>> audioExtractor,
        int minValue,
        int maxValue
    ) implements PatternVariable {
        
        public NumericVariable {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Variable name cannot be null or blank");
            }
            if (minValue >= maxValue) {
                throw new IllegalArgumentException("Min value must be less than max value");
            }
        }
        
        @Override
        public Optional<Object> extractFromTrackMetadata(TrackMetadata metadata) {
            if (metadata == null) return Optional.empty();
            return trackExtractor.apply(metadata).map(i -> (Object) i);
        }
        
        @Override
        public Optional<Object> extractFromAudioMetadata(AudioMetadata metadata) {
            if (metadata == null) return Optional.empty();
            return audioExtractor.apply(metadata).map(i -> (Object) i);
        }
        
        @Override
        public boolean isValid(Object value) {
            return value instanceof Integer num && 
                   num >= minValue && 
                   num <= maxValue;
        }
    }
    
    /**
     * Variables that may be present or absent (composer, musicbrainz, etc.)
     */
    record OptionalVariable(
        String name,
        String description,
        String defaultValue,
        Function<TrackMetadata, Optional<String>> trackExtractor,
        Function<AudioMetadata, Optional<String>> audioExtractor
    ) implements PatternVariable {
        
        public OptionalVariable {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Variable name cannot be null or blank");
            }
        }
        
        @Override
        public Optional<Object> extractFromTrackMetadata(TrackMetadata metadata) {
            if (metadata == null) return Optional.empty();
            return trackExtractor.apply(metadata).map(s -> (Object) s);
        }
        
        @Override
        public Optional<Object> extractFromAudioMetadata(AudioMetadata metadata) {
            if (metadata == null) return Optional.empty();
            return audioExtractor.apply(metadata).map(s -> (Object) s);
        }
        
        @Override
        public boolean isValid(Object value) {
            return value instanceof String str && !str.isBlank();
        }
    }
    
    /**
     * Predefined pattern variables for common metadata fields
     */
    final class Variables {
        public static final StringVariable ARTIST = new StringVariable(
            "artist",
            "Primary artist or performer",
            "Unknown Artist",
            TrackMetadata::artist,
            metadata -> metadata.artist(),
            100
        );
        
        public static final StringVariable ALBUM = new StringVariable(
            "album",
            "Album or collection name",
            "Unknown Album",
            TrackMetadata::album,
            metadata -> metadata.album(),
            100
        );
        
        public static final StringVariable TITLE = new StringVariable(
            "title",
            "Track title",
            "Unknown Track",
            TrackMetadata::title,
            metadata -> metadata.title(),
            150
        );
        
        public static final StringVariable GENRE = new StringVariable(
            "genre",
            "Musical genre",
            "Unknown",
            TrackMetadata::genre,
            metadata -> metadata.genre(),
            50
        );
        
        public static final StringVariable FORMAT = new StringVariable(
            "format",
            "Audio format (mp3, flac, etc.)",
            "Unknown",
            TrackMetadata::format,
            metadata -> metadata.format(),
            10
        );
        
        public static final NumericVariable YEAR = new NumericVariable(
            "year",
            "Release year",
            "0000",
            TrackMetadata::year,
            metadata -> metadata.year(),
            1900,
            2099
        );
        
        public static final NumericVariable TRACK = new NumericVariable(
            "track",
            "Track number",
            "00",
            TrackMetadata::trackNumber,
            metadata -> metadata.trackNumber(),
            1,
            999
        );
        
        public static final NumericVariable DISC = new NumericVariable(
            "disc",
            "Disc number",
            "1",
            TrackMetadata::discNumber,
            metadata -> Optional.empty(), // Not available in AudioMetadata
            1,
            99
        );
        
        public static final NumericVariable TOTAL_TRACKS = new NumericVariable(
            "totaltracks",
            "Total tracks on album",
            "00",
            TrackMetadata::totalTracks,
            metadata -> metadata.totalTracks(),
            1,
            999
        );
        
        public static final NumericVariable TOTAL_DISCS = new NumericVariable(
            "totaldiscs",
            "Total discs in album",
            "1",
            TrackMetadata::totalDiscs,
            metadata -> Optional.empty(), // Not available in AudioMetadata
            1,
            99
        );
        
        public static final OptionalVariable ALBUM_ARTIST = new OptionalVariable(
            "albumartist",
            "Album artist (for compilations)",
            "Various Artists",
            TrackMetadata::albumArtist,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        public static final OptionalVariable COMPOSER = new OptionalVariable(
            "composer",
            "Composer or songwriter",
            "Unknown Composer",
            TrackMetadata::composer,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        public static final OptionalVariable PUBLISHER = new OptionalVariable(
            "publisher",
            "Record label or publisher",
            "Independent",
            TrackMetadata::publisher,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        public static final OptionalVariable KEY = new OptionalVariable(
            "key",
            "Musical key",
            "Unknown",
            TrackMetadata::key,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        public static final OptionalVariable MOOD = new OptionalVariable(
            "mood",
            "Musical mood",
            "Unknown",
            TrackMetadata::mood,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        public static final OptionalVariable MUSICBRAINZ_ID = new OptionalVariable(
            "mbid",
            "MusicBrainz track ID",
            "",
            TrackMetadata::musicBrainzId,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        public static final OptionalVariable LANGUAGE = new OptionalVariable(
            "language",
            "Track language",
            "Unknown",
            TrackMetadata::language,
            metadata -> Optional.empty() // Not available in AudioMetadata
        );
        
        private Variables() {} // Utility class
    }
}