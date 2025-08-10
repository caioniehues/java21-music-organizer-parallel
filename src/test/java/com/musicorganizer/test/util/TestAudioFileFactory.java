package com.musicorganizer.test.util;

import com.musicorganizer.model.AudioFile;
import com.musicorganizer.model.AudioMetadata;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for creating test AudioFile objects with realistic data.
 * Useful for testing without actual file I/O operations.
 */
public class TestAudioFileFactory {
    
    private static final Random RANDOM = new Random();
    private static final String[] GENRES = {"Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip Hop", "Country", "Blues"};
    private static final String[] FORMATS = {"MP3", "FLAC", "AAC", "OGG", "WAV", "M4A"};
    
    /**
     * Creates a test audio file with specified parameters.
     */
    public static AudioFile createAudioFile(String filename, long sizeInBytes, String checksum, AudioMetadata metadata) {
        Path path = Paths.get("/test/music/" + filename);
        return new AudioFile(path, sizeInBytes, java.time.Instant.now(), metadata, checksum);
    }
    
    /**
     * Creates a large test audio file (250MB+).
     */
    public static AudioFile createLargeAudioFile(String filename, long sizeInMB) {
        long sizeInBytes = sizeInMB * 1024 * 1024;
        String checksum = generateChecksum();
        AudioMetadata metadata = createRandomMetadata();
        return createAudioFile(filename, sizeInBytes, checksum, metadata);
    }
    
    /**
     * Creates a test audio file with random but realistic metadata.
     */
    public static AudioFile createRandomAudioFile() {
        String filename = "song_" + UUID.randomUUID().toString().substring(0, 8) + ".mp3";
        long size = ThreadLocalRandom.current().nextLong(1_000_000, 50_000_000); // 1MB to 50MB
        String checksum = generateChecksum();
        AudioMetadata metadata = createRandomMetadata();
        return createAudioFile(filename, size, checksum, metadata);
    }
    
    /**
     * Creates test metadata with specified values.
     */
    public static AudioMetadata createMetadata(String title, String artist, String album) {
        return new AudioMetadata.Builder()
            .title(title)
            .artist(artist)
            .album(album)
            .genre(GENRES[RANDOM.nextInt(GENRES.length)])
            .year(2000 + RANDOM.nextInt(24))
            .trackNumber(RANDOM.nextInt(20) + 1)
            .totalTracks(RANDOM.nextInt(10) + 10)
            .duration(Duration.ofSeconds(180 + RANDOM.nextInt(240)))
            .bitrate(128 + RANDOM.nextInt(192))
            .format(FORMATS[RANDOM.nextInt(FORMATS.length)])
            .build();
    }
    
    /**
     * Creates test metadata with specified values including year and track.
     */
    public static AudioMetadata createMetadata(String title, String artist, String album, int year, int trackNumber) {
        return new AudioMetadata.Builder()
            .title(title)
            .artist(artist)
            .album(album)
            .year(year)
            .trackNumber(trackNumber)
            .genre(GENRES[RANDOM.nextInt(GENRES.length)])
            .totalTracks(RANDOM.nextInt(10) + 10)
            .duration(Duration.ofSeconds(180 + RANDOM.nextInt(240)))
            .bitrate(128 + RANDOM.nextInt(192))
            .format(FORMATS[RANDOM.nextInt(FORMATS.length)])
            .build();
    }
    
    /**
     * Creates random metadata for testing.
     */
    public static AudioMetadata createRandomMetadata() {
        int artistNum = RANDOM.nextInt(100);
        int albumNum = RANDOM.nextInt(50);
        int songNum = RANDOM.nextInt(1000);
        
        return new AudioMetadata.Builder()
            .title("Song " + songNum)
            .artist("Artist " + artistNum)
            .album("Album " + albumNum)
            .genre(GENRES[RANDOM.nextInt(GENRES.length)])
            .year(2000 + RANDOM.nextInt(24))
            .trackNumber(RANDOM.nextInt(20) + 1)
            .totalTracks(RANDOM.nextInt(10) + 10)
            .duration(Duration.ofSeconds(180 + RANDOM.nextInt(240)))
            .bitrate(128 + RANDOM.nextInt(192))
            .format(FORMATS[RANDOM.nextInt(FORMATS.length)])
            .build();
    }
    
    /**
     * Creates empty metadata for testing files without tags.
     */
    public static AudioMetadata createEmptyMetadata() {
        return AudioMetadata.empty();
    }
    
    /**
     * Generates a random SHA-256-like checksum string.
     */
    public static String generateChecksum() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Creates an array of test audio files for bulk testing.
     */
    public static AudioFile[] createBulkAudioFiles(int count) {
        AudioFile[] files = new AudioFile[count];
        for (int i = 0; i < count; i++) {
            files[i] = createRandomAudioFile();
        }
        return files;
    }
    
    /**
     * Creates test audio files with duplicate checksums.
     */
    public static AudioFile[] createDuplicateFiles(int groupCount, int filesPerGroup) {
        AudioFile[] files = new AudioFile[groupCount * filesPerGroup];
        int index = 0;
        
        for (int g = 0; g < groupCount; g++) {
            String checksum = generateChecksum();
            AudioMetadata baseMetadata = createRandomMetadata();
            
            for (int f = 0; f < filesPerGroup; f++) {
                String filename = String.format("file_%d_%d.mp3", g, f);
                long size = ThreadLocalRandom.current().nextLong(1_000_000, 50_000_000);
                files[index++] = createAudioFile(filename, size, checksum, baseMetadata);
            }
        }
        
        return files;
    }
    
    /**
     * Creates test audio files for performance testing with large sizes.
     */
    public static AudioFile[] createLargeFiles(int count, long minSizeMB, long maxSizeMB) {
        AudioFile[] files = new AudioFile[count];
        for (int i = 0; i < count; i++) {
            long sizeMB = ThreadLocalRandom.current().nextLong(minSizeMB, maxSizeMB + 1);
            files[i] = createLargeAudioFile("large_file_" + i + ".flac", sizeMB);
        }
        return files;
    }
}