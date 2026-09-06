import React, { useMemo } from 'react';
import { createPortal } from 'react-dom';
import {
  TrendingUp,
  X,
  Headphones,
  Clock,
  Heart,
  ListMusic,
  Activity,
  Cpu,
  Radio,
  Sliders,
  Volume2,
} from 'lucide-react';
import { useLibraryStore } from '../../stores/libraryStore';
import { usePlayerStore } from '../../stores/playerStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { audioEngine } from '../../audio/AudioEngine';
import { formatTime } from '../../utils/formatters';

interface StatsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const StatsModal: React.FC<StatsModalProps> = ({ isOpen, onClose }) => {
  const { history, likedSongs, playlists, savedAlbums } = useLibraryStore();
  const { currentSong, status } = usePlayerStore();
  const { bitrate, crossfadeSec, loudnessNormalization, visualizerStyle } = useSettingsStore();

  // Calculate total listening time in seconds
  const totalListeningSeconds = useMemo(() => {
    return history.reduce((acc, h) => acc + (h.song?.duration || 180), 0);
  }, [history]);

  // Compute top artists from history & likes
  const topArtists = useMemo(() => {
    const artistCounts: Record<string, number> = {};
    for (const h of history) {
      if (h.song?.primaryArtists) {
        const parts = h.song.primaryArtists.split(',').map((p) => p.trim());
        for (const a of parts) {
          if (a) artistCounts[a] = (artistCounts[a] || 0) + 1;
        }
      }
    }
    return Object.entries(artistCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, count]) => ({
        name,
        count,
        percent: Math.min(100, Math.round((count / Math.max(1, history.length)) * 100)),
      }));
  }, [history]);

  const formatHoursMins = (secs: number) => {
    const hours = Math.floor(secs / 3600);
    const mins = Math.floor((secs % 3600) / 60);
    if (hours > 0) return `${hours} hrs ${mins} mins`;
    return `${mins} mins`;
  };

  if (!isOpen) return null;

  return createPortal(
    <div
      onClick={onClose}
      className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
    >
      <div
        className="relative w-full max-w-2xl max-h-[85vh] bg-[#121118] border border-white/10 rounded-[32px] shadow-2xl flex flex-col overflow-hidden animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-3 rounded-2xl shadow-inner"
            >
              <TrendingUp className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white font-['Outfit']">
                Listening Stats & Telemetry
              </h2>
              <p className="text-xs text-[#6B6A7A]">
                Your playback insights and audio engine status
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Key Metrics Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col gap-1">
              <div className="flex items-center gap-2 text-[#6B6A7A] text-xs font-semibold">
                <Headphones className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Plays</span>
              </div>
              <span className="text-2xl font-black text-white font-['Outfit'] mt-1">
                {history.length}
              </span>
              <span className="text-[10px] text-[#6B6A7A]">Streamed tracks</span>
            </div>

            <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col gap-1">
              <div className="flex items-center gap-2 text-[#6B6A7A] text-xs font-semibold">
                <Clock className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Listen Time</span>
              </div>
              <span className="text-xl font-black text-white font-['Outfit'] mt-1 truncate">
                {formatHoursMins(totalListeningSeconds)}
              </span>
              <span className="text-[10px] text-[#6B6A7A]">Total duration</span>
            </div>

            <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col gap-1">
              <div className="flex items-center gap-2 text-[#6B6A7A] text-xs font-semibold">
                <Heart className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Liked</span>
              </div>
              <span className="text-2xl font-black text-white font-['Outfit'] mt-1">
                {likedSongs.length}
              </span>
              <span className="text-[10px] text-[#6B6A7A]">Favorite songs</span>
            </div>

            <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col gap-1">
              <div className="flex items-center gap-2 text-[#6B6A7A] text-xs font-semibold">
                <ListMusic className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Collection</span>
              </div>
              <span className="text-2xl font-black text-white font-['Outfit'] mt-1">
                {playlists.length + savedAlbums.length}
              </span>
              <span className="text-[10px] text-[#6B6A7A]">Playlists & Albums</span>
            </div>
          </div>

          {/* Top Played Artists */}
          <div className="flex flex-col gap-3">
            <h3 className="text-sm font-bold text-white uppercase tracking-wider text-[#6B6A7A]">
              Top Artists
            </h3>
            {topArtists.length > 0 ? (
              <div className="flex flex-col gap-2.5 bg-white/[0.02] p-4 rounded-2xl border border-white/5">
                {topArtists.map((artist, idx) => (
                  <div key={artist.name} className="flex flex-col gap-1">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-white">
                        <span className="text-[#6B6A7A] mr-2">#{idx + 1}</span>
                        {artist.name}
                      </span>
                      <span className="text-[#8E8D9F] font-mono font-medium">
                        {artist.count} {artist.count === 1 ? 'play' : 'plays'}
                      </span>
                    </div>
                    <div className="w-full h-1.5 rounded-full bg-white/5 overflow-hidden">
                      <div
                        style={{
                          width: `${artist.percent}%`,
                          backgroundColor: 'var(--vynce-primary)',
                        }}
                        className="h-full rounded-full transition-all duration-500"
                      />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="p-6 rounded-2xl bg-white/[0.02] border border-white/5 text-center text-xs text-[#6B6A7A]">
                Listen to songs to discover your top artists here.
              </div>
            )}
          </div>

          {/* Audio Engine & DSP Telemetry */}
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-[var(--vynce-primary)]" />
              <h3 className="text-sm font-bold text-white uppercase tracking-wider text-[#6B6A7A]">
                Audio Engine Telemetry (DSP)
              </h3>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <Radio className="w-4 h-4 text-[var(--vynce-primary)]" />
                  <span className="text-xs font-medium text-[#E8E6F0]">Stream Bitrate</span>
                </div>
                <span className="text-xs font-bold text-white font-mono bg-white/5 px-2.5 py-1 rounded-full">
                  {bitrate}
                </span>
              </div>

              <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <Cpu className="w-4 h-4 text-[var(--vynce-primary)]" />
                  <span className="text-xs font-medium text-[#E8E6F0]">WebAudio Graph</span>
                </div>
                <span className="text-xs font-bold text-white font-mono bg-white/5 px-2.5 py-1 rounded-full">
                  48 kHz • Float32
                </span>
              </div>

              <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <Sliders className="w-4 h-4 text-[var(--vynce-primary)]" />
                  <span className="text-xs font-medium text-[#E8E6F0]">Equalizer & Filters</span>
                </div>
                <span className="text-xs font-bold text-[var(--vynce-primary)] bg-[var(--vynce-primary-container)] px-2.5 py-1 rounded-full">
                  10-Band Active
                </span>
              </div>

              <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <Volume2 className="w-4 h-4 text-[var(--vynce-primary)]" />
                  <span className="text-xs font-medium text-[#E8E6F0]">Normalization</span>
                </div>
                <span className="text-xs font-bold text-white font-mono bg-white/5 px-2.5 py-1 rounded-full">
                  {loudnessNormalization ? 'Enabled' : 'Bypass'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
};
