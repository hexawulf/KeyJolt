# Deployment Guide

This guide covers building and self-hosting KeyJolt behind a TLS-terminating reverse proxy.

## Prerequisites

- Java 17 (OpenJDK / Eclipse Temurin)
- Maven 3.9+ (3.6+ minimum)
- A reverse proxy with TLS (Nginx, Caddy, or Apache) for production HTTPS

## Build

```bash
git clone https://github.com/hexawulf/KeyJolt.git
cd KeyJolt
mvn -B verify
# Produces target/keyjolt-1.3.0.jar after running tests, JaCoCo >= 80%, and SpotBugs
```

## Configuration

KeyJolt reads defaults from `src/main/resources/application.properties` and supports environment variables or Spring Boot CLI flags:

| Setting | Env var / CLI flag | Default | Notes |
|---|---|---|---|
| Listen port | `PORT` or `--server.port` | `5005` | |
| Bind address | `SERVER_ADDRESS` or `--server.address` | `127.0.0.1` | Loopback by default; override only if proxied from another host |
| Trusted proxy | `--app.trusted-proxy=true` | `false` | Set `true` only behind a reverse proxy that overwrites `X-Real-IP` |
| Temp key dir | `--app.temp-dir=/path` | `${java.io.tmpdir}/keyjolt` | Created `0700`; purged on startup and clean `SIGTERM` shutdown |
| Key TTL (ms) | `--app.key-cleanup-delay` | `300000` | 5 minutes |
| Rate limit | `APP_RATE_LIMIT_REQUESTS_PER_HOUR`, `APP_RATE_LIMIT_BURST_CAPACITY` | `10` / `5` | Per-client IP via Bucket4j |
| Concurrency cap | `--app.generation.max-concurrent` | `2` | Extra heavy RSA requests wait 5 s then receive `503` + `Retry-After` |
| Log directory | `LOG_DIR` | `logs` | Rolling log written to `${LOG_DIR}/keyjolt.log` |

## Self-Hosting (Direct JAR + Process Manager)

Run the JAR as an unprivileged user (never `root`). Give the JVM at least 15 seconds on `SIGTERM` so the `@PreDestroy` shutdown hook can securely overwrite and delete any pending key files before exit.

```bash
LOG_DIR=/var/log/keyjolt \
java -Xms256m -Xmx512m -XX:+UseG1GC \
  -jar target/keyjolt-1.3.0.jar \
  --app.trusted-proxy=true
```

### systemd Example

```ini
[Unit]
Description=KeyJolt PGP & SSH Key Generator
After=network.target

[Service]
Type=simple
User=keyjolt
WorkingDirectory=/opt/keyjolt
Environment=LOG_DIR=/var/log/keyjolt
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/keyjolt/target/keyjolt-1.3.0.jar --app.trusted-proxy=true
Restart=on-failure
RestartSec=5
TimeoutStopSec=15

[Install]
WantedBy=multi-user.target
```

### pm2 Example

See [`ecosystem.config.cjs`](ecosystem.config.cjs) for a production pm2 configuration with `kill_timeout: 15000` and `--app.trusted-proxy=true`. Note that pm2 only reloads `kill_timeout` on `pm2 delete` + `pm2 start`, not `pm2 restart`.

## Reverse Proxy Setup

KeyJolt sends its own security headers (`Content-Security-Policy`, `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`). Do not duplicate those headers in your proxy vhost.

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
        proxy_pass http://127.0.0.1:5005;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 75s;
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
    ProxyPass / http://127.0.0.1:5005/
    ProxyPassReverse / http://127.0.0.1:5005/
</VirtualHost>
```

## Monitoring and Logging

### Health Check

KeyJolt exposes a lightweight, uncached health endpoint:

```bash
curl -s http://127.0.0.1:5005/healthz
# {"status":"ok","service":"keyjolt"}
```

### Application Logs

Logs roll automatically (5 MB max file size, 7-day history via `logback-spring.xml`) in `${LOG_DIR}/keyjolt.log`. Download paths are redacted to `/download/<redacted>`, and tokens, emails, filenames, and key material are never logged.

```bash
tail -f "${LOG_DIR:-logs}/keyjolt.log"
```

## Security Checklist

- [ ] HTTPS enabled with valid TLS certificate
- [ ] Application bound to `127.0.0.1` (default) behind reverse proxy
- [ ] `--app.trusted-proxy=true` enabled only when the proxy overwrites `X-Real-IP`
- [ ] Process runs as a non-root user with >= 15 s graceful stop timeout
- [ ] Firewall allows only 80, 443, and SSH
- [ ] Security headers verified (each header sent once)

## Troubleshooting

1. **Port already in use:** check with `ss -ltnp | grep ':5005 '` and stop the existing process gracefully with `kill -TERM <pid>`.
2. **Slow RSA-4096 generation / 503 busy:** `app.generation.max-concurrent` defaults to `2` to prevent CPU exhaustion; tune it to match available CPU cores.
3. **Rate limiting hits:** check `grep "429" "${LOG_DIR:-logs}/keyjolt.log"`.

For support: dev@0xwulf.dev
