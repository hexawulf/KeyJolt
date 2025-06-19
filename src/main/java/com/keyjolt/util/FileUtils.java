package com.keyjolt.util;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for secure file handling and cleanup
 */
@Component
public class FileUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    
    @Value("${app.temp-dir:${java.io.tmpdir}/keyjolt}")
    private String tempDir;
    
    @Value("${app.key-cleanup-delay:300000}")
    private long cleanupDelay; // 5 minutes default
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * Initialize temp directory
     */
    public void initTempDirectory() {
        try {
            Path tempPath = Paths.get(tempDir);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory: " + tempDir, e);
        }
    }
    
    /**
     * Write content to a temporary file
     */
    public File writeToTempFile(String filename, String content) throws IOException {
        initTempDirectory();
        
        Path filePath = Paths.get(tempDir, filename);
        Files.write(filePath, content.getBytes("UTF-8"));
        
        File file = filePath.toFile();
        
        // Schedule automatic deletion
        scheduleFileDeletion(file);
        
        return file;
    }
    
    /**
     * Write bytes to a temporary file
     */
    public File writeToTempFile(String filename, byte[] content) throws IOException {
        initTempDirectory();
        
        Path filePath = Paths.get(tempDir, filename);
        Files.write(filePath, content);
        
        File file = filePath.toFile();
        
        // Schedule automatic deletion
        scheduleFileDeletion(file);
        
        return file;
    }
    
    /**
     * Read file content as string
     */
    public String readFileAsString(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8");
    }
    
    /**
     * Read file content as bytes
     */
    public byte[] readFileAsBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }
    
    /**
     * Schedule file deletion after delay
     */
    private void scheduleFileDeletion(File file) {
        scheduler.schedule(() -> {
            try {
                if (file.exists()) {
                    // Overwrite file content before deletion for security
                    secureDelete(file);
                }
            } catch (Exception e) {
                logger.error("Failed to delete file: {} - {}", file.getAbsolutePath(), e.getMessage(), e);
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
            new java.security.SecureRandom().nextBytes(data);
            
            for (long pos = 0; pos < length; pos += data.length) {
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
     * Check if file exists in temp directory
     */
    public boolean fileExists(String filename) {
        Path filePath = Paths.get(tempDir, filename);
        return Files.exists(filePath);
    }
    
    /**
     * Get file from temp directory
     */
    public File getFile(String filename) {
        Path filePath = Paths.get(tempDir, filename);
        return filePath.toFile();
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
