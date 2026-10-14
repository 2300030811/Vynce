import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Replicate handleImageFallback logic to test contract in Node isolation
function handleImageFallback(
  e: { currentTarget: { src: string; dataset: Record<string, string> } },
  fallback: string = 'fallback.svg'
) {
  const target = e.currentTarget;
  if (!target.dataset.fallbackApplied) {
    target.dataset.fallbackApplied = 'true';
    target.src = fallback;
  }
}

async function runAssetValidationTests() {
  console.log('==================================================');
  console.log(' VYNCE ASSET LOADING & PATH INTEGRITY TEST SUITE');
  console.log('==================================================\n');

  let passed = 0;
  const test = (name: string, fn: () => void) => {
    try {
      fn();
      console.log(`[PASS] ${name}`);
      passed++;
    } catch (err: any) {
      console.error(`[FAIL] ${name}: ${err.message}`);
      throw err;
    }
  };

  // TEST 1: assets.ts contract and exports
  test('1. web/src/utils/assets.ts defines APP_ICONS and handleImageFallback', () => {
    const assetsPath = path.join(__dirname, 'src', 'utils', 'assets.ts');
    assert.ok(fs.existsSync(assetsPath), 'assets.ts must exist');
    const content = fs.readFileSync(assetsPath, 'utf8');

    assert.ok(content.includes('APP_ICONS'), 'assets.ts must export APP_ICONS');
    assert.ok(content.includes('faviconSvg'), 'assets.ts must bundle faviconSvg');
    assert.ok(content.includes('vynceLogoPng'), 'assets.ts must bundle vynceLogoPng');
    assert.ok(content.includes('iconPng'), 'assets.ts must bundle iconPng');
    assert.ok(content.includes('handleImageFallback'), 'assets.ts must export handleImageFallback');
    assert.ok(content.includes('fallbackApplied'), 'assets.ts must guard against infinite fallback loops');
  });

  // TEST 2: handleImageFallback applies fallback and flags element
  test('2. handleImageFallback sets src and prevents infinite loops', () => {
    const mockImgElement = {
      src: 'https://broken-remote-artwork.example.com/art.jpg',
      dataset: {} as Record<string, string>,
    };
    const mockEvent = {
      currentTarget: mockImgElement,
    };

    // First error: applies fallback
    handleImageFallback(mockEvent, 'bundled-asset.svg');
    assert.strictEqual(mockImgElement.src, 'bundled-asset.svg', 'Must apply fallback on first failure');
    assert.strictEqual(mockImgElement.dataset.fallbackApplied, 'true', 'Must flag fallbackApplied');

    // Second error (simulating fallback itself failing to load): does not loop or overwrite
    const previousSrc = mockImgElement.src;
    handleImageFallback(mockEvent, 'https://another-fallback.png');
    assert.strictEqual(mockImgElement.src, previousSrc, 'Must not change src once fallbackApplied is true');
  });

  // TEST 3: Static scan of web/src to ensure no hardcoded root-relative '/favicon.svg' in components
  test('3. No hardcoded root-relative /favicon.svg references in web/src components', () => {
    const srcDir = path.join(__dirname, 'src');
    const violations: string[] = [];

    function scanDir(dir: string) {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
          scanDir(fullPath);
        } else if (/\.(tsx?|jsx?)$/.test(entry.name)) {
          // Exclude assets.ts which legitimately imports from ../assets/favicon.svg
          if (entry.name === 'assets.ts') continue;
          const content = fs.readFileSync(fullPath, 'utf8');
          if (content.includes("'/favicon.svg'") || content.includes('"/favicon.svg"')) {
            violations.push(path.relative(__dirname, fullPath));
          }
        }
      }
    }

    scanDir(srcDir);
    assert.strictEqual(
      violations.length,
      0,
      `Found hardcoded root-relative /favicon.svg in: ${violations.join(', ')}`
    );
  });

  // TEST 4: Production dist/index.html uses relative paths (compatible with file:// in Electron)
  test('4. Production dist/index.html uses strictly relative ./ paths', () => {
    const distIndexPath = path.join(__dirname, 'dist', 'index.html');
    if (!fs.existsSync(distIndexPath)) {
      execSync('npm run build', { cwd: __dirname, stdio: 'ignore' });
    }
    assert.ok(fs.existsSync(distIndexPath), 'dist/index.html must exist from build');

    const html = fs.readFileSync(distIndexPath, 'utf8');
    assert.ok(html.includes('href="./favicon.svg"'), 'Favicon in dist/index.html must use relative path ./favicon.svg');
    assert.ok(html.includes('src="./assets/'), 'Script tags in dist/index.html must use relative path ./assets/');
    assert.ok(html.includes('href="./assets/'), 'Stylesheets in dist/index.html must use relative path ./assets/');
    assert.ok(!html.includes('src="/assets/'), 'Must not contain root-relative /assets/ in HTML');
    assert.ok(!html.includes('href="/assets/'), 'Must not contain root-relative /assets/ in HTML');
  });

  // TEST 5: Verify dist/ contains bundled icons and fallbacks
  test('5. Production dist/ directory contains bundled icons and assets', () => {
    const distDir = path.join(__dirname, 'dist');
    assert.ok(fs.existsSync(path.join(distDir, 'favicon.svg')), 'dist/favicon.svg must exist');
    assert.ok(fs.existsSync(path.join(distDir, 'icon.png')), 'dist/icon.png must exist');
    assert.ok(fs.existsSync(path.join(distDir, 'vynce_logo.png')), 'dist/vynce_logo.png must exist');
  });

  console.log(`\n==================================================`);
  console.log(` ALL ${passed} ASSET INTEGRITY TESTS PASSED`);
  console.log(`==================================================\n`);
}

runAssetValidationTests().catch((err) => {
  console.error('Test failed:', err);
  process.exit(1);
});
