#!/bin/bash

# Music Organizer Java 21 - Unix/Linux Shell Script
# Optimized for Java 21 with Virtual Threads, ZGC, and performance tuning

set -euo pipefail

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }

# Function to check Java version
check_java_version() {
    if ! command -v java &> /dev/null; then
        print_error "Java not found. Please install Java 21 or later."
        print_info "Download from: https://jdk.java.net/21/"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" -lt 21 ]]; then
        print_error "Java 21 or later is required. Found Java $JAVA_VERSION"
        print_info "Download from: https://jdk.java.net/21/"
        exit 1
    fi
    
    print_info "Using Java $JAVA_VERSION"
}

# Function to detect system capabilities
detect_system() {
    # Detect CPU cores
    if command -v nproc &> /dev/null; then
        CPU_CORES=$(nproc)
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        CPU_CORES=$(sysctl -n hw.ncpu)
    else
        CPU_CORES=4  # fallback
    fi
    
    # Detect available memory (in MB)
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        TOTAL_MEM=$(( $(sysctl -n hw.memsize) / 1024 / 1024 ))
    else
        TOTAL_MEM=8192  # fallback 8GB
    fi
    
    print_info "Detected: $CPU_CORES CPU cores, ${TOTAL_MEM}MB RAM"
}

# Function to calculate optimal JVM settings
calculate_jvm_settings() {
    # Calculate heap size (50-75% of available memory, but cap at reasonable limits)
    if [[ $TOTAL_MEM -gt 16384 ]]; then  # > 16GB
        MAX_HEAP="8g"
        MIN_HEAP="2g"
    elif [[ $TOTAL_MEM -gt 8192 ]]; then  # > 8GB
        MAX_HEAP="4g"
        MIN_HEAP="1g"
    elif [[ $TOTAL_MEM -gt 4096 ]]; then  # > 4GB
        MAX_HEAP="2g"
        MIN_HEAP="512m"
    else
        MAX_HEAP="1g"
        MIN_HEAP="256m"
    fi
    
    # Calculate virtual thread pool size based on CPU cores
    VTHREAD_PARALLELISM=$((CPU_CORES * 2))
    VTHREAD_MAX_POOL=$((CPU_CORES * 32))
    
    print_info "JVM Settings: Min Heap=$MIN_HEAP, Max Heap=$MAX_HEAP, VThread Pool=$VTHREAD_PARALLELISM"
}

# Main execution function
main() {
    print_info "Music Organizer Java 21 - Starting..."
    
    # System checks
    check_java_version
    detect_system
    calculate_jvm_settings
    
    # Application JAR file
    APP_JAR="target/music-organizer-1.0-SNAPSHOT.jar"
    
    # Check if JAR exists
    if [[ ! -f "$APP_JAR" ]]; then
        print_error "Application JAR not found: $APP_JAR"
        print_info "Please build the application first with: mvn clean package"
        exit 1
    fi
    
    # Create logs directory
    mkdir -p logs
    
    # Set log file with timestamp
    LOG_FILE="logs/music-organizer-$(date +%Y%m%d-%H%M%S).log"
    
    # Java 21 Virtual Thread Settings
    VIRTUAL_THREAD_OPTS="--enable-preview"
    VIRTUAL_THREAD_OPTS="$VIRTUAL_THREAD_OPTS -Djdk.virtualThreadScheduler.parallelism=$VTHREAD_PARALLELISM"
    VIRTUAL_THREAD_OPTS="$VIRTUAL_THREAD_OPTS -Djdk.virtualThreadScheduler.maxPoolSize=$VTHREAD_MAX_POOL"
    VIRTUAL_THREAD_OPTS="$VIRTUAL_THREAD_OPTS -Djdk.virtualThreadScheduler.minRunnable=1"
    
    # ZGC Garbage Collector Settings (optimized for Java 21)
    GC_OPTS="-XX:+UseZGC"
    GC_OPTS="$GC_OPTS -XX:+UnlockExperimentalVMOptions"
    GC_OPTS="$GC_OPTS -XX:ZCollectionInterval=1"
    GC_OPTS="$GC_OPTS -XX:ZAllocationSpikeTolerance=100"
    GC_OPTS="$GC_OPTS -XX:ZUncommitDelay=300"
    
    # Performance and Monitoring Options
    PERF_OPTS="-XX:+EnableDynamicAgentLoading"
    PERF_OPTS="$PERF_OPTS -XX:+FlightRecorder"
    PERF_OPTS="$PERF_OPTS -XX:+UseTransparentHugePages"
    
    # JIT Compiler Optimizations
    JIT_OPTS="-XX:+UseCompressedOops"
    JIT_OPTS="$JIT_OPTS -XX:+UseCompressedClassPointers"
    JIT_OPTS="$JIT_OPTS -XX:+OptimizeStringConcat"
    JIT_OPTS="$JIT_OPTS -XX:+DoEscapeAnalysis"
    
    # I/O and NIO Optimizations
    IO_OPTS="-Djava.awt.headless=true"
    IO_OPTS="$IO_OPTS -Djava.nio.channels.DefaultThreadPool.threadFactory=java.util.concurrent.ThreadPerTaskExecutor"
    IO_OPTS="$IO_OPTS -Djdk.nio.maxCachedBufferSize=262144"
    
    # Application-specific JVM options
    APP_OPTS="-Dfile.encoding=UTF-8"
    APP_OPTS="$APP_OPTS -Duser.timezone=UTC"
    APP_OPTS="$APP_OPTS -Djava.net.preferIPv4Stack=true"
    APP_OPTS="$APP_OPTS -Djava.security.egd=file:/dev/./urandom"
    
    # Security and Module System
    MODULE_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED"
    MODULE_OPTS="$MODULE_OPTS --add-opens java.base/java.nio=ALL-UNNAMED"
    MODULE_OPTS="$MODULE_OPTS --add-opens java.desktop/java.awt=ALL-UNNAMED"
    MODULE_OPTS="$MODULE_OPTS --add-opens java.base/java.util.concurrent=ALL-UNNAMED"
    
    # Debug options (uncomment for debugging)
    # DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    
    # Combine all JVM options
    JVM_OPTS="-Xms$MIN_HEAP -Xmx$MAX_HEAP -XX:MetaspaceSize=256m"
    JVM_OPTS="$JVM_OPTS $VIRTUAL_THREAD_OPTS"
    JVM_OPTS="$JVM_OPTS $GC_OPTS"
    JVM_OPTS="$JVM_OPTS $PERF_OPTS"
    JVM_OPTS="$JVM_OPTS $JIT_OPTS"
    JVM_OPTS="$JVM_OPTS $IO_OPTS"
    JVM_OPTS="$JVM_OPTS $APP_OPTS"
    JVM_OPTS="$JVM_OPTS $MODULE_OPTS"
    JVM_OPTS="$JVM_OPTS ${DEBUG_OPTS:-}"
    
    print_info "Starting application..."
    print_info "Log file: $LOG_FILE"
    echo
    
    # Run the application with output to both console and log file
    if command -v tee &> /dev/null; then
        java $JVM_OPTS -jar "$APP_JAR" "$@" 2>&1 | tee "$LOG_FILE"
        EXIT_CODE=${PIPESTATUS[0]}
    else
        # Fallback if tee is not available
        java $JVM_OPTS -jar "$APP_JAR" "$@" 2>&1
        EXIT_CODE=$?
        # Still try to capture output
        java $JVM_OPTS -jar "$APP_JAR" "$@" &> "$LOG_FILE" &
    fi
    
    # Check exit code
    if [[ $EXIT_CODE -eq 0 ]]; then
        print_success "Application completed successfully."
    else
        print_error "Application exited with error code: $EXIT_CODE"
        print_info "Check the log file: $LOG_FILE"
        exit $EXIT_CODE
    fi
}

# Signal handling for graceful shutdown
trap 'print_warn "Received interrupt signal. Shutting down..."; exit 130' INT TERM

# Help function
show_help() {
    echo "Music Organizer Java 21"
    echo "Usage: $0 [options] [music-directory]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -v, --version  Show version information"
    echo "  --debug        Enable debug mode"
    echo ""
    echo "Examples:"
    echo "  $0 /path/to/music"
    echo "  $0 --debug ~/Music"
    echo ""
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -v|--version)
            java -jar "$APP_JAR" --version 2>/dev/null || echo "Music Organizer Java 21"
            exit 0
            ;;
        --debug)
            DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
            print_info "Debug mode enabled - listening on port 5005"
            shift
            ;;
        *)
            break
            ;;
    esac
done

# Make sure the script is executable
if [[ ! -x "$0" ]]; then
    print_warn "Making script executable..."
    chmod +x "$0"
fi

# Run main function
main "$@"