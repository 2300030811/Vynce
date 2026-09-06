const CACHE_NAME = 'vynce-audio-stream-v1';
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
      const cache = await caches.open(CACHE_NAME);
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
   * Returns a cached Blob URL if available in local CacheStorage, or the original stream URL.
   * Cleans up previously allocated Blob URLs to prevent memory leaks during long listening sessions.
   */
  public static async getPlayableUrl(url: string): Promise<string> {
    if (!this.isSupported() || !url || url.startsWith('blob:')) return url;

    try {
      const cache = await caches.open(CACHE_NAME);
      const match = await cache.match(url);
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
      await caches.delete(CACHE_NAME);
    } catch {}
  }

  /**
   * Keep cache bounded to MAX_CACHED_TRACKS
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
