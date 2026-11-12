package com.keyjolt.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Utility class for secure file handling and cleanup.
 *
 * Every generation request gets its own directory named by a random 128-bit token,
 * so download URLs are unguessable and concurrent requests never share a filename.
 */
@Component
public class FileUtils {

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[a-f0-9]{32}$");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^[A-Za-z0-9@._+-]{1,200}$");
    private static final Set<PosixFilePermission> OWNER_RW = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_RWX = PosixFilePermissions.fromString("rwx------");

    @Value("${app.temp-dir:${java.io.tmpdir}/keyjolt}")
    private String tempDir;

    @Value("${app.key-cleanup-delay:300000}")
    private long cleanupDelay; // 5 minutes default

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void initTempDirectory() {
        try {
            Path tempPath = ensureBaseDir();
            // Request tokens are secrets: other local users must not be able to list them
            if (isPosix()) {
                Files.setPosixFilePermissions(tempPath, OWNER_RWX);
            }
            logger.info("Temp directory ready: {}", tempPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory: " + tempDir, e);
        }
    }

    /**
     * Make sure the base temp directory exists (owner-only) and return it.
     *
     * The directory usually lives under /tmp and is empty between requests, so
     * tmp cleaners that prune empty directories remove it while the app is running.
     * It is therefore re-checked on every request instead of only at startup.
     */
    private Path ensureBaseDir() throws IOException {
        Path base = basePath();
        if (!Files.isDirectory(base)) {
            Files.createDirectories(base);
            if (isPosix()) {
                Files.setPosixFilePermissions(base, OWNER_RWX);
            }
            logger.warn("Temp directory {} was missing; recreated it", base);
        }
        return base;
    }

    /**
     * Create a fresh per-request directory and return its token.
     */
    public String newRequestDir() throws IOException {
        byte[] raw = new byte[16];
        SECURE_RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        Path dir = ensureBaseDir().resolve(token);
        if (isPosix()) {
            Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(OWNER_RWX));
            Files.setPosixFilePermissions(dir, OWNER_RWX); // not narrowed by umask, but be explicit
        } else {
            Files.createDirectory(dir);
        }
        return token;
    }

    private static boolean isPosix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /**
     * Write content to {tempDir}/{requestDir}/{filename} and schedule secure deletion.
     * Secret files are created owner-read/write only where the filesystem supports POSIX.
     */
    public File writeToTempFile(String requestDir, String filename, byte[] content, boolean secret)
            throws IOException {
        Path filePath = resolveDownloadPath(requestDir, filename);
        if (filePath == null) {
            throw new IOException("Invalid request directory or filename");
        }
        Path parent = filePath.getParent();
        if (parent == null) {
            throw new IOException("Invalid request directory");
        }
        Files.createDirectories(parent);

        if (secret && isPosix()) {
            Files.deleteIfExists(filePath);
            Files.createFile(filePath, PosixFilePermissions.asFileAttribute(OWNER_RW));
        }
        Files.write(filePath, content);

        File file = filePath.toFile();
        String absPath = filePath.toAbsolutePath().toString();
        logger.info("File written: {} (scheduled deletion in {}ms)", absPath, cleanupDelay);

        scheduleFileDeletion(file, absPath);
        return file;
    }

    /**
     * Resolve a download path from untrusted input. Returns null if either part is invalid
     * or the result would escape the temp directory.
     */
    public Path resolveDownloadPath(String requestDir, String filename) {
        if (requestDir == null || filename == null
                || !TOKEN_PATTERN.matcher(requestDir).matches()
                || !FILENAME_PATTERN.matcher(filename).matches()
                || filename.equals(".") || filename.equals("..")) {
            return null;
        }
        Path base = basePath();
        Path dir = base.resolve(requestDir).normalize();
        Path file = dir.resolve(filename).normalize();
        if (!base.equals(dir.getParent()) || !dir.equals(file.getParent())) {
            return null;
        }
        return file;
    }

    private Path basePath() {
        return Paths.get(tempDir).toAbsolutePath().normalize();
    }

    /**
     * Schedule file deletion after delay; removes the request directory once empty.
     */
    private void scheduleFileDeletion(File file, String originalPathForLogging) {
        scheduler.schedule(() -> {
            try {
                if (file.exists()) {
                    logger.info("Attempting to securely delete file: {}", originalPathForLogging);
                    secureDelete(file);
                    logger.info("Successfully deleted file: {}", originalPathForLogging);
                } else {
                    logger.warn("File scheduled for deletion no longer exists: {}", originalPathForLogging);
                }
                removeEmptyRequestDir(file.toPath().getParent());
            } catch (Exception e) {
                logger.error("Failed to delete file: {} - {}", originalPathForLogging, e.getMessage(), e);
            }
        }, cleanupDelay, TimeUnit.MILLISECONDS);
    }

    private void removeEmptyRequestDir(Path dir) {
        if (dir == null) {
            return;
        }
        Path normalized = dir.toAbsolutePath().normalize();
        if (normalized.equals(basePath()) || !basePath().equals(normalized.getParent())) {
            return;
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (DirectoryNotEmptyException ignored) {
            // other files from this request are still pending deletion
        } catch (IOException e) {
            logger.warn("Failed to remove request directory {}: {}", normalized, e.getMessage());
        }
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
                try (java.util.stream.Stream<Path> paths = Files.walk(tempPath)) {
                    paths.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                secureDelete(path.toFile());
                            } catch (IOException e) {
                                logger.error("Failed to delete file: {} - {}", path, e.getMessage(), e);
                            }
                        });
                }
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
