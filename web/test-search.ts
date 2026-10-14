import assert from 'node:assert';

// Mock Web Browser Globals before importing app modules
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

(globalThis as any).window = globalThis;
(globalThis as any).Audio = MockAudio;
(globalThis as any).document = {
  documentElement: {
    style: {
      setProperty: (_name: string, _val: string) => {},
    },
  },
  createElement: (_tag: string) => {
    let content = '';
    return {
      style: {},
      setAttribute: () => {},
      click: () => {},
      set innerHTML(h: string) {
        content = h;
      },
      get innerHTML() {
        return content;
      },
      get value() {
        return content;
      },
      set value(v: string) {
        content = v;
      },
    };
  },
  body: { appendChild: () => {}, removeChild: () => {} },
  head: { appendChild: () => {} },
};

// Mock global fetch for testing search flows
let simulatedResponses: Map<string, { status: number; data: any; delayMs?: number }> = new Map();
let abortCount = 0;

const originalFetch = globalThis.fetch;

(globalThis as any).fetch = async (url: string, init?: RequestInit) => {
  const signal = init?.signal;
  if (signal?.aborted) {
    abortCount++;
    const err = new Error('The operation was aborted');
    err.name = 'AbortError';
    throw err;
  }

  const urlStr = String(url);
  let matched: { status: number; data: any; delayMs?: number } | undefined;

  for (const [key, val] of simulatedResponses.entries()) {
    if (urlStr.includes(key)) {
      matched = val;
      break;
    }
  }

  if (matched?.delayMs && matched.delayMs > 0) {
    await new Promise((resolve, reject) => {
      const timer = setTimeout(resolve, matched!.delayMs);
      signal?.addEventListener('abort', () => {
        clearTimeout(timer);
        abortCount++;
        const err = new Error('The operation was aborted');
        err.name = 'AbortError';
        reject(err);
      });
    });
  }

  if (signal?.aborted) {
    abortCount++;
    const err = new Error('The operation was aborted');
    err.name = 'AbortError';
    throw err;
  }

  if (!matched) {
    return new Response(JSON.stringify({ status: 'SUCCESS', data: { results: [] } }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  if (matched.status >= 400) {
    return new Response('Server Error', { status: matched.status });
  }

  const body = typeof matched.data === 'string' ? matched.data : JSON.stringify(matched.data);
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': typeof matched.data === 'string' ? 'text/html' : 'application/json' },
  });
};

async function main() {
  const { JioSaavnApi } = await import('./src/api/jiosaavn');
  const { SpotifyImporter } = await import('./src/api/spotify');
  type Song = import('./src/types/music').Song;
  type TopQueryResult = import('./src/types/music').TopQueryResult;
  type SpotifyPlaylistResult = import('./src/api/spotify').SpotifyPlaylistResult;

  /**
   * Harness simulating SearchPage logic and state transitions
   */
  class SearchPageTestHarness {
    public query = '';
    public topResult: TopQueryResult | null = null;
    public songs: Song[] = [];
    public albums: any[] = [];
    public artists: any[] = [];
    public playlists: any[] = [];
    public isLoading = false;
    public matchedPlaylistResult: SpotifyPlaylistResult | null = null;
    public statusMsg: string | null = null;

    public activeSearchId = 0;
    public abortController: AbortController | null = null;

    public handleClearSearch() {
      this.activeSearchId++;
      this.abortController?.abort();
      this.abortController = null;
      this.query = '';
      this.topResult = null;
      this.songs = [];
      this.albums = [];
      this.artists = [];
      this.playlists = [];
      this.matchedPlaylistResult = null;
      this.isLoading = false;
      this.statusMsg = null;
    }

    public async performSearch(searchTerm: string) {
      const trimmed = searchTerm.trim();
      if (!trimmed) {
        this.handleClearSearch();
        return;
      }

      this.abortController?.abort();
      const controller = new AbortController();
      this.abortController = controller;
      const searchId = ++this.activeSearchId;

      this.isLoading = true;
      this.matchedPlaylistResult = null;

      // 1. Spotify Track link check
      const trackId = SpotifyImporter.extractTrackId(trimmed);
      if (trackId) {
        try {
          this.statusMsg = 'Resolving audio stream...';
          const resolvedSong = await SpotifyImporter.resolveSpotifyTrack(trimmed);
          if (searchId !== this.activeSearchId || controller.signal.aborted) return;
          if (resolvedSong) {
            this.topResult = {
              id: resolvedSong.id,
              name: resolvedSong.name,
              type: 'song',
              image: resolvedSong.image,
              subtitle: `Matched Track • ${resolvedSong.primaryArtists}`,
            };
            this.songs = [resolvedSong];
            this.albums = [];
            this.artists = [];
            this.playlists = [];
            this.isLoading = false;
            this.statusMsg = null;
            return;
          }
        } catch {
          if (searchId !== this.activeSearchId || controller.signal.aborted) return;
          this.statusMsg = null;
        }
      }

      if (searchId !== this.activeSearchId || controller.signal.aborted) return;

      // 2. Spotify Playlist link check
      let spotifyPlaylistTopResult: TopQueryResult | null = null;
      const playlistId = SpotifyImporter.extractPlaylistId(trimmed);
      if (playlistId) {
        try {
          this.statusMsg = 'Loading playlist tracks...';
          const spData = await SpotifyImporter.fetchSpotifyPlaylist(playlistId);
          if (searchId !== this.activeSearchId || controller.signal.aborted) return;
          if (spData) {
            this.matchedPlaylistResult = spData;
            spotifyPlaylistTopResult = {
              id: spData.id,
              name: spData.name,
              type: 'playlist',
              image: spData.coverArt,
              subtitle: `Curated Playlist • ${spData.tracks.length} tracks`,
            };
            this.topResult = spotifyPlaylistTopResult;
          }
          this.statusMsg = null;
        } catch {
          if (searchId !== this.activeSearchId || controller.signal.aborted) return;
          this.statusMsg = null;
        }
      }

      if (searchId !== this.activeSearchId || controller.signal.aborted) return;

      // 3. JioSaavn search
      try {
        const data = await JioSaavnApi.searchGlobal(trimmed, controller.signal);
        if (searchId !== this.activeSearchId || controller.signal.aborted) return;

        this.topResult = spotifyPlaylistTopResult || data.topQuery;
        this.songs = data.songs;
        this.albums = data.albums;
        this.artists = data.artists;
        this.playlists = data.playlists;
      } catch {
        if (searchId !== this.activeSearchId || controller.signal.aborted) return;
        if (!spotifyPlaylistTopResult) {
          this.topResult = null;
        }
        this.songs = [];
        this.albums = [];
        this.artists = [];
        this.playlists = [];
      } finally {
        if (searchId === this.activeSearchId) {
          this.isLoading = false;
        }
      }
    }
  }

  console.log('==================================================');
  console.log(' VYNCE SEARCH RESULT CORRECTNESS REGRESSION SUITE');
  console.log('==================================================\n');

  let passed = 0;
  const test = async (name: string, fn: () => Promise<void>) => {
    try {
      await fn();
      console.log(`[PASS] ${name}`);
      passed++;
    } catch (err: any) {
      console.error(`[FAIL] ${name}: ${err.message}`);
      throw err;
    }
  };

  // Setup common mock responses
  simulatedResponses.set('query=arijit', {
    status: 200,
    data: {
      status: 'SUCCESS',
      data: {
        topQuery: { results: [{ id: '459320', title: 'Arijit Singh', type: 'artist' }] },
        songs: {
          results: [
            { id: 'song_arijit_1', name: 'Tum Hi Ho', primaryArtists: 'Arijit Singh' },
            { id: 'song_arijit_2', name: 'Channa Mereya', primaryArtists: 'Arijit Singh' },
          ],
        },
        artists: { results: [{ id: '459320', title: 'Arijit Singh' }] },
        albums: { results: [] },
        playlists: { results: [] },
      },
    },
  });

  simulatedResponses.set('query=coldplay', {
    status: 200,
    data: {
      status: 'SUCCESS',
      data: {
        topQuery: { results: [{ id: '568', title: 'Coldplay', type: 'artist' }] },
        songs: {
          results: [
            { id: 'song_coldplay_1', name: 'Yellow', primaryArtists: 'Coldplay' },
            { id: 'song_coldplay_2', name: 'Fix You', primaryArtists: 'Coldplay' },
            { id: 'song_coldplay_3', name: 'Viva La Vida', primaryArtists: 'Coldplay' },
          ],
        },
        artists: { results: [{ id: '568', title: 'Coldplay' }] },
        albums: { results: [] },
        playlists: { results: [] },
      },
    },
  });

  // TEST 1: Search for one song or artist
  await test('1. Search for one song or artist populates topResult and songs', async () => {
    const harness = new SearchPageTestHarness();
    await harness.performSearch('arijit');

    assert.strictEqual(harness.isLoading, false);
    assert.strictEqual(harness.topResult?.name, 'Arijit Singh');
    assert.strictEqual(harness.songs.length, 2);
    assert.strictEqual(harness.songs[0].name, 'Tum Hi Ho');
  });

  // TEST 2: Search for a different query immediately afterward (fixes AUD-002 stale results bug)
  await test('2. Search for a different query immediately afterward refreshes song results', async () => {
    const harness = new SearchPageTestHarness();
    await harness.performSearch('arijit');
    assert.strictEqual(harness.songs[0].name, 'Tum Hi Ho');

    // Perform query 2
    await harness.performSearch('coldplay');

    assert.strictEqual(harness.isLoading, false);
    assert.strictEqual(harness.topResult?.name, 'Coldplay');
    assert.strictEqual(harness.songs.length, 3, 'Songs must be refreshed from 2 to 3 Coldplay songs');
    assert.strictEqual(harness.songs[0].name, 'Yellow');
    assert.strictEqual(harness.songs[1].name, 'Fix You');
  });

  // TEST 3: Rapid typing triggering overlapping requests (fixes race condition AUD-010)
  await test('3. Out-of-order async responses: slower older query does NOT overwrite newer query', async () => {
    const harness = new SearchPageTestHarness();

    // Query 1 is slow (150ms delay)
    simulatedResponses.set('query=slow_query', {
      status: 200,
      delayMs: 150,
      data: {
        status: 'SUCCESS',
        data: {
          topQuery: { results: [{ id: 'slow', title: 'Old Slow Result' }] },
          songs: { results: [{ id: 'slow_1', name: 'Old Slow Song' }] },
        },
      },
    });

    // Query 2 is fast (20ms delay)
    simulatedResponses.set('query=fast_query', {
      status: 200,
      delayMs: 20,
      data: {
        status: 'SUCCESS',
        data: {
          topQuery: { results: [{ id: 'fast', title: 'New Fast Result' }] },
          songs: { results: [{ id: 'fast_1', name: 'New Fast Song' }] },
        },
      },
    });

    // Start slow search then immediately start fast search
    const p1 = harness.performSearch('slow_query');
    const p2 = harness.performSearch('fast_query');

    await Promise.allSettled([p1, p2]);

    assert.strictEqual(harness.topResult?.name, 'New Fast Result', 'Must retain newer fast query result');
    assert.strictEqual(harness.songs[0]?.name, 'New Fast Song', 'Must retain newer song result');
  });

  // TEST 4: Clear search input while a request is in progress
  await test('4. Clear search while request is in progress aborts and leaves state clean', async () => {
    const harness = new SearchPageTestHarness();

    simulatedResponses.set('query=in_flight', {
      status: 200,
      delayMs: 100,
      data: {
        status: 'SUCCESS',
        data: {
          songs: { results: [{ id: 's1', name: 'Should Not Appear' }] },
        },
      },
    });

    const searchPromise = harness.performSearch('in_flight');
    assert.strictEqual(harness.isLoading, true);

    // User clears search while in-flight
    harness.handleClearSearch();

    assert.strictEqual(harness.isLoading, false);
    assert.strictEqual(harness.songs.length, 0);

    // Wait for async search to settle
    await searchPromise;

    // Verify it didn't write back
    assert.strictEqual(harness.songs.length, 0, 'In-flight response must not overwrite cleared state');
    assert.strictEqual(harness.isLoading, false);
  });

  // TEST 5: Empty search results
  await test('5. Empty search results clear previous songs and reset state without error', async () => {
    const harness = new SearchPageTestHarness();

    // First populate with songs
    await harness.performSearch('arijit');
    assert.strictEqual(harness.songs.length, 2);

    // Mock query with 0 results
    simulatedResponses.set('query=nonexistent_query_xyz', {
      status: 200,
      data: {
        status: 'SUCCESS',
        data: {
          topQuery: null,
          songs: { results: [] },
          albums: { results: [] },
          artists: { results: [] },
          playlists: { results: [] },
        },
      },
    });

    await harness.performSearch('nonexistent_query_xyz');

    assert.strictEqual(harness.isLoading, false);
    assert.strictEqual(harness.topResult, null);
    assert.strictEqual(harness.songs.length, 0, 'Previous songs must be cleared on empty result');
  });

  // TEST 6: Failed request (network error / 500)
  await test('6. Failed request halts loading, clears previous results, does not crash', async () => {
    const harness = new SearchPageTestHarness();

    // First populate
    await harness.performSearch('coldplay');
    assert.strictEqual(harness.songs.length, 3);

    simulatedResponses.set('query=server_fail', {
      status: 500,
      data: null,
    });

    await harness.performSearch('server_fail');

    assert.strictEqual(harness.isLoading, false, 'Loading must stop on failure');
    assert.strictEqual(harness.songs.length, 0, 'Previous songs must be cleared on failure');
    assert.strictEqual(harness.topResult, null);
  });

  // TEST 7: Spotify playlist matching and JioSaavn fallback preservation
  await test('7. Preserve Spotify-matched results and existing fallback behavior', async () => {
    const harness = new SearchPageTestHarness();

    const mockNextData = JSON.stringify({
      props: {
        pageProps: {
          state: {
            data: {
              entity: {
                id: '37i9dQZF1DXcBWIGoYBM5M',
                name: "Today's Top Hits",
                description: 'Jung Kook is on top of the Hottest 50!',
                visualIdentity: { image: [{ url: 'https://images.example.com/cover.jpg' }] },
                trackList: [
                  {
                    title: 'Seven',
                    subtitle: 'Jung Kook, Latto',
                    album: { name: 'Seven' },
                    duration: 184000,
                  },
                ],
              },
            },
          },
        },
      },
    });
    const mockHtml = `<html><head><script id="__NEXT_DATA__" type="application/json">${mockNextData}</script></head><body></body></html>`;

    // Mock Spotify playlist embed response for a playlist ID
    simulatedResponses.set('playlist/37i9dQZF1DXcBWIGoYBM5M', {
      status: 200,
      data: mockHtml,
    });

    // Perform search with Spotify playlist link
    await harness.performSearch('https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M');

    assert.strictEqual(harness.isLoading, false);
    assert.ok(harness.matchedPlaylistResult !== null, 'Spotify playlist result must be set');
    assert.strictEqual(harness.topResult?.type, 'playlist', 'Top result must be Spotify playlist, not overwritten by JioSaavn topQuery');
    assert.strictEqual(harness.topResult?.name, "Today's Top Hits");
  });

  // TEST 8: Native fetch cancellation propagation via signal in JioSaavnApi
  await test('8. Native signal abort terminates JioSaavnApi.searchGlobal immediately', async () => {
    const controller = new AbortController();
    simulatedResponses.set('query=aborted_test', {
      status: 200,
      delayMs: 200,
      data: { status: 'SUCCESS', data: {} },
    });

    const promise = JioSaavnApi.searchGlobal('aborted_test', controller.signal);
    controller.abort();

    try {
      await promise;
      assert.fail('Expected call to reject with AbortError');
    } catch (err: any) {
      assert.ok(err.name === 'AbortError' || err.message.includes('aborted'));
    }
  });

  console.log(`\n==================================================`);
  console.log(` ALL ${passed} SEARCH CORRECTNESS TESTS PASSED`);
  console.log(`==================================================\n`);
}

main()
  .catch((err) => {
    console.error('Test suite failed:', err);
    process.exit(1);
  })
  .finally(() => {
    globalThis.fetch = originalFetch;
  });
