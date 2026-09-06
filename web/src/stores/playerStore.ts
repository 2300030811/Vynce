import { create } from 'zustand';
import { Song, LyricData } from '../types/music';
import { audioEngine, EqPreset, EQ_PRESETS } from '../audio/AudioEngine';
import { useQueueStore } from './queueStore';
import { useLibraryStore } from './libraryStore';
import {
  generateVynceTheme,
  extractSeedColorFromImage,
  applyVynceTheme,
  DEFAULT_VYNCE_SEED,
  VynceThemeTokens,
} from '../utils/themeGenerator';
import { LyricsApi } from '../api/lyrics';
import { JioSaavnApi } from '../api/jiosaavn';
import { SpotifyMapper } from '../api/spotify';
import { updateMediaSession, updateMediaSessionPlaybackState } from '../utils/mediaSession';
import { useSettingsStore } from './settingsStore';

export type RepeatMode = 'off' | 'all' | 'one';

export interface ExtractedColors {
  dominant: string;
  accent: string;
  gradient: string;
  glow: string;
}

export type FullscreenVisualMode = 'artwork' | 'karaoke';

export interface PlayerState {
  currentSong: Song | null;
  status: 'playing' | 'paused' | 'buffering' | 'stopped';
  currentTime: number;
  duration: number;
  volume: number;
  isMuted: boolean;
  repeatMode: RepeatMode;
  isShuffled: boolean;
  isFullscreen: boolean;
  fullscreenMode: FullscreenVisualMode;
  isLyricsOpen: boolean;
  isQueueOpen: boolean;
  isEqualizerOpen: boolean;
  isSleepTimerOpen: boolean;
  sleepTimerRemaining: number | null; // in seconds
  eqPreset: EqPreset;
  eqGains: number[];
  bassBoost: number; // 0 to 100%
  vocalClarity: number; // 0 to 100%
  preampGain: number; // -6 to +6 dB
  isEqEnabled: boolean;
  palette: ExtractedColors;
  theme: VynceThemeTokens;
  lyrics: LyricData | null;
  isLyricsLoading: boolean;
  lyricsOffset: number; // in milliseconds (e.g. +500ms or -500ms)

  playSong: (song: Song, queueContext?: Song[], index?: number) => Promise<void>;
  togglePlay: () => void;
  seek: (seconds: number) => void;
  setVolume: (vol: number) => void;
  toggleMute: () => void;
  toggleRepeat: () => void;
  toggleShuffle: () => void;
  nextTrack: () => Promise<void>;
  prevTrack: () => Promise<void>;
  toggleFullscreen: () => void;
  setFullscreenMode: (mode: FullscreenVisualMode) => void;
  toggleLyrics: () => void;
  toggleQueue: () => void;
  toggleEqualizer: () => void;
  toggleSleepTimer: () => void;
  setSleepTimer: (minutes: number | null) => void;
  setEqPreset: (preset: EqPreset) => void;
  setEqGain: (bandIndex: number, gain: number) => void;
  setBassBoost: (percent: number) => void;
  setVocalClarity: (percent: number) => void;
  setPreampGain: (gainDb: number) => void;
  toggleEqEnabled: () => void;
  setLyricsOffset: (offset: number) => void;
  setCustomLyrics: (lyricData: LyricData) => void;
  fetchLyrics: () => Promise<void>;
  togglePiP: () => Promise<boolean>;
  playbackSpeed: number;
  setPlaybackSpeed: (speed: number) => void;
  startSongRadio: (song: Song) => Promise<void>;
}

const savedAccent = typeof localStorage !== 'undefined' ? (() => {
  try {
    const raw = localStorage.getItem('vynce_settings');
    if (raw) {
      const parsed = JSON.parse(raw);
      return parsed?.state?.accentColor;
    }
  } catch {}
  return null;
})() : null;

const initialSeed = (savedAccent && savedAccent !== 'dynamic') ? savedAccent : DEFAULT_VYNCE_SEED;
const initialTheme = generateVynceTheme(initialSeed);
if (typeof document !== 'undefined') {
  applyVynceTheme(initialTheme);
}

const defaultPalette: ExtractedColors = {
  dominant: initialTheme.primary,
  accent: initialTheme.secondary,
  gradient: initialTheme.ambientGradient,
  glow: initialTheme.glow,
};

let sleepTimerInterval: ReturnType<typeof setInterval> | null = null;

import { pipManager } from '../utils/pipManager';
import { AudioCacheManager } from '../utils/audioCache';

export const usePlayerStore = create<PlayerState>((set, get) => {
  // Wire PiP manager state getter
  pipManager.init(() => ({
    currentSong: get().currentSong,
    currentTime: get().currentTime,
    duration: get().duration,
    lyrics: get().lyrics,
    lyricsOffset: get().lyricsOffset,
    status: get().status,
  }));

  // Track pre-fetch debounce flag
  let nextTrackPreloaded = false;

  // Wire audio engine events
  audioEngine.onTimeUpdate((currentTime, duration) => {
    set({ currentTime, duration: duration || get().duration });

    // Gapless pre-buffering: when 12s remaining in current track, pre-fetch next track
    const totalDuration = duration || get().duration;
    if (totalDuration > 20 && currentTime > totalDuration - 12 && !nextTrackPreloaded) {
      nextTrackPreloaded = true;
      const nextSong = useQueueStore.getState().getNextSong();
      if (nextSong?.downloadUrl) {
        AudioCacheManager.prefetchTrack(nextSong.downloadUrl);
      }
    }
  });

  audioEngine.onStatusChange((status) => {
    set({ status });
    updateMediaSessionPlaybackState(status === 'playing' ? 'playing' : status === 'paused' ? 'paused' : 'none');
  });

  audioEngine.onEnded(async () => {
    nextTrackPreloaded = false;
    const { repeatMode, nextTrack } = get();
    if (repeatMode === 'one') {
      audioEngine.seek(0);
      await audioEngine.play();
    } else {
      await nextTrack();
    }
  });

  return {
    currentSong: null,
    status: 'stopped',
    currentTime: 0,
    duration: 0,
    volume: 0.85,
    isMuted: false,
    repeatMode: 'off',
    isShuffled: false,
    isFullscreen: false,
    fullscreenMode: 'artwork',
    isLyricsOpen: false,
    isQueueOpen: false,
    isEqualizerOpen: false,
    isSleepTimerOpen: false,
    sleepTimerRemaining: null,
    eqPreset: 'Flat',
    eqGains: [...EQ_PRESETS.Flat],
    bassBoost: 0,
    vocalClarity: 0,
    preampGain: 0,
    isEqEnabled: true,
    palette: defaultPalette,
    theme: initialTheme,
    lyrics: null,
    isLyricsLoading: false,
    lyricsOffset: 0,

    playSong: async (song: Song, queueContext?: Song[], index?: number) => {
      if (!song) return;

      // Set queue if provided
      if (queueContext && queueContext.length > 0) {
        useQueueStore.getState().setQueue(queueContext, index ?? 0);
      } else {
        const queueState = useQueueStore.getState();
        if (queueState.queue.length === 0) {
          queueState.setQueue([song], 0);
        }
      }

      set({
        currentSong: song,
        currentTime: 0,
        duration: song.duration || 0,
        status: 'buffering',
        lyrics: null,
        lyricsOffset: 0,
      });

      // Record to history
      useLibraryStore.getState().recordHistory(song);

      // If user selected 'dynamic', extract Material 3 Dynamic Palette from Album Artwork and morph entire UI theme
      const userAccent = useSettingsStore.getState().accentColor;
      if (userAccent === 'dynamic') {
        extractSeedColorFromImage(song.image).then((seedHex) => {
          const dynamicTheme = generateVynceTheme(seedHex);
          applyVynceTheme(dynamicTheme);
          set({
            palette: {
              dominant: dynamicTheme.primary,
              accent: dynamicTheme.secondary,
              gradient: dynamicTheme.ambientGradient,
              glow: dynamicTheme.glow,
            },
            theme: dynamicTheme,
          });
        });
      }

      // Fetch stream URL if missing
      let streamUrl = song.downloadUrl;
      if (!streamUrl) {
        try {
          if (song.id.startsWith('sp_') || (song as any).spotifyTrackQuery) {
            const query = (song as any).spotifyTrackQuery || `${song.name} ${song.primaryArtists}`.trim();
            const candidates = await JioSaavnApi.searchSongs(query, 5);
            const best = SpotifyMapper.findBestMatch(
              {
                name: song.name,
                artists: song.primaryArtists || '',
                durationMs: (song.duration || 0) * 1000,
              },
              candidates
            );
            if (best?.downloadUrl) {
              streamUrl = best.downloadUrl;
              set((state) => ({
                currentSong: state.currentSong
                  ? {
                      ...best,
                      ...state.currentSong,
                      id: best.id,
                      downloadUrl: streamUrl,
                    }
                  : null,
              }));
            }
          } else {
            const detailed = await JioSaavnApi.getSong(song.id);
            if (detailed?.downloadUrl) {
              streamUrl = detailed.downloadUrl;
              set((state) => ({
                currentSong: state.currentSong ? { ...state.currentSong, downloadUrl: streamUrl } : null,
              }));
            }
          }
        } catch {}
      }

      if (streamUrl) {
        try {
          const crossfadeDuration = useSettingsStore.getState().crossfadeSec || 0;
          const playableUrl = await AudioCacheManager.getPlayableUrl(streamUrl);
          await audioEngine.setSource(playableUrl, true, crossfadeDuration);
          // Pre-cache current stream in background for fast replays
          AudioCacheManager.prefetchTrack(streamUrl);
        } catch {
          set({ status: 'paused' });
        }
      }

      // MediaSession setup
      updateMediaSession(song, {
        onPlay: () => get().togglePlay(),
        onPause: () => get().togglePlay(),
        onNext: () => get().nextTrack(),
        onPrev: () => get().prevTrack(),
        onSeek: (details) => {
          if (details.seekTime !== undefined) {
            get().seek(details.seekTime);
          }
        },
      });

      // Fetch lyrics in background
      get().fetchLyrics();
    },

    togglePlay: () => {
      const { status, currentSong } = get();
      if (!currentSong) return;

      if (status === 'playing') {
        audioEngine.pause();
      } else {
        audioEngine.play().catch(() => {});
      }
    },

    seek: (seconds: number) => {
      audioEngine.seek(seconds);
      set({ currentTime: seconds });
    },

    setVolume: (vol: number) => {
      audioEngine.setVolume(vol);
      set({ volume: vol, isMuted: vol === 0 });
    },

    toggleMute: () => {
      const { isMuted, volume } = get();
      if (isMuted) {
        audioEngine.setVolume(volume || 0.85);
        set({ isMuted: false });
      } else {
        audioEngine.setVolume(0);
        set({ isMuted: true });
      }
    },

    toggleRepeat: () => {
      const modes: RepeatMode[] = ['off', 'all', 'one'];
      const next = modes[(modes.indexOf(get().repeatMode) + 1) % modes.length];
      set({ repeatMode: next });
    },

    toggleShuffle: () => {
      const next = !get().isShuffled;
      set({ isShuffled: next });
      useQueueStore.getState().shuffleQueue(next);
    },

    nextTrack: async () => {
      const queue = useQueueStore.getState();
      let next = queue.advance();

      // If at end of queue and repeat mode is ALL, loop back to start
      if (!next && get().repeatMode === 'all' && queue.queue.length > 0) {
        queue.setQueue(queue.queue, 0);
        next = queue.getCurrentSong();
      }

      // If queue ended and repeat is OFF, try fetching recommended songs
      if (!next && get().currentSong) {
        try {
          const recs = await JioSaavnApi.getRecommendations(get().currentSong!.id);
          if (recs && recs.length > 0) {
            queue.addToQueue(recs);
            next = queue.advance();
          }
        } catch {}
      }

      if (next) {
        await get().playSong(next);
      } else {
        set({ status: 'stopped' });
      }
    },

    prevTrack: async () => {
      const { currentTime, playSong } = get();
      if (currentTime > 3) {
        audioEngine.seek(0);
        set({ currentTime: 0 });
        return;
      }

      const queue = useQueueStore.getState();
      const prev = queue.rewind();
      if (prev) {
        await playSong(prev);
      } else {
        audioEngine.seek(0);
        set({ currentTime: 0 });
      }
    },

    toggleFullscreen: () => set((state) => ({ isFullscreen: !state.isFullscreen })),
    setFullscreenMode: (mode: FullscreenVisualMode) => set({ fullscreenMode: mode }),
    toggleLyrics: () => set((state) => ({ isLyricsOpen: !state.isLyricsOpen })),
    toggleQueue: () => set((state) => ({ isQueueOpen: !state.isQueueOpen })),
    toggleEqualizer: () => set((state) => ({ isEqualizerOpen: !state.isEqualizerOpen })),
    toggleSleepTimer: () => set((state) => ({ isSleepTimerOpen: !state.isSleepTimerOpen })),
    togglePiP: async () => {
      return await pipManager.togglePiP();
    },

    setSleepTimer: (minutes: number | null) => {
      if (sleepTimerInterval) {
        clearInterval(sleepTimerInterval);
        sleepTimerInterval = null;
      }

      if (minutes === null || minutes <= 0) {
        set({ sleepTimerRemaining: null, isSleepTimerOpen: false });
        return;
      }

      const totalSecs = Math.round(minutes * 60);
      set({ sleepTimerRemaining: totalSecs, isSleepTimerOpen: false });

      const initialVolume = get().volume;
      const FADE_WINDOW_SECS = Math.min(15, Math.floor(totalSecs / 2));

      sleepTimerInterval = setInterval(() => {
        const rem = get().sleepTimerRemaining;
        if (rem === null || rem <= 1) {
          if (sleepTimerInterval) clearInterval(sleepTimerInterval);
          sleepTimerInterval = null;

          audioEngine.pause();
          audioEngine.setVolume(initialVolume);
          set({ sleepTimerRemaining: null, status: 'paused' });
        } else {
          const nextRem = rem - 1;
          // Smooth progressive fade over the final FADE_WINDOW_SECS
          if (nextRem <= FADE_WINDOW_SECS && FADE_WINDOW_SECS > 0) {
            const factor = Math.max(0.05, nextRem / FADE_WINDOW_SECS);
            audioEngine.setVolume(initialVolume * factor);
          }
          set({ sleepTimerRemaining: nextRem });
        }
      }, 1000);
    },

    setEqPreset: (preset: EqPreset) => {
      const gains = EQ_PRESETS[preset] || EQ_PRESETS.Flat;
      audioEngine.applyEqPreset(preset, gains);
      set({ eqPreset: preset, eqGains: [...gains] });
    },

    setEqGain: (bandIndex: number, gain: number) => {
      audioEngine.setEqGain(bandIndex, gain);
      const nextGains = [...get().eqGains];
      nextGains[bandIndex] = gain;
      set({ eqPreset: 'Custom', eqGains: nextGains });
    },

    setBassBoost: (percent: number) => {
      audioEngine.setBassBoost(percent);
      set({ bassBoost: percent });
    },

    setVocalClarity: (percent: number) => {
      audioEngine.setVocalClarity(percent);
      set({ vocalClarity: percent });
    },

    setPreampGain: (gainDb: number) => {
      audioEngine.setPreamp(gainDb);
      set({ preampGain: gainDb });
    },

    toggleEqEnabled: () => {
      const current = get().isEqEnabled;
      const next = !current;
      audioEngine.setEqBypassed(!next);
      set({ isEqEnabled: next });
    },

    setLyricsOffset: (offset: number) => {
      set({ lyricsOffset: offset });
    },

    setCustomLyrics: (lyricData: LyricData) => {
      set({ lyrics: lyricData, isLyricsLoading: false });
      const { currentSong } = get();
      if (currentSong?.id) {
        LyricsApi.cacheLyrics(currentSong.id, lyricData);
      }
    },

    fetchLyrics: async () => {
      const { currentSong } = get();
      if (!currentSong) return;

      set({ isLyricsLoading: true });
      try {
        const lyricData = await LyricsApi.getLyrics(
          currentSong.name,
          currentSong.primaryArtists,
          currentSong.album,
          currentSong.duration,
          currentSong.id
        );
        set({ lyrics: lyricData, isLyricsLoading: false });
      } catch {
        set({ lyrics: null, isLyricsLoading: false });
      }
    },

    playbackSpeed: 1.0,
    setPlaybackSpeed: (speed: number) => {
      audioEngine.setPlaybackRate(speed);
      set({ playbackSpeed: speed });
    },

    startSongRadio: async (song: Song) => {
      if (!song) return;
      try {
        const recs = await JioSaavnApi.getRecommendations(song.id);
        const radioQueue = [song, ...(recs || [])];
        useQueueStore.getState().setQueue(radioQueue, 0);
        await get().playSong(song, radioQueue, 0);
      } catch {
        await get().playSong(song, [song], 0);
      }
    },
  };
});
