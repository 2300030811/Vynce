import { Song } from '../types/music';
import { JioSaavnApi } from '../api/jiosaavn';
import { AudioCacheManager } from './audioCache';
import { useLibraryStore } from '../stores/libraryStore';

export interface DownloadResult {
  success: boolean;
  deviceFileSaved: boolean;
  inAppOfflineSaved: boolean;
}

export async function downloadSongFile(
  song: Song,
  onStatus?: (status: 'fetching' | 'downloading' | 'done' | 'error', msg?: string) => void
): Promise<DownloadResult> {
  if (!song) {
    return { success: false, deviceFileSaved: false, inAppOfflineSaved: false };
  }

  onStatus?.('fetching', `Preparing "${song.name}"...`);

  let streamUrl = song.downloadUrl;
  if (!streamUrl) {
    try {
      const details = await JioSaavnApi.getSong(song.id);
      streamUrl = details?.downloadUrl || '';
    } catch {
      // fallback
    }
  }

  if (!streamUrl) {
    onStatus?.('error', `Could not locate audio stream for "${song.name}".`);
    return { success: false, deviceFileSaved: false, inAppOfflineSaved: false };
  }

  onStatus?.('downloading', `Downloading 320kbps audio for "${song.name}"...`);

  // Clean filename for the OS filesystem
  const cleanTitle = (song.name || 'Track').replace(/[/\\?%*:|"<>]/g, '').trim();
  const cleanArtist = (song.primaryArtists || 'Vynce').replace(/[/\\?%*:|"<>]/g, '').trim();
  const filename = `${cleanTitle} - ${cleanArtist}.mp3`;

  try {
    let blob: Blob | null = null;
    let fetchError: string | null = null;

    try {
      const response = await fetch(streamUrl, { mode: 'cors' });
      if (response.ok) {
        blob = await response.blob();
      } else {
        fetchError = `Server responded with ${response.status} ${response.statusText}`;
      }
    } catch (err: any) {
      // Direct CORS or network blocked, check if already in local cache
      const cached = await AudioCacheManager.getPlayableUrl(streamUrl);
      if (cached && cached.startsWith('blob:')) {
        try {
          const cachedRes = await fetch(cached);
          if (cachedRes.ok) blob = await cachedRes.blob();
        } catch {
          // ignore
        }
      }
      if (!blob) {
        fetchError = err?.message ? `CORS/Network error: ${err.message}` : 'CORS or network error';
      }
    }

    // STRICT CHECK: Reject false downloads if no valid audio bytes were retrieved
    if (!blob || blob.size === 0) {
      const msg = fetchError
        ? `Failed to download audio for "${song.name}" (${fetchError}). Track was not saved offline.`
        : `Empty audio stream received for "${song.name}". Track was not saved offline.`;
      onStatus?.('error', msg);
      return { success: false, deviceFileSaved: false, inAppOfflineSaved: false };
    }

    // 1. Attempt persistent storage in dedicated offline CacheStorage for in-app playback
    const inAppOfflineSaved = await AudioCacheManager.storeDownloadedTrack(streamUrl, blob);

    // 2. Trigger browser file download so user receives the physical .mp3 on their device
    let deviceFileSaved = false;
    try {
      const audioBlob = new Blob([blob], { type: 'audio/mpeg' });
      const blobUrl = URL.createObjectURL(audioBlob);

      const a = document.createElement('a');
      a.style.display = 'none';
      a.href = blobUrl;
      a.download = filename;
      a.setAttribute('download', filename);
      document.body.appendChild(a);

      a.click();
      deviceFileSaved = true;

      setTimeout(() => {
        if (a.parentNode) {
          document.body.removeChild(a);
        }
        URL.revokeObjectURL(blobUrl);
      }, 5000);
    } catch (triggerErr) {
      console.warn('Failed to trigger browser download anchor:', triggerErr);
    }

    const sizeMb = (blob.size / (1024 * 1024)).toFixed(1);

    // 3. Clear distinction between in-app offline storage vs device-only file download
    if (inAppOfflineSaved) {
      // In-app persistent offline storage succeeded: record in IndexedDB
      const updatedSong: Song = { ...song, downloadUrl: streamUrl };
      await useLibraryStore.getState().recordDownload(updatedSong, blob.size);
      onStatus?.('done', `Downloaded "${song.name}" (${sizeMb} MB) for offline playback!`);
      return { success: true, deviceFileSaved, inAppOfflineSaved: true };
    } else {
      // In-app storage failed (quota exhausted, disabled, or write error).
      // DO NOT record in IndexedDB as an offline download!
      const statusMsg = deviceFileSaved
        ? `Downloaded "${song.name}" to device, but unavailable for offline playback inside Vynce (storage full/unsupported).`
        : `Fetched audio for "${song.name}", but storage failed. Track not saved offline.`;
      onStatus?.('done', statusMsg);
      return { success: false, deviceFileSaved, inAppOfflineSaved: false };
    }
  } catch (err: any) {
    const errorDetail = err?.message || 'Unexpected download error';
    onStatus?.('error', `Download failed for "${song.name}": ${errorDetail}. Track was not saved offline.`);
    return { success: false, deviceFileSaved: false, inAppOfflineSaved: false };
  }
}
