package com.musicorganizer.validator;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.musicorganizer.model.TrackMetadata;
import com.musicorganizer.util.ProgressTracker;
import com.musicorganizer.validator.CollectionValidator.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive unit tests for CollectionValidator class.
 * Tests validation logic, configuration options, file integrity checks,
 * album completeness analysis, and concurrent processing.
 */
@ExtendWith(MockitoExtension.class)
class CollectionValidatorTest {

    @Mock
    private ProgressTracker progressTracker;
    
    private FileSystem fileSystem;
    private CollectionValidator validator;
    private ValidationConfig defaultConfig;
    private Path testRoot;

    @BeforeEach
    void setUp() throws IOException {
        // Create in-memory file system for safe testing
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        testRoot = fileSystem.getPath("/test");
        Files.createDirectories(testRoot);

        // Setup default configuration
        defaultConfig = ValidationConfig.defaultConfig();
        
        // Setup validator
        validator = new CollectionValidator(defaultConfig, progressTracker);

        // Setup progress tracker mock (lenient to avoid unnecessary stubbing warnings)
        lenient().when(progressTracker.startOperation(anyString(), anyInt())).thenReturn("test-op-1");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
    }

    // ===== Configuration Tests =====

    @Test
    @DisplayName("Should create default validation configuration")
    void testDefaultValidationConfig() {
        var config = ValidationConfig.defaultConfig();
        
        assertTrue(config.checkFileIntegrity());
        assertTrue(config.validateMetadata());
        assertTrue(config.detectIncompleteAlbums());
        assertTrue(config.findDuplicates());
        assertTrue(config.validateCoverArt());
        assertFalse(config.strictMetadataValidation());
        assertEquals(128, config.minBitrate());
        assertTrue(config.supportedFormats().contains("mp3"));
        assertTrue(config.supportedFormats().contains("flac"));
    }

    @Test
    @DisplayName("Should use custom validation configuration")
    void testCustomValidationConfig() {
        var customConfig = new ValidationConfig(
            false,  // checkFileIntegrity
            true,   // validateMetadata
            false,  // detectIncompleteAlbums
            false,  // findDuplicates
            false,  // validateCoverArt
            Set.of("mp3", "wav"),
            256,    // minBitrate
            true    // strictMetadataValidation
        );

        var customValidator = new CollectionValidator(customConfig, progressTracker);
        assertNotNull(customValidator);
    }

    // ===== Basic Validation Tests =====

    @Test
    @DisplayName("Should validate empty collection successfully")
    void testValidateEmptyCollection() {
        var emptyCollection = Map.<Path, TrackMetadata>of();
        
        var result = validator.validateCollection(emptyCollection);
        
        assertNotNull(result);
        assertTrue(result.issues().isEmpty());
        assertTrue(result.albumCompleteness().isEmpty());
        assertTrue(result.fileIntegrityResults().isEmpty());
        assertEquals(0, result.summary().totalFiles());
        assertNotNull(result.validationTime());
        
        verify(progressTracker).startOperation("collection_validation", 0);
        verify(progressTracker).completeOperation("collection_validation");
    }

    @Test
    @DisplayName("Should validate single valid file")
    void testValidateSingleValidFile() throws IOException {
        // Create test file
        var testFile = createTestFile("test.mp3", "test content");
        
        // Create valid metadata
        var metadata = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .trackNumber(1)
            .totalTracks(10)
            .bitRate(320L)
            .build();

        var collection = Map.of(testFile, metadata);
        
        var result = validator.validateCollection(collection);
        
        assertNotNull(result);
        assertEquals(1, result.summary().totalFiles());
        assertEquals(1, result.summary().validFiles());
        assertEquals(0, result.summary().corruptFiles());
        
        // Should have file integrity result
        assertTrue(result.fileIntegrityResults().containsKey(testFile));
        var integrity = result.fileIntegrityResults().get(testFile);
        assertTrue(integrity.isReadable());
        assertTrue(integrity.hasValidMetadata());
        assertTrue(integrity.checksum().isPresent());
    }

    @Test
    @DisplayName("Should handle null metadata gracefully")
    void testValidateWithNullMetadata() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var collection = new HashMap<Path, TrackMetadata>();
        collection.put(testFile, null);
        
        var result = validator.validateCollection(collection);
        
        // Should report missing metadata issue
        var metadataIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_METADATA)
            .toList();
        
        assertFalse(metadataIssues.isEmpty());
        assertEquals(IssueSeverity.ERROR, metadataIssues.get(0).severity());
        assertEquals(testFile, metadataIssues.get(0).filePath());
    }

    // ===== File Integrity Tests =====

    @Test
    @DisplayName("Should detect unreadable files")
    void testDetectUnreadableFiles() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        
        // Make file unreadable (simulate by creating non-existent path)
        var unreadableFile = testRoot.resolve("nonexistent.mp3");
        
        var metadata = createBasicMetadata();
        var collection = Map.of(unreadableFile, metadata);
        
        var result = validator.validateCollection(collection);
        
        // Should report corrupt file issue
        var corruptIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.CORRUPT_FILE)
            .toList();
        
        assertFalse(corruptIssues.isEmpty());
        assertEquals(IssueSeverity.CRITICAL, corruptIssues.get(0).severity());
        assertEquals(1, result.summary().corruptFiles());
    }

    @Test
    @DisplayName("Should calculate file checksums")
    void testFileChecksumCalculation() throws IOException {
        var testFile = createTestFile("test.mp3", "test content for checksum");
        var metadata = createBasicMetadata();
        var collection = Map.of(testFile, metadata);
        
        var result = validator.validateCollection(collection);
        
        var integrity = result.fileIntegrityResults().get(testFile);
        assertNotNull(integrity);
        assertTrue(integrity.checksum().isPresent());
        assertTrue(integrity.checksum().get().length() > 0);
    }

    @Test
    @DisplayName("Should skip file integrity checks when disabled")
    void testSkipFileIntegrityChecks() throws IOException {
        var configWithoutIntegrity = new ValidationConfig(
            false, true, true, true, true,
            defaultConfig.supportedFormats(),
            defaultConfig.minBitrate(),
            false
        );
        var validatorWithoutIntegrity = new CollectionValidator(configWithoutIntegrity, progressTracker);
        
        var testFile = createTestFile("test.mp3", "content");
        var metadata = createBasicMetadata();
        var collection = Map.of(testFile, metadata);
        
        var result = validatorWithoutIntegrity.validateCollection(collection);
        
        var integrity = result.fileIntegrityResults().get(testFile);
        assertNotNull(integrity);
        assertTrue(integrity.checksum().isEmpty()); // Should not calculate checksum
    }

    // ===== Metadata Validation Tests =====

    @Test
    @DisplayName("Should validate complete metadata")
    void testValidateCompleteMetadata() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var completeMetadata = TrackMetadata.builder()
            .title("Complete Song")
            .artist("Complete Artist")
            .album("Complete Album")
            .trackNumber(1)
            .year(2024)
            .genre("Rock")
            .bitRate(320L)
            .build();
        
        var collection = Map.of(testFile, completeMetadata);
        var result = validator.validateCollection(collection);
        
        // Should have minimal issues (maybe only INFO level for optional fields)
        var errorIssues = result.issues().stream()
            .filter(issue -> issue.severity() == IssueSeverity.ERROR)
            .toList();
        
        assertTrue(errorIssues.isEmpty());
    }

    @Test
    @DisplayName("Should detect missing required metadata fields")
    void testDetectMissingRequiredFields() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var incompleteMetadata = TrackMetadata.builder()
            .title("Title Only")
            // Missing artist and album
            .build();
        
        var collection = Map.of(testFile, incompleteMetadata);
        var result = validator.validateCollection(collection);
        
        var metadataIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_METADATA)
            .toList();
        
        assertFalse(metadataIssues.isEmpty());
        
        // Check that missing fields are reported
        var issueMessage = metadataIssues.get(0).message();
        assertTrue(issueMessage.contains("artist"));
        assertTrue(issueMessage.contains("album"));
    }

    @Test
    @DisplayName("Should report missing recommended fields as INFO")
    void testReportMissingRecommendedFields() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var metadataWithoutOptional = TrackMetadata.builder()
            .title("Title")
            .artist("Artist")
            .album("Album")
            // Missing trackNumber, year, genre
            .build();
        
        var collection = Map.of(testFile, metadataWithoutOptional);
        var result = validator.validateCollection(collection);
        
        var infoIssues = result.issues().stream()
            .filter(issue -> issue.severity() == IssueSeverity.INFO)
            .toList();
        
        assertFalse(infoIssues.isEmpty());
        
        var issueMessage = infoIssues.get(0).message();
        assertTrue(issueMessage.contains("recommended"));
    }

    @Test
    @DisplayName("Should enforce strict metadata validation when enabled")
    void testStrictMetadataValidation() throws IOException {
        var strictConfig = new ValidationConfig(
            true, true, true, true, true,
            defaultConfig.supportedFormats(),
            defaultConfig.minBitrate(),
            true // strict validation
        );
        var strictValidator = new CollectionValidator(strictConfig, progressTracker);
        
        var testFile = createTestFile("test.mp3", "content");
        var incompleteMetadata = TrackMetadata.builder()
            .title("Title Only")
            .build();
        
        var collection = Map.of(testFile, incompleteMetadata);
        var result = strictValidator.validateCollection(collection);
        
        var errorIssues = result.issues().stream()
            .filter(issue -> issue.severity() == IssueSeverity.ERROR)
            .toList();
        
        assertFalse(errorIssues.isEmpty());
    }

    // ===== Audio Quality Tests =====

    @Test
    @DisplayName("Should detect low bitrate audio")
    void testDetectLowBitrateAudio() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var lowBitrateMetadata = TrackMetadata.builder()
            .title("Low Quality Song")
            .artist("Test Artist")
            .album("Test Album")
            .bitRate(96L) // Below default minimum of 128
            .build();
        
        var collection = Map.of(testFile, lowBitrateMetadata);
        var result = validator.validateCollection(collection);
        
        var qualityIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.LOW_QUALITY_AUDIO)
            .toList();
        
        assertFalse(qualityIssues.isEmpty());
        assertEquals(IssueSeverity.WARNING, qualityIssues.get(0).severity());
        assertTrue(qualityIssues.get(0).message().contains("96"));
        assertTrue(qualityIssues.get(0).message().contains("128"));
    }

    @Test
    @DisplayName("Should accept high bitrate audio")
    void testAcceptHighBitrateAudio() throws IOException {
        var testFile = createTestFile("test.flac", "content");
        var highBitrateMetadata = TrackMetadata.builder()
            .title("High Quality Song")
            .artist("Test Artist")
            .album("Test Album")
            .bitRate(1411L) // CD quality
            .build();
        
        var collection = Map.of(testFile, highBitrateMetadata);
        var result = validator.validateCollection(collection);
        
        var qualityIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.LOW_QUALITY_AUDIO)
            .toList();
        
        assertTrue(qualityIssues.isEmpty());
    }

    // ===== File Format Tests =====

    @Test
    @DisplayName("Should validate supported file formats")
    void testValidateSupportedFormats() throws IOException {
        var mp3File = createTestFile("test.mp3", "mp3 content");
        var flacFile = createTestFile("test.flac", "flac content");
        
        var metadata = createBasicMetadata();
        var collection = Map.of(
            mp3File, metadata,
            flacFile, metadata
        );
        
        var result = validator.validateCollection(collection);
        
        var formatIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.INVALID_FILENAME)
            .toList();
        
        assertTrue(formatIssues.isEmpty());
    }

    @Test
    @DisplayName("Should detect unsupported file formats")
    void testDetectUnsupportedFormats() throws IOException {
        var unsupportedFile = createTestFile("test.wma", "wma content");
        var metadata = createBasicMetadata();
        var collection = Map.of(unsupportedFile, metadata);
        
        var result = validator.validateCollection(collection);
        
        var formatIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.INVALID_FILENAME)
            .toList();
        
        assertFalse(formatIssues.isEmpty());
        assertEquals(IssueSeverity.WARNING, formatIssues.get(0).severity());
        assertTrue(formatIssues.get(0).message().contains("wma"));
    }

    // ===== Cover Art Validation Tests =====

    @Test
    @DisplayName("Should detect missing cover art")
    void testDetectMissingCoverArt() throws IOException {
        var testFile = createTestFile("album/track.mp3", "content");
        var metadataWithoutCover = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            // No album art
            .build();
        
        var collection = Map.of(testFile, metadataWithoutCover);
        var result = validator.validateCollection(collection);
        
        var coverIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_COVER_ART)
            .toList();
        
        assertFalse(coverIssues.isEmpty());
        assertEquals(IssueSeverity.WARNING, coverIssues.get(0).severity());
    }

    @Test
    @DisplayName("Should accept embedded cover art")
    void testAcceptEmbeddedCoverArt() throws IOException {
        var testFile = createTestFile("album/track.mp3", "content");
        var metadataWithCover = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .albumArt(new byte[50000]) // Large enough cover art
            .build();
        
        var collection = Map.of(testFile, metadataWithCover);
        var result = validator.validateCollection(collection);
        
        var coverIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_COVER_ART)
            .toList();
        
        assertTrue(coverIssues.isEmpty());
    }

    @Test
    @DisplayName("Should detect low resolution cover art")
    void testDetectLowResolutionCoverArt() throws IOException {
        var testFile = createTestFile("album/track.mp3", "content");
        var metadataWithSmallCover = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .albumArt(new byte[5000]) // Small cover art
            .build();
        
        var collection = Map.of(testFile, metadataWithSmallCover);
        var result = validator.validateCollection(collection);
        
        var coverIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_COVER_ART && 
                           issue.message().contains("low resolution"))
            .toList();
        
        assertFalse(coverIssues.isEmpty());
        assertEquals(IssueSeverity.INFO, coverIssues.get(0).severity());
    }

    @Test
    @DisplayName("Should find folder cover art")
    void testFindFolderCoverArt() throws IOException {
        var albumDir = testRoot.resolve("album");
        Files.createDirectories(albumDir);
        var testFile = createTestFile("album/track.mp3", "content");
        
        // Create folder cover art
        createTestFile("album/cover.jpg", "cover image data");
        
        var metadataWithoutEmbeddedCover = TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .build();
        
        var collection = Map.of(testFile, metadataWithoutEmbeddedCover);
        var result = validator.validateCollection(collection);
        
        var coverIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_COVER_ART)
            .toList();
        
        assertTrue(coverIssues.isEmpty());
    }

    @Test
    @DisplayName("Should skip cover art validation when disabled")
    void testSkipCoverArtValidation() throws IOException {
        var configWithoutCoverValidation = new ValidationConfig(
            true, true, true, true, false,
            defaultConfig.supportedFormats(),
            defaultConfig.minBitrate(),
            false
        );
        var validatorWithoutCover = new CollectionValidator(configWithoutCoverValidation, progressTracker);
        
        var testFile = createTestFile("test.mp3", "content");
        var metadataWithoutCover = createBasicMetadata();
        var collection = Map.of(testFile, metadataWithoutCover);
        
        var result = validatorWithoutCover.validateCollection(collection);
        
        var coverIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.MISSING_COVER_ART)
            .toList();
        
        assertTrue(coverIssues.isEmpty());
    }

    // ===== Album Completeness Tests =====

    @Test
    @DisplayName("Should detect complete album")
    void testDetectCompleteAlbum() throws IOException {
        var albumTracks = createCompleteAlbumTracks("Test Artist", "Test Album", 5);
        
        var result = validator.validateCollection(albumTracks);
        
        var albumKey = "Test Artist - Test Album";
        assertTrue(result.albumCompleteness().containsKey(albumKey));
        
        var completeness = result.albumCompleteness().get(albumKey);
        assertTrue(completeness.isComplete());
        assertEquals(5, completeness.totalTracks());
        assertTrue(completeness.missingTracks().isEmpty());
        assertEquals(5, completeness.presentTracks().size());
    }

    @Test
    @DisplayName("Should detect incomplete album")
    void testDetectIncompleteAlbum() throws IOException {
        var incompleteAlbum = createIncompleteAlbumTracks("Test Artist", "Test Album", 
                                                        Arrays.asList(1, 3, 5), 5);
        
        var result = validator.validateCollection(incompleteAlbum);
        
        var albumKey = "Test Artist - Test Album";
        var completeness = result.albumCompleteness().get(albumKey);
        
        assertFalse(completeness.isComplete());
        assertEquals(5, completeness.totalTracks());
        assertEquals(Arrays.asList(2, 4), completeness.missingTracks());
        assertEquals(3, completeness.presentTracks().size());
    }

    @Test
    @DisplayName("Should handle albums without track numbers")
    void testHandleAlbumsWithoutTrackNumbers() throws IOException {
        var tracksWithoutNumbers = Map.of(
            createTestFile("track1.mp3", "content1"), 
            TrackMetadata.builder()
                .title("Song 1")
                .artist("Artist")
                .album("Album")
                .build(),
            createTestFile("track2.mp3", "content2"),
            TrackMetadata.builder()
                .title("Song 2")
                .artist("Artist")
                .album("Album")
                .build()
        );
        
        var result = validator.validateCollection(tracksWithoutNumbers);
        
        var albumKey = "Artist - Album";
        var completeness = result.albumCompleteness().get(albumKey);
        
        // Should be marked as incomplete due to missing track numbers
        assertFalse(completeness.isComplete());
    }

    @Test
    @DisplayName("Should skip album analysis when disabled")
    void testSkipAlbumAnalysis() throws IOException {
        var configWithoutAlbumAnalysis = new ValidationConfig(
            true, true, false, true, true,
            defaultConfig.supportedFormats(),
            defaultConfig.minBitrate(),
            false
        );
        var validatorWithoutAlbums = new CollectionValidator(configWithoutAlbumAnalysis, progressTracker);
        
        var albumTracks = createCompleteAlbumTracks("Test Artist", "Test Album", 3);
        var result = validatorWithoutAlbums.validateCollection(albumTracks);
        
        assertTrue(result.albumCompleteness().isEmpty());
    }

    // ===== Duplicate Detection Tests =====

    @Test
    @DisplayName("Should detect exact duplicate files")
    void testDetectExactDuplicates() throws IOException {
        var duplicateMetadata = TrackMetadata.builder()
            .title("Duplicate Song")
            .artist("Test Artist")
            .album("Test Album")
            .trackNumber(1)
            .build();
        
        var duplicates = Map.of(
            createTestFile("path1/song.mp3", "content"),
            duplicateMetadata,
            createTestFile("path2/song.mp3", "content"),
            duplicateMetadata
        );
        
        var result = validator.validateCollection(duplicates);
        
        var duplicateIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.DUPLICATE_FILE)
            .toList();
        
        assertEquals(2, duplicateIssues.size()); // Both files should be flagged
        assertEquals(IssueSeverity.WARNING, duplicateIssues.get(0).severity());
    }

    @Test
    @DisplayName("Should not report unique files as duplicates")
    void testNotReportUniqueDuplicates() throws IOException {
        var uniqueTracks = Map.of(
            createTestFile("song1.mp3", "content1"),
            TrackMetadata.builder()
                .title("Song One")
                .artist("Artist")
                .album("Album")
                .trackNumber(1)
                .build(),
            createTestFile("song2.mp3", "content2"),
            TrackMetadata.builder()
                .title("Song Two")
                .artist("Artist")
                .album("Album")
                .trackNumber(2)
                .build()
        );
        
        var result = validator.validateCollection(uniqueTracks);
        
        var duplicateIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.DUPLICATE_FILE)
            .toList();
        
        assertTrue(duplicateIssues.isEmpty());
    }

    @Test
    @DisplayName("Should skip duplicate detection when disabled")
    void testSkipDuplicateDetection() throws IOException {
        var configWithoutDuplicates = new ValidationConfig(
            true, true, true, false, true,
            defaultConfig.supportedFormats(),
            defaultConfig.minBitrate(),
            false
        );
        var validatorWithoutDuplicates = new CollectionValidator(configWithoutDuplicates, progressTracker);
        
        var duplicateMetadata = createBasicMetadata();
        var duplicates = Map.of(
            createTestFile("dup1.mp3", "content"),
            duplicateMetadata,
            createTestFile("dup2.mp3", "content"),
            duplicateMetadata
        );
        
        var result = validatorWithoutDuplicates.validateCollection(duplicates);
        
        var duplicateIssues = result.issues().stream()
            .filter(issue -> issue.type() == IssueType.DUPLICATE_FILE)
            .toList();
        
        assertTrue(duplicateIssues.isEmpty());
    }

    // ===== Summary Generation Tests =====

    @Test
    @DisplayName("Should generate accurate validation summary")
    void testGenerateValidationSummary() throws IOException {
        var mixedCollection = new HashMap<Path, TrackMetadata>();
        
        // Valid file
        mixedCollection.put(createTestFile("valid.mp3", "content"), createBasicMetadata());
        
        // File with missing metadata
        mixedCollection.put(createTestFile("incomplete.mp3", "content"), 
                          TrackMetadata.builder().title("Only Title").build());
        
        // Unreadable file (simulated)
        mixedCollection.put(testRoot.resolve("nonexistent.mp3"), createBasicMetadata());
        
        var result = validator.validateCollection(mixedCollection);
        var summary = result.summary();
        
        assertEquals(3, summary.totalFiles());
        assertTrue(summary.validFiles() >= 1);
        assertTrue(summary.corruptFiles() >= 1);
        assertTrue(summary.issueCounts().containsKey(IssueType.MISSING_METADATA));
        assertTrue(summary.issueCounts().containsKey(IssueType.CORRUPT_FILE));
    }

    // ===== Async Validation Tests =====

    @Test
    @DisplayName("Should validate collection asynchronously")
    void testAsyncValidation() throws IOException {
        var collection = Map.of(
            createTestFile("test.mp3", "content"),
            createBasicMetadata()
        );
        
        var futureResult = validator.validateCollectionAsync(collection);
        
        assertNotNull(futureResult);
        assertDoesNotThrow(() -> {
            var result = futureResult.get(5, TimeUnit.SECONDS);
            assertNotNull(result);
            assertEquals(1, result.summary().totalFiles());
        });
    }

    @Test
    @DisplayName("Should handle concurrent validation requests")
    void testConcurrentValidation() throws IOException {
        var collection1 = Map.of(createTestFile("test1.mp3", "content1"), createBasicMetadata());
        var collection2 = Map.of(createTestFile("test2.mp3", "content2"), createBasicMetadata());
        
        var future1 = validator.validateCollectionAsync(collection1);
        var future2 = validator.validateCollectionAsync(collection2);
        
        assertDoesNotThrow(() -> {
            var result1 = future1.get(5, TimeUnit.SECONDS);
            var result2 = future2.get(5, TimeUnit.SECONDS);
            
            assertNotNull(result1);
            assertNotNull(result2);
            assertEquals(1, result1.summary().totalFiles());
            assertEquals(1, result2.summary().totalFiles());
        });
    }

    // ===== Progress Tracking Tests =====

    @Test
    @DisplayName("Should track validation progress")
    void testProgressTracking() throws IOException {
        var largeCollection = new HashMap<Path, TrackMetadata>();
        for (int i = 0; i < 10; i++) {
            largeCollection.put(
                createTestFile("track" + i + ".mp3", "content" + i),
                createBasicMetadata()
            );
        }
        
        validator.validateCollection(largeCollection);
        
        verify(progressTracker).startOperation("collection_validation", 10);
        verify(progressTracker, atLeast(1)).updateProgress(eq("collection_validation"), anyInt());
        verify(progressTracker).completeOperation("collection_validation");
    }

    // ===== Incomplete Album Analysis Tests =====

    @Test
    @DisplayName("Should find incomplete albums asynchronously")
    void testFindIncompleteAlbumsAsync() throws IOException {
        var mixedAlbums = new HashMap<Path, TrackMetadata>();
        
        // Complete album
        mixedAlbums.putAll(createCompleteAlbumTracks("Artist1", "Complete Album", 3));
        
        // Incomplete album
        mixedAlbums.putAll(createIncompleteAlbumTracks("Artist2", "Incomplete Album", 
                                                     Arrays.asList(1, 3), 5));
        
        var futureIncomplete = validator.findIncompleteAlbumsAsync(mixedAlbums);
        
        assertDoesNotThrow(() -> {
            var incompleteAlbums = futureIncomplete.get(5, TimeUnit.SECONDS);
            
            assertEquals(1, incompleteAlbums.size());
            assertEquals("Incomplete Album", incompleteAlbums.get(0).album());
            assertEquals(Arrays.asList(2, 4, 5), incompleteAlbums.get(0).missingTracks());
        });
    }

    // ===== Error Handling Tests =====

    @Test
    @DisplayName("Should handle validation errors gracefully")
    void testHandleValidationErrors() {
        // Create a collection with problematic metadata
        var problematicCollection = Map.of(
            testRoot.resolve("test.mp3"),
            TrackMetadata.builder().build() // Minimal metadata
        );
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            var result = validator.validateCollection(problematicCollection);
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Should validate with different configurations without errors")
    void testValidateWithDifferentConfigurations() throws IOException {
        var collection = Map.of(
            createTestFile("test.mp3", "content"),
            createBasicMetadata()
        );
        
        var configs = List.of(
            ValidationConfig.defaultConfig(),
            new ValidationConfig(false, true, false, false, false, 
                               Set.of("mp3"), 128, false),
            new ValidationConfig(true, false, true, true, true, 
                               Set.of("mp3", "flac", "m4a"), 320, true)
        );
        
        for (var config : configs) {
            var validator = new CollectionValidator(config, progressTracker);
            assertDoesNotThrow(() -> {
                var result = validator.validateCollection(collection);
                assertNotNull(result);
            });
        }
    }

    // ===== Record Validation Tests =====

    @Test
    @DisplayName("Should validate ValidationResult record structure")
    void testValidationResultRecord() throws IOException {
        var collection = Map.of(
            createTestFile("test.mp3", "content"),
            createBasicMetadata()
        );
        
        var result = validator.validateCollection(collection);
        
        // Test record structure
        assertNotNull(result.issues());
        assertNotNull(result.albumCompleteness());
        assertNotNull(result.fileIntegrityResults());
        assertNotNull(result.summary());
        assertNotNull(result.validationTime());
        assertTrue(result.validationTime().toNanos() > 0);
    }

    @Test
    @DisplayName("Should validate ValidationIssue record structure")
    void testValidationIssueRecord() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var incompleteMetadata = TrackMetadata.builder().title("Only Title").build();
        var collection = Map.of(testFile, incompleteMetadata);
        
        var result = validator.validateCollection(collection);
        
        assertFalse(result.issues().isEmpty());
        var issue = result.issues().get(0);
        
        assertNotNull(issue.type());
        assertNotNull(issue.severity());
        assertNotNull(issue.filePath());
        assertNotNull(issue.message());
        assertNotNull(issue.suggestion());
        assertTrue(issue.message().length() > 0);
        assertTrue(issue.suggestion().length() > 0);
    }

    @Test
    @DisplayName("Should validate FileIntegrity record structure")
    void testFileIntegrityRecord() throws IOException {
        var testFile = createTestFile("test.mp3", "content");
        var metadata = createBasicMetadata();
        var collection = Map.of(testFile, metadata);
        
        var result = validator.validateCollection(collection);
        
        var integrity = result.fileIntegrityResults().get(testFile);
        assertNotNull(integrity);
        assertNotNull(integrity.file());
        assertNotNull(integrity.integrityIssues());
        assertEquals(testFile, integrity.file());
    }

    // ===== Helper Methods =====

    private Path createTestFile(String filename, String content) throws IOException {
        var filePath = testRoot.resolve(filename);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        return filePath;
    }

    private TrackMetadata createBasicMetadata() {
        return TrackMetadata.builder()
            .title("Test Song")
            .artist("Test Artist")
            .album("Test Album")
            .trackNumber(1)
            .bitRate(320L)
            .build();
    }

    private Map<Path, TrackMetadata> createCompleteAlbumTracks(String artist, String album, int totalTracks) throws IOException {
        var tracks = new HashMap<Path, TrackMetadata>();
        var albumDir = artist.replaceAll("\\s+", "_").toLowerCase() + "_" + album.replaceAll("\\s+", "_").toLowerCase();
        
        for (int i = 1; i <= totalTracks; i++) {
            var file = createTestFile(String.format("%s/%02d - track.mp3", albumDir, i), "content" + i);
            var metadata = TrackMetadata.builder()
                .title("Track " + i)
                .artist(artist)
                .album(album)
                .trackNumber(i)
                .totalTracks(totalTracks)
                .build();
            tracks.put(file, metadata);
        }
        
        return tracks;
    }

    private Map<Path, TrackMetadata> createIncompleteAlbumTracks(String artist, String album, 
                                                               List<Integer> presentTracks, int totalTracks) throws IOException {
        var tracks = new HashMap<Path, TrackMetadata>();
        var albumDir = artist.replaceAll("\\s+", "_").toLowerCase() + "_" + album.replaceAll("\\s+", "_").toLowerCase();
        
        for (int trackNum : presentTracks) {
            var file = createTestFile(String.format("%s/%02d - track.mp3", albumDir, trackNum), "content" + trackNum);
            var metadata = TrackMetadata.builder()
                .title("Track " + trackNum)
                .artist(artist)
                .album(album)
                .trackNumber(trackNum)
                .totalTracks(totalTracks)
                .build();
            tracks.put(file, metadata);
        }
        
        return tracks;
    }
}