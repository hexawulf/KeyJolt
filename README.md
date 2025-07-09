# 🔐 KeyJolt

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen?style=for-the-badge&logo=spring" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java" alt="Java">
  <img src="https://img.shields.io/badge/Security-PGP%20%26%20SSH-blue?style=for-the-badge&logo=gnuprivacyguard" alt="Security">
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">
</div>

<div align="center">
  <h3>🚀 Secure Keys, Instantly</h3>
  <p>A modern, secure web application for generating PGP and SSH key pairs with automatic cleanup and rate limiting.</p>
</div>

---

## ✨ Features

### 🔒 **Security First**
- **RSA Encryption**: Support for 2048, 3072, and 4096-bit key strengths
- **Automatic Cleanup**: Generated keys are securely deleted after 5 minutes
- **Rate Limiting**: 10 requests per hour with burst capacity protection
- **Security Headers**: Comprehensive HTTP security headers (HSTS, CSP, X-Frame-Options)
- **Secure File Handling**: Private keys are overwritten with random data before deletion

### 🎨 **Modern UI/UX**
- **Responsive Design**: Mobile-first approach with clean, minimal interface
- **Twitter Blue Theme**: Professional color scheme with accessibility features
- **Real-time Validation**: Live form validation with clear error messages
- **Progress Indicators**: Loading states and generation progress feedback
- **Tooltips**: Helpful explanations for technical concepts

### 🔑 **Key Generation**
- **PGP Key Pairs**: RSA-based PGP keys with configurable expiration
- **SSH Key Pairs**: Optional SSH keys for server authentication
- **Custom Metadata**: User name and email integration
- **Flexible Expiry**: Keys can expire from 1 day to 10 years (or never)

### 🛡️ **Enterprise Ready**
- **Spring Security**: Comprehensive security configuration
- **Input Validation**: Server-side and client-side validation
- **Error Handling**: Graceful error management and user feedback
- **Logging**: Detailed security and operation logging

---

## 🏗️ Tech Stack

### **Backend**
- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Security**: Spring Security with custom headers
- **Cryptography**: Bouncy Castle (PGP), JSch (SSH)
- **Rate Limiting**: Bucket4j
- **Build Tool**: Maven

### **Frontend** 
- **Template Engine**: Thymeleaf
- **Styling**: Custom CSS with CSS Grid and Flexbox
- **JavaScript**: Vanilla JS with modern ES6+ features
- **Icons**: Feather Icons
- **Responsive**: Mobile-first design patterns

### **Security & Performance**
- **HTTPS Ready**: Security headers for production deployment
- **CSP**: Content Security Policy for XSS protection
- **Rate Limiting**: Token bucket algorithm
- **Memory Management**: Automatic resource cleanup

---

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/hexawulf/KeyJolt.git
   cd KeyJolt
   ```

2. **Build the application**
   ```bash
   mvn clean install
   ```

3. **Run the server**
   ```bash
   mvn spring-boot:run
   ```

4. **Access the application**
   ```
   http://localhost:5000
   ```

### Configuration

The application can be configured via `application.properties`:

```properties
# Server Configuration
server.port=5000
server.address=0.0.0.0

# Rate Limiting
app.rate-limit.requests-per-hour=10
app.rate-limit.burst-capacity=5

# File Cleanup (milliseconds)
app.key-cleanup-delay=300000

# Encryption Defaults
app.encryption.default-strength=4096
app.encryption.max-expiry-days=3650
```

Copy `src/main/resources/application-example.properties` to
`src/main/resources/application.properties` and adjust values for
your environment. **Do not** commit secrets to version control.

---

## 📖 Usage

### Generating PGP Keys

1. **Fill out the form** with your name and email address
2. **Select encryption strength** (2048, 3072, or 4096 bits)
3. **Set key expiry** (0 for never expires, or 1-3650 days)
4. **Optionally enable SSH key generation**
5. **Click "Generate Keys"** and wait for processing
6. **Download your keys** immediately (they're deleted after 5 minutes)

### File Naming Convention

Generated files follow this pattern:
- **PGP Public**: `email_name_pubkey_KEYID.asc`
- **PGP Private**: `email_name_seckey_KEYID.asc`
- **SSH Public**: `email_name_ssh_pub.key`
- **SSH Private**: `email_name_ssh_priv.key`

---

## 🏛️ Architecture

### Project Structure
```
keyjolt/
├── src/main/java/com/keyjolt/
│   ├── KeyjoltApplication.java          # Spring Boot main class
│   ├── config/
│   │   └── SecurityConfig.java          # Security configuration
│   ├── controller/
│   │   └── KeyController.java           # REST endpoints
│   ├── service/
│   │   ├── PgpKeyService.java          # PGP key generation
│   │   └── SshKeyService.java          # SSH key generation
│   ├── model/
│   │   ├── KeyRequest.java             # Request DTO
│   │   └── KeyResponse.java            # Response DTO
│   └── util/
│       └── FileUtils.java              # Secure file operations
├── src/main/resources/
│   ├── templates/
│   │   └── index.html                  # Main UI template
│   ├── static/
│   │   ├── css/style.css              # Styling
│   │   └── js/script.js               # Frontend logic
│   └── application.properties          # Configuration
└── pom.xml                            # Maven dependencies
```

### Security Model

- **Stateless Sessions**: No server-side session storage
- **CSRF Protection**: Disabled for API endpoints (stateless design)
- **Rate Limiting**: Per-IP token bucket with hourly limits
- **File Security**: Automatic cleanup with secure deletion
- **Input Validation**: Both client and server-side validation

---

## 🔒 Security Considerations

### **Production Deployment**
- Use HTTPS in production environments
- Configure proper firewall rules
- Set up monitoring and alerting
- Regular security updates
- Restrict access to `/actuator` endpoints or disable them
- Set `SPRING_PROFILES_ACTIVE=production` for production builds

### **Key Safety**
- Private keys are generated server-side and immediately deleted
- No keys are logged or permanently stored
- Users must download keys immediately
- Secure random number generation used throughout

### **Rate Limiting**
- Default: 10 requests per hour per IP
- Burst capacity: 5 requests per minute
- Configurable via application properties

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**0xWulf**
- Email: dev@0xwulf.dev
- GitHub: [@hexawulf](https://github.com/hexawulf)

---

## 🙏 Acknowledgments

- [Bouncy Castle](https://www.bouncycastle.org/) for robust cryptographic libraries
- [Spring Boot](https://spring.io/projects/spring-boot) for the excellent framework
- [Feather Icons](https://feathericons.com/) for beautiful, lightweight icons
- [JSch](http://www.jcraft.com/jsch/) for SSH key generation capabilities

---

<div align="center">
  <p>Made with ❤️ for secure key generation</p>
  <p><strong>KeyJolt</strong> - Secure Keys, Instantly</p>
</div>