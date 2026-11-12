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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    /**
     * KJ-01: stopping the app inside the cleanup delay used to drop the scheduled
     * deletions and leave private keys on disk for good.
     */
    @Test
    void shutdownDeletesKeysWhoseTimedDeletionHasNotFiredYet() throws Exception {
        String first = fileUtils.newRequestDir();
        String second = fileUtils.newRequestDir();
        File secret = fileUtils.writeToTempFile(first, "id_ed25519", bytes("secret"), true);
        File pub = fileUtils.writeToTempFile(first, "id_ed25519.pub", bytes("public"), false);
        File other = fileUtils.writeToTempFile(second, "a_b_seckey_1234.asc", bytes("pgp"), true);

        long started = System.nanoTime();
        fileUtils.shutdown();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertFalse(secret.exists());
        assertFalse(pub.exists());
        assertFalse(other.exists());
        assertFalse(Files.exists(base.resolve(first)), "request directory removed");
        assertFalse(Files.exists(base.resolve(second)), "request directory removed");
        assertTrue(Files.isDirectory(base), "base directory is kept");
        assertTrue(elapsedMs < 2_000, "shutdown no longer waits for the timers: " + elapsedMs + " ms");
    }

    /** KJ-01: files that survived a hard kill are removed when the app starts again. */
    @Test
    void startupDeletesLeftoversFromPreviousRun() throws Exception {
        Path staleDir = base.resolve("0123456789abcdef0123456789abcdef");
        Files.createDirectories(staleDir);
        Path staleKey = Files.write(staleDir.resolve("id_ed25519"), bytes("left behind"));
        // not created by KeyJolt: must survive
        Path foreignFile = Files.write(base.resolve("notes.txt"), bytes("not ours"));
        Path foreignDir = Files.createDirectories(base.resolve("some-other-app"));
        Path foreignNested = Files.write(foreignDir.resolve("data.bin"), bytes("not ours either"));

        FileUtils restarted = new FileUtils();
        ReflectionTestUtils.setField(restarted, "tempDir", base.toString());
        ReflectionTestUtils.setField(restarted, "cleanupDelay", 600_000L);
        try {
            restarted.initTempDirectory();

            assertFalse(Files.exists(staleKey));
            assertFalse(Files.exists(staleDir));
            assertTrue(Files.isDirectory(base));
            assertEquals("not ours", Files.readString(foreignFile));
            assertEquals("not ours either", Files.readString(foreignNested));

            // the cleaned directory is immediately usable
            String token = restarted.newRequestDir();
            assertTrue(restarted.writeToTempFile(token, "id_ed25519", bytes("new"), true).isFile());
        } finally {
            restarted.shutdown();
        }
    }

    /** Cleanup must not follow a symlink out of the temp directory. */
    @Test
    void cleanupRemovesSymlinksWithoutTouchingTheirTargets() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path outsideDir = Files.createDirectories(parent.resolve("outside"));
        Path outsideFile = Files.write(outsideDir.resolve("keep.txt"), bytes("keep me"));

        String token = fileUtils.newRequestDir();
        Path fileLink = Files.createSymbolicLink(base.resolve(token).resolve("file-link"), outsideFile);
        Path dirLink = Files.createSymbolicLink(base.resolve(token).resolve("dir-link"), outsideDir);
        // a link that merely looks like a request directory is not entered
        Path fakeRequestDir = Files.createSymbolicLink(
                base.resolve("ffffffffffffffffffffffffffffffff"), outsideDir);

        assertEquals(0, fileUtils.cleanupTempDirectory());

        assertFalse(Files.exists(fileLink, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(dirLink, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(base.resolve(token)), "request directory removed");
        assertTrue(Files.isSymbolicLink(fakeRequestDir));
        assertEquals("keep me", Files.readString(outsideFile));
    }

    @Test
    void cleanupReportsHowManyFilesItDeleted() throws Exception {
        String token = fileUtils.newRequestDir();
        fileUtils.writeToTempFile(token, "one", bytes("1"), true);
        fileUtils.writeToTempFile(token, "two", bytes("2"), false);

        assertEquals(2, fileUtils.cleanupTempDirectory());
        assertEquals(0, fileUtils.cleanupTempDirectory());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
