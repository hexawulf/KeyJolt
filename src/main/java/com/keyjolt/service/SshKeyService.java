package com.keyjolt.service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.keyjolt.model.KeyRequest;
import com.keyjolt.util.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Service for generating SSH key pairs using JSch
 */
@Service
public class SshKeyService {
    
    @Autowired
    private FileUtils fileUtils;
    
    /**
     * Generate SSH key pair based on request parameters
     */
    public SshKeyPair generateKeyPair(KeyRequest request) throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, request.getEncryptionStrength());
        try {
            String comment = String.format("%s@%s", request.getName(), request.getEmail());
            keyPair.setPublicKeyComment(comment);

            ByteArrayOutputStream publicKeyOut = new ByteArrayOutputStream();
            keyPair.writePublicKey(publicKeyOut, comment);
            String publicKeyContent = publicKeyOut.toString("UTF-8");

            ByteArrayOutputStream privateKeyOut = new ByteArrayOutputStream();
            keyPair.writePrivateKey(privateKeyOut);
            String privateKeyContent = privateKeyOut.toString("UTF-8");

            String baseFilename = String.format("%s_%s",
                request.getSanitizedEmail(),
                request.getSanitizedName());

            File publicKeyFile = fileUtils.writeToTempFile(baseFilename + "_ssh_pub.key", publicKeyContent);
            File privateKeyFile = fileUtils.writeToTempFile(baseFilename + "_ssh_priv.key", privateKeyContent);

            return new SshKeyPair(publicKeyFile, privateKeyFile, publicKeyContent, privateKeyContent);
        } finally {
            keyPair.dispose();
        }
    }
    
    /**
     * Inner class to hold SSH key pair information
     */
    public static class SshKeyPair {
        private final File publicKeyFile;
        private final File privateKeyFile;
        private final String publicKeyContent;
        private final String privateKeyContent;
        
        public SshKeyPair(File publicKeyFile, File privateKeyFile, 
                         String publicKeyContent, String privateKeyContent) {
            this.publicKeyFile = publicKeyFile;
            this.privateKeyFile = privateKeyFile;
            this.publicKeyContent = publicKeyContent;
            this.privateKeyContent = privateKeyContent;
        }
        
        // Getters
        public File getPublicKeyFile() { return publicKeyFile; }
        public File getPrivateKeyFile() { return privateKeyFile; }
        public String getPublicKeyContent() { return publicKeyContent; }
        public String getPrivateKeyContent() { return privateKeyContent; }
    }
}
