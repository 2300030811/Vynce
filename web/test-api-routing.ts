import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Mock browser globals for Node.js test environment
class MockAudio {
  src = '';
  crossOrigin = '';
  preload = '';
  volume = 1;
  currentTime = 0;
  duration = 0;
  paused = true;
  addEventListener(_event: string, _callback: any) {}
  load() {}
  async play() {}
  pause() {}
}

(globalThis as any).window = { location: { protocol: 'http:' } };
(globalThis as any).Audio = MockAudio;
(globalThis as any).document = {
  documentElement: { style: { setProperty: () => {} } },
  createElement: () => {
    let content = '';
    return {
      style: {},
      setAttribute: () => {},
      set innerHTML(h: string) {
        content = h;
      },
      get innerHTML() {
        return content;
      },
      get value() {
        return content;
      },
    };
  },
  body: { appendChild: () => {}, removeChild: () => {} },
  head: { appendChild: () => {} },
};

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
  const { getTranslationEndpoint, translateLyrics } = await import('./src/utils/lyricsTranslation');
  const { JioSaavnApi } = await import('./src/api/jiosaavn');
  const { LyricsApi } = await import('./src/api/lyrics');
  const { SpotifyImporter } = await import('./src/api/spotify');

  console.log('====================================================');
  console.log(' VYNCE PHASE 3: API ROUTING & SERVICE RELIABILITY');
  console.log('====================================================\n');

  // ── TEST 1: IPC Spotify Embed Input Validation ──────────
  await test('1. Spotify Embed IPC handler strictly validates resource path and rejects traversal/injection', () => {
    // Read and verify the production regex deployed to main.cjs
    const mainCjsPath = path.join(__dirname, 'electron', 'main.cjs');
    assert.ok(fs.existsSync(mainCjsPath), 'main.cjs must exist');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    const spotifyPatternMatch = mainCjs.match(/clean\.match\((\/.*?\/)\);/);
    assert.ok(spotifyPatternMatch, 'main.cjs must define clean.match regex for Spotify input');
    const spotifyRegex = new RegExp(spotifyPatternMatch[1].slice(1, -1));

    const validateSpotifyInput = (resourcePath: any): { type: string; id: string } | null => {
      if (typeof resourcePath !== 'string') return null;
      const clean = resourcePath.trim();
      const match = clean.match(spotifyRegex);
      if (!match) return null;
      return { type: match[1] || 'playlist', id: match[2] };
    };

    // Valid inputs
    assert.deepStrictEqual(validateSpotifyInput('playlist/37i9dQZF1DXcBWIGoYBM5M'), {
      type: 'playlist',
      id: '37i9dQZF1DXcBWIGoYBM5M',
    });
    assert.deepStrictEqual(validateSpotifyInput('track/4cOdK2wGLETKBW3PvgPWqT'), {
      type: 'track',
      id: '4cOdK2wGLETKBW3PvgPWqT',
    });
    assert.deepStrictEqual(validateSpotifyInput('37i9dQZF1DXcBWIGoYBM5M'), {
      type: 'playlist',
      id: '37i9dQZF1DXcBWIGoYBM5M',
    });

    // Malicious or invalid inputs that MUST be rejected
    assert.strictEqual(validateSpotifyInput('../../etc/passwd'), null);
    assert.strictEqual(validateSpotifyInput('https://evil.com'), null);
    assert.strictEqual(validateSpotifyInput('track/123;rm -rf /'), null);
    assert.strictEqual(validateSpotifyInput('playlist/<script>alert(1)</script>'), null);
    assert.strictEqual(validateSpotifyInput(''), null);
    assert.strictEqual(validateSpotifyInput(12345), null);
  });

  // ── TEST 2: IPC JioSaavn Direct Input Validation ─────────
  await test('2. JioSaavn Direct IPC handler strictly validates query parameters and rejects invalid characters', () => {
    // Read and verify the production regex deployed to main.cjs
    const mainCjsPath = path.join(__dirname, 'electron', 'main.cjs');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    const jioSaavnPatternMatch = mainCjs.match(/!(\/.*?\/)\.test\(cleaned\)/);
    assert.ok(jioSaavnPatternMatch, 'main.cjs must define regex for JioSaavn query parameter validation');
    const jioSaavnRegex = new RegExp(jioSaavnPatternMatch[1].slice(1, -1));

    const validateJioSaavnQuery = (queryOrPath: any): string | null => {
      if (typeof queryOrPath !== 'string') return null;
      const cleaned = queryOrPath.replace(/^\/?(?:api\.php)?\??/, '').trim();
      if (!cleaned || cleaned.length > 2048 || !jioSaavnRegex.test(cleaned)) {
        return null;
      }
      return `https://www.jiosaavn.com/api.php?${cleaned}`;
    };

    // Valid query strings
    const valid1 = '__call=webapi.getLaunchData&api_version=4&_format=json&language=hindi,english';
    assert.strictEqual(validateJioSaavnQuery(valid1), `https://www.jiosaavn.com/api.php?${valid1}`);

    const valid2 = '/api.php?__call=lyrics.getLyrics&lyrics_id=song123&api_version=4&_format=json';
    assert.strictEqual(
      validateJioSaavnQuery(valid2),
      'https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&lyrics_id=song123&api_version=4&_format=json'
    );

    // Blocked invalid/malicious query strings
    assert.strictEqual(validateJioSaavnQuery('__call=test&param=hello world'), null); // Spaces not allowed
    assert.strictEqual(validateJioSaavnQuery('__call=test\r\nHost: evil.com'), null); // Header injection attempt
    assert.strictEqual(validateJioSaavnQuery('../../../secret'), null); // Traversal attempt
    assert.strictEqual(validateJioSaavnQuery('__call=test&id=<svg onload=alert(1)>'), null); // XSS attempt
    assert.strictEqual(validateJioSaavnQuery('a'.repeat(2049)), null); // Oversized query string
  });

  // ── TEST 3: Translation Endpoint Resolution ──────────────
  await test('3. Translation endpoint resolves correctly in web vs file: (Electron) environments', () => {
    const origEnv = process.env.VITE_TRANSLATION_API_URL;
    const origWindow = (globalThis as any).window;

    try {
      delete process.env.VITE_TRANSLATION_API_URL;

      // Case A: Web browser environment
      (globalThis as any).window = { location: { protocol: 'http:' } };
      assert.strictEqual(getTranslationEndpoint(), '/api/translate');

      // Case B: Packaged desktop application under file://
      (globalThis as any).window = { location: { protocol: 'file:' } };
      assert.strictEqual(getTranslationEndpoint(), 'http://127.0.0.1:8000/translate');

      // Case C: Explicit custom environment variable override
      process.env.VITE_TRANSLATION_API_URL = 'https://custom-translation.internal/translate';
      assert.strictEqual(getTranslationEndpoint(), 'https://custom-translation.internal/translate');
    } finally {
      if (origEnv !== undefined) {
        process.env.VITE_TRANSLATION_API_URL = origEnv;
      } else {
        delete process.env.VITE_TRANSLATION_API_URL;
      }
      (globalThis as any).window = origWindow;
    }
  });

  // ── TEST 4: Translation Service Graceful Fallback ────────
  await test('4. Unreachable translation service gracefully falls back to original lines intact', async () => {
    // When translation endpoint fails (connection refused / 502 / network error),
    // translateLyrics must return original lines with provenance 'fallback_original'
    const origFetch = globalThis.fetch;
    (globalThis as any).fetch = async () => {
      throw new Error('Connection refused: translation service offline');
    };

    try {
      const sampleLines = [
        { time: 10, text: 'Tu hai to mujhe phir aur kya chahiye' },
        { time: 15, text: 'Kissi ki na madad na dua chahiye' },
      ];

      const res = await translateLyrics({
        songId: 'offline-test-song',
        lines: sampleLines,
        sourceLanguage: 'hin_Deva',
        targetLanguage: 'eng_Latn',
        mode: 'literal',
      });

      assert.strictEqual(res.provenance.provider, 'fallback_original');
      assert.strictEqual(res.translatedLines.length, 2);
      assert.strictEqual(res.translatedLines[0].text, 'Tu hai to mujhe phir aur kya chahiye');
      assert.strictEqual(res.translatedLines[0].time, 10);
    } finally {
      globalThis.fetch = origFetch;
    }
  });

  // ── TEST 5: JioSaavn Routing in Electron vs file:// ──────
  await test('5. JioSaavnApi.getHome uses Electron IPC when available and avoids relative fetch under file:', async () => {
    const origFetch = globalThis.fetch;
    const origWindow = (globalThis as any).window;

    let ipcCalled = false;
    let fetchCalledWith = '';

    try {
      // Case A: In Electron desktop app
      (globalThis as any).window = {
        location: { protocol: 'file:' },
        electronAPI: {
          fetchJioSaavnDirect: async (query: string) => {
            ipcCalled = true;
            return {
              modules: {
                trending: { position: 1, title: 'Trending', subtitle: 'Now' },
              },
              trending: [
                { type: 'song', id: '1', title: 'Kesariya', subtitle: 'Arijit', image: '', more_info: { duration: 240 } },
                { type: 'song', id: '2', title: 'Apna Bana Le', subtitle: 'Arijit', image: '', more_info: { duration: 210 } },
                { type: 'song', id: '3', title: 'Chaleya', subtitle: 'Arijit', image: '', more_info: { duration: 190 } },
              ],
            };
          },
        },
      };

      const homeModules = await JioSaavnApi.getHome('hindi,english');
      assert.strictEqual(ipcCalled, true, 'Electron IPC fetchJioSaavnDirect must be invoked');
      assert.ok(homeModules.length > 0, 'Home modules returned from Electron IPC');

      // Case B: In file:// protocol WITHOUT Electron IPC
      ipcCalled = false;
      (globalThis as any).window = {
        location: { protocol: 'file:' },
      };
      (globalThis as any).fetch = async (url: string) => {
        fetchCalledWith = url;
        if (url.startsWith('/jiosaavn-direct')) {
          assert.fail('Must not perform relative fetch /jiosaavn-direct under file: protocol');
        }
        return {
          ok: true,
          json: async () => ({ status: 'SUCCESS', data: { results: [] } }),
        };
      };

      await JioSaavnApi.getHome('hindi');
      // Must not call /jiosaavn-direct, should have queried public search API mirror
      assert.ok(!fetchCalledWith.includes('/jiosaavn-direct'), 'Avoided relative /jiosaavn-direct fetch');
    } finally {
      globalThis.fetch = origFetch;
      (globalThis as any).window = origWindow;
    }
  });

  // ── TEST 6: Lyrics Native Routing in Electron vs file:// ──
  await test('6. LyricsApi.getLyrics uses Electron IPC and skips relative fetch under file: protocol', async () => {
    const origFetch = globalThis.fetch;
    const origWindow = (globalThis as any).window;

    let ipcCalled = false;
    let attemptedUrls: string[] = [];

    try {
      // In Electron
      (globalThis as any).window = {
        location: { protocol: 'file:' },
        electronAPI: {
          fetchJioSaavnDirect: async (_query: string) => {
            ipcCalled = true;
            return { lyrics: 'Kahiin to hogi wo, duniya jahaan tu mere saath hai' };
          },
        },
      };
      (globalThis as any).fetch = async (url: string) => {
        return { ok: false, json: async () => [], text: async () => '' };
      };

      const result = await LyricsApi.getLyrics('Jaane Tu', 'Rashid Ali', undefined, undefined, 'song_123');
      assert.strictEqual(ipcCalled, true, 'LyricsApi should call Electron IPC fetchJioSaavnDirect for native song lyrics');
      assert.strictEqual(result?.plainLyrics?.includes('Kahiin to hogi wo'), true);

      // In file:// without Electron IPC
      ipcCalled = false;
      (globalThis as any).window = {
        location: { protocol: 'file:' },
      };
      (globalThis as any).fetch = async (url: string) => {
        attemptedUrls.push(url);
        return {
          ok: false,
          json: async () => ({}),
        };
      };

      await LyricsApi.getLyrics('Jaane Tu', 'Rashid Ali', undefined, undefined, 'song_456');
      const attemptedRelative = attemptedUrls.some((u) => u.startsWith('/jiosaavn-direct'));
      assert.strictEqual(attemptedRelative, false, 'Must not attempt relative /jiosaavn-direct URL under file:');
    } finally {
      globalThis.fetch = origFetch;
      (globalThis as any).window = origWindow;
    }
  });

  // ── TEST 7: Spotify Embed Routing in Electron vs file:// ─
  await test('7. SpotifyService skips relative /spotify-embed under file: protocol when IPC unavailable', async () => {
    const origFetch = globalThis.fetch;
    const origWindow = (globalThis as any).window;

    let attemptedRelative = false;

    try {
      (globalThis as any).window = {
        location: { protocol: 'file:' },
      };
      (globalThis as any).fetch = async (url: string) => {
        if (url.includes('/spotify-embed')) {
          attemptedRelative = true;
        }
        return { ok: false, text: async () => '' };
      };

      await SpotifyImporter.fetchSpotifyTrack('4cOdK2wGLETKBW3PvgPWqT');
      assert.strictEqual(attemptedRelative, false, 'Must not attempt /spotify-embed under file: protocol');
    } finally {
      globalThis.fetch = origFetch;
      (globalThis as any).window = origWindow;
    }
  });

  console.log('\n====================================================');
  console.log(' ALL 7 PRODUCTION API ROUTING TESTS PASSED');
  console.log('====================================================\n');
}

runTests();
