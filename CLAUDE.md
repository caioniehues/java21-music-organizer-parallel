# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

High-performance music library organizer built with Java 21, leveraging virtual threads for massive parallel processing of audio files. Processes thousands of files concurrently with duplicate detection, metadata management, and intelligent organization.

## Build and Run Commands

### Building the Project
```bash
# Standard build
mvn clean package

# Build with tests
mvn clean test package

# Build skipping tests (faster for development)
mvn clean package -DskipTests

# Run specific test class
mvn test -Dtest=ParallelMusicScannerTest

# Run tests with specific pattern
mvn test -Dtest=*Scanner*
```

### Running the Application
```bash
# Windows - with optimized JVM settings
run.bat E:\Music --scan --find-duplicates

# Direct Java execution with full JVM tuning
java -Xms512m -Xmx4g -XX:+UseZGC \
     --enable-preview \
     -Djdk.virtualThreadScheduler.parallelism=10 \
     -Djdk.virtualThreadScheduler.maxPoolSize=256 \
     -jar target/music-organizer-1.0-SNAPSHOT.jar E:\Music

# Development run with debugging
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/music-organizer-1.0-SNAPSHOT.jar --help
```

## Architecture Overview

### Core Processing Pipeline

The application follows a concurrent pipeline architecture:

```
MusicOrganizerCLI (Entry Point)
    ├── ParallelMusicScanner (Virtual Threads)
    │   └── Concurrent file discovery and metadata extraction
    ├── ConcurrentDuplicateFinder
    │   ├── SHA-256 checksum calculation (parallel)
    │   └── Metadata similarity matching
    ├── BatchMetadataProcessor
    │   └── MusicBrainzService (rate-limited API calls)
    ├── NIOFileOrganizer
    │   └── Atomic file operations with rollback
    └── CollectionValidator
        └── Integrity and completeness checks
```

### Concurrency Model

- **Virtual Threads**: All I/O operations use `Executors.newVirtualThreadPerTaskExecutor()`
- **Default Parallelism**: 1000 concurrent file operations
- **Semaphore-based throttling**: Controls MusicBrainz API calls
- **CompletableFuture chains**: For async metadata processing

### Key Design Patterns

1. **Builder Pattern**: Used in model classes for fluent construction
2. **Strategy Pattern**: Different duplicate detection strategies
3. **Command Pattern**: PicoCLI command structure
4. **Repository Pattern**: File system abstraction in organizer

## Critical Code Paths

### Virtual Thread Initialization
Located in `ParallelMusicScanner`:
- Creates virtual thread executor
- Manages concurrent file processing
- Implements backpressure with semaphores

### Duplicate Detection Algorithm
In `ConcurrentDuplicateFinder`:
- Parallel checksum calculation using SHA-256
- Metadata-based fuzzy matching
- Concurrent hash map for duplicate tracking

### File Organization Logic
`NIOFileOrganizer` implements:
- Atomic moves with `Files.move(ATOMIC_MOVE)`
- Rollback capability with transaction log
- Pattern-based directory structure creation

## Performance Tuning

### JVM Settings (in run.bat/run.sh)
```
-XX:+UseZGC                    # Low-latency GC for large heaps
-XX:+EnableDynamicAgentLoading # Virtual thread optimization
-Xmx4G                         # Heap size (adjust based on collection)
-XX:MaxDirectMemorySize=2G    # Direct memory for NIO operations
```

### Virtual Thread Tuning
```java
// Adjust in code or system properties
-Djdk.virtualThreadScheduler.parallelism=256   # CPU-bound operations
-Djdk.virtualThreadScheduler.maxPoolSize=2000  # I/O-bound operations
```

## Adding New Features

### Adding a New Processor
1. Create class in `com.musicorganizer.processor`
2. Implement virtual thread executor pattern
3. Add to `MusicOrganizerCLI` command options
4. Follow existing patterns for progress tracking

### Adding File Format Support
1. Update `SUPPORTED_EXTENSIONS` in `ParallelMusicScanner`
2. Add metadata extraction logic
3. Update tests with sample files

## Testing Strategy

### Unit Tests
- Test individual processors with mock data
- Use `CompletableFuture.get(timeout)` for async tests
- Mock file system operations with `jimfs`

### Integration Tests
- Use small test music collections
- Verify concurrent operations with CountDownLatch
- Test rollback scenarios for file operations

## Common Development Tasks

### Profiling Performance
```bash
# Enable JFR (Java Flight Recorder)
java -XX:StartFlightRecording=filename=profile.jfr,duration=60s \
     -jar target/music-organizer-1.0-SNAPSHOT.jar E:\Music

# Analyze with JDK Mission Control
jmc profile.jfr
```

### Debugging Concurrent Issues
```bash
# Enable virtual thread pinning detection
-Djdk.tracePinnedThreads=full

# Monitor thread dumps
jcmd <pid> Thread.dump_to_file threads.txt
```

### Memory Analysis
```bash
# Heap dump on OutOfMemoryError
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./dumps

# Live heap analysis
jmap -histo:live <pid>
```

## Important Patterns

### Error Handling in Concurrent Code
- Always use try-with-resources for AutoCloseable
- Propagate exceptions through CompletableFuture.exceptionally()
- Log errors without stopping parallel processing

### Resource Management
- Virtual thread executors are AutoCloseable
- File handles released immediately after use
- Memory-mapped files for large file operations

## Dependencies

- **jaudiotagger 3.0.1**: Audio metadata reading
- **picocli 4.7.5**: Command-line interface
- **JUnit 5.10.1**: Testing framework

## Known Limitations

- MusicBrainz API rate limit: 1 request/second
- Windows path limit: 260 characters (use UNC paths for longer)
- Maximum concurrent files limited by OS file handles

## Development Instructions

- **IMPORTANT**: ALWAYS READ THE INSTRUCTIONS DEFINED IN C:\Users\caio\.claude/CLAUDE.md before excecuting any instruction

## Memory Markers

- **IMPORTANT! ALWAYS USE INTELLIJ MCP TOOLS!!**
- Important: proactively delegate tasks to agents based on the task description of the request, the description field in the subagent configurations and current context and available tools
- Important: For complex workflows, you can chain multiple subagents, such as this example: "> First use the code-analyzer subagent to find performance issues, then use the optimizer subagent to fix them"
- IMPORTANT: To encourage more proactive subagent use, include phrases like “use PROACTIVELY” or “MUST BE USED” in your subagent description field.
- IMPORTANT: DYNAMIC SUBAGENT SELECTION: Claude Code intelligently selects subagents based on context. Make your description fields specific and action-oriented for best results.