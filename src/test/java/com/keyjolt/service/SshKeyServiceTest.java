package com.keyjolt.service;

import com.keyjolt.model.KeyRequest;
import com.keyjolt.model.SshKeyRequest;
import com.keyjolt.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies generated SSH keys are real OpenSSH keys. When ssh-keygen is
 * installed, it is used as the reference parser (with and without passphrase).
 */
class SshKeyServiceTest {

    private static final Path SSH_KEYGEN = Paths.get("/usr/bin/ssh-keygen");

    @TempDir
    Path tempDir;

    private FileUtils fileUtils;
    private SshKeyService service;

    @BeforeEach
    void setUp() {
        fileUtils = new FileUtils();
        ReflectionTestUtils.setField(fileUtils, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(fileUtils, "cleanupDelay", 600_000L);
        fileUtils.initTempDirectory();
        service = new SshKeyService();
        ReflectionTestUtils.setField(service, "fileUtils", fileUtils);
    }

    @AfterEach
    void tearDown() {
        fileUtils.shutdown();
    }

    @ParameterizedTest(name = "{0} passphrase=''{1}''")
    @CsvSource({
        "ed25519, '',          id_ed25519, ssh-ed25519",
        "ed25519, Secret123!,  id_ed25519, ssh-ed25519",
        "rsa,     '',          id_rsa,     ssh-rsa",
        "rsa,     Secret123!,  id_rsa,     ssh-rsa"
    })
    void generatesValidOpenSshKeys(String type, String passphrase, String privName, String prefix) throws Exception {
        String dir = fileUtils.newRequestDir();
        SshKeyService.SshKeyPair pair = service.generateKeyPair(
                new SshKeyRequest(type, "zk@test", passphrase), dir);

        File priv = pair.getPrivateKeyFile();
        File pub = pair.getPublicKeyFile();
        assertEquals(privName, priv.getName());
        assertEquals(privName + ".pub", pub.getName());
        assertEquals(dir, priv.getParentFile().getName());

        String privText = Files.readString(priv.toPath(), StandardCharsets.UTF_8);
        assertTrue(privText.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"), privText);

        String pubLine = Files.readString(pub.toPath(), StandardCharsets.UTF_8).trim();
        assertEquals(pair.getPublicKeyContent(), pubLine);
        assertTrue(pubLine.startsWith(prefix + " "), pubLine);
        assertTrue(pubLine.endsWith(" zk@test"), pubLine);
        assertTrue(pair.getFingerprint().startsWith("SHA256:"), pair.getFingerprint());

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(priv.toPath())));
            assertEquals("rwx------", PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(priv.getParentFile().toPath())), "request dir must be 0700");
            assertEquals("rwx------", PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(tempDir)), "base temp dir must be 0700");
        }

        assumeTrue(Files.isExecutable(SSH_KEYGEN), "ssh-keygen not installed; skipping OpenSSH cross-check");

        ProcessResult derived = sshKeygen("-y", "-P", passphrase, "-f", priv.getAbsolutePath());
        assertEquals(0, derived.exitCode, derived.output);
        assertEquals(keyBody(pubLine), keyBody(derived.output.trim()));

        if (!passphrase.isEmpty()) {
            ProcessResult wrong = sshKeygen("-y", "-P", "wrong-passphrase", "-f", priv.getAbsolutePath());
            assertNotEquals(0, wrong.exitCode, "wrong passphrase must not decrypt the key");
        }
    }

    @Test
    void emptyCommentProducesTwoFieldPublicKey() throws Exception {
        SshKeyService.SshKeyPair pair = service.generateKeyPair(
                new SshKeyRequest("ed25519", "  ", null), fileUtils.newRequestDir());
        assertEquals(2, pair.getPublicKeyContent().split(" ").length, pair.getPublicKeyContent());
    }

    @Test
    void combinedModeUsesEmailAsComment() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 0, true, null);
        SshKeyService.SshKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        assertTrue(pair.getPublicKeyContent().startsWith("ssh-rsa "));
        assertTrue(pair.getPublicKeyContent().endsWith(" test@example.com"), pair.getPublicKeyContent());
        assertEquals("test@example.com_Test_User_ssh_priv.key", pair.getPrivateKeyFile().getName());
    }

    @Test
    void resolveDownloadPathRejectsBadInput() throws Exception {
        String dir = fileUtils.newRequestDir();
        assertNotNull(fileUtils.resolveDownloadPath(dir, "id_ed25519"));
        assertNull(fileUtils.resolveDownloadPath("../" + dir, "id_ed25519"));
        assertNull(fileUtils.resolveDownloadPath(dir, ".."));
        assertNull(fileUtils.resolveDownloadPath(dir, "../id_ed25519"));
        assertNull(fileUtils.resolveDownloadPath(dir.toUpperCase(), "id_ed25519"));
        assertNull(fileUtils.resolveDownloadPath(dir, "id ed25519"));
    }

    private static String keyBody(String line) {
        String[] parts = line.split(" ");
        return parts[0] + " " + parts[1];
    }

    private static ProcessResult sshKeygen(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = SSH_KEYGEN.toString();
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("ssh-keygen timed out");
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(p.exitValue(), out);
    }

    private record ProcessResult(int exitCode, String output) {}

    /** The OpenSSH container names its cipher in clear text right after the magic. */
    private static String opensshCipher(File privateKey) throws Exception {
        String body = Files.readString(privateKey.toPath(), StandardCharsets.UTF_8)
                .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(java.util.Base64.getDecoder().decode(body));
        buf.position("openssh-key-v1\0".length());
        byte[] name = new byte[buf.getInt()];
        buf.get(name);
        return new String(name, StandardCharsets.US_ASCII);
    }

    /**
     * KJ-02: in combined mode the password protected only the PGP key; the SSH
     * private key was written in clear.
     */
    @Test
    void combinedModeEncryptsSshKeyWithThePgpPassword() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 0, true, "Secret123!");
        SshKeyService.SshKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        File priv = pair.getPrivateKeyFile();

        assertEquals("aes256-ctr", opensshCipher(priv));

        assumeTrue(Files.isExecutable(SSH_KEYGEN), "ssh-keygen not installed; skipping OpenSSH cross-check");
        ProcessResult right = sshKeygen("-y", "-P", "Secret123!", "-f", priv.getAbsolutePath());
        assertEquals(0, right.exitCode, right.output);
        assertEquals(keyBody(pair.getPublicKeyContent()), keyBody(right.output.trim()));
        assertNotEquals(0, sshKeygen("-y", "-P", "", "-f", priv.getAbsolutePath()).exitCode,
                "the key must not open without the password");
    }

    @ParameterizedTest(name = "password=[{0}]")
    @CsvSource(value = {"NULL", "''"}, nullValues = "NULL")
    void combinedModeWithoutPasswordStaysUnencrypted(String password) throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 0, true, password);
        SshKeyService.SshKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        assertEquals("none", opensshCipher(pair.getPrivateKeyFile()));
    }
}
