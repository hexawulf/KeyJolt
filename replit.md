# KeyJolt PGP Key Generator

## Overview

KeyJolt is a secure web application for generating PGP and SSH key pairs instantly. Built with Spring Boot and Java 17, it provides a simple, modern interface for creating cryptographically secure keys with configurable parameters. The application emphasizes security through automatic key cleanup, rate limiting, and stateless operation.

## System Architecture

### Frontend Architecture
- **Template Engine**: Thymeleaf for server-side rendering
- **Styling**: Custom CSS with Twitter Blue (#1DA1F2) color scheme
- **JavaScript**: Vanilla JS for form validation and user interactions
- **Responsive Design**: Mobile-first approach with clean, minimal UI
- **Icons**: Feather Icons for consistent iconography

### Backend Architecture
- **Framework**: Spring Boot 3.2.0 with Java 17
- **Security**: Spring Security with custom configuration
- **Validation**: Jakarta Bean Validation for input sanitization
- **Async Processing**: Spring's @EnableAsync for background tasks
- **Scheduling**: @EnableScheduling for automatic cleanup

### Key Generation Libraries
- **PGP Keys**: Bouncy Castle (bcpg-jdk15on) for RSA key generation
- **SSH Keys**: JSch library for SSH key pair creation
- **Encryption**: RSA with configurable strength (2048-4096 bits)

## Key Components

### Controllers
- **KeyController**: Main REST endpoint handler
  - Manages form submissions and key generation requests
  - Implements rate limiting using Bucket4j
  - Handles file downloads with secure headers
  - Validates user inputs and provides error handling

### Services
- **PgpKeyService**: Handles PGP key pair generation
  - Uses Bouncy Castle for RSA key creation
  - Supports configurable key expiry and encryption strength
  - Generates armored ASCII output format
  
- **SshKeyService**: Manages SSH key pair creation
  - Leverages JSch for RSA SSH key generation
  - Creates OpenSSH format private keys
  - Adds user comments to public keys

### Models
- **KeyRequest**: Data transfer object for key generation parameters
  - Validates name (alphanumeric, max 50 chars)
  - Validates email format
  - Ensures encryption strength within 2048-4096 bit range
  - Limits key expiry to maximum 10 years

- **KeyResponse**: Response wrapper for generated keys
  - Contains success/failure status
  - Includes file information and download links
  - Provides error messages for failed generations

### Utilities
- **FileUtils**: Secure file handling and cleanup
  - Creates temporary files in designated directory
  - Implements automatic file deletion after configurable delay
  - Schedules cleanup tasks to prevent disk space issues

## Data Flow

1. **User Input**: Form submission with name, email, encryption parameters
2. **Validation**: Server-side validation using Jakarta Bean Validation
3. **Rate Limiting**: IP-based request throttling using token bucket algorithm
4. **Key Generation**: 
   - PGP keys generated using Bouncy Castle
   - Optional SSH keys created with JSch
   - Files written to temporary directory
5. **Response**: Download links provided to user
6. **Cleanup**: Scheduled deletion of temporary files after 5-minute delay

## External Dependencies

### Core Dependencies
- **Spring Boot Starter Web**: REST API and embedded Tomcat
- **Spring Boot Starter Thymeleaf**: Template engine for UI
- **Spring Boot Starter Security**: Security framework and headers
- **Spring Boot Starter Validation**: Input validation framework

### Cryptographic Libraries
- **Bouncy Castle (bcpg-jdk15on)**: PGP key generation and manipulation
- **JSch**: SSH key pair generation (implied from service structure)

### Frontend Assets
- **Feather Icons**: SVG icon library via CDN
- **Custom CSS**: Twitter Blue themed styling

## Deployment Strategy

### Replit Configuration
- **Runtime**: Java module with Maven build system
- **Port**: Application runs on port 5000
- **Build Process**: `mvn clean install && mvn spring-boot:run`
- **Environment**: Nix channel stable-24_05

### Application Configuration
- **Server**: Binds to 0.0.0.0:5000 for external access
- **File Handling**: 10MB max file size limit
- **Rate Limiting**: 10 requests per hour with burst capacity of 5
- **Cleanup**: 5-minute delay before file deletion
- **Security**: Stateless sessions, disabled CSRF for API endpoints

### Security Headers
- **HSTS**: 1-year max age with subdomain inclusion
- **CSP**: Restrictive content security policy
- **Frame Options**: Deny to prevent clickjacking
- **XSS Protection**: Browser-level XSS filtering enabled

## User Preferences

Preferred communication style: Simple, everyday language.

## Recent Changes

- June 18, 2025: Complete KeyJolt application deployed successfully
  - Spring Boot backend with PGP/SSH key generation
  - Twitter-blue themed responsive UI
  - Real-time form validation and security features
  - Rate limiting and automatic file cleanup
  - GitHub repository created at https://github.com/hexawulf/KeyJolt

## GitHub Repository

Repository URL: https://github.com/hexawulf/KeyJolt
Author: 0xWulf (dev@0xwulf.dev, @hexawulf)

### Repository Files Created:
- README.md: Comprehensive documentation with tech stack and features
- LICENSE: MIT license
- .gitignore: Java/Maven/Spring Boot gitignore
- CONTRIBUTING.md: Development and contribution guidelines
- SECURITY.md: Security policy and vulnerability reporting
- DEPLOYMENT.md: Production deployment guide