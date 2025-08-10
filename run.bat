@echo off
REM Music Organizer Java 21 - Windows Batch Script
REM Optimized for Java 21 with Virtual Threads, ZGC, and performance tuning

setlocal

REM Check if Java 21 is available
java -version 2>nul | findstr "21\." >nul
if errorlevel 1 (
    echo ERROR: Java 21 not found. Please install Java 21 or later.
    echo Download from: https://jdk.java.net/21/
    pause
    exit /b 1
)

REM Set application directory
set APP_DIR=%~dp0
cd /d "%APP_DIR%"

REM JVM Memory Settings (Increased for large file processing)
set MIN_HEAP=2g
set MAX_HEAP=12g
set METASPACE=512m
set MAX_DIRECT_MEMORY=4g

REM Java 21 Virtual Thread Settings (Increased for large file processing)
set VIRTUAL_THREAD_OPTS=--enable-preview -Djdk.virtualThreadScheduler.parallelism=20 -Djdk.virtualThreadScheduler.maxPoolSize=5000

REM ZGC Garbage Collector Settings (Java 21 optimized for large heaps)
set GC_OPTS=-XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:-ZGenerational -XX:ZCollectionInterval=1 -XX:ZAllocationSpikeTolerance=100 -XX:ConcGCThreads=4 -XX:MaxGCPauseMillis=50

REM Performance and Monitoring Options
set PERF_OPTS=-XX:+UseTransparentHugePages -XX:+EnableDynamicAgentLoading -XX:+FlightRecorder

REM JIT Compiler Optimizations
set JIT_OPTS=-XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+OptimizeStringConcat

REM I/O and NIO Optimizations
set IO_OPTS=-Djava.nio.file.spi.DefaultFileSystemProvider=sun.nio.fs.WindowsFileSystemProvider -Djava.awt.headless=true

REM Application-specific JVM options
set APP_OPTS=-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.net.preferIPv4Stack=true

REM Security and Module System
set MODULE_OPTS=--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.desktop/java.awt=ALL-UNNAMED

REM Debug options (uncomment for debugging)
REM set DEBUG_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

REM Combine all JVM options
set JVM_OPTS=-Xms%MIN_HEAP% -Xmx%MAX_HEAP% -XX:MetaspaceSize=%METASPACE% -XX:MaxDirectMemorySize=%MAX_DIRECT_MEMORY% %VIRTUAL_THREAD_OPTS% %GC_OPTS% %PERF_OPTS% %JIT_OPTS% %IO_OPTS% %APP_OPTS% %MODULE_OPTS% %DEBUG_OPTS%

REM Application JAR file
set APP_JAR=target\music-organizer-1.0-SNAPSHOT.jar

REM Check if JAR exists
if not exist "%APP_JAR%" (
    echo ERROR: Application JAR not found: %APP_JAR%
    echo Please build the application first with: mvn clean package
    pause
    exit /b 1
)

REM Create logs directory
if not exist "logs" mkdir logs

REM Set log file with timestamp
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /format:list') do set datetime=%%I
set LOG_FILE=logs\music-organizer-%datetime:~0,8%-%datetime:~8,6%.log

echo Starting Music Organizer with Java 21...
echo JVM Options: %JVM_OPTS%
echo Log file: %LOG_FILE%
echo.

REM Run the application
java %JVM_OPTS% -jar "%APP_JAR%" %* 2>&1 | tee "%LOG_FILE%"

REM Check exit code
if errorlevel 1 (
    echo.
    echo Application exited with error code: %errorlevel%
    echo Check the log file: %LOG_FILE%
    pause
    exit /b %errorlevel%
) else (
    echo.
    echo Application completed successfully.
)

endlocal