package com.musicorganizer.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.*;

/**
 * Thread-safe progress tracker supporting multiple concurrent operations
 * with real-time progress reporting and detailed statistics.
 */
public class ProgressTracker {
    
    public record OperationProgress(
        String operationId,
        String description,
        int totalItems,
        AtomicInteger completedItems,
        AtomicLong bytesProcessed,
        LocalDateTime startTime,
        LocalDateTime lastUpdateTime,
        OperationStatus status,
        Map<String, Object> metadata
    ) {
        public double getProgressPercentage() {
            return totalItems > 0 ? (double) completedItems.get() / totalItems * 100.0 : 0.0;
        }
        
        public Duration getElapsedTime() {
            return Duration.between(startTime, 
                status == OperationStatus.COMPLETED ? lastUpdateTime : LocalDateTime.now());
        }
        
        public Duration getEstimatedTimeRemaining() {
            if (completedItems.get() == 0) return Duration.ZERO;
            
            var elapsed = getElapsedTime();
            var completed = completedItems.get();
            var remaining = totalItems - completed;
            
            if (remaining <= 0) return Duration.ZERO;
            
            var avgTimePerItem = elapsed.dividedBy(completed);
            return avgTimePerItem.multipliedBy(remaining);
        }
        
        public double getItemsPerSecond() {
            var elapsed = getElapsedTime();
            return elapsed.toMillis() > 0 ? 
                (double) completedItems.get() / elapsed.toMillis() * 1000.0 : 0.0;
        }
    }
    
    public enum OperationStatus {
        NOT_STARTED, IN_PROGRESS, PAUSED, COMPLETED, FAILED, CANCELLED
    }
    
    public record ProgressSnapshot(
        String operationId,
        String description,
        double progressPercentage,
        int completedItems,
        int totalItems,
        long bytesProcessed,
        Duration elapsedTime,
        Duration estimatedTimeRemaining,
        double itemsPerSecond,
        OperationStatus status,
        Map<String, Object> metadata
    ) {}
    
    private final ConcurrentHashMap<String, OperationProgress> operations = new ConcurrentHashMap<>();
    private final List<Consumer<ProgressSnapshot>> progressListeners = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger nextOperationId = new AtomicInteger(1);
    
    /**
     * Start tracking a new operation
     */
    public String startOperation(String description, int totalItems) {
        return startOperation(description, totalItems, Map.of());
    }
    
    /**
     * Start tracking a new operation with custom ID
     */
    public String startOperation(String operationId, String description, int totalItems) {
        return startOperation(operationId, description, totalItems, Map.of());
    }
    
    /**
     * Start tracking a new operation with metadata
     */
    public String startOperation(String description, int totalItems, Map<String, Object> metadata) {
        var operationId = "op_" + nextOperationId.getAndIncrement();
        return startOperation(operationId, description, totalItems, metadata);
    }
    
    /**
     * Start tracking a new operation with custom ID and metadata
     */
    public String startOperation(String operationId, String description, int totalItems, 
                               Map<String, Object> metadata) {
        var now = LocalDateTime.now();
        var operation = new OperationProgress(
            operationId,
            description,
            totalItems,
            new AtomicInteger(0),
            new AtomicLong(0),
            now,
            now,
            OperationStatus.IN_PROGRESS,
            new ConcurrentHashMap<>(metadata)
        );
        
        operations.put(operationId, operation);
        notifyListeners(createSnapshot(operation));
        
        return operationId;
    }
    
    /**
     * Update progress for an operation
     */
    public void updateProgress(String operationId, int completedItems) {
        updateProgress(operationId, completedItems, 0L);
    }
    
    /**
     * Update progress with bytes processed
     */
    public void updateProgress(String operationId, int completedItems, long bytesProcessed) {
        var operation = operations.get(operationId);
        if (operation != null && operation.status() == OperationStatus.IN_PROGRESS) {
            operation.completedItems().set(completedItems);
            operation.bytesProcessed().addAndGet(bytesProcessed);
            
            // Update last update time through reflection or recreation
            var updatedOperation = new OperationProgress(
                operation.operationId(),
                operation.description(),
                operation.totalItems(),
                operation.completedItems(),
                operation.bytesProcessed(),
                operation.startTime(),
                LocalDateTime.now(),
                operation.status(),
                operation.metadata()
            );
            
            operations.put(operationId, updatedOperation);
            notifyListeners(createSnapshot(updatedOperation));
        }
    }
    
    /**
     * Increment progress by 1
     */
    public void incrementProgress(String operationId) {
        var operation = operations.get(operationId);
        if (operation != null) {
            var newCompleted = operation.completedItems().incrementAndGet();
            updateProgress(operationId, newCompleted);
        }
    }
    
    /**
     * Increment progress with bytes
     */
    public void incrementProgress(String operationId, long bytesProcessed) {
        var operation = operations.get(operationId);
        if (operation != null) {
            var newCompleted = operation.completedItems().incrementAndGet();
            updateProgress(operationId, newCompleted, bytesProcessed);
        }
    }
    
    /**
     * Complete an operation
     */
    public void completeOperation(String operationId) {
        setOperationStatus(operationId, OperationStatus.COMPLETED);
    }
    
    /**
     * Fail an operation
     */
    public void failOperation(String operationId) {
        setOperationStatus(operationId, OperationStatus.FAILED);
    }
    
    /**
     * Cancel an operation
     */
    public void cancelOperation(String operationId) {
        setOperationStatus(operationId, OperationStatus.CANCELLED);
    }
    
    /**
     * Pause an operation
     */
    public void pauseOperation(String operationId) {
        setOperationStatus(operationId, OperationStatus.PAUSED);
    }
    
    /**
     * Resume a paused operation
     */
    public void resumeOperation(String operationId) {
        setOperationStatus(operationId, OperationStatus.IN_PROGRESS);
    }
    
    private void setOperationStatus(String operationId, OperationStatus status) {
        var operation = operations.get(operationId);
        if (operation != null) {
            var updatedOperation = new OperationProgress(
                operation.operationId(),
                operation.description(),
                operation.totalItems(),
                operation.completedItems(),
                operation.bytesProcessed(),
                operation.startTime(),
                LocalDateTime.now(),
                status,
                operation.metadata()
            );
            
            operations.put(operationId, updatedOperation);
            notifyListeners(createSnapshot(updatedOperation));
        }
    }
    
    /**
     * Add metadata to an operation
     */
    public void addMetadata(String operationId, String key, Object value) {
        var operation = operations.get(operationId);
        if (operation != null) {
            operation.metadata().put(key, value);
            notifyListeners(createSnapshot(operation));
        }
    }
    
    /**
     * Get current progress snapshot
     */
    public Optional<ProgressSnapshot> getProgress(String operationId) {
        var operation = operations.get(operationId);
        return operation != null ? Optional.of(createSnapshot(operation)) : Optional.empty();
    }
    
    /**
     * Get all active operations
     */
    public List<ProgressSnapshot> getAllActiveOperations() {
        return operations.values().stream()
            .filter(op -> op.status() == OperationStatus.IN_PROGRESS || op.status() == OperationStatus.PAUSED)
            .map(this::createSnapshot)
            .sorted(Comparator.comparing(ProgressSnapshot::operationId))
            .toList();
    }
    
    /**
     * Get all operations (including completed)
     */
    public List<ProgressSnapshot> getAllOperations() {
        return operations.values().stream()
            .map(this::createSnapshot)
            .sorted(Comparator.comparing(ProgressSnapshot::operationId))
            .toList();
    }
    
    /**
     * Clear completed operations
     */
    public int clearCompletedOperations() {
        var completed = operations.entrySet().stream()
            .filter(entry -> {
                var status = entry.getValue().status();
                return status == OperationStatus.COMPLETED || 
                       status == OperationStatus.FAILED || 
                       status == OperationStatus.CANCELLED;
            })
            .map(Map.Entry::getKey)
            .toList();
        
        completed.forEach(operations::remove);
        return completed.size();
    }
    
    /**
     * Add progress listener
     */
    public void addProgressListener(Consumer<ProgressSnapshot> listener) {
        progressListeners.add(listener);
    }
    
    /**
     * Remove progress listener
     */
    public void removeProgressListener(Consumer<ProgressSnapshot> listener) {
        progressListeners.remove(listener);
    }
    
    /**
     * Create progress snapshot from operation
     */
    private ProgressSnapshot createSnapshot(OperationProgress operation) {
        return new ProgressSnapshot(
            operation.operationId(),
            operation.description(),
            operation.getProgressPercentage(),
            operation.completedItems().get(),
            operation.totalItems(),
            operation.bytesProcessed().get(),
            operation.getElapsedTime(),
            operation.getEstimatedTimeRemaining(),
            operation.getItemsPerSecond(),
            operation.status(),
            Map.copyOf(operation.metadata())
        );
    }
    
    /**
     * Notify all listeners
     */
    private void notifyListeners(ProgressSnapshot snapshot) {
        progressListeners.forEach(listener -> {
            try {
                listener.accept(snapshot);
            } catch (Exception e) {
                // Log error but don't let it affect other listeners
                System.err.println("Progress listener error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get overall progress summary
     */
    public record OverallProgress(
        int totalOperations,
        int activeOperations,
        int completedOperations,
        int failedOperations,
        double averageProgress,
        Duration totalElapsedTime,
        long totalBytesProcessed
    ) {}
    
    public OverallProgress getOverallProgress() {
        var allOps = operations.values();
        
        var totalOperations = allOps.size();
        var activeOperations = (int) allOps.stream()
            .filter(op -> op.status() == OperationStatus.IN_PROGRESS || op.status() == OperationStatus.PAUSED)
            .count();
        var completedOperations = (int) allOps.stream()
            .filter(op -> op.status() == OperationStatus.COMPLETED)
            .count();
        var failedOperations = (int) allOps.stream()
            .filter(op -> op.status() == OperationStatus.FAILED)
            .count();
        
        var averageProgress = allOps.stream()
            .mapToDouble(OperationProgress::getProgressPercentage)
            .average()
            .orElse(0.0);
        
        var totalElapsedTime = allOps.stream()
            .map(OperationProgress::getElapsedTime)
            .reduce(Duration.ZERO, Duration::plus);
        
        var totalBytesProcessed = allOps.stream()
            .mapToLong(op -> op.bytesProcessed().get())
            .sum();
        
        return new OverallProgress(
            totalOperations,
            activeOperations,
            completedOperations,
            failedOperations,
            averageProgress,
            totalElapsedTime,
            totalBytesProcessed
        );
    }
    
    /**
     * Format progress as a user-friendly string
     */
    public String formatProgress(String operationId) {
        return getProgress(operationId)
            .map(this::formatProgressSnapshot)
            .orElse("Operation not found: " + operationId);
    }
    
    private String formatProgressSnapshot(ProgressSnapshot snapshot) {
        return String.format(
            "%s: %.1f%% (%d/%d) - %.1f items/sec - ETA: %s - %s",
            snapshot.description(),
            snapshot.progressPercentage(),
            snapshot.completedItems(),
            snapshot.totalItems(),
            snapshot.itemsPerSecond(),
            formatDuration(snapshot.estimatedTimeRemaining()),
            snapshot.status()
        );
    }
    
    private String formatDuration(Duration duration) {
        if (duration.isZero()) return "0s";
        
        var hours = duration.toHours();
        var minutes = duration.toMinutesPart();
        var seconds = duration.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Create a console progress reporter
     */
    public Consumer<ProgressSnapshot> createConsoleReporter() {
        return snapshot -> {
            var progressBar = createProgressBar(snapshot.progressPercentage(), 30);
            System.out.printf("\r%s [%s] %.1f%% (%d/%d) - %.1f/sec - ETA: %s",
                snapshot.description(),
                progressBar,
                snapshot.progressPercentage(),
                snapshot.completedItems(),
                snapshot.totalItems(),
                snapshot.itemsPerSecond(),
                formatDuration(snapshot.estimatedTimeRemaining())
            );
            
            if (snapshot.status() == OperationStatus.COMPLETED) {
                System.out.println(" - COMPLETED");
            }
        };
    }
    
    private String createProgressBar(double percentage, int length) {
        var filled = (int) (percentage / 100.0 * length);
        var empty = length - filled;
        
        return "=".repeat(filled) + " ".repeat(empty);
    }
}