package com.keyjolt.service;

import com.keyjolt.model.KeyRequest;
import com.keyjolt.model.SshKeyRequest;
import com.keyjolt.util.FileUtils;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;

/**
 * Generates SSH key pairs in OpenSSH format (openssh-key-v1) using Apache MINA sshd-common.
 * Passphrase-protected keys use bcrypt KDF + aes256-ctr, the same as ssh-keygen's default.
 */
@Service
public class SshKeyService {

    static final int RSA_SSH_ONLY_BITS = 4096;

    @Autowired
    private FileUtils fileUtils;

    /**
     * SSH pair generated alongside PGP keys (combined mode): RSA at the PGP strength.
     */
    public SshKeyPair generateKeyPair(KeyRequest request, String requestDir)
            throws IOException, GeneralSecurityException {
        KeyPair keyPair = KeyUtils.generateKeyPair(KeyPairProvider.SSH_RSA, request.getEncryptionStrength());
        String base = request.getSanitizedEmail() + "_" + request.getSanitizedName();
        return write(keyPair, request.getEmail(), null, requestDir,
                base + "_ssh_pub.key", base + "_ssh_priv.key");
    }

    /**
     * Standalone SSH pair (SSH-only mode): Ed25519 or RSA-4096, optional passphrase.
     */
    public SshKeyPair generateKeyPair(SshKeyRequest request, String requestDir)
            throws IOException, GeneralSecurityException {
        boolean ed25519 = request.isEd25519();
        KeyPair keyPair = ed25519
                ? KeyUtils.generateKeyPair(KeyPairProvider.SSH_ED25519, 256)
                : KeyUtils.generateKeyPair(KeyPairProvider.SSH_RSA, RSA_SSH_ONLY_BITS);
        String base = ed25519 ? "id_ed25519" : "id_rsa";
        return write(keyPair, request.getTrimmedComment(),
                request.hasPassphrase() ? request.getPassphrase() : null,
                requestDir, base + ".pub", base);
    }

    private SshKeyPair write(KeyPair keyPair, String comment, String passphrase, String requestDir,
                             String publicName, String privateName)
            throws IOException, GeneralSecurityException {
        String safeComment = comment == null ? "" : comment;

        OpenSSHKeyEncryptionContext encryption = null;
        if (passphrase != null) {
            encryption = new OpenSSHKeyEncryptionContext();
            encryption.setPassword(passphrase);
            encryption.setCipherName("AES");
            encryption.setCipherMode("CTR");
            encryption.setCipherType("256");
        }

        ByteArrayOutputStream privateOut = new ByteArrayOutputStream();
        OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, safeComment, encryption, privateOut);
        byte[] privateBytes = privateOut.toByteArray();

        ByteArrayOutputStream publicOut = new ByteArrayOutputStream();
        OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(keyPair, safeComment, publicOut);
        String publicLine = publicOut.toString(StandardCharsets.UTF_8).trim();

        File publicFile;
        File privateFile;
        try {
            publicFile = fileUtils.writeToTempFile(requestDir, publicName,
                    (publicLine + "\n").getBytes(StandardCharsets.UTF_8), false);
            privateFile = fileUtils.writeToTempFile(requestDir, privateName, privateBytes, true);
        } finally {
            Arrays.fill(privateBytes, (byte) 0);
        }

        String fingerprint = KeyUtils.getFingerPrint(keyPair.getPublic());
        return new SshKeyPair(publicFile, privateFile, publicLine, fingerprint);
    }

    /**
     * Result of an SSH key generation. Holds no private key material in memory.
     */
    public static class SshKeyPair {
        private final File publicKeyFile;
        private final File privateKeyFile;
        private final String publicKeyContent;
        private final String fingerprint;

        public SshKeyPair(File publicKeyFile, File privateKeyFile, String publicKeyContent, String fingerprint) {
            this.publicKeyFile = publicKeyFile;
            this.privateKeyFile = privateKeyFile;
            this.publicKeyContent = publicKeyContent;
            this.fingerprint = fingerprint;
        }

        public File getPublicKeyFile() { return publicKeyFile; }
        public File getPrivateKeyFile() { return privateKeyFile; }
        public String getPublicKeyContent() { return publicKeyContent; }
        public String getFingerprint() { return fingerprint; }
    }
}
