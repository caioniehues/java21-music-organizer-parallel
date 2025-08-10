# Code Style and Conventions

## Java 21 Features Used
- Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`)
- Pattern Matching (switch expressions)
- Records for data classes
- Sealed classes for command hierarchies
- Text blocks for multi-line strings
- Preview features enabled (`--enable-preview`)

## Naming Conventions
- **Classes**: PascalCase (e.g., `ParallelMusicScanner`)
- **Methods**: camelCase (e.g., `findDuplicates`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `SUPPORTED_EXTENSIONS`)
- **Fields**: camelCase with descriptive names
- **Test Methods**: snake_case with `should` prefix (e.g., `shouldFindExactDuplicates`)

## Package Structure
```
com.musicorganizer
├── model/          - Data models (AudioFile, AudioMetadata, etc.)
├── scanner/        - File scanning components
├── processor/      - Processing logic (duplicate finding, metadata)
├── organizer/      - File organization logic
├── service/        - External services (MusicBrainz, HTTP)
├── util/           - Utility classes (ProgressTracker, factories)
└── validator/      - Collection validation
```

## Design Patterns
- **Builder Pattern**: Used in model classes
- **Strategy Pattern**: Duplicate detection strategies
- **Command Pattern**: PicoCLI command structure
- **Repository Pattern**: File system abstraction
- **Factory Pattern**: ExecutorServiceFactory, HttpClientProvider
- **Interface-based DI**: All major services use interfaces

## Testing Conventions
- JUnit 5 with nested test classes (`@Nested`)
- Display names for all tests (`@DisplayName`)
- Mock dependencies with Mockito
- Use `jimfs` for file system mocking
- Timeout annotations for concurrent tests
- Comprehensive edge case coverage

## Concurrency Patterns
- Always use virtual threads for I/O operations
- Semaphore-based rate limiting
- CompletableFuture for async operations
- AutoCloseable for resource management
- Proper executor shutdown in close()

## Error Handling
- Try-with-resources for AutoCloseable
- CompletableFuture.exceptionally() for async errors
- Log errors without stopping parallel processing
- Graceful degradation on failures