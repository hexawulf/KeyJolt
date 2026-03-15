package com.keyjolt.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for secure file handling and cleanup
 */
@Component
public class FileUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @Value("${app.temp-dir:${java.io.tmpdir}/keyjolt}")
    private String tempDir;
    
    @Value("${app.key-cleanup-delay:300000}")
    private long cleanupDelay; // 5 minutes default
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @PostConstruct
    public void initTempDirectory() {
        try {
            Path tempPath = Paths.get(tempDir);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
            }
            logger.info("Temp directory ready: {}", tempPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory: " + tempDir, e);
        }
    }

    public File writeToTempFile(String filename, String content) throws IOException {
        return writeToTempFile(filename, content.getBytes("UTF-8"));
    }

    public File writeToTempFile(String filename, byte[] content) throws IOException {
        Path filePath = Paths.get(tempDir, filename);
        Files.write(filePath, content);

        File file = filePath.toFile();
        String absPath = filePath.toAbsolutePath().toString();
        logger.info("File written: {} (scheduled deletion in {}ms)", absPath, cleanupDelay);

        scheduleFileDeletion(file, absPath);
        return file;
    }
    
    
    /**
     * Schedule file deletion after delay
     */
    private void scheduleFileDeletion(File file, String originalPathForLogging) {
        scheduler.schedule(() -> {
            try {
                if (file.exists()) {
                    logger.info("Attempting to securely delete file: {}", originalPathForLogging);
                    // Overwrite file content before deletion for security
                    secureDelete(file);
                    logger.info("Successfully deleted file: {}", originalPathForLogging);
                } else {
                    logger.warn("File scheduled for deletion no longer exists: {}", originalPathForLogging);
                }
            } catch (Exception e) {
                logger.error("Failed to delete file: {} - {}", originalPathForLogging, e.getMessage(), e);
            }
        }, cleanupDelay, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Securely delete a file by overwriting its content
     */
    public void secureDelete(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        
        long length = file.length();
        
        // Overwrite with random data
        try (RandomAccessFile raf = new RandomAccessFile(file, "rws")) {
            byte[] data = new byte[1024];
            
            for (long pos = 0; pos < length; pos += data.length) {
                SECURE_RANDOM.nextBytes(data);
                raf.seek(pos);
                int writeLength = (int) Math.min(data.length, length - pos);
                raf.write(data, 0, writeLength);
            }
            
            raf.getFD().sync(); // Force write to disk
        }
        
        // Delete the file
        if (!file.delete()) {
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }
    
    /**
     * Get file size
     */
    public long getFileSize(File file) {
        return file.exists() ? file.length() : 0;
    }
    
    /**
     * Get the configured temporary directory path
     */
    public String getTempDir() {
        return tempDir;
    }
    
    /**
     * Clean up all files in temp directory
     */
    public void cleanupTempDirectory() {
        try {
            Path tempPath = Paths.get(tempDir);
            if (Files.exists(tempPath)) {
                Files.walk(tempPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            secureDelete(path.toFile());
                        } catch (IOException e) {
                            logger.error("Failed to delete file: {} - {}", path, e.getMessage(), e);
                        }
                    });
            }
        } catch (IOException e) {
            logger.error("Failed to cleanup temp directory: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Shutdown cleanup scheduler
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down FileUtils scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
