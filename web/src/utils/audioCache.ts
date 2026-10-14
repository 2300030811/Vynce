const STREAM_CACHE_NAME = 'vynce-audio-stream-v1';
const OFFLINE_DOWNLOADS_CACHE_NAME = 'vynce-downloaded-audio-v1';
const MAX_CACHED_TRACKS = 25;

export class AudioCacheManager {
  private static activeBlobUrl: string | null = null;

  private static isSupported(): boolean {
    return typeof window !== 'undefined' && 'caches' in window;
  }

  /**
   * Pre-fetches and stores audio stream into CacheStorage for instant 0ms playback
   */
  public static async prefetchTrack(url: string): Promise<void> {
    if (!this.isSupported() || !url || url.startsWith('blob:')) return;

    try {
      const cache = await caches.open(STREAM_CACHE_NAME);
      const match = await cache.match(url);
      if (!match) {
        // Fetch audio chunk in background
        const res = await fetch(url, { mode: 'cors', cache: 'force-cache' });
        if (res.ok) {
          await cache.put(url, res);
          await this.enforceQuota(cache);
        }
      }
    } catch {
      // Non-critical background pre-fetch
    }
  }

  /**
   * Requests persistent browser storage via the Storage API where available
   * to reduce the likelihood of browser eviction. Note that browser storage
   * can still be cleared if the user clears site data or under extreme disk pressure.
   */
  public static async requestPersistentStorage(): Promise<boolean> {
    if (typeof navigator !== 'undefined' && navigator.storage?.persist) {
      try {
        return await navigator.storage.persist();
      } catch {
        return false;
      }
    }
    return false;
  }

  /**
   * Stores a genuinely fetched audio blob for persistent offline playback.
   * Stored in dedicated OFFLINE_DOWNLOADS_CACHE_NAME to avoid eviction by prefetch quota.
   */
  public static async storeDownloadedTrack(url: string, blob: Blob): Promise<boolean> {
    if (!this.isSupported() || !url || !blob || blob.size === 0) return false;

    try {
      // Proactively request persistent storage if supported
      await this.requestPersistentStorage();

      const offlineCache = await caches.open(OFFLINE_DOWNLOADS_CACHE_NAME);
      const response = new Response(blob, {
        headers: {
          'Content-Type': blob.type || 'audio/mpeg',
          'Content-Length': blob.size.toString(),
        },
      });
      await offlineCache.put(url, response);

      // Verify the audio data is truly stored and matches
      const match = await offlineCache.match(url);
      if (!match) return false;
      const verifiedBlob = await match.blob();
      return !!verifiedBlob && verifiedBlob.size > 0;
    } catch (err) {
      console.warn('Failed to store downloaded audio in CacheStorage:', err);
      return false;
    }
  }

  /**
   * Checks whether playable audio bytes are actually stored offline for this stream URL.
   * STRICT: ONLY inspects OFFLINE_DOWNLOADS_CACHE_NAME. Transient prefetch streams in
   * STREAM_CACHE_NAME are NOT considered confirmed offline downloads.
   */
  public static async isTrackStored(url: string): Promise<boolean> {
    if (!this.isSupported() || !url) return false;

    try {
      const offlineCache = await caches.open(OFFLINE_DOWNLOADS_CACHE_NAME);
      const offlineMatch = await offlineCache.match(url);
      if (offlineMatch) {
        const blob = await offlineMatch.blob();
        if (blob && blob.size > 0) return true;
      }
    } catch {}

    return false;
  }

  /**
   * Deletes a downloaded track from persistent offline cache storage.
   */
  public static async deleteDownloadedTrack(url: string): Promise<void> {
    if (!this.isSupported() || !url) return;
    try {
      const offlineCache = await caches.open(OFFLINE_DOWNLOADS_CACHE_NAME);
      await offlineCache.delete(url);
    } catch {}
  }

  /**
   * Clears all persistent offline downloaded audio from CacheStorage.
   */
  public static async clearAllDownloadedAudio(): Promise<void> {
    if (!this.isSupported()) return;
    try {
      await caches.delete(OFFLINE_DOWNLOADS_CACHE_NAME);
    } catch {}
  }

  /**
   * Returns a cached Blob URL if available in local CacheStorage, or the original stream URL.
   * Cleans up previously allocated Blob URLs to prevent memory leaks during long listening sessions.
   */
  public static async getPlayableUrl(url: string): Promise<string> {
    if (!this.isSupported() || !url || url.startsWith('blob:')) return url;

    try {
      // Check offline downloads cache first for guaranteed offline playback
      const offlineCache = await caches.open(OFFLINE_DOWNLOADS_CACHE_NAME);
      let match = await offlineCache.match(url);

      // Fallback to transient stream cache
      if (!match) {
        const streamCache = await caches.open(STREAM_CACHE_NAME);
        match = await streamCache.match(url);
      }

      if (match) {
        const blob = await match.blob();
        if (blob && blob.size > 0) {
          // Revoke previous blob URL to prevent memory leaks
          if (this.activeBlobUrl) {
            URL.revokeObjectURL(this.activeBlobUrl);
            this.activeBlobUrl = null;
          }
          const newBlobUrl = URL.createObjectURL(blob);
          this.activeBlobUrl = newBlobUrl;
          return newBlobUrl;
        }
      }
    } catch {
      // Fall back to original url
    }
    return url;
  }

  /**
   * Completely clears CacheStorage audio streams and revokes any active blob URL
   */
  public static async clearCache(): Promise<void> {
    if (!this.isSupported()) return;
    try {
      if (this.activeBlobUrl) {
        URL.revokeObjectURL(this.activeBlobUrl);
        this.activeBlobUrl = null;
      }
      await caches.delete(STREAM_CACHE_NAME);
      await caches.delete(OFFLINE_DOWNLOADS_CACHE_NAME);
    } catch {}
  }

  /**
   * Keep stream cache bounded to MAX_CACHED_TRACKS
   */
  private static async enforceQuota(cache: Cache): Promise<void> {
    try {
      const keys = await cache.keys();
      if (keys.length > MAX_CACHED_TRACKS) {
        const excess = keys.length - MAX_CACHED_TRACKS;
        for (let i = 0; i < excess; i++) {
          await cache.delete(keys[i]);
        }
      }
    } catch {}
  }
}
