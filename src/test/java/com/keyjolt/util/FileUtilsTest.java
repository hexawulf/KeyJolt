package com.keyjolt.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

    @TempDir
    Path parent;

    private Path base;
    private FileUtils fileUtils;

    @BeforeEach
    void setUp() {
        base = parent.resolve("keyjolt");
        fileUtils = new FileUtils();
        ReflectionTestUtils.setField(fileUtils, "tempDir", base.toString());
        ReflectionTestUtils.setField(fileUtils, "cleanupDelay", 600_000L);
        fileUtils.initTempDirectory();
    }

    @AfterEach
    void tearDown() {
        fileUtils.shutdown();
    }

    /**
     * Regression test for v1.1.1: a tmp cleaner removed the (empty) base directory
     * while the app was running and every later request failed with NoSuchFileException.
     */
    @Test
    void newRequestDirRecreatesBaseDirRemovedAtRuntime() throws Exception {
        Files.delete(base);
        assertFalse(Files.exists(base));

        String token = fileUtils.newRequestDir();
        File written = fileUtils.writeToTempFile(token, "id_ed25519",
                "secret".getBytes(StandardCharsets.UTF_8), true);

        assertTrue(written.isFile());
        assertEquals(base.resolve(token), written.toPath().getParent());
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(base)));
            assertEquals("rwx------",
                    PosixFilePermissions.toString(Files.getPosixFilePermissions(base.resolve(token))));
        }
    }
}
