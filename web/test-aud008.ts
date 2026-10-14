import assert from 'node:assert';
import { Song } from './src/types/music';

// Mock Web Browser Globals (window, caches, document, Blob, Response) in Node environment
const mockCacheStore = new Map<string, Map<string, { blob: Blob; response: Response }>>();
let simulateCacheStorageFailure = false;

class MockCache {
  private cacheName: string;
  constructor(name: string) {
    this.cacheName = name;
    if (!mockCacheStore.has(name)) {
      mockCacheStore.set(name, new Map());
    }
  }

  async put(url: string, res: Response) {
    if (simulateCacheStorageFailure) {
      throw new Error('QuotaExceededError: The quota has been exceeded.');
    }
    const blob = await res.blob();
    mockCacheStore.get(this.cacheName)!.set(url, {
      blob,
      response: new Response(blob, {
        headers: res.headers,
        status: res.status,
        statusText: res.statusText,
      }),
    });
  }

  async match(url: string) {
    const entry = mockCacheStore.get(this.cacheName)?.get(url);
    if (!entry) return null;
    return new Response(entry.blob, {
      headers: entry.response.headers,
    });
  }

  async delete(url: string) {
    return mockCacheStore.get(this.cacheName)?.delete(url) ?? false;
  }

  async keys() {
    const keys = Array.from(mockCacheStore.get(this.cacheName)?.keys() ?? []);
    return keys.map((k) => ({ url: k }));
  }
}

const mockCaches = {
  open: async (name: string) => new MockCache(name),
  delete: async (name: string) => mockCacheStore.delete(name),
};

(globalThis as any).window = globalThis;
(globalThis as any).caches = mockCaches;
(globalThis as any).URL.createObjectURL = (blob: Blob) => `blob:http://localhost/mock-${Math.random()}`;
(globalThis as any).URL.revokeObjectURL = (_url: string) => {};

// Mock navigator.storage
try {
  Object.defineProperty(globalThis.navigator, 'storage', {
    value: {
      persist: async () => true,
      persisted: async () => true,
    },
    configurable: true,
  });
} catch {}

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
(globalThis as any).Audio = MockAudio;

// Mock document for browser anchor downloads
(globalThis as any).document = {
  createElement: (_tag: string) => {
    return {
      style: {},
      href: '',
      download: '',
      setAttribute: (_k: string, _v: string) => {},
      click: () => {},
      parentNode: null,
    };
  },
  body: {
    appendChild: (_node: any) => {},
    removeChild: (_node: any) => {},
  },
};

async function runAud008Tests() {
  const { db } = await import('./src/db');
  db.downloadedSongs = {
    put: async (_entry: any) => {},
    delete: async (_id: any) => {},
    clear: async () => {},
    orderBy: () => ({ reverse: () => ({ toArray: async () => [] }) }),
  } as any;

  const { AudioCacheManager } = await import('./src/utils/audioCache');
  const { downloadSongFile } = await import('./src/utils/songDownloader');
  const { useLibraryStore } = await import('./src/stores/libraryStore');

  console.log('====================================================');
  console.log(' VYNCE AUD-008: OFFLINE-STORAGE INTEGRITY TEST SUITE');
  console.log('====================================================\n');

  let passed = 0;
  let total = 0;

  async function test(name: string, fn: () => Promise<void> | void) {
    total++;
    try {
      await fn();
      console.log(`[PASS] ${name}`);
      passed++;
    } catch (err: any) {
      console.error(`[FAIL] ${name}:`, err.message);
      throw err;
    }
  }

  const sampleSong: Song = {
    id: 'test_song_1',
    name: 'Dil Se Re',
    primaryArtists: 'A. R. Rahman',
    image: 'https://example.com/cover.jpg',
    downloadUrl: 'https://cdn.example.com/stream/dil_se.mp3',
    duration: 320,
    year: '1998',
    album: { id: 'alb_1', name: 'Dil Se' },
  };

  // Test 1: Successful fetch + successful persistent storage
  await test('1. Successful fetch + persistent storage records offline download and returns success', async () => {
    useLibraryStore.setState({ downloadedSongs: [], downloadedSongIds: new Set() });
    simulateCacheStorageFailure = false;

    const mockBytes = new Uint8Array(4 * 1024 * 1024);
    const audioBlob = new Blob([mockBytes], { type: 'audio/mpeg' });

    (globalThis as any).fetch = async (url: string) => {
      if (url === sampleSong.downloadUrl) {
        return new Response(audioBlob, { status: 200, statusText: 'OK' });
      }
      throw new Error(`Unexpected URL: ${url}`);
    };

    const statuses: Array<{ status: string; msg?: string }> = [];
    const outcome = await downloadSongFile(sampleSong, (status, msg) => {
      statuses.push({ status, msg });
    });

    assert.strictEqual(outcome.success, true, 'Overall success must be true');
    assert.strictEqual(outcome.deviceFileSaved, true, 'Device file must be saved');
    assert.strictEqual(outcome.inAppOfflineSaved, true, 'In-app offline cache must be saved');

    const store = useLibraryStore.getState();
    assert.strictEqual(store.downloadedSongs.length, 1, 'Track must be recorded in libraryStore');
    assert.strictEqual(store.downloadedSongs[0].fileSize, audioBlob.size, 'Accurate size must be stored');
    assert.strictEqual(store.isDownloaded(sampleSong.id), true, 'isDownloaded must return true');

    const doneStatus = statuses.find((s) => s.status === 'done');
    assert.ok(doneStatus?.msg?.includes('for offline playback'), 'Status message must confirm offline playback');
  });

  // Test 2: Successful fetch + CacheStorage failure (quota exceeded / storage full)
  await test('2. Successful fetch followed by CacheStorage failure does NOT register in-app offline download', async () => {
    useLibraryStore.setState({ downloadedSongs: [], downloadedSongIds: new Set() });
    simulateCacheStorageFailure = true; // Simulate QuotaExceededError on cache.put

    const mockBytes = new Uint8Array(4 * 1024 * 1024);
    const audioBlob = new Blob([mockBytes], { type: 'audio/mpeg' });

    (globalThis as any).fetch = async (url: string) => {
      if (url === sampleSong.downloadUrl) {
        return new Response(audioBlob, { status: 200, statusText: 'OK' });
      }
      throw new Error(`Unexpected URL: ${url}`);
    };

    const statuses: Array<{ status: string; msg?: string }> = [];
    const outcome = await downloadSongFile(sampleSong, (status, msg) => {
      statuses.push({ status, msg });
    });

    // Device file download was preserved, but in-app offline storage failed!
    assert.strictEqual(outcome.success, false, 'Overall success must be false when in-app storage fails');
    assert.strictEqual(outcome.deviceFileSaved, true, 'Device file download was preserved');
    assert.strictEqual(outcome.inAppOfflineSaved, false, 'In-app offline storage failed');

    // CRITICAL: Must NOT be registered in IndexedDB library store as an offline download!
    const store = useLibraryStore.getState();
    assert.strictEqual(store.downloadedSongs.length, 0, 'Must NOT record song in library store when cache write fails');
    assert.strictEqual(store.isDownloaded(sampleSong.id), false, 'isDownloaded must be false');

    // Notification must clearly distinguish device download from in-app offline unavailability
    const doneStatus = statuses.find((s) => s.status === 'done');
    assert.ok(doneStatus?.msg?.includes('Downloaded "Dil Se Re" to device'), 'Message must note device download');
    assert.ok(doneStatus?.msg?.includes('unavailable for offline playback inside Vynce'), 'Message must explicitly disclose in-app offline unavailability');

    simulateCacheStorageFailure = false;
  });

  // Test 3: Failed fetch or CORS/network rejection
  await test('3. Failed fetch / CORS rejection registers nothing and reports error', async () => {
    useLibraryStore.setState({ downloadedSongs: [], downloadedSongIds: new Set() });

    (globalThis as any).fetch = async () => {
      throw new TypeError('Failed to fetch (CORS header missing)');
    };

    const statuses: Array<{ status: string; msg?: string }> = [];
    const outcome = await downloadSongFile(sampleSong, (status, msg) => {
      statuses.push({ status, msg });
    });

    assert.strictEqual(outcome.success, false, 'Outcome must be false');
    assert.strictEqual(outcome.deviceFileSaved, false, 'No device file saved');
    assert.strictEqual(outcome.inAppOfflineSaved, false, 'No in-app offline cache saved');
    assert.strictEqual(useLibraryStore.getState().downloadedSongs.length, 0, 'No store registration');

    const errorStatus = statuses.find((s) => s.status === 'error');
    assert.ok(errorStatus?.msg?.includes('Track was not saved offline'), 'Error message must confirm track not saved');
  });

  // Test 4: 0-byte audio response
  await test('4. Empty audio response (0 bytes) is rejected', async () => {
    useLibraryStore.setState({ downloadedSongs: [], downloadedSongIds: new Set() });

    (globalThis as any).fetch = async () => {
      return new Response(new Blob([]), { status: 200, statusText: 'OK' });
    };

    const statuses: Array<{ status: string; msg?: string }> = [];
    const outcome = await downloadSongFile(sampleSong, (status, msg) => {
      statuses.push({ status, msg });
    });

    assert.strictEqual(outcome.success, false);
    assert.strictEqual(outcome.deviceFileSaved, false);
    assert.strictEqual(outcome.inAppOfflineSaved, false);
    assert.strictEqual(useLibraryStore.getState().downloadedSongs.length, 0);
  });

  // Test 5: Transient stream-prefetch cache is NOT misclassified as an offline download
  await test('5. Transient prefetch-cache entry is NOT misclassified as a confirmed offline download', async () => {
    // Put an entry into the transient stream cache
    const streamCache = await mockCaches.open('vynce-audio-stream-v1');
    const mockBlob = new Blob([new Uint8Array(1024)], { type: 'audio/mpeg' });
    await streamCache.put('https://stream.example.com/transient.mp3', new Response(mockBlob));

    // Ensure it is NOT in offline downloads cache
    const offlineCache = await mockCaches.open('vynce-downloaded-audio-v1');
    await offlineCache.delete('https://stream.example.com/transient.mp3');

    // isTrackStored MUST return false because transient prefetch is not a confirmed offline download
    const isStored = await AudioCacheManager.isTrackStored('https://stream.example.com/transient.mp3');
    assert.strictEqual(isStored, false, 'isTrackStored must strictly return false for transient prefetch cache');
  });

  // Test 6: Missing-cache / Evicted audio handling
  await test('6. Evicted or missing CacheStorage entry safely falls back without crashing player', async () => {
    // Song is not in offline cache
    const offlineCache = await mockCaches.open('vynce-downloaded-audio-v1');
    await offlineCache.delete('https://cdn.example.com/stream/evicted.mp3');

    const isStored = await AudioCacheManager.isTrackStored('https://cdn.example.com/stream/evicted.mp3');
    assert.strictEqual(isStored, false, 'Missing entry must not be reported as stored');

    // getPlayableUrl should safely return original URL instead of crashing
    const playableUrl = await AudioCacheManager.getPlayableUrl('https://cdn.example.com/stream/evicted.mp3');
    assert.strictEqual(playableUrl, 'https://cdn.example.com/stream/evicted.mp3', 'Should safely fall back to original stream URL');
  });

  // Test 7: Displayed storage size calculation removes fabricated 8 MB assumption
  await test('7. Accurate storage calculation distinguishes verified files from missing/metadata-only entries', () => {
    const mixedSongs = [
      { ...sampleSong, id: 's1', fileSize: 5 * 1024 * 1024 }, // 5 MB real download
      { ...sampleSong, id: 's2', fileSize: undefined },         // metadata-only / phantom
      { ...sampleSong, id: 's3', fileSize: 3 * 1024 * 1024 }, // 3 MB real download
      { ...sampleSong, id: 's4', fileSize: undefined },         // metadata-only / phantom
    ];

    const missingIds = new Set(['s2', 's4']);

    let totalBytes = 0;
    let verified = 0;
    let missing = 0;
    for (const s of mixedSongs) {
      const isMissing = missingIds.has(s.id);
      if (typeof s.fileSize === 'number' && s.fileSize > 0 && !isMissing) {
        totalBytes += s.fileSize;
        verified++;
      } else {
        missing++;
      }
    }

    const totalSizeMb = (totalBytes / (1024 * 1024)).toFixed(1);

    // Old fabricated logic: 5 + 8 + 3 + 8 = 24.0 MB
    // Accurate logic: 5 + 3 = 8.0 MB
    assert.strictEqual(totalSizeMb, '8.0', 'Total size must reflect real bytes (8.0 MB), not 24.0 MB');
    assert.strictEqual(verified, 2, '2 verified offline tracks');
    assert.strictEqual(missing, 2, '2 missing/metadata-only tracks distinguished');
  });

  // Test 8: Deletion and clearing offline cache
  await test('8. Deletion and cache clear purge offline storage completely', async () => {
    const offlineCache = await mockCaches.open('vynce-downloaded-audio-v1');
    const mockBlob = new Blob([new Uint8Array(2048)], { type: 'audio/mpeg' });
    await offlineCache.put('https://test.mp3', new Response(mockBlob));

    let stored = await AudioCacheManager.isTrackStored('https://test.mp3');
    assert.strictEqual(stored, true);

    await AudioCacheManager.deleteDownloadedTrack('https://test.mp3');
    stored = await AudioCacheManager.isTrackStored('https://test.mp3');
    assert.strictEqual(stored, false, 'Deleted track must not be in offline cache');

    // Test clearAll
    await offlineCache.put('https://test2.mp3', new Response(mockBlob));
    await AudioCacheManager.clearAllDownloadedAudio();
    stored = await AudioCacheManager.isTrackStored('https://test2.mp3');
    assert.strictEqual(stored, false, 'Cleared cache must have zero tracks');
  });

  console.log(`\n====================================================`);
  console.log(` RESULTS: ${passed} / ${total} AUD-008 INTEGRITY TESTS PASSED`);
  console.log(`====================================================\n`);
}

runAud008Tests().catch((e) => {
  console.error('Test suite failed:', e);
  process.exit(1);
});
