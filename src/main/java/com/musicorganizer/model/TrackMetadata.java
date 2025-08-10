package com.musicorganizer.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public class TrackMetadata {
    private final String title;
    private final String artist;
    private final String album;
    private final String genre;
    private final Integer year;
    private final Integer trackNumber;
    private final Integer totalTracks;
    private final Integer discNumber;
    private final Integer totalDiscs;
    private final String albumArtist;
    private final String composer;
    private final String comment;
    private final Duration duration;
    private final Long bitRate;
    private final Integer sampleRate;
    private final String format;
    private final LocalDate releaseDate;
    private final String musicBrainzId;
    private final String isrc;
    private final String publisher;
    private final String copyright;
    private final String language;
    private final String lyrics;
    private final Double replayGain;
    private final Integer bpm;
    private final String key;
    private final String mood;
    private final byte[] albumArt;

    private TrackMetadata(Builder builder) {
        this.title = builder.title;
        this.artist = builder.artist;
        this.album = builder.album;
        this.genre = builder.genre;
        this.year = builder.year;
        this.trackNumber = builder.trackNumber;
        this.totalTracks = builder.totalTracks;
        this.discNumber = builder.discNumber;
        this.totalDiscs = builder.totalDiscs;
        this.albumArtist = builder.albumArtist;
        this.composer = builder.composer;
        this.comment = builder.comment;
        this.duration = builder.duration;
        this.bitRate = builder.bitRate;
        this.sampleRate = builder.sampleRate;
        this.format = builder.format;
        this.releaseDate = builder.releaseDate;
        this.musicBrainzId = builder.musicBrainzId;
        this.isrc = builder.isrc;
        this.publisher = builder.publisher;
        this.copyright = builder.copyright;
        this.language = builder.language;
        this.lyrics = builder.lyrics;
        this.replayGain = builder.replayGain;
        this.bpm = builder.bpm;
        this.key = builder.key;
        this.mood = builder.mood;
        this.albumArt = builder.albumArt;
    }

    public String title() { return title; }
    public String artist() { return artist; }
    public String album() { return album; }
    public String genre() { return genre; }
    public Optional<Integer> year() { return Optional.ofNullable(year); }
    public Optional<Integer> trackNumber() { return Optional.ofNullable(trackNumber); }
    public Optional<Integer> totalTracks() { return Optional.ofNullable(totalTracks); }
    public Optional<Integer> discNumber() { return Optional.ofNullable(discNumber); }
    public Optional<Integer> totalDiscs() { return Optional.ofNullable(totalDiscs); }
    public Optional<String> albumArtist() { return Optional.ofNullable(albumArtist); }
    public Optional<String> composer() { return Optional.ofNullable(composer); }
    public Optional<String> comment() { return Optional.ofNullable(comment); }
    public Optional<Duration> duration() { return Optional.ofNullable(duration); }
    public Optional<Long> bitRate() { return Optional.ofNullable(bitRate); }
    public Optional<Integer> sampleRate() { return Optional.ofNullable(sampleRate); }
    public String format() { return format; }
    public Optional<LocalDate> releaseDate() { return Optional.ofNullable(releaseDate); }
    public Optional<String> musicBrainzId() { return Optional.ofNullable(musicBrainzId); }
    public Optional<String> isrc() { return Optional.ofNullable(isrc); }
    public Optional<String> publisher() { return Optional.ofNullable(publisher); }
    public Optional<String> copyright() { return Optional.ofNullable(copyright); }
    public Optional<String> language() { return Optional.ofNullable(language); }
    public Optional<String> lyrics() { return Optional.ofNullable(lyrics); }
    public Optional<Double> replayGain() { return Optional.ofNullable(replayGain); }
    public Optional<Integer> bpm() { return Optional.ofNullable(bpm); }
    public Optional<String> key() { return Optional.ofNullable(key); }
    public Optional<String> mood() { return Optional.ofNullable(mood); }
    public Optional<byte[]> albumArt() { return Optional.ofNullable(albumArt); }

    public boolean hasCompleteBasicInfo() {
        return title != null && !title.isBlank() &&
               artist != null && !artist.isBlank() &&
               album != null && !album.isBlank();
    }

    public boolean hasTrackInfo() {
        return trackNumber != null && trackNumber > 0;
    }

    public boolean hasDiscInfo() {
        return discNumber != null && discNumber > 0;
    }

    public boolean hasExtendedInfo() {
        return albumArtist != null || composer != null || 
               releaseDate != null || musicBrainzId != null;
    }

    public boolean hasAudioInfo() {
        return duration != null || bitRate != null || 
               sampleRate != null || format != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackMetadata that = (TrackMetadata) o;
        return Objects.equals(title, that.title) &&
               Objects.equals(artist, that.artist) &&
               Objects.equals(album, that.album) &&
               Objects.equals(trackNumber, that.trackNumber) &&
               Objects.equals(discNumber, that.discNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, artist, album, trackNumber, discNumber);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TrackMetadata{");
        sb.append("title='").append(title).append('\'');
        if (artist != null) sb.append(", artist='").append(artist).append('\'');
        if (album != null) sb.append(", album='").append(album).append('\'');
        if (trackNumber != null) sb.append(", track=").append(trackNumber);
        if (year != null) sb.append(", year=").append(year);
        sb.append('}');
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(TrackMetadata metadata) {
        return new Builder()
            .title(metadata.title)
            .artist(metadata.artist)
            .album(metadata.album)
            .genre(metadata.genre)
            .year(metadata.year)
            .trackNumber(metadata.trackNumber)
            .totalTracks(metadata.totalTracks)
            .discNumber(metadata.discNumber)
            .totalDiscs(metadata.totalDiscs)
            .albumArtist(metadata.albumArtist)
            .composer(metadata.composer)
            .comment(metadata.comment)
            .duration(metadata.duration)
            .bitRate(metadata.bitRate)
            .sampleRate(metadata.sampleRate)
            .format(metadata.format)
            .releaseDate(metadata.releaseDate)
            .musicBrainzId(metadata.musicBrainzId)
            .isrc(metadata.isrc)
            .publisher(metadata.publisher)
            .copyright(metadata.copyright)
            .language(metadata.language)
            .lyrics(metadata.lyrics)
            .replayGain(metadata.replayGain)
            .bpm(metadata.bpm)
            .key(metadata.key)
            .mood(metadata.mood)
            .albumArt(metadata.albumArt);
    }

    public static class Builder {
        private String title;
        private String artist;
        private String album;
        private String genre;
        private Integer year;
        private Integer trackNumber;
        private Integer totalTracks;
        private Integer discNumber;
        private Integer totalDiscs;
        private String albumArtist;
        private String composer;
        private String comment;
        private Duration duration;
        private Long bitRate;
        private Integer sampleRate;
        private String format;
        private LocalDate releaseDate;
        private String musicBrainzId;
        private String isrc;
        private String publisher;
        private String copyright;
        private String language;
        private String lyrics;
        private Double replayGain;
        private Integer bpm;
        private String key;
        private String mood;
        private byte[] albumArt;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder artist(String artist) {
            this.artist = artist;
            return this;
        }

        public Builder album(String album) {
            this.album = album;
            return this;
        }

        public Builder genre(String genre) {
            this.genre = genre;
            return this;
        }

        public Builder year(Integer year) {
            this.year = year;
            return this;
        }

        public Builder trackNumber(Integer trackNumber) {
            this.trackNumber = trackNumber;
            return this;
        }

        public Builder totalTracks(Integer totalTracks) {
            this.totalTracks = totalTracks;
            return this;
        }

        public Builder discNumber(Integer discNumber) {
            this.discNumber = discNumber;
            return this;
        }

        public Builder totalDiscs(Integer totalDiscs) {
            this.totalDiscs = totalDiscs;
            return this;
        }

        public Builder albumArtist(String albumArtist) {
            this.albumArtist = albumArtist;
            return this;
        }

        public Builder composer(String composer) {
            this.composer = composer;
            return this;
        }

        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder bitRate(Long bitRate) {
            this.bitRate = bitRate;
            return this;
        }

        public Builder sampleRate(Integer sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder releaseDate(LocalDate releaseDate) {
            this.releaseDate = releaseDate;
            return this;
        }

        public Builder musicBrainzId(String musicBrainzId) {
            this.musicBrainzId = musicBrainzId;
            return this;
        }

        public Builder isrc(String isrc) {
            this.isrc = isrc;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder copyright(String copyright) {
            this.copyright = copyright;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder lyrics(String lyrics) {
            this.lyrics = lyrics;
            return this;
        }

        public Builder replayGain(Double replayGain) {
            this.replayGain = replayGain;
            return this;
        }

        public Builder bpm(Integer bpm) {
            this.bpm = bpm;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder mood(String mood) {
            this.mood = mood;
            return this;
        }

        public Builder albumArt(byte[] albumArt) {
            this.albumArt = albumArt;
            return this;
        }

        public TrackMetadata build() {
            return new TrackMetadata(this);
        }
    }
}