# Security Policy

## Supported Versions

We actively support the following versions with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.3.x   | :white_check_mark: |
| 1.2.x   | :white_check_mark: (until 1.4.0) |
| < 1.2   | :x:                |

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please follow these steps:

### Reporting Process

1. **DO NOT** create a public GitHub issue for security vulnerabilities
2. Email security reports directly to: **dev@0xwulf.dev**
3. Include the following information:
   - Description of the vulnerability
   - Steps to reproduce the issue
   - Potential impact assessment
   - Suggested fix (if available)

### Response Timeline

- **Initial Response**: Within 48 hours
- **Assessment**: Within 7 days
- **Fix Development**: Based on severity (1-30 days)
- **Public Disclosure**: After fix is deployed

### Security Measures

KeyJolt implements multiple security layers:

#### Application Security
- **Input Validation**: Server-side validation for all user inputs
- **Rate Limiting**: Token bucket algorithm with configurable limits
- **Security Headers**: HSTS, CSP, X-Frame-Options, X-Content-Type-Options
- **Stateless Design**: No server-side session storage

#### Cryptographic Security
- **Key Generation**: Uses cryptographically secure random number generators
- **RSA Implementation**: Bouncy Castle library with industry-standard algorithms
- **Key Strength**: Supports 2048, 3072, and 4096-bit RSA keys
- **Secure Deletion**: Files overwritten with random data before deletion

#### Ephemeral Key Handling
- **Per-request directories**: every generation request gets its own directory named by a
  random 128-bit token (`/download/<token>/<filename>`), created with mode `0700`; the base
  temp directory is `0700` too, and private key files are created `0600`.
- **5-minute TTL**: each key file is overwritten with random data and deleted 5 minutes
  (`app.key-cleanup-delay`) after it was generated. Downloads within that window are allowed;
  afterwards the link answers `404`.
- **Startup and shutdown purge**: on a clean stop (`SIGTERM`) all pending key files are wiped
  immediately; on startup any leftovers from a crash or `kill -9` are wiped before the app
  serves requests. Only token-named directories are touched; symlinks are never followed.
- **No persistence**: keys are never stored outside that temp directory, and never in a database.

#### Abuse Protection
- **Rate limiting**: per client, 10 generations/hour with a burst of 5 (Bucket4j). Behind a
  trusted reverse proxy (`app.trusted-proxy=true`) the client is taken from `X-Real-IP`, else
  the last `X-Forwarded-For` hop; otherwise proxy headers are ignored.
- **Concurrency cap**: at most 2 RSA generations run at once (`app.generation.max-concurrent`);
  further requests wait up to 5 s and then get `503` with `Retry-After: 10`, without losing a
  rate-limit token.

#### Logging
- Download tokens, file names, e-mail addresses, passphrases and key material are never
  logged. Download paths are redacted to `/download/<redacted>` in the request log. A unit test
  (`LogPrivacyTest`) enforces this.

### Known Security Considerations

#### By Design
- Private keys are generated server-side (necessary for web interface)
- Keys exist temporarily in server memory during generation
- Network transmission of generated keys (use HTTPS in production)

#### Mitigations
- Secure deletion 5 minutes after generation, and on every restart
- Rate limiting and a concurrency cap prevent automated abuse
- No logging of key material, tokens or personal data
- Secure random number generation

### Security Best Practices for Users

1. **Always use HTTPS** in production deployments
2. **Download keys immediately** after generation
3. **Verify key integrity** using fingerprints
4. **Store private keys securely** offline
5. **Never share private keys** via insecure channels

### Production Deployment Security

#### Required
- HTTPS with valid TLS certificates
- Proper firewall configuration
- Regular security updates
- Monitoring and alerting

#### Recommended
- Web Application Firewall (WAF)
- DDoS protection
- Regular security audits
- Backup and recovery procedures

### Vulnerability Categories

We particularly welcome reports for:

- **Authentication/Authorization** bypass
- **Input validation** vulnerabilities
- **Cryptographic** implementation issues
- **Information disclosure** vulnerabilities
- **Denial of Service** attack vectors
- **File handling** security issues

### Acknowledgments

We appreciate responsible disclosure and will acknowledge security researchers who help improve KeyJolt's security:

- Public acknowledgment in release notes (if desired)
- Recognition in security hall of fame
- Direct communication with development team

## Contact

For security-related questions or concerns:
- Email: dev@0xwulf.dev
- GitHub: @hexawulf

Thank you for helping keep KeyJolt secure!
