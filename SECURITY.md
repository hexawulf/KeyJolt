# Security Policy

## Supported Versions

We actively support the following versions with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

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

#### Infrastructure Security
- **Automatic Cleanup**: Generated keys deleted after 5 minutes
- **No Persistence**: Keys never stored permanently
- **Memory Management**: Secure handling of sensitive data in memory
- **File System**: Restricted access to temporary file directories

### Known Security Considerations

#### By Design
- Private keys are generated server-side (necessary for web interface)
- Keys exist temporarily in server memory during generation
- Network transmission of generated keys (use HTTPS in production)

#### Mitigations
- Immediate secure deletion after download
- Rate limiting prevents automated abuse
- No logging of key material
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