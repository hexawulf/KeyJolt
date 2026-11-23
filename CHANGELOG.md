# Changelog

All notable changes to KeyJolt are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.1] - 2026-11-23

Security hardening, WCAG AA teal visual refresh, layout polish and repository cleanup.
Includes two post-1.3.0 commits (`24eb3d1`, `638ce55`) that were previously built into
production under the `1.3.0` JAR filename.

### Security
- **Loopback bind by default:** `server.address` now defaults to `${SERVER_ADDRESS:127.0.0.1}`
  instead of `0.0.0.0` (overridable via `SERVER_ADDRESS` or `--server.address`), backed by a
  unit test (`HealthControllerTest.defaultBindAddressIsLoopback`).
- **Zero third-party requests:** vendored `feather-icons@4.29.0` (`dist/feather.min.js`) into
  `src/main/resources/static/js/feather.min.js` (content-hashed via Thymeleaf) and tightened
  `SecurityConfig` CSP `script-src` from `'self' https://cdn.jsdelivr.net` to `'self'`.

### Fixed
- **Expired download error display:** clicking an expired download link now hides the stale
  results card (preventing dead download buttons from remaining stacked above the error card)
  and displays a *Download Failed* heading instead of *Generation Failed*.
- **WCAG 2.1 AA contrast:** darkened interactive teal button fills, active mode tab, links,
  and headings to `#0F766E` / `#115E59` (≥ 5.23:1) and tagline to `#475569` (7.24:1) so every
  interactive element meets WCAG 2.1 AA (4.5:1) while preserving the canonical teal accent.

### Changed
- **Visual refresh & favicons (`638ce55`, `24eb3d1`):** refreshed to the 0xWulf teal palette
  with KJ SVG/PNG/ICO favicons, added release notes to the About modal, and tightened layout
  density (2-column strength/expiry row on desktop, form-first order on mobile, single-screen
  About modal) so the primary CTA fits above the fold.
- **Docs & screenshots:** rewrote stale `DEPLOYMENT.md` for accurate self-hosting, aligned
  `README.md` and `robots.txt`, and refreshed `docs/screenshots/*.png` with the AA teal UI.

### Removed
- Deleted dead legacy files: `.replit`, `render.yaml`, `replit.md`,
  `src/main/resources/application-example.properties`, unused `src/main/resources/static/Keyjolt.png`
  (981 KB), dead `--twitter-blue*` CSS aliases, and duplicate Replit lines in `.gitignore`.

## [1.3.0] - 2026-11-22

Tier-S parity pass: tests, coverage gate, browser smoke tests, CI supply-chain
hardening and an accessibility pass. No API or key-format changes.

### Fixed
- **"Generate New Keys" and "Try Again" did nothing.** Both buttons used inline
  `onclick` handlers, which the app's own Content Security Policy (`script-src 'self'`)
  blocks. They are now bound from `script.js`, and a test fails if an inline event
  handler comes back.
- The page said keys are deleted "after download"; they are deleted **5 minutes after
  generation** (downloads within that window work). The copy now says so.
- The Security dialog claimed key files are "overwritten in memory"; they are overwritten
  with random data on disk before deletion.

### Changed
- **About dialog attribution** follows the fleet standard:
  `Erling Wulf Weinreich (0xWulf) · dev@0xwulf.dev`.
- `app.version` is filled in from `pom.xml` at build time, so the About dialog's
  version and release link can no longer drift from the JAR.
- **Accessibility:** the About, Security and Privacy dialogs have `role="dialog"`,
  `aria-modal`, labelled titles and real close buttons; focus moves into the dialog,
  is trapped on Tab, and returns to the footer link on Escape, backdrop click or close.
  Generation progress and results are announced through `aria-live` regions, field
  errors are linked to their inputs (`aria-describedby`), and help tooltips open on
  keyboard focus.
- Clearer errors: `503` (server busy) shows the `Retry-After` wait, `429` states the
  limits, and a malformed download link (`400`) is reported as invalid.
- Download cards are built with DOM APIs instead of `innerHTML`; console logging removed.
- CSS: one `[hidden]` rule, dialogs scroll on small screens, focus-visible outlines,
  duplicate and placeholder rules tidied.
- `SECURITY.md`: supported versions (1.3.x, 1.2.x) and a precise description of the
  ephemeral key handling (0700 per-request token dirs, 0600 private keys, 5-minute TTL,
  startup/shutdown purge, concurrency cap, rate limits, log privacy).

### Added
- `PgpKeyServiceTest`: parses the generated key rings with Bouncy Castle and checks
  armor, user ID, key ID, key flags, self-signatures, expiry, file names and `0600`
  permissions; proves the password opens every secret key (AES-256) and a wrong one
  does not; locks down the combined PGP + SSH password contract (KJ-02); and rejects
  blank, control-character, overlong and malformed names and e-mail addresses.
- `HealthControllerTest`: `/healthz`, security headers (CSP, `nosniff`, `X-Frame-Options`,
  `Referrer-Policy`, HSTS behind the TLS proxy), no inline script allowance, and clean
  `403`/`400`/`415` responses without stack traces.
- **JaCoCo** coverage report (XML/HTML/CSV) and a `jacoco:check` gate of **80 % line
  coverage** on `mvn verify` (currently about 87 %).
- **Playwright smoke suite** in `e2e/` (`@playwright/test` 1.65.1): health, homepage
  with zero page errors/CSP violations/5xx, About dialog attribution + version + Escape,
  Security/Privacy dialogs, 390×844 layout without horizontal overflow, an Ed25519 key
  generated and downloaded through the UI, invalid download links, and server-side
  validation errors. `npm run test:e2e` boots the built JAR with an isolated temp and
  log directory, stops it with `SIGTERM` and fails if any key file or download token
  survives. `npm run test:e2e:live` runs the read-only checks against keyjolt.dev.
- README screenshots in `docs/screenshots/`.
- `CHANGELOG.md` (this file).

### CI / supply chain
- `actions/checkout` v7.0.1, `actions/setup-java` v6.3.0, `actions/setup-node` v7.1.0,
  `actions/upload-artifact` v7.0.2, `actions/download-artifact` v8.0.2 and
  `github/codeql-action/upload-sarif` v4.39.4, all SHA-pinned (Node 24 runtimes).
- Runs on `main` are no longer cancelled by a newer push; only pull-request runs are.
- New **Trivy** job (image `aquasec/trivy:0.76.1`, digest-pinned) scans every library in
  the built JAR, uploads SARIF to the Security tab and fails on fixable CRITICAL findings.
- New **e2e** job runs the Playwright suite against the built JAR on `127.0.0.1:5095`.
- Dependabot: weekly on Monday 04:00 Asia/Taipei for Maven, GitHub Actions and the
  `e2e/` npm package, minor and patch updates grouped.

## [1.2.1] - 2026-11-14

### Fixed
- The About dialog shows the running Spring Boot and Java versions (it said Spring Boot
  3.2.0), and the version links to its GitHub release tag.

## [1.2.0] - 2026-11-14

### Changed
- **Spring Boot 4.1.2** (from 3.5, whose open-source support ended on 2026-06-30);
  Spring Security 7.1, Tomcat 11, Jackson 3. Still Java 17.
- Request models bind JSON through setters (`@JsonCreator` on the no-arg constructor);
  Jackson 3 would otherwise use the all-args constructors and reject requests that
  leave out optional fields.

## [1.1.5] - 2026-11-14

### Fixed
- Clicking a download after the 5-minute window now says the link has expired instead
  of doing nothing (KJ-09).
- Requests time out with a message (generation 80 s, download 30 s, validation 10 s)
  instead of an endless spinner.

### Changed
- The request log runs before Spring Security, so denied requests (`403`) are logged
  too (KJ-11).

## [1.1.4] - 2026-11-12

### Security
- **The SSH private key was not password-protected in combined mode.** The password now
  encrypts the SSH private key as well (OpenSSH format, bcrypt KDF + AES-256-CTR) (KJ-02).
- Download tokens, file names and e-mail addresses are no longer logged; the unused default
  Spring Security user is gone (KJ-08).
- HSTS is actually sent (the app only sees plain HTTP behind the reverse proxy).
- E-mail addresses are limited to 100 characters; names accept plain spaces only.

### Changed
- At most two RSA generations run at once (`app.generation.max-concurrent`); further
  requests wait up to 5 s, then get `503` with `Retry-After` without losing a rate-limit token.
- Dependencies: Spring Boot 3.2.0 → 3.5.16, Bouncy Castle 1.80 → 1.87, Bucket4j 7.6 → 8.22,
  SpotBugs plugin 4.10.5.

### Fixed
- Rate-limit buckets of idle clients are evicted again (slow memory leak).
- pm2 gives the JVM 15 s to stop, so the shutdown cleanup runs.

## [1.1.3] - 2026-11-12

### Security
- **Private keys could stay on disk after a restart.** On shutdown the app now deletes all
  pending key files at once, and on startup it removes anything a previous run left behind
  (crash, `kill -9`, power loss). Only KeyJolt's own per-request directories are touched.

## [1.1.2] - 2026-11-12

### Fixed
- Every generation failed with HTTP 500 once a tmp cleaner had removed the empty temp
  directory; it is now re-created (`0700`) on demand.

### Changed
- The PGP master key, encryption subkey and optional SSH RSA key are generated in parallel.
- Static assets are cacheable for 7 days (content-hashed URLs); templates are cached.

## [1.1.1] - 2026-11-06

### Security
- The temp directory and every per-request token directory are `0700`.
- Per-client rate limiting behind a trusted proxy (`app.trusted-proxy=true`): the client is
  taken from `X-Real-IP`, else the last `X-Forwarded-For` hop; the client-controlled first
  hop is no longer trusted.
- Tests no longer write into the production temp directory.

## [1.1.0] - 2026-11-06

### Added
- **SSH-only mode**: Ed25519 (default) or RSA 4096, optional comment and passphrase,
  `SHA256:` fingerprint and a Copy button; `POST /api/generate/ssh`; `keyjolt.dev/#ssh`.
- Keys in the modern OpenSSH format via Apache MINA sshd-common (replaces JSch).

### Security
- **Unguessable download URLs**: `/download/<128-bit token>/<filename>` with strict
  validation, `Cache-Control: no-store` and `0600` private key files. The old
  `/download/{filename}` route is gone.

## [1.0.1-secure] - 2025-07-09

### Security
- HTTP security headers (HSTS, CSP, X-XSS-Protection, Referrer-Policy) and a
  `SecurityFilterChain` configuration; stateless sessions; WordPress probe paths denied.

### Changed
- Removed unused files, dependencies and frontend debug code; added
  `application-example.properties`.

[Unreleased]: https://github.com/hexawulf/KeyJolt/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/hexawulf/KeyJolt/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/hexawulf/KeyJolt/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/hexawulf/KeyJolt/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/hexawulf/KeyJolt/compare/v1.1.5...v1.2.0
[1.1.5]: https://github.com/hexawulf/KeyJolt/compare/v1.1.4...v1.1.5
[1.1.4]: https://github.com/hexawulf/KeyJolt/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/hexawulf/KeyJolt/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/hexawulf/KeyJolt/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/hexawulf/KeyJolt/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/hexawulf/KeyJolt/compare/v1.0.1-secure...v1.1.0
[1.0.1-secure]: https://github.com/hexawulf/KeyJolt/releases/tag/v1.0.1-secure
