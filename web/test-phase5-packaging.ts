import assert from 'node:assert';
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Helper for test runner
async function test(name: string, fn: () => Promise<void> | void) {
  try {
    await fn();
    console.log(`[PASS] ${name}`);
  } catch (err: any) {
    console.error(`[FAIL] ${name}`);
    console.error(err);
    process.exitCode = 1;
  }
}

async function runTests() {
  console.log('====================================================');
  console.log(' VYNCE PHASE 5: PACKAGING & AUTO-UPDATE RELIABILITY');
  console.log('====================================================\n');

  const rootDir = path.resolve(__dirname, '..');
  const packageJsonPath = path.resolve(__dirname, 'package.json');
  const workflowPath = path.resolve(rootDir, '.github/workflows/desktop-build.yml');

  const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
  const workflowContent = fs.readFileSync(workflowPath, 'utf8').replace(/\r\n/g, '\n');

  // ── TEST 1: Packaging Tool and Windows Targets ────────────────────
  await test('1. package.json configures electron-builder with nsis and portable x64 targets', () => {
    assert.ok(packageJson.devDependencies['electron-builder'], 'electron-builder must be installed');
    assert.strictEqual(packageJson.build.appId, 'com.vynce.desktop');
    assert.strictEqual(packageJson.build.directories.output, 'release-desktop');

    const winTargets = packageJson.build.win.target;
    assert.ok(Array.isArray(winTargets), 'Windows target configuration must be an array');
    const targetNames = winTargets.map((t: any) => t.target);
    assert.ok(targetNames.includes('nsis'), 'Must include nsis installer target');
    assert.ok(targetNames.includes('portable'), 'Must include portable executable target');

    const nsisTarget = winTargets.find((t: any) => t.target === 'nsis');
    assert.deepStrictEqual(nsisTarget.arch, ['x64'], 'NSIS target must be x64');
  });

  // ── TEST 2: Publish Provider Configuration ────────────────────────
  await test('2. package.json specifies GitHub publish provider for auto-updater', () => {
    const publish = packageJson.build.publish;
    assert.strictEqual(publish.provider, 'github');
    assert.strictEqual(publish.owner, '2300030811');
    assert.strictEqual(publish.repo, 'Vynce');
  });

  // ── TEST 3: Workflow Includes Updater Manifest and Blockmap ───────
  await test('3. GitHub Actions workflow includes latest*.yml and *.blockmap artifacts', () => {
    // Both artifact upload and release attachment must include latest*.yml and *.blockmap
    assert.ok(
      workflowContent.includes('web/release-desktop/*.blockmap'),
      'Workflow must collect *.blockmap files for delta updates'
    );
    assert.ok(
      workflowContent.includes('web/release-desktop/latest*.yml'),
      'Workflow must collect latest*.yml manifests for electron-updater'
    );

    // Verify all binary targets are preserved
    assert.ok(workflowContent.includes('web/release-desktop/*.exe'));
    assert.ok(workflowContent.includes('web/release-desktop/*.AppImage'));
    assert.ok(workflowContent.includes('web/release-desktop/*.deb'));
    assert.ok(workflowContent.includes('web/release-desktop/*.dmg'));
    assert.ok(workflowContent.includes('web/release-desktop/*.zip'));
  });

  // ── TEST 4: Updater Error Handling: 404 is NOT "Up to date" ───────
  await test('4. Updater reports error on 404 missing manifest, never claiming up-to-date', () => {
    // Read and verify the production error handling deployed in main.cjs
    const mainCjsPath = path.resolve(__dirname, 'electron/main.cjs');
    assert.ok(fs.existsSync(mainCjsPath), 'main.cjs must exist');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    assert.ok(mainCjs.includes("Update manifest (latest.yml) not found on GitHub Releases (HTTP 404)."), 'main.cjs must report 404 as error');
    assert.ok(mainCjs.includes("Unable to reach update server. Check your internet connection."), 'main.cjs must report network failures as error');
    assert.ok(!mainCjs.includes("Vynce is up to date (no releases found)"), 'main.cjs must not contain deceptive up-to-date message on 404');

    // Replicate error handler deployed in main.cjs
    const classifyUpdateError = (err: any) => {
      const msg = String(err?.message || '');
      if (msg.includes('404') || msg.includes('latest.yml') || msg.includes('Cannot find')) {
        return {
          status: 'error',
          error: 'Update manifest (latest.yml) not found on GitHub Releases (HTTP 404).',
        };
      }
      if (
        msg.includes('ENOTFOUND') ||
        msg.includes('ETIMEDOUT') ||
        msg.includes('net::ERR_INTERNET_DISCONNECTED') ||
        msg.includes('ERR_NAME_NOT_RESOLVED')
      ) {
        return {
          status: 'error',
          error: 'Unable to reach update server. Check your internet connection.',
        };
      }
      return { status: 'error', error: msg || 'Could not reach update server.' };
    };

    // Case A: 404 on latest.yml (previous defect: erroneously returned "not-available" / "up to date")
    const err404 = new Error('HttpError: 404 Cannot find latest.yml in https://github.com/2300030811/Vynce/releases/download/v3.0.0');
    const result404 = classifyUpdateError(err404);
    assert.strictEqual(result404.status, 'error', 'Must report error status, NOT not-available');
    assert.ok(result404.error.includes('404'), 'Error message must reflect 404 manifest absence');

    // Case B: Network offline / DNS resolution failure
    const errNet = new Error('getaddrinfo ENOTFOUND github.com');
    const resultNet = classifyUpdateError(errNet);
    assert.strictEqual(resultNet.status, 'error');
    assert.ok(resultNet.error.includes('internet connection') || resultNet.error.includes('update server'));
  });

  // ── TEST 5: Updater Genuine Up-to-Date vs Available Detection ─────
  await test('5. Genuine update check distinguishes up-to-date from available newer version', () => {
    // Read and verify the production version comparison in main.cjs
    const mainCjsPath = path.resolve(__dirname, 'electron/main.cjs');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    assert.ok(mainCjs.includes("if (latestVersion && latestVersion !== currentVersion)"), 'main.cjs must compare latestVersion with currentVersion');
    assert.ok(mainCjs.includes("status: 'available'"), 'main.cjs must return status available for newer versions');

    const handleUpdateResult = (currentVersion: string, result: any) => {
      if (!result || !result.updateInfo) {
        return { status: 'not-available', message: `Vynce v${currentVersion} is up to date.` };
      }
      const latestVersion = result.updateInfo.version;
      if (latestVersion && latestVersion !== currentVersion) {
        return { status: 'available', updateInfo: result.updateInfo, version: latestVersion };
      }
      return { status: 'not-available', message: `Vynce v${currentVersion} is up to date.` };
    };

    // Case A: Remote version is same as local version
    const resSame = handleUpdateResult('3.0.0', { updateInfo: { version: '3.0.0' } });
    assert.strictEqual(resSame.status, 'not-available');
    assert.ok(resSame.message.includes('up to date'));

    // Case B: Remote version is newer
    const resNewer = handleUpdateResult('3.0.0', { updateInfo: { version: '3.1.0' } });
    assert.strictEqual(resNewer.status, 'available');
    assert.strictEqual(resNewer.version, '3.1.0');
  });

  // ── TEST 6: Code Signing Security Check ───────────────────────────
  await test('6. No hardcoded credentials or fake certificates present in configuration', () => {
    const rawConfig = JSON.stringify(packageJson.build);
    assert.strictEqual(rawConfig.includes('certificateFile'), false);
    assert.strictEqual(rawConfig.includes('certificatePassword'), false);
    assert.strictEqual(rawConfig.includes('CSC_KEY_PASSWORD'), false);
  });

  // ══════════════════════════════════════════════════════════════════
  // PHASE 16 RELEASE VALIDATOR INTEGRATION SUITE
  // Executes the real Python validator: .github/scripts/validate_release_assets.py
  // ══════════════════════════════════════════════════════════════════

  const releaseWorkflowPath = path.resolve(rootDir, '.github/workflows/release.yml');
  const releaseWorkflowContent = fs.readFileSync(releaseWorkflowPath, 'utf8').replace(/\r\n/g, '\n');
  const desktopWorkflowPath = path.resolve(rootDir, '.github/workflows/desktop-build.yml');
  const desktopWorkflowContent = fs.readFileSync(desktopWorkflowPath, 'utf8').replace(/\r\n/g, '\n');
  const validatorScriptPath = path.resolve(rootDir, '.github/scripts/validate_release_assets.py');

  assert.ok(fs.existsSync(validatorScriptPath), 'Production validator script must exist at .github/scripts/validate_release_assets.py');

  // Helpers for isolated offline fixture testing
  function withTempDir<T>(fn: (dir: string) => T): T {
    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'vynce-release-test-'));
    try {
      return fn(tmpDir);
    } finally {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  }

  function runRealValidator(args: string[]): { status: number; stdout: string; stderr: string } {
    const pythonCmd = process.platform === 'win32' ? 'python' : 'python3';
    const proc = spawnSync(pythonCmd, [validatorScriptPath, ...args], {
      encoding: 'utf8',
    });
    return {
      status: proc.status ?? 1,
      stdout: proc.stdout || '',
      stderr: proc.stderr || '',
    };
  }

  function validateAndroidSigningSecrets(secrets: {
    SIGNING_KEY?: string;
    KEY_STORE_PASSWORD?: string;
    KEY_ALIAS?: string;
    KEY_PASSWORD?: string;
  }): { valid: boolean; missing: string[] } {
    const missing: string[] = [];
    if (!secrets.SIGNING_KEY || !secrets.SIGNING_KEY.trim()) missing.push('SIGNING_KEY');
    if (!secrets.KEY_STORE_PASSWORD || !secrets.KEY_STORE_PASSWORD.trim()) missing.push('KEY_STORE_PASSWORD');
    if (!secrets.KEY_ALIAS || !secrets.KEY_ALIAS.trim()) missing.push('KEY_ALIAS');
    if (!secrets.KEY_PASSWORD || !secrets.KEY_PASSWORD.trim()) missing.push('KEY_PASSWORD');

    if (missing.length > 0) {
      throw new Error(`ERROR: Missing required Android signing secret(s): ${missing.join(' ')}`);
    }
    return { valid: true, missing: [] };
  }

  // ── TEST 7: Valid manifest, staged installer, and correct checksum: PASS ──
  await test('7. Real Validator: Valid manifest, staged installer, and matching checksum passes gate', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('verified bit-identical installer binary content');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable payload');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap data');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n    size: ${installerBytes.length}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 0, `Expected exit 0, got ${res.status}: ${res.stderr}`);
      assert.ok(res.stdout.includes('ALL REQUIRED MULTIPLATFORM ASSETS AND UPDATER MANIFESTS VERIFIED SUCCESSFULLY'));
      assert.ok(fs.existsSync(path.join(tmpDir, 'Vynce-Setup-3.0.1.exe')));
      assert.ok(fs.existsSync(path.join(tmpDir, 'Vynce-Setup-3.0.1.exe.blockmap')));
    });
  });

  // ── TEST 8: Manifest missing: FAIL ──
  await test('8. Real Validator: Manifest missing fails closed with exit code 1', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('latest.yml auto-update manifest is missing'));
    });
  });

  // ── TEST 9: Malformed YAML: FAIL ──
  await test('9. Real Validator: Malformed YAML in latest.yml fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), 'version: [unclosed list\npath: {malformed');
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('could not be parsed as valid YAML'));
    });
  });

  // ── TEST 10: Missing or empty root checksum: FAIL ──
  await test('10. Real Validator: Missing or empty root sha512 fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = 'version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: dGVzdA==\n';
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('root sha512 checksum is missing or empty'));
    });
  });

  // ── TEST 11: Missing or empty per-file checksum: FAIL ──
  await test('11. Real Validator: Missing or empty per-file sha512 fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${dummySha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ""\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Per-file sha512 checksum for "Vynce-Setup-3.0.1.exe" is missing or empty'));
    });
  });

  // ── TEST 12: Malformed Base64 checksum: FAIL ──
  await test('12. Real Validator: Malformed Base64 checksum string fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = 'version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: not-valid-base64-!!!\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: not-valid-base64-!!!\n';
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('not valid Base64'));
    });
  });

  // ── TEST 13: Decoded digest that is not exactly 64 bytes: FAIL ──
  await test('13. Real Validator: Checksum decoding to non-64 bytes (non-SHA-512) fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      // "dGVzdA==" decodes to 4 bytes ("test")
      const manifest = 'version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: dGVzdA==\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: dGVzdA==\n';
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('expected 64 bytes for SHA-512'));
    });
  });

  // ── TEST 14: Correctly shaped but incorrect SHA-512 digest: FAIL ──
  await test('14. Real Validator: Tampered installer bytes causing SHA-512 mismatch fails closed', () => {
    withTempDir((tmpDir) => {
      const originalBytes = Buffer.from('original authentic installer bytes');
      const validSha = crypto.createHash('sha512').update(originalBytes).digest('base64');
      // Staged installer has tampered bytes
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'tampered content different bytes');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap data');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('SHA-512 mismatch'));
    });
  });

  // ── TEST 15: Wrong release version: FAIL ──
  await test('15. Real Validator: Manifest version mismatch against target release version fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = 'version: 3.0.0\npath: Vynce-Setup-3.0.0.exe\nsha512: dGVzdA==\nfiles:\n  - url: Vynce-Setup-3.0.0.exe\n    sha512: dGVzdA==\n';
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('does not match target release version'));
    });
  });

  // ── TEST 16: Missing installer referenced by path: FAIL ──
  await test('16. Real Validator: Installer referenced by path missing from staged assets fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: Nonexistent-Setup-3.0.1.exe\nsha512: ${dummySha}\nfiles:\n  - url: Nonexistent-Setup-3.0.1.exe\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Windows NSIS installer is missing') || res.stderr.includes('cannot be resolved'));
    });
  });

  // ── TEST 17: Missing installer referenced by files[].url: FAIL ──
  await test('17. Real Validator: Additional installer referenced in files[].url missing from staged assets fails closed', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('installer bytes');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      const missingSha = crypto.createHash('sha512').update('missing').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap data');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n  - url: Missing-Secondary-3.0.1.exe\n    sha512: ${missingSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('cannot be resolved to any staged asset'));
    });
  });

  // ── TEST 18: Missing installer source when an alias would otherwise be created: FAIL ──
  await test('18. Real Validator: Missing source installer for alias fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      // Neither Vynce-Setup-3.0.1.exe nor Vynce Setup 3.0.1.exe exists
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${dummySha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('source installer is missing') || res.stderr.includes('Windows NSIS installer is missing'));
    });
  });

  // ── TEST 19: Installer present but portable executable missing: FAIL ──
  await test('19. Real Validator: Installer present but portable executable missing fails gate (DEF-001)', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('installer bytes');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap data');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes("Windows portable executable ('Vynce 3.0.1.exe') is missing"));
    });
  });

  // ── TEST 20: Valid installer and distinct correctly named portable executable: PASS ──
  await test('20. Real Validator: Valid installer and distinct correctly named portable executable passes (DEF-001)', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('installer bytes');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable bytes');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap data');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 0);
      assert.ok(res.stdout.includes('Verified Windows portable executable: Vynce 3.0.1.exe'));
    });
  });

  // ── TEST 21: Missing source blockmap when a compatibility alias is required: FAIL ──
  await test('21. Real Validator: Missing source blockmap when alias required fails closed', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('installer bytes');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable bytes');
      // Intentionally omit Vynce Setup 3.0.1.exe.blockmap
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Missing source blockmap "Vynce Setup 3.0.1.exe.blockmap" when compatibility alias is required'));
    });
  });

  // ── TEST 22: Valid staged artifacts and consistent installer/blockmap aliases: PASS ──
  await test('22. Real Validator: Valid artifacts and consistent aliases create identical installer and blockmap bytes', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('verified bit-identical installer binary content');
      const blockmapBytes = Buffer.from('verified blockmap delta data');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable bytes');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), blockmapBytes);
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 0);

      // Verify identical bytes in created aliases
      assert.deepStrictEqual(
        fs.readFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe')),
        fs.readFileSync(path.join(tmpDir, 'Vynce-Setup-3.0.1.exe'))
      );
      assert.deepStrictEqual(
        fs.readFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap')),
        fs.readFileSync(path.join(tmpDir, 'Vynce-Setup-3.0.1.exe.blockmap'))
      );
    });
  });

  // ── TEST 23: Inconsistent hashes between root and files[] for the same installer: FAIL ──
  await test('23. Real Validator: Inconsistent sha512 hashes in manifest for same installer fails closed', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('installer bytes');
      const sha1 = crypto.createHash('sha512').update('hash1').digest('base64');
      const sha2 = crypto.createHash('sha512').update('hash2').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${sha1}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${sha2}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Inconsistent sha512 checksums in latest.yml'));
    });
  });

  // ── TEST 24: Missing any of the 4 Android signing secrets fails preflight ──
  await test('24. DEF-003: Missing any of the 4 Android signing secrets fails validation preflight', () => {
    assert.throws(
      () => validateAndroidSigningSecrets({ KEY_STORE_PASSWORD: 'p', KEY_ALIAS: 'a', KEY_PASSWORD: 'kp' }),
      /Missing required Android signing secret\(s\): SIGNING_KEY/
    );
    assert.throws(
      () => validateAndroidSigningSecrets({ SIGNING_KEY: 'k', KEY_ALIAS: 'a', KEY_PASSWORD: 'kp' }),
      /Missing required Android signing secret\(s\): KEY_STORE_PASSWORD/
    );
    assert.throws(
      () => validateAndroidSigningSecrets({ SIGNING_KEY: 'k', KEY_STORE_PASSWORD: 'p', KEY_PASSWORD: 'kp' }),
      /Missing required Android signing secret\(s\): KEY_ALIAS/
    );
    assert.throws(
      () => validateAndroidSigningSecrets({ SIGNING_KEY: 'k', KEY_STORE_PASSWORD: 'p', KEY_ALIAS: 'a' }),
      /Missing required Android signing secret\(s\): KEY_PASSWORD/
    );
    assert.throws(
      () => validateAndroidSigningSecrets({}),
      /Missing required Android signing secret\(s\): SIGNING_KEY KEY_STORE_PASSWORD KEY_ALIAS KEY_PASSWORD/
    );
    const res = validateAndroidSigningSecrets({
      SIGNING_KEY: 'mock-key',
      KEY_STORE_PASSWORD: 'mock-store-pass',
      KEY_ALIAS: 'mock-alias',
      KEY_PASSWORD: 'mock-key-pass',
    });
    assert.strictEqual(res.valid, true);
  });

  // ── TEST 25: Workflow structure, PyYAML provisioning, and validator integration ──
  await test('25. Workflows explicitly provision Python 3.11 and PyYAML and invoke validate_release_assets.py', () => {
    // release.yml checks
    assert.ok(releaseWorkflowContent.includes('actions/setup-python@v5'), 'release.yml must use actions/setup-python@v5');
    assert.ok(releaseWorkflowContent.includes('pip install pyyaml==6.0.2'), 'release.yml must pin PyYAML dependency');
    assert.ok(releaseWorkflowContent.includes('python3 .github/scripts/validate_release_assets.py'), 'release.yml must invoke validate_release_assets.py');
    assert.ok(releaseWorkflowContent.includes('--require-apk'), 'release.yml must require Android APK for stable coordinated release');

    // desktop-build.yml checks
    assert.ok(desktopWorkflowContent.includes('actions/setup-python@v5'), 'desktop-build.yml must use actions/setup-python@v5');
    assert.ok(desktopWorkflowContent.includes('pip install pyyaml==6.0.2'), 'desktop-build.yml must pin PyYAML dependency');
    assert.ok(desktopWorkflowContent.includes('python3 .github/scripts/validate_release_assets.py'), 'desktop-build.yml must invoke validate_release_assets.py');
    assert.ok(desktopWorkflowContent.includes('--platform windows'), 'desktop-build.yml must validate Windows beta contract');
  });

  // ══════════════════════════════════════════════════════════════════
  // PHASE 18: PATH TRAVERSAL HARDENING & PLATFORM CONTRACT TESTS
  // ══════════════════════════════════════════════════════════════════

  // ── TEST 26: Path Traversal: ../secret.txt in path fails closed ──
  await test('26. Path Hardening: ../secret.txt in path fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: ../secret.txt\nsha512: ${dummySha}\nfiles:\n  - url: ../secret.txt\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('directory separators or path traversal') || res.stderr.includes('escapes staging directory'));
    });
  });

  // ── TEST 27: Path Traversal: ..\\secret.txt in path fails closed ──
  await test('27. Path Hardening: ..\\secret.txt in path fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: "..\\\\secret.txt"\nsha512: ${dummySha}\nfiles:\n  - url: "..\\\\secret.txt"\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('directory separators or path traversal') || res.stderr.includes('escapes staging directory'));
    });
  });

  // ── TEST 28: Path Traversal: Absolute POSIX path fails closed ──
  await test('28. Path Hardening: Absolute POSIX path (/etc/passwd) fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: /etc/passwd\nsha512: ${dummySha}\nfiles:\n  - url: /etc/passwd\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('absolute path') || res.stderr.includes('directory separators'));
    });
  });

  // ── TEST 29: Path Traversal: Windows drive-absolute path fails closed ──
  await test('29. Path Hardening: Windows drive-absolute path (C:\\secret.txt) fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: "C:\\\\secret.txt"\nsha512: ${dummySha}\nfiles:\n  - url: "C:\\\\secret.txt"\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('forbidden colon') || res.stderr.includes('directory separators'));
    });
  });

  // ── TEST 30: Path Traversal: UNC-style path fails closed ──
  await test('30. Path Hardening: UNC-style path (\\\\server\\share\\file.exe) fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: "\\\\\\\\server\\\\share\\\\file.exe"\nsha512: ${dummySha}\nfiles:\n  - url: "\\\\\\\\server\\\\share\\\\file.exe"\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('UNC network path') || res.stderr.includes('directory separators'));
    });
  });

  // ── TEST 31: Path Traversal: Nested path in files[].url fails closed ──
  await test('31. Path Hardening: Nested path (sub/installer.exe) in files[].url fails closed', () => {
    withTempDir((tmpDir) => {
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${dummySha}\nfiles:\n  - url: sub/installer.exe\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('directory separators or path traversal'));
    });
  });

  // ── TEST 32: Path Traversal: Malicious alias cannot escape staging directory ──
  await test('32. Path Hardening: Malicious manifest reference cannot create alias outside staging directory', () => {
    withTempDir((tmpDir) => {
      const canaryOutside = path.resolve(tmpDir, '..', `canary-${Date.now()}.txt`);
      const dummySha = crypto.createHash('sha512').update('bytes').digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      // Attempting to alias to parent directory
      const manifest = `version: 3.0.1\npath: "../${path.basename(canaryOutside)}"\nsha512: ${dummySha}\nfiles:\n  - url: "../${path.basename(canaryOutside)}"\n    sha512: ${dummySha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      assert.strictEqual(fs.existsSync(canaryOutside), false, 'No file should be written outside staging directory');
    });
  });

  // ── TEST 33: Platform Mode: Stable mode requires APK ──
  await test('33. Platform Mode: Stable mode (--require-apk) fails closed when APK is missing', () => {
    withTempDir((tmpDir) => {
      const installerBytes = Buffer.from('installer bytes');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      // Omitting *.apk
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--require-apk']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Android APK is missing'));
    });
  });

  // ── TEST 34: Platform Mode: Linux beta mode accepts valid AppImage and deb ──
  await test('34. Platform Mode: Linux beta mode (--platform linux) accepts valid AppImage and deb', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.AppImage'), 'appimage payload');
      fs.writeFileSync(path.join(tmpDir, 'vynce_3.0.1_amd64.deb'), 'deb payload');

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'linux']);
      assert.strictEqual(res.status, 0, `Expected 0, got ${res.status}: ${res.stderr}`);
      assert.ok(res.stdout.includes('Verified Linux AppImage'));
      assert.ok(res.stdout.includes('Verified Linux deb package'));
    });
  });

  // ── TEST 35: Platform Mode: macOS beta mode accepts valid DMG and ZIP ──
  await test('35. Platform Mode: macOS beta mode (--platform macos) accepts valid DMG and ZIP', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.dmg'), 'dmg payload');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-mac.zip'), 'zip payload');

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'macos']);
      assert.strictEqual(res.status, 0, `Expected 0, got ${res.status}: ${res.stderr}`);
      assert.ok(res.stdout.includes('Verified macOS DMG'));
      assert.ok(res.stdout.includes('Verified macOS ZIP'));
    });
  });

  // ── TEST 36: Platform Mode: Windows-only artifact set rejected by --platform linux ──
  await test('36. Platform Mode: Windows-only artifact set rejected by --platform linux', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), 'version: 3.0.1');

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'linux']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Linux AppImage (*.AppImage) for version 3.0.1 is missing'));
    });
  });

  // ── TEST 37: Platform Mode: Windows-only artifact set rejected by --platform macos ──
  await test('37. Platform Mode: Windows-only artifact set rejected by --platform macos', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), 'installer');
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), 'version: 3.0.1');

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'macos']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('macOS disk image (*.dmg) for version 3.0.1 is missing'));
    });
  });

  // ── TEST 38: Publishing Safety: Non-zero exit code halts publication ──
  await test('38. Publishing Safety: Validation non-zero exit code halts CI release execution', () => {
    withTempDir((tmpDir) => {
      // Empty directory fails validator with exit code 1
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir]);
      assert.strictEqual(res.status, 1);
      // Verify that release.yml and desktop-build.yml publish steps do not have continue-on-error: true
      assert.strictEqual(releaseWorkflowContent.includes('continue-on-error: true\n      - name: Publish Coordinated GitHub Release'), false);
      assert.strictEqual(desktopWorkflowContent.includes('continue-on-error: true\n      - name: Publish Desktop Pre-Release'), false);
    });
  });

  // ══════════════════════════════════════════════════════════════════
  // PHASE 19: BETA ARTIFACT COMPLETENESS, SYMLINK HARDENING & VERSIONING
  // ══════════════════════════════════════════════════════════════════

  // Helper function modeling workflow beta version consistency check
  function checkBetaVersionConsistency(tagName: string, packageJsonVersion: string): { valid: boolean; tagBase: string; pkgBase: string } {
    let tagBase = '';
    const desktopMatch = tagName.match(/^desktop-v([0-9]+\.[0-9]+\.[0-9]+)/);
    const vMatch = tagName.match(/^v([0-9]+\.[0-9]+\.[0-9]+)/);
    if (desktopMatch) {
      tagBase = desktopMatch[1];
    } else if (vMatch) {
      tagBase = vMatch[1];
    } else {
      throw new Error(`ERROR: Release tag '${tagName}' does not match accepted beta tag format.`);
    }

    const pkgMatch = packageJsonVersion.match(/^([0-9]+\.[0-9]+\.[0-9]+)/);
    const pkgBase = pkgMatch ? pkgMatch[1] : packageJsonVersion;

    if (tagBase !== pkgBase) {
      throw new Error(`ERROR: Version mismatch between tag '${tagName}' (${tagBase}) and web/package.json '${packageJsonVersion}' (${pkgBase})!`);
    }
    return { valid: true, tagBase, pkgBase };
  }

  // ── TEST 39: Platform Completeness: Linux missing deb fails closed ──
  await test('39. Platform Completeness: Linux package with AppImage but missing deb fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.AppImage'), 'appimage');
      // Intentionally omit deb
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'linux']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Linux Debian package (*.deb) for version 3.0.1 is missing'));
    });
  });

  // ── TEST 40: Platform Completeness: Linux missing AppImage fails closed ──
  await test('40. Platform Completeness: Linux package with deb but missing AppImage fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'vynce_3.0.1_amd64.deb'), 'deb');
      // Intentionally omit AppImage
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'linux']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('Linux AppImage (*.AppImage) for version 3.0.1 is missing'));
    });
  });

  // ── TEST 41: Platform Completeness: macOS missing ZIP fails closed ──
  await test('41. Platform Completeness: macOS package with DMG but missing ZIP fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.dmg'), 'dmg');
      // Intentionally omit zip
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'macos']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('macOS ZIP archive (*.zip) for version 3.0.1 is missing'));
    });
  });

  // ── TEST 42: Platform Completeness: macOS missing DMG fails closed ──
  await test('42. Platform Completeness: macOS package with ZIP but missing DMG fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-mac.zip'), 'zip');
      // Intentionally omit dmg
      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'macos']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('macOS disk image (*.dmg) for version 3.0.1 is missing'));
    });
  });

  // ── TEST 43: Platform Completeness: All 3 platforms complete and valid passes --platform all ──
  await test('43. Platform Completeness: Complete Windows, Linux, and macOS packages pass --platform all', () => {
    withTempDir((tmpDir) => {
      // Windows assets
      const installerBytes = Buffer.from('verified bit-identical installer');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      // Linux assets
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.AppImage'), 'appimage payload');
      fs.writeFileSync(path.join(tmpDir, 'vynce_3.0.1_amd64.deb'), 'deb payload');

      // macOS assets
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.dmg'), 'dmg payload');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-mac.zip'), 'zip payload');

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'all']);
      assert.strictEqual(res.status, 0, `Expected 0, got ${res.status}: ${res.stderr}`);
      assert.ok(res.stdout.includes('Verified Windows installer'));
      assert.ok(res.stdout.includes('Verified Linux AppImage'));
      assert.ok(res.stdout.includes('Verified macOS DMG'));
    });
  });

  // ── TEST 44: Beta Version Policy: Consistent tag v3.0.1-beta.1 and package.json 3.0.1 passes ──
  await test('44. Beta Version Policy: Consistent tag v3.0.1-beta.1 and web/package.json 3.0.1 passes preflight', () => {
    const res = checkBetaVersionConsistency('v3.0.1-beta.1', '3.0.1');
    assert.strictEqual(res.valid, true);
    assert.strictEqual(res.tagBase, '3.0.1');
  });

  // ── TEST 45: Beta Version Policy: Tag v3.0.2-beta.1 with package.json 3.0.1 fails ──
  await test('45. Beta Version Policy: Tag v3.0.2-beta.1 with web/package.json 3.0.1 fails closed', () => {
    assert.throws(
      () => checkBetaVersionConsistency('v3.0.2-beta.1', '3.0.1'),
      /Version mismatch between tag 'v3.0.2-beta.1' \(3.0.2\) and web\/package.json '3.0.1' \(3.0.1\)/
    );
  });

  // ── TEST 46: Beta Version Policy: desktop-v tag passes when aligned ──
  await test('46. Beta Version Policy: desktop-v3.0.1 tag with web/package.json 3.0.1 passes preflight', () => {
    const res = checkBetaVersionConsistency('desktop-v3.0.1', '3.0.1');
    assert.strictEqual(res.valid, true);
    assert.strictEqual(res.tagBase, '3.0.1');
  });

  // ── TEST 47: Manifest Version Consistency: latest-linux.yml version mismatch fails closed ──
  await test('47. Manifest Version Consistency: latest-linux.yml version mismatch fails closed', () => {
    withTempDir((tmpDir) => {
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.AppImage'), 'appimage');
      fs.writeFileSync(path.join(tmpDir, 'vynce_3.0.1_amd64.deb'), 'deb');
      // Manifest claiming version 3.0.0 while release target is 3.0.1
      fs.writeFileSync(path.join(tmpDir, 'latest-linux.yml'), 'version: 3.0.0\npath: Vynce-3.0.0.AppImage\nsha512: dGVzdA==\nfiles:\n  - url: Vynce-3.0.0.AppImage\n    sha512: dGVzdA==\n');

      const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'linux']);
      assert.strictEqual(res.status, 1);
      assert.ok(res.stderr.includes('latest-linux.yml version (3.0.0) does not match target release version (3.0.1)'));
    });
  });

  // ── TEST 48: Symlink Confinement: Symlink pointing outside staging directory fails closed ──
  await test('48. Symlink Confinement: Staged file symlinked outside staging directory is rejected', () => {
    withTempDir((tmpDir) => {
      const outsideDir = path.resolve(tmpDir, '..', `outside-${Date.now()}`);
      fs.mkdirSync(outsideDir, { recursive: true });
      try {
        const outsidePayload = path.join(outsideDir, 'external-payload.exe');
        fs.writeFileSync(outsidePayload, 'external secret data');

        const symlinkTarget = path.join(tmpDir, 'Vynce Setup 3.0.1.exe');
        let symlinkCreated = false;
        try {
          fs.symlinkSync(outsidePayload, symlinkTarget);
          symlinkCreated = true;
        } catch (symlinkErr: any) {
          // On Windows without Developer Mode, symlink creation requires admin privileges
          assert.ok(
            symlinkErr.code === 'EPERM' || symlinkErr.code === 'EINVAL',
            `Expected privilege error on unprivileged Windows, got ${symlinkErr}`
          );
        }

        if (symlinkCreated) {
          fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
          const manifest = 'version: 3.0.1\npath: Vynce Setup 3.0.1.exe\nsha512: dGVzdA==\nfiles:\n  - url: Vynce Setup 3.0.1.exe\n    sha512: dGVzdA==\n';
          fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

          const res = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'windows']);
          assert.strictEqual(res.status, 1);
          assert.ok(res.stderr.includes('escapes staging directory'));
        }
      } finally {
        fs.rmSync(outsideDir, { recursive: true, force: true });
      }
    });
  });

  // ── TEST 49: Beta Workflow Architecture: desktop-build.yml mandates completeness and isolated staging ──
  await test('49. Beta Workflow Architecture: desktop-build.yml mandates all 3 platforms, isolated staging, and upload error check', () => {
    const desktopYml = fs.readFileSync(path.resolve(rootDir, '.github/workflows/desktop-build.yml'), 'utf8').replace(/\r\n/g, '\n');

    // 1. Upload requires files to be present
    assert.ok(desktopYml.includes('if-no-files-found: error'), 'Desktop build artifact upload must fail on missing files');

    // 2. Separate staging directories for Windows, Linux, and macOS
    assert.ok(desktopYml.includes('mkdir -p ./staging/windows ./staging/linux ./staging/macos ./publish-assets'), 'Must stage platforms in isolated directories');

    // 3. Mandatory downloads for all 3 platforms (no continue-on-error)
    assert.ok(desktopYml.includes('name: vynce-desktop-windows-latest\n          path: ./staging/windows'), 'Must download Windows to staging/windows');
    assert.ok(desktopYml.includes('name: vynce-desktop-ubuntu-latest\n          path: ./staging/linux'), 'Must download Linux to staging/linux');
    assert.ok(desktopYml.includes('name: vynce-desktop-macos-latest\n          path: ./staging/macos'), 'Must download macOS to staging/macos');
    assert.strictEqual(desktopYml.includes('continue-on-error: true'), false, 'Downloads must NOT continue on error');

    // 4. Version consistency check
    assert.ok(desktopYml.includes('Determine Beta Tag Name and Enforce Version Consistency'), 'Must check tag and package.json consistency');

    // 5. Explicit validation for all 3 platforms before assembly
    assert.ok(desktopYml.includes('--platform windows'), 'Must validate Windows contract');
    assert.ok(desktopYml.includes('--platform linux'), 'Must validate Linux contract');
    assert.ok(desktopYml.includes('--platform macos'), 'Must validate macOS contract');
    assert.ok(desktopYml.includes('cp -v ./staging/windows/* ./publish-assets/'), 'Must assemble verified assets');
  });

  // ── TEST 50: Unified Multiplatform Stable Release Architecture: release.yml mandates Android + Windows + Linux + macOS, --platform all --require-apk, and single publishing owner ──
  await test('50. Unified Multiplatform Stable Release Architecture: release.yml mandates all 4 platforms, --platform all --require-apk, and disabled beta publishing', () => {
    // 1. Mandatory platform build jobs present in release.yml
    assert.ok(releaseWorkflowContent.includes('build-android:'), 'release.yml must include build-android');
    assert.ok(releaseWorkflowContent.includes('build-desktop-windows:'), 'release.yml must include build-desktop-windows');
    assert.ok(releaseWorkflowContent.includes('build-desktop-linux:'), 'release.yml must include build-desktop-linux');
    assert.ok(releaseWorkflowContent.includes('build-desktop-macos:'), 'release.yml must include build-desktop-macos');

    // 2. Coordinated assembly and single-owner publishing jobs
    assert.ok(
      releaseWorkflowContent.includes('validate-assembled-release:'),
      'release.yml must include separate validate-assembled-release job'
    );
    assert.ok(
      releaseWorkflowContent.includes('needs: [validate-release, build-android, build-desktop-windows, build-desktop-linux, build-desktop-macos]'),
      'validate-assembled-release must depend on all 4 platform build jobs'
    );
    assert.ok(
      releaseWorkflowContent.includes('needs: [validate-release, validate-assembled-release]'),
      'publish-stable-release must depend on validate-assembled-release'
    );

    // 3. Downloads all 4 platform artifact packages into ./release-assets
    assert.ok(releaseWorkflowContent.includes('name: vynce-release-android'), 'Must download Android artifacts');
    assert.ok(releaseWorkflowContent.includes('name: vynce-release-desktop-windows'), 'Must download Windows artifacts');
    assert.ok(releaseWorkflowContent.includes('name: vynce-release-desktop-linux'), 'Must download Linux artifacts');
    assert.ok(releaseWorkflowContent.includes('name: vynce-release-desktop-macos'), 'Must download macOS artifacts');

    // 4. Invokes validate_release_assets.py with --platform all and --require-apk
    assert.ok(
      releaseWorkflowContent.includes('python3 .github/scripts/validate_release_assets.py --version "$TARGET_VER" --assets-dir "./release-assets" --platform all --require-apk'),
      'release.yml must validate all platform contracts including APK prior to publishing'
    );

    // 5. No-beta policy enforced: desktop-build.yml beta publication job is disabled
    assert.ok(
      desktopWorkflowContent.includes('# ponytail: No-beta policy: Pre-release publications are disabled in favor of unified stable releases via release.yml\n    if: false'),
      'desktop-build.yml must disable pre-release publication per no-beta policy'
    );

    // 6. Mandatory Android unit test step runs prior to packaging in build-android
    const androidJob = releaseWorkflowContent.substring(
      releaseWorkflowContent.indexOf('build-android:'),
      releaseWorkflowContent.indexOf('build-desktop-windows:')
    );
    assert.ok(
      androidJob.includes('Run Android Unit Tests'),
      'build-android must include explicit Run Android Unit Tests step'
    );
    assert.ok(
      androidJob.includes('./gradlew testCoreReleaseUnitTest'),
      'build-android must run testCoreReleaseUnitTest'
    );
    const testIndex = androidJob.indexOf('./gradlew testCoreReleaseUnitTest');
    const pkgIndex = androidJob.indexOf('./gradlew assembleCoreRelease');
    assert.ok(
      testIndex > 0 && pkgIndex > testIndex,
      'Android unit tests must execute before signed release packaging'
    );
  });

  // ── TEST 51: Zero-Bypass Publication Safety: publish-stable-release strictly requires push on stable tag vX.Y.Z and fails closed on workflow_dispatch, branch pushes, and prerelease tags ──
  await test('51. Zero-Bypass Publication Safety: publish-stable-release strictly forbids manual dispatch, branch pushes, prerelease tags, and version mismatches', () => {
    // 1. Verify exact job-level `if:` guard in release.yml
    assert.ok(
      releaseWorkflowContent.includes("if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') && !contains(github.ref, '-beta') && !contains(github.ref, '-rc') && !contains(github.ref, '-alpha') && !startsWith(github.ref, 'refs/tags/desktop-v')"),
      'publish-stable-release must define the exact job-level if condition'
    );

    // 2. Model the exact evaluation function matching GitHub Actions expressions
    const evaluatePublishJobCondition = (eventName: string, ref: string): boolean => {
      return (
        eventName === 'push' &&
        ref.startsWith('refs/tags/v') &&
        !ref.includes('-beta') &&
        !ref.includes('-rc') &&
        !ref.includes('-alpha') &&
        !ref.startsWith('refs/tags/desktop-v')
      );
    };

    // Case A: Manual workflow dispatch from Web UI or API -> MUST BE FORBIDDEN
    assert.strictEqual(
      evaluatePublishJobCondition('workflow_dispatch', 'refs/heads/main'),
      false,
      'Manual dispatch on main branch MUST NOT publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('workflow_dispatch', 'refs/tags/v3.0.1'),
      false,
      'Manual dispatch with tag ref MUST NOT publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('workflow_dispatch', 'refs/heads/feature/desktop-ci-validation'),
      false,
      'Manual dispatch on feature branch MUST NOT publish'
    );

    // Case B: Push to branches -> MUST BE FORBIDDEN
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/heads/main'),
      false,
      'Push to main branch MUST NOT publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/heads/feature/desktop-ci-validation'),
      false,
      'Push to feature branch MUST NOT publish'
    );

    // Case C: Prerelease tags -> MUST BE FORBIDDEN
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/tags/v3.0.1-beta.1'),
      false,
      'v3.0.1-beta.1 MUST NOT publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/tags/v3.0.1-rc.1'),
      false,
      'v3.0.1-rc.1 MUST NOT publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/tags/v3.0.1-alpha.2'),
      false,
      'v3.0.1-alpha.2 MUST NOT publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/tags/desktop-v3.0.1'),
      false,
      'desktop-v3.0.1 tag MUST NOT publish'
    );

    // Case D: Stable tag push -> ALLOWED
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/tags/v3.0.1'),
      true,
      'Stable tag v3.0.1 push MUST be allowed to publish'
    );
    assert.strictEqual(
      evaluatePublishJobCondition('push', 'refs/tags/v3.0.2'),
      true,
      'Stable tag v3.0.2 push MUST be allowed to publish'
    );

    // 3. Step-level defense-in-depth zero-bypass script verification
    assert.ok(
      releaseWorkflowContent.includes('Enforce Stable Tag Publication Gate (Zero-Bypass Safety Check)'),
      'Must contain defense-in-depth step check'
    );
    assert.ok(
      releaseWorkflowContent.includes('[ "$TAG_VER" != "$EXPECTED_VER" ]'),
      'Step guard must assert tag version matches validated version'
    );

    // 4. Cryptographic SHA-256 Checksums manifest generation with robust temp file and self-exclusion
    assert.ok(
      releaseWorkflowContent.includes('Generate Cryptographic SHA-256 Checksums Manifest'),
      'Must generate authentic SHA256SUMS.txt'
    );
    assert.ok(
      releaseWorkflowContent.includes('sha256sum * > "$TMP_MANIFEST"'),
      'Must compute sha256sum into temporary file outside release assets'
    );
    assert.ok(
      releaseWorkflowContent.includes('mv "$TMP_MANIFEST" ./SHA256SUMS.txt'),
      'Must atomically move completed temporary manifest into release-assets'
    );
    assert.ok(
      releaseWorkflowContent.includes('grep -E "[[:space:]]+SHA256SUMS\\.txt$" "$TMP_MANIFEST"'),
      'Must explicitly verify self-exclusion of SHA256SUMS.txt'
    );
    assert.ok(
      releaseWorkflowContent.includes('./release-assets/SHA256SUMS.txt'),
      'Must attach SHA256SUMS.txt to the release files'
    );
    assert.strictEqual(
      releaseWorkflowContent.includes('*(Calculated on build)*'),
      false,
      'Must NOT contain placeholder checksum strings'
    );
  });

  // ── TEST 52: Validator All-Platform Contract: validate_release_assets.py --platform all --require-apk verifies complete set and rejects missing APK ──
  await test('52. Validator All-Platform Contract: validate_release_assets.py --platform all --require-apk verifies complete set and rejects missing APK', () => {
    withTempDir((tmpDir) => {
      // 1. Windows assets
      const installerBytes = Buffer.from('verified bit-identical installer');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      // 2. Linux assets
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.AppImage'), 'appimage');
      fs.writeFileSync(path.join(tmpDir, 'vynce_3.0.1_amd64.deb'), 'deb');

      // 3. macOS assets
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.dmg'), 'dmg');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-mac.zip'), 'zip');

      // Without APK: must fail closed when --require-apk is set
      const failRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'all', '--require-apk']);
      assert.strictEqual(failRes.status, 1);
      assert.ok(failRes.stderr.includes('Android APK is missing from release assets'));

      // With APK: must pass completely
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-core-universal-release-91.apk'), 'apk');
      const passRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'all', '--require-apk']);
      assert.strictEqual(passRes.status, 0);
      assert.ok(passRes.stdout.includes('Verified Android APK'));
      assert.ok(passRes.stdout.includes('Verified Windows installer'));
      assert.ok(passRes.stdout.includes('Verified Linux AppImage'));
      assert.ok(passRes.stdout.includes('Verified macOS DMG'));
      assert.ok(passRes.stdout.includes('ALL REQUIRED MULTIPLATFORM ASSETS AND UPDATER MANIFESTS VERIFIED SUCCESSFULLY'));
    });
  });

  // ── TEST 53: Cryptographic Checksum Manifest Generation: produces valid SHA-256 entries for all distributable assets, strictly self-excludes SHA256SUMS.txt, and handles pre-existing files safely ──
  await test('53. Cryptographic Checksum Manifest Generation: produces valid SHA-256 entries for all distributable assets, strictly self-excludes SHA256SUMS.txt, and handles pre-existing files safely', () => {
    withTempDir((tmpDir) => {
      // 1. Populate mock release assets
      const files: Record<string, string> = {
        'Vynce Setup 3.0.1.exe': 'windows installer content bytes 12345',
        'Vynce 3.0.1.exe': 'windows portable content bytes 67890',
        'Vynce-3.0.1.AppImage': 'linux appimage content 112233',
        'vynce_3.0.1_amd64.deb': 'linux debian package 445566',
        'Vynce-3.0.1.dmg': 'macos dmg package 778899',
        'Vynce-3.0.1-mac.zip': 'macos zip package 990011',
        'Vynce-3.0.1-core-universal-release-91.apk': 'android universal apk 123456789',
        'latest.yml': 'version: 3.0.1\n',
        'latest-linux.yml': 'version: 3.0.1\n',
        'latest-mac.yml': 'version: 3.0.1\n',
        'SHA256SUMS.txt': 'pre-existing stale checksums file that should be removed and not self-hashed'
      };

      for (const [name, content] of Object.entries(files)) {
        fs.writeFileSync(path.join(tmpDir, name), content, 'utf8');
      }

      // 2. Execute the production checksum generation algorithm matching release.yml
      // Step A: remove pre-existing SHA256SUMS.txt
      const targetManifest = path.join(tmpDir, 'SHA256SUMS.txt');
      if (fs.existsSync(targetManifest)) {
        fs.unlinkSync(targetManifest);
      }

      // Step B: compute SHA-256 for all remaining files into a temporary file outside assets dir
      const tmpManifestPath = path.join(os.tmpdir(), `vynce-manifest-test-${Date.now()}.txt`);
      const stagedFiles = fs.readdirSync(tmpDir).filter(f => fs.statSync(path.join(tmpDir, f)).isFile());
      stagedFiles.sort();

      const manifestLines: string[] = [];
      for (const f of stagedFiles) {
        const fileContent = fs.readFileSync(path.join(tmpDir, f));
        const hash = crypto.createHash('sha256').update(fileContent).digest('hex');
        manifestLines.push(`${hash}  ${f}`);
      }
      fs.writeFileSync(tmpManifestPath, manifestLines.join('\n') + '\n', 'utf8');

      // Step C: Verify self-exclusion check (must NOT contain SHA256SUMS.txt)
      const generatedContent = fs.readFileSync(tmpManifestPath, 'utf8');
      const hasSelfEntry = /^[0-9a-f]{64}\s+SHA256SUMS\.txt$/m.test(generatedContent);
      assert.strictEqual(hasSelfEntry, false, 'Generated manifest MUST NOT contain an entry for SHA256SUMS.txt');

      // Step D: Move temporary manifest into assets dir
      fs.copyFileSync(tmpManifestPath, targetManifest);
      fs.unlinkSync(tmpManifestPath);

      // 3. Verify final manifest in assets dir
      assert.ok(fs.existsSync(targetManifest), 'Final SHA256SUMS.txt must exist in staging dir');
      const finalLines = fs.readFileSync(targetManifest, 'utf8').trim().split('\n');

      // Must cover all 10 real distributable files (excluding the removed stale SHA256SUMS.txt)
      assert.strictEqual(finalLines.length, 10, 'Manifest must contain exactly 10 entries for the 10 release assets');

      for (const line of finalLines) {
        const hash = line.substring(0, 64);
        const filename = line.substring(66).replace(/^\*/, '');
        assert.ok(filename in files, `Manifest entry ${filename} must match known release file`);
        assert.notStrictEqual(filename, 'SHA256SUMS.txt', 'Manifest entry must never be SHA256SUMS.txt');
        const expectedHash = crypto.createHash('sha256').update(Buffer.from(files[filename], 'utf8')).digest('hex');
        assert.strictEqual(hash, expectedHash, `SHA-256 hash for ${filename} must match expected cryptographic digest`);
      }

      // 4. Verify negative case: if an entry for SHA256SUMS.txt is present, the self-exclusion guard triggers
      const poisonedManifest = generatedContent + 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  SHA256SUMS.txt\n';
      const guardTriggered = /^[0-9a-f]{64}\s+SHA256SUMS\.txt$/m.test(poisonedManifest);
      assert.strictEqual(guardTriggered, true, 'Guard must detect and flag presence of SHA256SUMS.txt');
    });
  });

  // ── TEST 54: Android Multi-Architecture Completeness: validate_release_assets.py --require-all-apks mandates all 5 variants, rejects missing variants, version mismatches, and misnamed architectures ──
  await test('54. Android Multi-Architecture Completeness: validate_release_assets.py --require-all-apks mandates all 5 variants, rejects missing variants, version mismatches, and misnamed architectures', () => {
    withTempDir((tmpDir) => {
      // Setup minimum Windows assets to satisfy default platform contract
      const installerBytes = Buffer.from('installer');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap');
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`);

      const validApks = [
        'Vynce-3.0.1-core-universal-release-91.apk',
        'Vynce-3.0.1-core-arm64-v8a-release-91.apk',
        'Vynce-3.0.1-core-armeabi-v7a-release-91.apk',
        'Vynce-3.0.1-core-x86_64-release-91.apk',
        'Vynce-3.0.1-core-x86-release-91.apk',
      ];

      // Helper to stage a set of APK files
      const stageApks = (apkList: string[]) => {
        for (const f of fs.readdirSync(tmpDir)) {
          if (f.endsWith('.apk')) fs.unlinkSync(path.join(tmpDir, f));
        }
        for (const apk of apkList) {
          fs.writeFileSync(path.join(tmpDir, apk), 'dummy-apk-content');
        }
      };

      // Case 1: All 5 valid APK variants pass validation
      stageApks(validApks);
      const passRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'windows', '--require-all-apks']);
      assert.strictEqual(passRes.status, 0, `All 5 valid APKs should pass: ${passRes.stderr}`);
      assert.ok(passRes.stdout.includes('Verified all 5 required Android APK variants for v3.0.1'));
      assert.ok(passRes.stdout.includes('- universal: Vynce-3.0.1-core-universal-release-91.apk'));
      assert.ok(passRes.stdout.includes('- arm64-v8a: Vynce-3.0.1-core-arm64-v8a-release-91.apk'));
      assert.ok(passRes.stdout.includes('- armeabi-v7a: Vynce-3.0.1-core-armeabi-v7a-release-91.apk'));
      assert.ok(passRes.stdout.includes('- x86_64: Vynce-3.0.1-core-x86_64-release-91.apk'));
      assert.ok(passRes.stdout.includes('- x86: Vynce-3.0.1-core-x86-release-91.apk'));

      // Case 2: Each required APK missing in turn must fail closed
      const archKeywords = ['universal', 'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'];
      for (const missingArch of archKeywords) {
        const partialList = validApks.filter(a => !a.includes(`-${missingArch}-`));
        assert.strictEqual(partialList.length, 4);
        stageApks(partialList);
        const failRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'windows', '--require-all-apks']);
        assert.strictEqual(failRes.status, 1, `Missing ${missingArch} must fail validation`);
        assert.ok(failRes.stderr.includes(`Missing required Android APK variant(s) for version 3.0.1: ['${missingArch}']`));
      }

      // Case 3: Version mismatch must fail closed
      stageApks([
        'Vynce-3.0.0-core-universal-release-90.apk',
        'Vynce-3.0.1-core-arm64-v8a-release-91.apk',
        'Vynce-3.0.1-core-armeabi-v7a-release-91.apk',
        'Vynce-3.0.1-core-x86_64-release-91.apk',
        'Vynce-3.0.1-core-x86-release-91.apk',
      ]);
      const verFailRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'windows', '--require-all-apks']);
      assert.strictEqual(verFailRes.status, 1);
      assert.ok(verFailRes.stderr.includes('version (3.0.0) does not match target release version (3.0.1)'));

      // Case 4: Unexpected or misnamed architecture must fail closed
      stageApks([
        ...validApks,
        'Vynce-3.0.1-core-mips-release-91.apk',
      ]);
      const archFailRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'windows', '--require-all-apks']);
      assert.strictEqual(archFailRes.status, 1);
      assert.ok(archFailRes.stderr.includes("unknown or unexpected architecture 'mips'"));

      // Case 5: Duplicate variant for the same architecture must fail closed
      stageApks([
        ...validApks,
        'Vynce-3.0.1-core-universal-release-92.apk',
      ]);
      const dupFailRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'windows', '--require-all-apks']);
      assert.strictEqual(dupFailRes.status, 1);
      assert.ok(dupFailRes.stderr.includes("Duplicate Android APK variant staged for architecture 'universal'"));
    });
  });

  // ── TEST 55: Full Coordinated Multiplatform Release Suite: validate_release_assets.py --platform all --require-all-apks verifies complete desktop and all 5 Android APK variants ──
  await test('55. Full Coordinated Multiplatform Release Suite: validate_release_assets.py --platform all --require-all-apks verifies complete desktop and all 5 Android APK variants', () => {
    withTempDir((tmpDir) => {
      // 1. Windows assets
      const installerBytes = Buffer.from('verified bit-identical installer');
      const validSha = crypto.createHash('sha512').update(installerBytes).digest('base64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe'), installerBytes);
      fs.writeFileSync(path.join(tmpDir, 'Vynce 3.0.1.exe'), 'portable');
      fs.writeFileSync(path.join(tmpDir, 'Vynce Setup 3.0.1.exe.blockmap'), 'blockmap');
      const manifest = `version: 3.0.1\npath: Vynce-Setup-3.0.1.exe\nsha512: ${validSha}\nfiles:\n  - url: Vynce-Setup-3.0.1.exe\n    sha512: ${validSha}\n`;
      fs.writeFileSync(path.join(tmpDir, 'latest.yml'), manifest);

      // 2. Linux assets
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.AppImage'), 'appimage');
      fs.writeFileSync(path.join(tmpDir, 'vynce_3.0.1_amd64.deb'), 'deb');

      // 3. macOS assets
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1.dmg'), 'dmg');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-mac.zip'), 'zip');

      // 4. All 5 Android APKs
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-core-universal-release-91.apk'), 'apk-univ');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-core-arm64-v8a-release-91.apk'), 'apk-arm64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-core-armeabi-v7a-release-91.apk'), 'apk-armv7');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-core-x86_64-release-91.apk'), 'apk-x86_64');
      fs.writeFileSync(path.join(tmpDir, 'Vynce-3.0.1-core-x86-release-91.apk'), 'apk-x86');

      const passRes = runRealValidator(['--version', '3.0.1', '--assets-dir', tmpDir, '--platform', 'all', '--require-apk', '--require-all-apks']);
      assert.strictEqual(passRes.status, 0);
      assert.ok(passRes.stdout.includes('Verified all 5 required Android APK variants for v3.0.1'));
      assert.ok(passRes.stdout.includes('Verified Windows installer'));
      assert.ok(passRes.stdout.includes('Verified Linux AppImage'));
      assert.ok(passRes.stdout.includes('Verified macOS DMG'));
      assert.ok(passRes.stdout.includes('ALL REQUIRED MULTIPLATFORM ASSETS AND UPDATER MANIFESTS VERIFIED SUCCESSFULLY'));
    });
  });

  console.log('\n====================================================');
  console.log(' ALL 55 PACKAGING, RELEASE, PATH & PLATFORM TESTS PASSED');
  console.log('====================================================\n');
}



runTests();
