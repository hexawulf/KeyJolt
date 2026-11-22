package com.keyjolt.service;

import com.keyjolt.model.KeyRequest;
import com.keyjolt.util.FileUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that generated PGP keys are real, parseable OpenPGP key rings with
 * the advertised properties, and that the optional password really protects
 * the secret keys. RSA-2048 keeps the suite fast on a Raspberry Pi; the code
 * path is the same for 3072 and 4096.
 */
class PgpKeyServiceTest {

    private static final String PASSWORD = "Correct-Horse-9";
    private static final BcKeyFingerprintCalculator FP = new BcKeyFingerprintCalculator();

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @TempDir
    Path tempDir;

    private FileUtils fileUtils;
    private PgpKeyService service;
    private SshKeyService sshService;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        fileUtils = new FileUtils();
        ReflectionTestUtils.setField(fileUtils, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(fileUtils, "cleanupDelay", 600_000L);
        fileUtils.initTempDirectory();
        service = new PgpKeyService();
        ReflectionTestUtils.setField(service, "fileUtils", fileUtils);
        sshService = new SshKeyService();
        ReflectionTestUtils.setField(sshService, "fileUtils", fileUtils);
    }

    @AfterEach
    void tearDown() {
        fileUtils.shutdown();
    }

    // ---------------------------------------------------------------- generation

    @Test
    void generatesArmoredKeyRingsWithUserIdKeyIdAndFiles() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, false, null);
        String dir = fileUtils.newRequestDir();

        PgpKeyService.PgpKeyPair pair = service.generateKeyPair(request, dir);

        String pub = pair.getPublicKeyContent();
        String sec = pair.getPrivateKeyContent();
        assertTrue(pub.startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----"), pub);
        assertTrue(pub.trim().endsWith("-----END PGP PUBLIC KEY BLOCK-----"), pub);
        assertTrue(sec.startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----"), sec);
        assertTrue(sec.trim().endsWith("-----END PGP PRIVATE KEY BLOCK-----"), sec);
        assertTrue(pub.contains("Comment: Key created by KeyJolt"), "armor comment header");

        PGPPublicKeyRing publicRing = readPublicRing(pub);
        PGPPublicKey master = publicRing.getPublicKey();
        assertTrue(master.isMasterKey());
        assertEquals(2048, master.getBitStrength());
        assertEquals(PublicKeyAlgorithmTags.RSA_GENERAL, master.getAlgorithm());
        assertEquals(List.of("Jane Doe <jane@example.com>"), userIds(master));

        // The reported key ID is the 64-bit ID of the master key, upper-case hex.
        assertEquals(Long.toHexString(master.getKeyID()).toUpperCase(), pair.getKeyId());
        assertTrue(pair.getKeyId().matches("^[0-9A-F]{1,16}$"), pair.getKeyId());
        assertEquals(20, master.getFingerprint().length, "v4 fingerprint is 160 bits");

        // Files carry the sanitized email, name and key ID and match the returned content.
        String base = "jane@example.com_Jane_Doe_";
        assertEquals(base + "pubkey_" + pair.getKeyId() + ".asc", pair.getPublicKeyFile().getName());
        assertEquals(base + "seckey_" + pair.getKeyId() + ".asc", pair.getPrivateKeyFile().getName());
        assertEquals(dir, pair.getPublicKeyFile().getParentFile().getName());
        assertEquals(pub, Files.readString(pair.getPublicKeyFile().toPath(), StandardCharsets.UTF_8));
        assertEquals(sec, Files.readString(pair.getPrivateKeyFile().toPath(), StandardCharsets.UTF_8));

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(pair.getPrivateKeyFile().toPath())),
                    "secret key file must be 0600");
        }
    }

    @Test
    void masterSignsAndCertifiesSubkeyEncryptsAndSelfSignaturesVerify() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, false, null);
        PgpKeyService.PgpKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        PGPPublicKeyRing ring = readPublicRing(pair.getPublicKeyContent());

        List<PGPPublicKey> keys = new ArrayList<>();
        ring.getPublicKeys().forEachRemaining(keys::add);
        assertEquals(2, keys.size(), "master key + one encryption subkey");
        PGPPublicKey master = keys.get(0);
        PGPPublicKey sub = keys.get(1);
        assertFalse(sub.isMasterKey());
        assertTrue(sub.isEncryptionKey());
        assertNotEquals(master.getKeyID(), sub.getKeyID(), "subkey is an independent RSA key");

        // User ID certification by the master key
        PGPSignature cert = single(master.getSignaturesForID("Jane Doe <jane@example.com>"));
        assertEquals(PGPSignature.POSITIVE_CERTIFICATION, cert.getSignatureType());
        cert.init(new BcPGPContentVerifierBuilderProvider(), master);
        assertTrue(cert.verifyCertification("Jane Doe <jane@example.com>", master));
        int masterFlags = cert.getHashedSubPackets().getKeyFlags();
        assertEquals(KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA, masterFlags);
        int[] preferredSymmetric = cert.getHashedSubPackets().getPreferredSymmetricAlgorithms();
        assertEquals(SymmetricKeyAlgorithmTags.AES_256, preferredSymmetric[0], "AES-256 preferred first");

        // Subkey binding by the master key
        PGPSignature binding = single(sub.getSignaturesOfType(PGPSignature.SUBKEY_BINDING));
        binding.init(new BcPGPContentVerifierBuilderProvider(), master);
        assertTrue(binding.verifyCertification(master, sub));
        assertEquals(KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE,
                binding.getHashedSubPackets().getKeyFlags());
    }

    @Test
    void zeroExpiryMeansTheKeyNeverExpires() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, false, null);
        PgpKeyService.PgpKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        PGPPublicKeyRing ring = readPublicRing(pair.getPublicKeyContent());
        ring.getPublicKeys().forEachRemaining(k ->
                assertEquals(0L, k.getValidSeconds(), "no expiry on key " + Long.toHexString(k.getKeyID())));
    }

    @Test
    void expiryAppliesToMasterAndSubkey() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 30, false, null);
        PgpKeyService.PgpKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        PGPPublicKeyRing ring = readPublicRing(pair.getPublicKeyContent());
        long thirtyDays = 30L * 24 * 60 * 60;
        ring.getPublicKeys().forEachRemaining(k ->
                assertEquals(thirtyDays, k.getValidSeconds(), "30-day expiry on key " + Long.toHexString(k.getKeyID())));
    }

    // ------------------------------------------------------------------ password

    @Test
    void withoutPasswordSecretKeysAreUnprotected() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, false, "");
        PgpKeyService.PgpKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        PGPSecretKeyRing ring = readSecretRing(pair.getPrivateKeyContent());

        for (PGPSecretKey key : secretKeys(ring)) {
            assertEquals(SymmetricKeyAlgorithmTags.NULL, key.getKeyEncryptionAlgorithm());
            assertNotNull(key.extractPrivateKey(null), "unprotected key opens without a password");
        }
    }

    @Test
    void passwordProtectsEverySecretKeyWithAes256() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, false, PASSWORD);
        PgpKeyService.PgpKeyPair pair = service.generateKeyPair(request, fileUtils.newRequestDir());
        PGPSecretKeyRing ring = readSecretRing(pair.getPrivateKeyContent());

        List<PGPSecretKey> keys = secretKeys(ring);
        assertEquals(2, keys.size());
        for (PGPSecretKey key : keys) {
            assertEquals(SymmetricKeyAlgorithmTags.AES_256, key.getKeyEncryptionAlgorithm(),
                    "key " + Long.toHexString(key.getKeyID()));

            PGPPrivateKey opened = key.extractPrivateKey(decryptor(PASSWORD));
            assertNotNull(opened);
            assertEquals(key.getKeyID(), opened.getKeyID());

            assertThrows(PGPException.class, () -> key.extractPrivateKey(decryptor("wrong-password")),
                    "a wrong password must not open the key");
        }
    }

    /**
     * KJ-02: in combined mode one password protects both the PGP and the SSH
     * private key, and an absent password leaves both unprotected. Locks the
     * contract the UI tooltip promises.
     */
    @Test
    void combinedModeProtectsPgpAndSshKeysWithTheSamePassword() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, true, PASSWORD);
        String dir = fileUtils.newRequestDir();

        PgpKeyService.PgpKeyPair pgp = service.generateKeyPair(request, dir);
        SshKeyService.SshKeyPair ssh = sshService.generateKeyPair(request, dir);

        PGPSecretKey master = readSecretRing(pgp.getPrivateKeyContent()).getSecretKey();
        assertNotNull(master.extractPrivateKey(decryptor(PASSWORD)));
        assertThrows(PGPException.class, () -> master.extractPrivateKey(decryptor("wrong-password")));

        assertEquals("aes256-ctr", opensshCipher(ssh.getPrivateKeyFile()));
        assertNotNull(loadOpenSsh(ssh.getPrivateKeyFile(), PASSWORD), "SSH key opens with the PGP password");
        assertThrows(Exception.class, () -> loadOpenSsh(ssh.getPrivateKeyFile(), "wrong-password"));

        // Both pairs live in the same request directory, so one token downloads all four files.
        assertEquals(pgp.getPrivateKeyFile().getParentFile(), ssh.getPrivateKeyFile().getParentFile());
    }

    @Test
    void combinedModeWithoutPasswordLeavesBothKeysUnprotected() throws Exception {
        KeyRequest request = new KeyRequest("Jane Doe", "jane@example.com", 2048, 0, true, null);
        String dir = fileUtils.newRequestDir();

        PgpKeyService.PgpKeyPair pgp = service.generateKeyPair(request, dir);
        SshKeyService.SshKeyPair ssh = sshService.generateKeyPair(request, dir);

        assertEquals(SymmetricKeyAlgorithmTags.NULL,
                readSecretRing(pgp.getPrivateKeyContent()).getSecretKey().getKeyEncryptionAlgorithm());
        assertEquals("none", opensshCipher(ssh.getPrivateKeyFile()));
    }

    // ------------------------------------------------------------ invalid input

    @ParameterizedTest(name = "name=[{0}]")
    @ValueSource(strings = {"", "   ", "Jane\tDoe", "Jane\nDoe", "Jane\rDoe", "Jane\u0000Doe", "Jane<Doe>"})
    void rejectsBlankOrControlCharacterNames(String name) {
        KeyRequest request = new KeyRequest(name, "jane@example.com", 2048, 0, false, null);
        assertTrue(violatedFields(request).contains("name"), "name [" + name + "] must be rejected");
    }

    @ParameterizedTest(name = "email=[{0}]")
    @ValueSource(strings = {"", "   ", "not-an-email", "jane@@example.com"})
    void rejectsBlankOrMalformedEmail(String email) {
        KeyRequest request = new KeyRequest("Jane Doe", email, 2048, 0, false, null);
        assertTrue(violatedFields(request).contains("email"), "email [" + email + "] must be rejected");
    }

    @Test
    void rejectsEmailLongerThan100Characters() {
        String local = "a".repeat(64);
        String domain = "b".repeat(32) + ".example.com"; // 64 + 1 + 44 = 109 characters
        String email = local + "@" + domain;
        assertTrue(email.length() > KeyRequest.MAX_EMAIL_LENGTH);
        KeyRequest request = new KeyRequest("Jane Doe", email, 2048, 0, false, null);
        assertTrue(violatedFields(request).contains("email"));
    }

    @Test
    void acceptsTheLongestAllowedNameAndEmail() {
        String name = "N".repeat(50);
        String email = "a".repeat(64) + "@" + "b".repeat(23) + ".example"; // exactly 96
        assertTrue(email.length() <= KeyRequest.MAX_EMAIL_LENGTH);
        KeyRequest request = new KeyRequest(name, email, 2048, 0, false, null);
        assertEquals(Set.of(), violatedFields(request));
    }

    @Test
    void rejectsOverlongNameAndOutOfRangeExpiry() {
        KeyRequest tooLong = new KeyRequest("N".repeat(51), "jane@example.com", 2048, 0, false, null);
        assertTrue(violatedFields(tooLong).contains("name"));

        KeyRequest negative = new KeyRequest("Jane Doe", "jane@example.com", 2048, -1, false, null);
        assertTrue(violatedFields(negative).contains("keyExpiry"));

        KeyRequest tooFar = new KeyRequest("Jane Doe", "jane@example.com", 2048, 3651, false, null);
        assertTrue(violatedFields(tooFar).contains("keyExpiry"));
    }

    // ------------------------------------------------------------------- helpers

    private static Set<String> violatedFields(KeyRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static PGPPublicKeyRing readPublicRing(String armored) throws IOException {
        try (InputStream in = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {
            return new PGPPublicKeyRing(in, FP);
        }
    }

    private static PGPSecretKeyRing readSecretRing(String armored) throws IOException, PGPException {
        try (InputStream in = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8)))) {
            return new PGPSecretKeyRing(in, FP);
        }
    }

    private static List<PGPSecretKey> secretKeys(PGPSecretKeyRing ring) {
        List<PGPSecretKey> keys = new ArrayList<>();
        ring.getSecretKeys().forEachRemaining(keys::add);
        return keys;
    }

    private static List<String> userIds(PGPPublicKey key) {
        List<String> ids = new ArrayList<>();
        key.getUserIDs().forEachRemaining(ids::add);
        return ids;
    }

    private static PGPSignature single(Iterator<PGPSignature> signatures) {
        assertTrue(signatures.hasNext(), "expected a signature");
        PGPSignature sig = signatures.next();
        assertFalse(signatures.hasNext(), "expected exactly one signature");
        return sig;
    }

    private static org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor decryptor(String password)
            throws PGPException {
        return new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider())
                .build(password.toCharArray());
    }

    private static Object loadOpenSsh(File privateKey, String passphrase) throws Exception {
        try (InputStream in = Files.newInputStream(privateKey.toPath())) {
            Iterable<java.security.KeyPair> pairs = org.apache.sshd.common.util.security.SecurityUtils
                    .loadKeyPairIdentities(null,
                            org.apache.sshd.common.NamedResource.ofName(privateKey.getName()), in,
                            org.apache.sshd.common.config.keys.FilePasswordProvider.of(passphrase));
            java.security.KeyPair pair = pairs.iterator().next();
            assertNotNull(pair.getPrivate());
            return pair;
        }
    }

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
}
