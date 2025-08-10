# Music Organizer Java Project Overview

## Purpose
High-performance music library organizer built with Java 21, designed to process thousands of audio files concurrently. Provides duplicate detection, metadata management, file organization, and collection validation.

## Tech Stack
- **Language**: Java 21 (with preview features enabled)
- **Build Tool**: Maven 3.8+
- **Core Dependencies**:
  - jaudiotagger 3.0.1 - Audio metadata reading
  - picocli 4.7.5 - Command-line interface
  - JUnit 5.10.1 - Testing framework
  - Mockito 5.7.0 - Mocking framework
  - jimfs 1.3.0 - In-memory file system for testing
  - awaitility 4.2.0 - Async testing

## Key Features
- Virtual Threads for massive concurrent processing (10,000+ files)
- SHA-256 checksum-based duplicate detection
- Metadata-based duplicate finding with similarity matching
- MusicBrainz API integration for metadata fixes
- Atomic file operations with rollback capability
- Zero-copy I/O with NIO.2
- ZGC garbage collector support for low-latency

## Performance Characteristics
- 10-20x faster than Python implementation
- Default parallelism: 1000 concurrent file operations
- Memory target: < 512MB for 10,000 files
- Build time target: < 30 seconds
- Metadata extraction target: < 100ms per file

## Supported Audio Formats
mp3, flac, m4a, aac, ogg, wav, wma, mp4