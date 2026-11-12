# 🔐 KeyJolt

<div align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen?style=for-the-badge&logo=spring" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java" alt="Java">
  <img src="https://img.shields.io/badge/Security-PGP%20%26%20SSH-blue?style=for-the-badge&logo=gnuprivacyguard" alt="Security">
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">
  <img src="https://img.shields.io/badge/Version-1.1.3-blue?style=for-the-badge" alt="Version 1.1.3">
</div>

<div align="center">
  <h3>🚀 Secure Keys, Instantly</h3>
  <p>A modern, secure web application for generating PGP and SSH key pairs with automatic cleanup and rate limiting.</p>
</div>

---

## 🆕 What's New in v1.1.3

A security patch:
- **Fixed: private keys could stay on disk after a restart.** Key files are deleted by a timer
  five minutes after generation. When the app stopped inside that window the timers were dropped
  and the files were never removed. On shutdown the app now deletes all pending key files at once,
  and on startup it removes anything a previous run left behind (crash, `kill -9`, power loss).
  Only KeyJolt's own per-request directories are touched.
- Shutdown no longer waits five seconds for the timers.

## 🆕 What's New in v1.1.2

A reliability and speed patch:
- **Fixed: every key generation failed with HTTP 500 once the temp directory was removed.**
  `app.temp-dir` defaults to `/tmp/keyjolt`, which is empty between requests, so tmp cleaners that
  prune empty directories delete it. Since v1.1.1 it was only created at startup. It is now
  re-created (still `0700`) on demand.
- **Faster PGP generation.** The master key, the encryption subkey and the optional SSH RSA key are
  generated in parallel instead of one after another.
- **Faster page loads.** Static assets are cacheable for 7 days (CSS/JS URLs carry a content hash),
  templates are cached, and scripts no longer block rendering.

## 🆕 What's New in v1.1.1

A hardening patch on top of v1.1.0:
- **Private temp storage.** The key directory (`app.temp-dir`) and every per-request token
  directory are now `0700`, so other local users on the host can't list request tokens.
- **Per-client rate limiting behind a proxy.** With `app.trusted-proxy=true`, the client is identified by
  `X-Real-IP`, which the proxy overwrites. If that header is missing, only the **last** `X-Forwarded-For` hop
  (the one our proxy appended) is used. The first hop is client-controlled and is no longer trusted, so
  rotating a fake `X-Forwarded-For` doesn't get around the limit. Production (nginx behind Cloudflare,
  with `real_ip_header CF-Connecting-IP`) now runs with `--app.trusted-proxy=true`. Before this,
  every visitor counted as `127.0.0.1` and shared one bucket.
- **Tests no longer write into the production temp directory.** They use `target/test-keys`.

## 🆕 What's New in v1.1.0

### 🔑 SSH-only mode
KeyJolt can now generate **just an SSH key pair**, with no PGP key, name or email needed.
Pick **SSH only** in the toggle at the top of the form (or open [`keyjolt.dev/#ssh`](https://keyjolt.dev/#ssh)):

| Field | Options |
|---|---|
| **Key type** | **Ed25519** (default, recommended) or **RSA 4096** for legacy systems |
| **Comment** | Optional, e.g. `you@laptop`, appended to the public key |
| **Passphrase** | Optional (8+ characters), encrypts the private key |

You get `id_ed25519` + `id_ed25519.pub` (or `id_rsa` + `id_rsa.pub`), the key's
`SHA256:` fingerprint, and a one-click **Copy** button for the public key line.

```bash
# after downloading
chmod 600 ~/Downloads/id_ed25519
mv ~/Downloads/id_ed25519 ~/Downloads/id_ed25519.pub ~/.ssh/
ssh-keygen -lf ~/.ssh/id_ed25519.pub   # fingerprint matches the one KeyJolt showed
```

**Under the hood:**
- Keys are written in the modern **OpenSSH format** (`-----BEGIN OPENSSH PRIVATE KEY-----`)
  by [Apache MINA sshd](https://mina.apache.org/sshd-project/), replacing JSch, which cannot
  write Ed25519 private keys.
- A passphrase uses **bcrypt KDF + AES-256-CTR**, the same as `ssh-keygen`'s default.
- Every key type, with and without a passphrase, is cross-checked against `ssh-keygen` in the test suite.
- The combined **PGP + SSH** mode is unchanged, but its SSH key now also uses the OpenSSH
  format, and its comment is your email address.

New endpoint for scripts:

```bash
curl -s -X POST https://keyjolt.dev/api/generate/ssh \
  -H 'Content-Type: application/json' \
  -d '{"keyType":"ed25519","comment":"you@laptop"}'
# → { "success": true, "fingerprint": "SHA256:…", "publicKey": "ssh-ed25519 AAAA… you@laptop",
#     "files": [ { "filename": "id_ed25519.pub", "downloadUrl": "/download/<token>/id_ed25519.pub", … },
#                { "filename": "id_ed25519",     "downloadUrl": "/download/<token>/id_ed25519", … } ] }
```

### 🛡️ Safer download URLs
In v1.0.x a download link was built only from the key's metadata
(`/download/email_name_seckey_KEYID.asc`). Anyone who could guess or observe those
values could fetch someone else's private key during its 5-minute window.

From v1.1.0:
- **Unguessable links.** Every generation request gets its own directory named by a random
  **128-bit token** from `SecureRandom`, and links look like `/download/<32-hex-token>/<filename>`.
  Knowing a name, email or key ID is no longer enough to download anything.
- **No clashes.** Two people generating `id_ed25519` at the same moment get separate directories.
- **Strict validation.** The token must be exactly 32 lowercase hex characters, and the filename must
  match a short allow-list (`A-Z a-z 0-9 @ . _ + -`). The resolved path must stay inside that request's
  directory, so anything else is rejected with `400` before the filesystem is touched.
- **`Cache-Control: no-store`** on every download, so browsers and proxies don't keep copies of keys.
- **Private key files are created `0600`** on the server, then overwritten with random data and deleted
  after 5 minutes as before. The empty request directory is removed with them.
- The old `/download/{filename}` route is gone.

---

## ✨ Features

### 🔒 **Security First**
- **RSA Encryption**: Support for 2048, 3072, and 4096-bit key strengths
- **Automatic Cleanup**: Generated keys are securely deleted after 5 minutes
- **Unguessable Download Links**: Per-request random 128-bit token in every download URL
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
- **SSH Key Pairs**: Alongside PGP, or on their own (SSH-only mode: Ed25519 or RSA 4096, optional passphrase)
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
- **Cryptography**: Bouncy Castle (PGP), Apache MINA sshd-common (SSH)
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
   http://localhost:5005
   ```

### Configuration

The application can be configured via `application.properties`:

```properties
# Server Configuration (uses PORT env var if set, else 5005)
server.port=${PORT:5005}
server.address=0.0.0.0

# Rate Limiting
app.rate-limit.requests-per-hour=10
app.rate-limit.burst-capacity=5

# Behind a reverse proxy that sets X-Real-IP (nginx): true = limit per client.
# Leave false when exposed directly, or clients could spoof their IP.
app.trusted-proxy=false

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

1. **Keep the toggle on "PGP + SSH"** and fill out the form with your name and email address
2. **Select encryption strength** (2048, 3072, or 4096 bits)
3. **Set key expiry** (0 for never expires, or 1-3650 days)
4. **Optionally enable SSH key generation**
5. **Click "Generate Keys"** and wait for processing
6. **Download your keys** immediately (they're deleted after 5 minutes)

### Generating SSH Keys Only

1. **Switch the toggle to "SSH only"** (or open `/#ssh`)
2. **Choose a key type**: Ed25519 (recommended) or RSA 4096
3. **Optionally add a comment and a passphrase**
4. **Click "Generate SSH Key"**, then copy the public key or download both files

### File Naming Convention

Generated files follow this pattern:
- **PGP Public**: `email_name_pubkey_KEYID.asc`
- **PGP Private**: `email_name_seckey_KEYID.asc`
- **SSH Public**: `email_name_ssh_pub.key`
- **SSH Private**: `email_name_ssh_priv.key`
- **SSH-only mode**: `id_ed25519` / `id_ed25519.pub` or `id_rsa` / `id_rsa.pub`

All files are served from `/download/<request-token>/<filename>`.

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
│   │   ├── KeyRequest.java             # Request DTO (PGP + SSH)
│   │   ├── SshKeyRequest.java          # Request DTO (SSH only)
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
- **File Security**: Per-request token directories, 0600 private keys, automatic cleanup with secure deletion
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
- [Apache MINA sshd](https://mina.apache.org/sshd-project/) for OpenSSH key generation

---

<div align="center">
  <p>Made with ❤️ for secure key generation</p>
  <p><strong>KeyJolt</strong> - Secure Keys, Instantly</p>
</div>