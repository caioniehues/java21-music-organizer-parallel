# Suggested Commands for Development

## Build Commands
```bash
# Standard build with tests
mvn clean test package

# Build without tests (faster)
mvn clean package -DskipTests

# Run specific test class
mvn test -Dtest=ParallelMusicScannerTest

# Run tests matching pattern
mvn test -Dtest=*Scanner*
```

## Run Commands
```bash
# Windows with batch script
run.bat E:\Music --scan --find-duplicates

# Direct Java execution with optimized settings
java -Xms512m -Xmx4g -XX:+UseZGC \
     --enable-preview \
     -Djdk.virtualThreadScheduler.parallelism=10 \
     -Djdk.virtualThreadScheduler.maxPoolSize=256 \
     -jar target/music-organizer-1.0-SNAPSHOT.jar E:\Music

# Development run with debugging
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/music-organizer-1.0-SNAPSHOT.jar --help
```

## Git Commands (Windows)
```bash
# Status check
git status

# Add all files
git add .

# Commit changes
git commit -m "message"

# View logs
git log --oneline -n 10
```

## Testing & Verification
```bash
# Run all tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Quick validation
mvn compile
```

## System Commands (Windows)
```bash
# List files
dir /b

# Navigate directories
cd path\to\directory

# Find files
dir /s /b *.java | findstr "pattern"

# View file content
type filename.txt
```

## Main Entry Point
`com.musicorganizer.MusicOrganizerCLI`