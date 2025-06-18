package com.keyjolt.service;

import com.keyjolt.model.KeyRequest;
import com.keyjolt.util.FileUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;

/**
 * Service for generating PGP key pairs using Bouncy Castle
 */
@Service
public class PgpKeyService {
    
    @Autowired
    private FileUtils fileUtils;
    
    private static final int[] PREFERRED_HASH_ALGORITHMS = {
        HashAlgorithmTags.SHA256,
        HashAlgorithmTags.SHA1,
        HashAlgorithmTags.SHA384,
        HashAlgorithmTags.SHA512,
        HashAlgorithmTags.SHA224
    };
    
    private static final int[] PREFERRED_SYMMETRIC_ALGORITHMS = {
        SymmetricKeyAlgorithmTags.AES_256,
        SymmetricKeyAlgorithmTags.AES_192,
        SymmetricKeyAlgorithmTags.AES_128,
        SymmetricKeyAlgorithmTags.CAST5,
        SymmetricKeyAlgorithmTags.TRIPLE_DES
    };
    
    /**
     * Generate a PGP key pair based on the request parameters
     */
    public PgpKeyPair generateKeyPair(KeyRequest request) throws Exception {
        // Generate RSA key pair
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(
            BigInteger.valueOf(65537), // public exponent
            new SecureRandom(),
            request.getEncryptionStrength(),
            80 // certainty for prime generation
        ));
        
        org.bouncycastle.crypto.AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        
        // Create PGP key pair
        Date now = new Date();
        Date expiry = null;
        if (request.getKeyExpiry() > 0) {
            expiry = new Date(now.getTime() + (request.getKeyExpiry() * 24L * 60L * 60L * 1000L));
        }
        
        PGPDigestCalculator sha1Calc = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
        PGPKeyPair pgpKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_GENERAL, keyPair, now);
        
        // Create user ID
        String userId = String.format("%s <%s>", request.getName(), request.getEmail());
        
        // Create signature subpacket generator for master key
        PGPSignatureSubpacketGenerator masterSubpktGen = new PGPSignatureSubpacketGenerator();
        masterSubpktGen.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
        masterSubpktGen.setPreferredHashAlgorithms(false, PREFERRED_HASH_ALGORITHMS);
        masterSubpktGen.setPreferredSymmetricAlgorithms(false, PREFERRED_SYMMETRIC_ALGORITHMS);
        masterSubpktGen.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);
        
        if (expiry != null) {
            masterSubpktGen.setKeyExpirationTime(false, (expiry.getTime() - now.getTime()) / 1000);
        }
        
        // Create key ring generator
        PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            pgpKeyPair,
            userId,
            sha1Calc,
            masterSubpktGen.generate(),
            null,
            new BcPGPContentSignerBuilder(pgpKeyPair.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256),
            (PBESecretKeyEncryptor) null // No passphrase protection for now
        );
        
        // Generate encryption subkey
        PGPKeyPair encKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_GENERAL, generator.generateKeyPair(), now);
        PGPSignatureSubpacketGenerator encSubpktGen = new PGPSignatureSubpacketGenerator();
        encSubpktGen.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);
        
        if (expiry != null) {
            encSubpktGen.setKeyExpirationTime(false, (expiry.getTime() - now.getTime()) / 1000);
        }
        
        keyRingGen.addSubKey(encKeyPair, encSubpktGen.generate(), null);
        
        // Generate key rings
        PGPPublicKeyRing publicKeyRing = keyRingGen.generatePublicKeyRing();
        PGPSecretKeyRing secretKeyRing = keyRingGen.generateSecretKeyRing();
        
        // Get key ID
        String keyId = Long.toHexString(publicKeyRing.getPublicKey().getKeyID()).toUpperCase();
        
        // Export keys to armored format
        String publicKeyArmored = exportPublicKey(publicKeyRing);
        String privateKeyArmored = exportPrivateKey(secretKeyRing);
        
        // Create filenames
        String baseFilename = String.format("%s_%s", 
            request.getSanitizedEmail(), 
            request.getSanitizedName());
        
        String publicKeyFilename = String.format("%s_pubkey_%s.asc", baseFilename, keyId);
        String privateKeyFilename = String.format("%s_seckey_%s.asc", baseFilename, keyId);
        
        // Write files
        File publicKeyFile = fileUtils.writeToTempFile(publicKeyFilename, publicKeyArmored);
        File privateKeyFile = fileUtils.writeToTempFile(privateKeyFilename, privateKeyArmored);
        
        return new PgpKeyPair(keyId, publicKeyFile, privateKeyFile, publicKeyArmored, privateKeyArmored);
    }
    
    /**
     * Export public key to armored format
     */
    private String exportPublicKey(PGPPublicKeyRing keyRing) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArmoredOutputStream armoredOut = new ArmoredOutputStream(out);
        armoredOut.setHeader("Comment", "Generated by KeyJolt PGP Key Generator");
        
        keyRing.encode(armoredOut);
        armoredOut.close();
        
        return out.toString("UTF-8");
    }
    
    /**
     * Export private key to armored format
     */
    private String exportPrivateKey(PGPSecretKeyRing keyRing) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArmoredOutputStream armoredOut = new ArmoredOutputStream(out);
        armoredOut.setHeader("Comment", "Generated by KeyJolt PGP Key Generator - Keep this private!");
        
        keyRing.encode(armoredOut);
        armoredOut.close();
        
        return out.toString("UTF-8");
    }
    
    /**
     * Inner class to hold PGP key pair information
     */
    public static class PgpKeyPair {
        private final String keyId;
        private final File publicKeyFile;
        private final File privateKeyFile;
        private final String publicKeyContent;
        private final String privateKeyContent;
        
        public PgpKeyPair(String keyId, File publicKeyFile, File privateKeyFile, 
                         String publicKeyContent, String privateKeyContent) {
            this.keyId = keyId;
            this.publicKeyFile = publicKeyFile;
            this.privateKeyFile = privateKeyFile;
            this.publicKeyContent = publicKeyContent;
            this.privateKeyContent = privateKeyContent;
        }
        
        // Getters
        public String getKeyId() { return keyId; }
        public File getPublicKeyFile() { return publicKeyFile; }
        public File getPrivateKeyFile() { return privateKeyFile; }
        public String getPublicKeyContent() { return publicKeyContent; }
        public String getPrivateKeyContent() { return privateKeyContent; }
    }
}
