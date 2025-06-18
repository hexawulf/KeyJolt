# Deployment Guide

This guide covers deploying KeyJolt to various platforms and production environments.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- SSL certificate for production (HTTPS required)

## Environment Variables

Set these environment variables for production:

```bash
# Server Configuration
SERVER_PORT=5000
SERVER_ADDRESS=0.0.0.0

# Rate Limiting
APP_RATE_LIMIT_REQUESTS_PER_HOUR=10
APP_RATE_LIMIT_BURST_CAPACITY=5

# File Cleanup (5 minutes = 300000ms)
APP_KEY_CLEANUP_DELAY=300000

# Security
SPRING_PROFILES_ACTIVE=production
```

## Production Build

```bash
# Clone and build
git clone https://github.com/hexawulf/KeyJolt.git
cd KeyJolt
mvn clean package -Dmaven.test.skip=true

# The JAR file will be in target/keyjolt-1.0.0.jar
```

## Platform-Specific Deployments

### Docker Deployment

Create `Dockerfile`:
```dockerfile
FROM openjdk:17-jre-slim

WORKDIR /app
COPY target/keyjolt-1.0.0.jar app.jar

EXPOSE 5000

CMD ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t keyjolt .
docker run -p 5000:5000 \
  -e SPRING_PROFILES_ACTIVE=production \
  keyjolt
```

### Cloud Platforms

#### Heroku
1. Create `Procfile`:
   ```
   web: java -jar target/keyjolt-1.0.0.jar
   ```

2. Deploy:
   ```bash
   heroku create your-app-name
   git push heroku main
   ```

#### AWS EC2
```bash
# Install Java and transfer JAR
sudo yum update -y
sudo yum install -y java-17-amazon-corretto

# Run as service
sudo java -jar keyjolt-1.0.0.jar
```

#### Digital Ocean
Use their App Platform with automatic deployment from GitHub.

## Reverse Proxy Setup

### Nginx Configuration

```nginx
server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name yourdomain.com;
    
    ssl_certificate /path/to/certificate.crt;
    ssl_certificate_key /path/to/private.key;
    
    location / {
        proxy_pass http://localhost:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### Apache Configuration

```apache
<VirtualHost *:443>
    ServerName yourdomain.com
    
    SSLEngine on
    SSLCertificateFile /path/to/certificate.crt
    SSLCertificateKeyFile /path/to/private.key
    
    ProxyPreserveHost On
    ProxyPass / http://localhost:5000/
    ProxyPassReverse / http://localhost:5000/
</VirtualHost>
```

## Monitoring and Logging

### Application Logs
```bash
# View logs
tail -f /var/log/keyjolt/application.log

# Log rotation setup
sudo logrotate -d /etc/logrotate.d/keyjolt
```

### Health Checks
KeyJolt includes Spring Boot Actuator endpoints:
- `/actuator/health` - Application health status
- `/actuator/metrics` - Application metrics

## Security Checklist

- [ ] HTTPS enabled with valid SSL certificate
- [ ] Firewall configured (only allow 80, 443, SSH)
- [ ] Regular security updates scheduled
- [ ] Monitoring and alerting configured
- [ ] Backup procedures implemented
- [ ] Rate limiting configured appropriately
- [ ] Security headers verified

## Performance Optimization

### JVM Tuning
```bash
java -Xms512m -Xmx1024m \
     -XX:+UseG1GC \
     -jar keyjolt-1.0.0.jar
```

### Database (if added)
Consider adding database connection pooling and caching for enhanced performance.

## Troubleshooting

### Common Issues

1. **Port Already in Use**
   ```bash
   sudo lsof -i :5000
   sudo kill -9 PID
   ```

2. **SSL Certificate Issues**
   ```bash
   openssl x509 -in certificate.crt -text -noout
   ```

3. **Memory Issues**
   ```bash
   free -h
   ps aux --sort=-%mem | head
   ```

### Log Analysis
```bash
# Error patterns
grep -i error /var/log/keyjolt/application.log

# Rate limiting hits
grep "Rate limit exceeded" /var/log/keyjolt/application.log
```

## Maintenance

### Regular Tasks
- Monitor disk space (key cleanup verification)
- Check SSL certificate expiration
- Review security logs
- Update dependencies monthly
- Performance monitoring

### Backup Strategy
- Configuration files
- SSL certificates
- Application logs
- Monitoring data

For support: dev@0xwulf.dev