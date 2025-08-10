# Task Completion Checklist

## After Writing/Modifying Code

### 1. Compile Check
```bash
mvn compile
```

### 2. Run Tests
```bash
# Run related tests
mvn test -Dtest=<TestClassName>

# Run all tests if changes affect multiple components
mvn test
```

### 3. Code Quality Checks
- Verify virtual thread usage for I/O operations
- Check AutoCloseable implementation
- Ensure proper exception handling
- Validate resource cleanup

### 4. Performance Validation
- Check memory usage patterns
- Verify concurrent execution
- Monitor thread pool behavior
- Validate timeout constraints

### 5. Documentation Updates
- Update JavaDoc if public APIs changed
- Update README if features added
- Update CLAUDE.md if development patterns changed

## Before Committing

### 1. Full Build
```bash
mvn clean package
```

### 2. Integration Test
```bash
# Test with sample data
run.bat test-data --scan --find-duplicates --dry-run
```

### 3. Git Operations
```bash
git status
git diff
git add .
git commit -m "descriptive message"
```

## Critical Checks
- ✅ All tests passing
- ✅ No compilation warnings
- ✅ Virtual threads used for I/O
- ✅ Resources properly closed
- ✅ Error handling in place
- ✅ Performance targets met
- ✅ Thread safety verified