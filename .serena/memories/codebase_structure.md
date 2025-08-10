# Codebase Structure

## Main Components

### Core Processing Pipeline
```
MusicOrganizerCLI (Entry Point)
├── ParallelMusicScanner (Virtual Threads for file discovery)
├── ConcurrentDuplicateFinder (Parallel duplicate detection)
├── BatchMetadataProcessor (MusicBrainz integration)
├── NIOFileOrganizer (Atomic file operations)
└── CollectionValidator (Integrity checks)
```

### Key Classes

#### Scanner Package
- `ParallelMusicScanner`: Main scanner using virtual threads
  - Supports multiple audio formats
  - Concurrent metadata extraction
  - Progress tracking

#### Processor Package  
- `ConcurrentDuplicateFinder`: Duplicate detection engine
  - SHA-256 checksum calculation
  - Metadata similarity matching
  - Size-based detection
- `BatchMetadataProcessor`: Metadata enrichment
  - MusicBrainz API calls
  - Rate limiting
  - Batch processing

#### Service Package
- `MusicBrainzService`: External API integration
- `ExecutorServiceFactory`: Thread pool management
- `HttpClientProvider`: HTTP client configuration
- `UrlEncoder`: URL encoding utilities

#### Model Package
- `AudioFile`: File representation with metadata
- `AudioMetadata`: Track metadata model
- `DuplicateInfo`: Duplicate analysis results
- `ScanResult`: Scan operation results (Success/Failure/Partial)
- `TrackMetadata`: Enhanced track information

#### Organizer Package
- `NIOFileOrganizer`: File organization with rollback

#### Validator Package
- `CollectionValidator`: Collection integrity validation

## Test Structure
- Comprehensive unit tests for all components
- Integration tests for end-to-end flows
- Performance tests with timeout constraints
- Concurrency tests for virtual thread validation

## Configuration Files
- `pom.xml`: Maven build configuration
- `run.bat/run.sh`: Execution scripts with JVM tuning
- `CLAUDE.md`: Project-specific Claude instructions
- Global instructions at `C:\Users\caio\.claude\CLAUDE.md`