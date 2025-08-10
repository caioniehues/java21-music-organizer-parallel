package com.musicorganizer.model;

import java.time.Duration;
import java.util.Optional;

/**
 * Record representing audio file metadata.
 * Uses Java 21 record with validation and optional fields.
 */
public record AudioMetadata(
    Optional<String> title,
    Optional<String> artist,
    Optional<String> album,
    Optional<String> genre,
    Optional<Integer> year,
    Optional<Integer> trackNumber,
    Optional<Integer> totalTracks,
    Optional<Duration> duration,
    Optional<Integer> bitrate,
    Optional<String> format
) {
    
    public AudioMetadata {
        // Validation for non-null optionals
        title = title != null ? title : Optional.empty();
        artist = artist != null ? artist : Optional.empty();
        album = album != null ? album : Optional.empty();
        genre = genre != null ? genre : Optional.empty();
        year = year != null ? year : Optional.empty();
        trackNumber = trackNumber != null ? trackNumber : Optional.empty();
        totalTracks = totalTracks != null ? totalTracks : Optional.empty();
        duration = duration != null ? duration : Optional.empty();
        bitrate = bitrate != null ? bitrate : Optional.empty();
        format = format != null ? format : Optional.empty();
        
        // Validate year range
        year.ifPresent(y -> {
            if (y < 1900 || y > 2030) {
                throw new IllegalArgumentException("Year must be between 1900 and 2030");
            }
        });
        
        // Validate track numbers
        trackNumber.ifPresent(track -> {
            if (track < 1) {
                throw new IllegalArgumentException("Track number must be positive");
            }
        });
        
        totalTracks.ifPresent(total -> {
            if (total < 1) {
                throw new IllegalArgumentException("Total tracks must be positive");
            }
        });
        
        // Validate bitrate
        bitrate.ifPresent(br -> {
            if (br < 8 || br > 2000) {
                throw new IllegalArgumentException("Bitrate must be between 8 and 2000 kbps");
            }
        });
    }
    
    /**
     * Creates an empty metadata record.
     */
    public static AudioMetadata empty() {
        return new AudioMetadata(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty()
        );
    }
    
    /**
     * Builder for creating AudioMetadata with fluent API.
     */
    public static class Builder {
        private Optional<String> title = Optional.empty();
        private Optional<String> artist = Optional.empty();
        private Optional<String> album = Optional.empty();
        private Optional<String> genre = Optional.empty();
        private Optional<Integer> year = Optional.empty();
        private Optional<Integer> trackNumber = Optional.empty();
        private Optional<Integer> totalTracks = Optional.empty();
        private Optional<Duration> duration = Optional.empty();
        private Optional<Integer> bitrate = Optional.empty();
        private Optional<String> format = Optional.empty();
        
        public Builder title(String title) {
            this.title = Optional.ofNullable(title);
            return this;
        }
        
        public Builder artist(String artist) {
            this.artist = Optional.ofNullable(artist);
            return this;
        }
        
        public Builder album(String album) {
            this.album = Optional.ofNullable(album);
            return this;
        }
        
        public Builder genre(String genre) {
            this.genre = Optional.ofNullable(genre);
            return this;
        }
        
        public Builder year(Integer year) {
            this.year = Optional.ofNullable(year);
            return this;
        }
        
        public Builder trackNumber(Integer trackNumber) {
            this.trackNumber = Optional.ofNullable(trackNumber);
            return this;
        }
        
        public Builder totalTracks(Integer totalTracks) {
            this.totalTracks = Optional.ofNullable(totalTracks);
            return this;
        }
        
        public Builder duration(Duration duration) {
            this.duration = Optional.ofNullable(duration);
            return this;
        }
        
        public Builder bitrate(Integer bitrate) {
            this.bitrate = Optional.ofNullable(bitrate);
            return this;
        }
        
        public Builder format(String format) {
            this.format = Optional.ofNullable(format);
            return this;
        }
        
        public AudioMetadata build() {
            return new AudioMetadata(title, artist, album, genre, year,
                trackNumber, totalTracks, duration, bitrate, format);
        }
    }
    
    /**
     * Returns a builder initialized with this metadata's values.
     */
    public Builder toBuilder() {
        return new Builder()
            .title(title.orElse(null))
            .artist(artist.orElse(null))
            .album(album.orElse(null))
            .genre(genre.orElse(null))
            .year(year.orElse(null))
            .trackNumber(trackNumber.orElse(null))
            .totalTracks(totalTracks.orElse(null))
            .duration(duration.orElse(null))
            .bitrate(bitrate.orElse(null))
            .format(format.orElse(null));
    }
    
    /**
     * Returns true if this metadata has any non-empty fields.
     */
    public boolean hasAnyData() {
        return title.isPresent() || artist.isPresent() || album.isPresent() ||
               genre.isPresent() || year.isPresent() || trackNumber.isPresent() ||
               totalTracks.isPresent() || duration.isPresent() || bitrate.isPresent() ||
               format.isPresent();
    }
    
    /**
     * Returns a formatted duration string.
     */
    public String getFormattedDuration() {
        return duration.map(d -> {
            long seconds = d.getSeconds();
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }).orElse("Unknown");
    }
    
    @Override
    public String toString() {
        return """
            AudioMetadata {
                title: %s
                artist: %s
                album: %s
                genre: %s
                year: %s
                track: %s/%s
                duration: %s
                bitrate: %s kbps
                format: %s
            }
            """.formatted(
                title.orElse("Unknown"),
                artist.orElse("Unknown"),
                album.orElse("Unknown"),
                genre.orElse("Unknown"),
                year.map(String::valueOf).orElse("Unknown"),
                trackNumber.map(String::valueOf).orElse("?"),
                totalTracks.map(String::valueOf).orElse("?"),
                getFormattedDuration(),
                bitrate.map(String::valueOf).orElse("Unknown"),
                format.orElse("Unknown")
            );
    }
}