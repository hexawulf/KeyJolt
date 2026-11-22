#!/usr/bin/env node
// Boot the built KeyJolt JAR in isolation, run the Playwright smoke suite
// against it, stop it with SIGTERM and verify the shutdown contract:
//
//   * the app gets its own app.temp-dir and LOG_DIR (startup purges leftover
//     request dirs in app.temp-dir, so it must never share production's)
//   * after SIGTERM every pending key file is gone (temp dir empty)
//   * the app log never contains a download token
//
// Usage (from e2e/):  npm run test:e2e   [-- extra playwright args]
// Env: JAVA_HOME (else `java` on PATH), KEYJOLT_JAR, KEYJOLT_PORT (5095),
//      KEYJOLT_TEMP_DIR (default /tmp/keyjolt-e2e-<pid>; must not be production's),
//      KEYJOLT_KEEP_LOGS=1 to keep the log dir for inspection.
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const port = process.env.KEYJOLT_PORT || '5095';
const baseURL = `http://127.0.0.1:${port}`;
const tempDir = process.env.KEYJOLT_TEMP_DIR || `/tmp/keyjolt-e2e-${process.pid}`;
const logDir = `/tmp/keyjolt-e2e-logs-${process.pid}`;

function log(msg) {
  console.log(`[e2e] ${msg}`);
}

function findJar() {
  if (process.env.KEYJOLT_JAR) return resolve(process.env.KEYJOLT_JAR);
  const target = join(root, 'target');
  const jars = existsSync(target)
    ? readdirSync(target).filter((f) => /^keyjolt-.*\.jar$/.test(f) && !f.endsWith('.original'))
    : [];
  if (jars.length !== 1) {
    throw new Error(`expected exactly one target/keyjolt-*.jar, found ${jars.length}; run "mvn -B verify" first`);
  }
  return join(target, jars[0]);
}

function javaBin() {
  return process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
}

async function waitForHealth(child, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error(`app exited early with code ${child.exitCode}`);
    try {
      const res = await fetch(`${baseURL}/healthz`);
      if (res.ok) return;
    } catch {
      /* not up yet */
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`app did not answer /healthz within ${timeoutMs / 1000}s`);
}

function stop(child, timeoutMs = 20_000) {
  return new Promise((res) => {
    if (child.exitCode !== null || child.signalCode !== null) return res({ code: child.exitCode, signal: child.signalCode });
    const timer = setTimeout(() => {
      log('app did not stop within 20 s after SIGTERM; sending SIGKILL');
      child.kill('SIGKILL');
    }, timeoutMs);
    child.once('exit', (code, signal) => {
      clearTimeout(timer);
      res({ code, signal });
    });
    child.kill('SIGTERM');
  });
}

function listFiles(dir) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(p + '/', ...listFiles(p));
    else out.push(p);
  }
  return out;
}

function runPlaywright(extraArgs) {
  return new Promise((res) => {
    const pw = spawn('npx', ['playwright', 'test', ...extraArgs], {
      cwd: here,
      stdio: 'inherit',
      env: { ...process.env, KEYJOLT_BASE_URL: baseURL, KEYJOLT_LOCAL: '1' },
    });
    pw.on('exit', (code) => res(code ?? 1));
  });
}

async function main() {
  const jar = findJar();
  if (!/^\/tmp\/keyjolt-(e2e|ci-e2e)/.test(tempDir)) {
    // Startup purges every request dir in app.temp-dir: refuse anything that
    // could be a real instance's directory (e.g. /tmp/keyjolt).
    throw new Error(`refusing KEYJOLT_TEMP_DIR=${tempDir}: must start with /tmp/keyjolt-e2e or /tmp/keyjolt-ci-e2e`);
  }
  mkdirSync(logDir, { recursive: true, mode: 0o700 });
  log(`jar: ${jar}`);
  log(`temp dir: ${tempDir}  log dir: ${logDir}`);

  const child = spawn(javaBin(), [
    '-jar', jar,
    `--server.port=${port}`,
    '--server.address=127.0.0.1',
    `--app.temp-dir=${tempDir}`,
    '--app.trusted-proxy=true',
  ], {
    cwd: logDir,
    env: { ...process.env, LOG_DIR: logDir },
    stdio: ['ignore', 'ignore', 'inherit'],
  });

  let failures = 0;
  try {
    await waitForHealth(child);
    log(`app is up on ${baseURL} (pid ${child.pid})`);
    const code = await runPlaywright(process.argv.slice(2));
    if (code !== 0) {
      failures++;
      log(`playwright exited with ${code}`);
    }
  } catch (err) {
    failures++;
    log(`error: ${err.message}`);
  } finally {
    const { code, signal } = await stop(child);
    log(`app stopped (exit code ${code}, signal ${signal})`);
    if (signal === 'SIGKILL') failures++;
  }

  // Shutdown contract: no key file may survive a clean stop.
  const leftovers = listFiles(tempDir);
  if (leftovers.length) {
    failures++;
    log(`FAIL: ${leftovers.length} entr(y/ies) left in ${tempDir} after SIGTERM:`);
    leftovers.forEach((f) => log(`  ${f}`));
  } else {
    log(`OK: ${tempDir} is empty after SIGTERM`);
  }

  const logFile = join(logDir, 'keyjolt.log');
  if (existsSync(logFile)) {
    const text = readFileSync(logFile, 'utf8');
    if (/[a-f0-9]{32}/.test(text)) {
      failures++;
      log('FAIL: the app log contains something that looks like a download token');
    } else {
      log('OK: no download token in the app log');
    }
    const shutdownLine = text.split('\n').find((l) => l.includes('Shutdown cleanup:'));
    if (shutdownLine) log(`app log: ${shutdownLine.replace(/^.* - /, '')}`);
    else {
      failures++;
      log('FAIL: no "Shutdown cleanup" line in the app log (shutdown hook did not run)');
    }
  } else {
    failures++;
    log(`FAIL: no app log at ${logFile}`);
  }

  rmSync(tempDir, { recursive: true, force: true });
  if (!process.env.KEYJOLT_KEEP_LOGS) rmSync(logDir, { recursive: true, force: true });

  log(failures ? `FAILED (${failures})` : 'PASSED');
  process.exit(failures ? 1 : 0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
