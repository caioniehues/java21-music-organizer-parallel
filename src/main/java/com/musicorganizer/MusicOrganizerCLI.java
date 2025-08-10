package com.musicorganizer;

import com.musicorganizer.model.*;
import com.musicorganizer.processor.ConcurrentDuplicateFinder;
import com.musicorganizer.scanner.ParallelMusicScanner;
import com.musicorganizer.service.ServiceConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Main CLI application for the Music Organizer using Java 21 features.
 * Uses PicoCLI for command-line parsing and virtual threads for performance.
 */
@Command(
    name = "music-organizer",
    description = "High-performance music library organizer with duplicate detection",
    version = "1.0.0",
    mixinStandardHelpOptions = true
)
public class MusicOrganizerCLI implements Callable<Integer> {
    
    @Parameters(
        index = "0",
        description = "Path to the music directory to scan",
        defaultValue = "."
    )
    private String musicPath;
    
    @Option(
        names = {"-c", "--checksums"},
        description = "Calculate file checksums for exact duplicate detection (default: true)",
        defaultValue = "true"
    )
    private boolean calculateChecksums;
    
    @Option(
        names = {"-m", "--metadata"},
        description = "Use metadata comparison for finding similar tracks (default: true)",
        defaultValue = "true"
    )
    private boolean useMetadataComparison;
    
    @Option(
        names = {"-t", "--threads"},
        description = "Maximum concurrent files to process (default: 1000)",
        defaultValue = "1000"
    )
    private int maxConcurrentFiles;
    
    @Option(
        names = {"-s", "--similarity"},
        description = "Similarity threshold for metadata matching (0.0-1.0, default: 0.85)",
        defaultValue = "0.85"
    )
    private double similarityThreshold;
    
    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose output"
    )
    private boolean verbose;
    
    @Option(
        names = {"--deep-scan"},
        description = "Perform deep metadata extraction (slower but more thorough)"
    )
    private boolean deepScan;
    
    @Option(
        names = {"--export"},
        description = "Export results to JSON file"
    )
    private String exportPath;
    
    @Option(
        names = {"--format"},
        description = "Output format: text, json, csv (default: text)",
        defaultValue = "text"
    )
    private String outputFormat;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MusicOrganizerCLI()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        try {
            Path scanPath = Paths.get(musicPath).toAbsolutePath();
            
            printHeader();
            printConfiguration(scanPath);
            
            // Create scanner with configuration using dependency injection
            ServiceConfiguration serviceConfig = ServiceConfiguration.create();
            try (ParallelMusicScanner scanner = serviceConfig.createParallelMusicScanner(
                maxConcurrentFiles, calculateChecksums, deepScan)) {
                
                // Perform scan
                ScanResult scanResult = scanner.scanDirectory(scanPath);
                
                // Process results using pattern matching
                return scanResult.match(
                    this::handleSuccessfulScan,
                    this::handleFailedScan,
                    this::handlePartialScan
                );
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
    
    /**
     * Handles successful scan results.
     */
    private Integer handleSuccessfulScan(ScanResult.Success success) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SCAN COMPLETED SUCCESSFULLY");
        System.out.println("=".repeat(60));
        
        printScanSummary(success);
        
        if (!success.audioFiles().isEmpty()) {
            // Perform duplicate analysis if requested
            if (calculateChecksums || useMetadataComparison) {
                performDuplicateAnalysis(success.audioFiles());
            }
            
            // Export results if requested
            if (exportPath != null) {
                exportResults(success);
            }
        }
        
        return 0; // Success
    }
    
    /**
     * Handles failed scan results.
     */
    private Integer handleFailedScan(ScanResult.Failure failure) {
        System.err.println("\n" + "=".repeat(60));
        System.err.println("SCAN FAILED");
        System.err.println("=".repeat(60));
        
        System.err.println("Error: " + failure.errorMessage());
        System.err.println("Duration: " + formatDuration(failure.getScanDuration()));
        
        if (!failure.failedPaths().isEmpty()) {
            System.err.println("Failed paths: " + failure.failedPaths().size());
            if (verbose) {
                failure.failedPaths().forEach(path -> 
                    System.err.println("  - " + path));
            }
        }
        
        if (verbose && failure.cause() != null) {
            System.err.println("\nStack trace:");
            failure.cause().printStackTrace();
        }
        
        return 1; // Failure
    }
    
    /**
     * Handles partial scan results.
     */
    private Integer handlePartialScan(ScanResult.Partial partial) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SCAN COMPLETED WITH WARNINGS");
        System.out.println("=".repeat(60));
        
        System.out.printf("Success Rate: %.1f%%%n", partial.getSuccessRate());
        System.out.println("Duration: " + formatDuration(partial.getScanDuration()));
        
        printFileTypeSummary(partial.fileTypeStats());
        
        System.out.printf("%nWarnings:%n");
        System.out.printf("  Failed to process %d files%n", partial.failedPaths().size());
        
        if (verbose && !partial.failedPaths().isEmpty()) {
            System.out.println("\nFailed files:");
            partial.failedPaths().forEach(path -> 
                System.out.println("  - " + path));
        }
        
        // Still perform duplicate analysis on successful files
        if (!partial.audioFiles().isEmpty() && (calculateChecksums || useMetadataComparison)) {
            performDuplicateAnalysis(partial.audioFiles());
        }
        
        return partial.failedPaths().isEmpty() ? 0 : 2; // Warning exit code
    }
    
    /**
     * Performs duplicate analysis on audio files.
     */
    private void performDuplicateAnalysis(List<AudioFile> audioFiles) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DUPLICATE ANALYSIS");
        System.out.println("=".repeat(60));
        
        try (ConcurrentDuplicateFinder duplicateFinder = new ConcurrentDuplicateFinder(
            useMetadataComparison, similarityThreshold)) {
            
            var analysisResult = duplicateFinder.findDuplicatesAsync(audioFiles).join();
            
            // Print results
            System.out.println(analysisResult.getSummary());
            
            if (verbose) {
                printDetailedDuplicateInfo(analysisResult);
            }
            
        } catch (Exception e) {
            System.err.println("Error during duplicate analysis: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Prints detailed duplicate information.
     */
    private void printDetailedDuplicateInfo(ConcurrentDuplicateFinder.DuplicateAnalysisResult result) {
        System.out.println("\nDetailed Duplicate Information:");
        System.out.println("-".repeat(40));
        
        // Exact duplicates
        if (!result.exactDuplicates().isEmpty()) {
            System.out.println("\nExact Duplicates:");
            result.exactDuplicates().forEach(duplicate -> {
                System.out.println("\n" + duplicate.getSummary());
                if (verbose) {
                    duplicate.duplicates().forEach(file -> 
                        System.out.println("  - " + file.path()));
                }
            });
        }
        
        // Metadata duplicates
        if (!result.metadataDuplicates().isEmpty()) {
            System.out.println("\nMetadata-based Duplicates:");
            result.metadataDuplicates().forEach(duplicate -> {
                System.out.println("\n" + duplicate.getSummary());
                if (verbose) {
                    duplicate.duplicates().forEach(file -> 
                        System.out.println("  - " + file.path()));
                }
            });
        }
    }
    
    /**
     * Exports scan results to file.
     */
    private void exportResults(ScanResult.Success success) {
        try {
            System.out.printf("%nExporting results to: %s%n", exportPath);
            
            String content = switch (outputFormat.toLowerCase()) {
                case "json" -> exportToJson(success);
                case "csv" -> exportToCsv(success);
                default -> exportToText(success);
            };
            
            java.nio.file.Files.writeString(Paths.get(exportPath), content);
            System.out.println("Export completed successfully.");
            
        } catch (Exception e) {
            System.err.println("Failed to export results: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Exports results to JSON format using text blocks.
     */
    private String exportToJson(ScanResult.Success success) {
        StringBuilder json = new StringBuilder();
        json.append("""
            {
              "scan_results": {
                "timestamp": "%s",
                "duration": "%s",
                "total_files": %d,
                "duplicates": %d,
                "file_types": {
            """.formatted(
                success.getScanTime(),
                formatDuration(success.getScanDuration()),
                success.getTotalFiles(),
                success.getTotalDuplicates()
            ));
        
        // Add file type statistics
        success.fileTypeStats().forEach((ext, count) -> 
            json.append(String.format("      \"%s\": %d,%n", ext, count)));
        
        json.append("""
                },
                "files": [
            """);
        
        // Add file information
        for (int i = 0; i < success.audioFiles().size(); i++) {
            AudioFile file = success.audioFiles().get(i);
            json.append(String.format("""
                    {
                      "path": "%s",
                      "size": %d,
                      "extension": "%s",
                      "checksum": "%s"
                    }%s
                """, 
                file.path().toString().replace("\\", "\\\\"),
                file.size(),
                file.getExtension(),
                file.checksum() != null ? file.checksum() : "",
                i < success.audioFiles().size() - 1 ? "," : ""
            ));
        }
        
        json.append("""
                ]
              }
            }
            """);
        
        return json.toString();
    }
    
    /**
     * Exports results to CSV format.
     */
    private String exportToCsv(ScanResult.Success success) {
        StringBuilder csv = new StringBuilder();
        csv.append("Path,Size,Extension,Checksum,Title,Artist,Album,Year\n");
        
        for (AudioFile file : success.audioFiles()) {
            csv.append(String.format("\"%s\",%d,%s,\"%s\",\"%s\",\"%s\",\"%s\",%s%n",
                file.path().toString().replace("\"", "\"\""),
                file.size(),
                file.getExtension(),
                file.checksum() != null ? file.checksum() : "",
                file.metadata().title().orElse(""),
                file.metadata().artist().orElse(""),
                file.metadata().album().orElse(""),
                file.metadata().year().map(String::valueOf).orElse("")
            ));
        }
        
        return csv.toString();
    }
    
    /**
     * Exports results to text format.
     */
    private String exportToText(ScanResult.Success success) {
        StringBuilder text = new StringBuilder();
        
        text.append("""
            Music Organizer Scan Results
            ============================
            
            """);
            
        text.append(success.getSummary());
        
        text.append("\n\nFile Listing:\n");
        text.append("-".repeat(40) + "\n");
        
        for (AudioFile file : success.audioFiles()) {
            text.append(String.format("%s (%s)%n", 
                file.path(), 
                formatFileSize(file.size())
            ));
        }
        
        return text.toString();
    }
    
    /**
     * Prints application header.
     */
    private void printHeader() {
        System.out.println("""
            ┌─────────────────────────────────────────────────────────────┐
            │                     Music Organizer                         │
            │                   Powered by Java 21                       │
            │                    Virtual Threads                         │
            └─────────────────────────────────────────────────────────────┘
            """);
    }
    
    /**
     * Prints scan configuration.
     */
    private void printConfiguration(Path scanPath) {
        System.out.printf("Configuration:%n");
        System.out.printf("  Scan Path: %s%n", scanPath);
        System.out.printf("  Calculate Checksums: %s%n", calculateChecksums);
        System.out.printf("  Metadata Comparison: %s%n", useMetadataComparison);
        System.out.printf("  Max Concurrent Files: %d%n", maxConcurrentFiles);
        System.out.printf("  Similarity Threshold: %.2f%n", similarityThreshold);
        System.out.printf("  Deep Scan: %s%n", deepScan);
        System.out.printf("  Available Processors: %d%n", Runtime.getRuntime().availableProcessors());
        System.out.println();
    }
    
    /**
     * Prints scan summary.
     */
    private void printScanSummary(ScanResult.Success success) {
        System.out.println("Scan Summary:");
        System.out.println("-".repeat(20));
        System.out.printf("  Files Found: %d%n", success.getTotalFiles());
        System.out.printf("  Scan Duration: %s%n", formatDuration(success.getScanDuration()));
        System.out.printf("  Total Size: %s%n", success.statistics().getFormattedTotalSize());
        
        if (success.getTotalDuplicates() > 0) {
            System.out.printf("  Duplicate Files: %d%n", success.getTotalDuplicates());
            System.out.printf("  Wasted Space: %s%n", formatFileSize(success.getTotalWastedSpace()));
        }
        
        printFileTypeSummary(success.fileTypeStats());
    }
    
    /**
     * Prints file type statistics.
     */
    private void printFileTypeSummary(java.util.Map<String, Integer> fileTypeStats) {
        if (!fileTypeStats.isEmpty()) {
            System.out.println("\nFile Types:");
            fileTypeStats.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> 
                    System.out.printf("  %s: %d files%n", 
                        entry.getKey().toUpperCase(), entry.getValue()));
        }
    }
    
    /**
     * Formats file size in human-readable format.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }
    
    /**
     * Formats duration in human-readable format.
     */
    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return String.format("%dm %02ds", minutes, seconds);
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format("%dh %02dm %02ds", hours, minutes, seconds);
    }
}