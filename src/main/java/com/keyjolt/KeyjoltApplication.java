package com.keyjolt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Main Spring Boot application class for KeyJolt PGP Key Generator
 * 
 * This application provides secure PGP and SSH key pair generation
 * with automatic cleanup and rate limiting features.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class KeyjoltApplication {

    private static final Logger logger = LoggerFactory.getLogger(KeyjoltApplication.class);

    public static void main(String[] args) {
        // Add Bouncy Castle as security provider
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        // Ensure log directory exists
        try {
            Files.createDirectories(Paths.get("/home/zk/logs"));
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }

        SpringApplication.run(KeyjoltApplication.class, args);
    }

    @PostConstruct
    public void onStartup() {
        logger.info("KeyJolt application started");
    }

    @PreDestroy
    public void onShutdown() {
        logger.info("KeyJolt application shutting down");
    }
}
