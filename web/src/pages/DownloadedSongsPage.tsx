import React, { useState, useMemo, useEffect } from 'react';
import { useLibraryStore } from '../stores/libraryStore';
import { usePlayerStore } from '../stores/playerStore';
import { AudioCacheManager } from '../utils/audioCache';
import { SongCard } from '../components/cards/SongCard';
import {
  Download,
  Play,
  Shuffle,
  Clock,
  Search,
  Compass,
  ArrowLeft,
  Trash2,
  HardDrive,
  Share2,
  Check,
} from 'lucide-react';
import { formatTime } from '../utils/formatters';

interface DownloadedSongsPageProps {
  onBack?: () => void;
  onNavigateToExplore: () => void;
}

export const DownloadedSongsPage: React.FC<DownloadedSongsPageProps> = ({
  onBack,
  onNavigateToExplore,
}) => {
  const { downloadedSongs, clearDownloadedSongs } = useLibraryStore();
  const { playSong } = usePlayerStore();
  const [searchFilter, setSearchFilter] = useState('');
  const [copied, setCopied] = useState(false);

  const filteredSongs = useMemo(() => {
    if (!searchFilter.trim()) return downloadedSongs;
    const q = searchFilter.toLowerCase();
    return downloadedSongs.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.primaryArtists.toLowerCase().includes(q) ||
        (s.album && (typeof s.album === 'string' ? s.album : (s.album as any).name || '').toLowerCase().includes(q))
    );
  }, [downloadedSongs, searchFilter]);

  const totalDuration = useMemo(() => {
    return downloadedSongs.reduce((acc, s) => acc + (s.duration || 0), 0);
  }, [downloadedSongs]);

  const [missingAudioIds, setMissingAudioIds] = useState<Set<string>>(new Set());

  // Proactively check CacheStorage to detect missing or evicted audio
  useEffect(() => {
    let isMounted = true;
    async function verifyCachedTracks() {
      const missing = new Set<string>();
      for (const song of downloadedSongs) {
        if (!song.fileSize || song.fileSize <= 0) {
          missing.add(song.id);
        } else if (song.downloadUrl) {
          const stored = await AudioCacheManager.isTrackStored(song.downloadUrl);
          if (!stored) {
            missing.add(song.id);
          }
        } else {
          missing.add(song.id);
        }
      }
      if (isMounted) {
        setMissingAudioIds(missing);
      }
    }
    verifyCachedTracks();
    return () => {
      isMounted = false;
    };
  }, [downloadedSongs]);

  const { totalSizeMb, verifiedOfflineCount, missingAudioCount } = useMemo(() => {
    let totalBytes = 0;
    let verified = 0;
    let missing = 0;
    for (const s of downloadedSongs) {
      const isMissing = missingAudioIds.has(s.id);
      if (typeof s.fileSize === 'number' && s.fileSize > 0 && !isMissing) {
        totalBytes += s.fileSize;
        verified++;
      } else {
        missing++;
      }
    }
    return {
      totalSizeMb: (totalBytes / (1024 * 1024)).toFixed(1),
      verifiedOfflineCount: verified,
      missingAudioCount: missing,
    };
  }, [downloadedSongs, missingAudioIds]);

  const handlePlayAll = () => {
    if (filteredSongs.length > 0) {
      playSong(filteredSongs[0], filteredSongs, 0);
    }
  };

  const handleShufflePlay = () => {
    if (filteredSongs.length > 0) {
      const shuffled = [...filteredSongs].sort(() => Math.random() - 0.5);
      playSong(shuffled[0], shuffled, 0);
    }
  };

  const handleClearAll = async () => {
    if (downloadedSongs.length === 0) return;
    if (confirm(`Remove all ${downloadedSongs.length} tracks from offline downloads?`)) {
      await clearDownloadedSongs();
    }
  };

  const handleShare = () => {
    navigator.clipboard.writeText(`Downloaded Songs on Vynce (${downloadedSongs.length} tracks)`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="flex flex-col gap-6 pb-36 w-full animate-in fade-in duration-300">
      {/* ── Top Header Controls ── */}
      <div className="flex items-center justify-between pt-1">
        <div className="flex items-center gap-2">
          {onBack && (
            <button
              onClick={onBack}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/5 shadow-sm"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back</span>
            </button>
          )}

          <button
            onClick={onNavigateToExplore}
            className="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/5 shadow-sm"
          >
            <Compass className="w-4 h-4 text-[var(--vynce-primary)]" />
            <span>Discover Music</span>
          </button>
        </div>

        <div className="flex items-center gap-2">
          {downloadedSongs.length > 0 && (
            <button
              onClick={handleClearAll}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs font-bold border border-red-500/20 transition-colors"
              title="Clear all downloaded tracks"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Clear All</span>
            </button>
          )}

          <button
            onClick={handleShare}
            className="p-2 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-colors border border-white/5"
            title="Share Collection Info"
          >
            {copied ? <Check className="w-4 h-4 text-green-400" /> : <Share2 className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* ── Compact Cinematic Hero Banner ── */}
      <div
        style={{
          background:
            'radial-gradient(ellipse at 80% 20%, var(--vynce-primary-container) 0%, rgba(18,22,34,0.95) 60%, #0A0A0A 100%)',
        }}
        className="relative overflow-hidden rounded-[28px] p-5 sm:p-6 md:p-7 border border-white/10 shadow-xl flex flex-col sm:flex-row items-center sm:items-center gap-5 sm:gap-6 group"
      >
        {/* Ambient Top Glow */}
        <div
          className="absolute right-0 top-0 w-72 h-72 pointer-events-none opacity-25 blur-3xl scale-125"
          style={{ background: 'var(--vynce-primary)' }}
        />

        {/* Compact Download Cover Art Icon */}
        <div className="relative shrink-0">
          <div
            style={{
              background:
                'radial-gradient(circle at 40% 35%, var(--vynce-primary-container) 0%, rgba(15,18,28,0.95) 100%)',
            }}
            className="relative w-24 h-24 sm:w-28 sm:h-28 md:w-32 md:h-32 rounded-2xl overflow-hidden shadow-lg border border-white/15 flex items-center justify-center group-hover:scale-105 transition-transform duration-300"
          >
            <Download
              style={{ color: 'var(--vynce-primary)' }}
              className="w-12 h-12 sm:w-14 sm:h-14 md:w-16 md:h-16 drop-shadow-lg"
            />
          </div>
        </div>

        {/* Metadata Info */}
        <div className="relative z-10 flex flex-col gap-2 min-w-0 flex-1 text-center sm:text-left">
          <div className="flex items-center justify-center sm:justify-start gap-2">
            <span
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase tracking-wider shadow-sm"
            >
              Offline Cache
            </span>
          </div>

          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight font-['Outfit'] drop-shadow-sm">
            Downloaded Songs
          </h1>

          <div className="flex items-center justify-center sm:justify-start gap-3 text-xs font-semibold text-[#8E8D9F] flex-wrap">
            <span>{downloadedSongs.length} tracks</span>
            {verifiedOfflineCount > 0 && (
              <>
                <span>•</span>
                <span className="flex items-center gap-1 font-mono">
                  <HardDrive className="w-3.5 h-3.5" />
                  ~{totalSizeMb} MB
                </span>
              </>
            )}
            {missingAudioCount > 0 && (
              <>
                <span>•</span>
                <span className="text-amber-400/90 font-medium bg-amber-400/10 px-2 py-0.5 rounded-full text-[11px]" title="Audio missing from offline cache or evicted">
                  {missingAudioCount} missing offline audio
                </span>
              </>
            )}
            {totalDuration > 0 && (
              <>
                <span>•</span>
                <span className="flex items-center gap-1 font-mono">
                  <Clock className="w-3.5 h-3.5" />
                  {formatTime(totalDuration)}
                </span>
              </>
            )}
          </div>

          {/* Action Buttons */}
          {downloadedSongs.length > 0 && (
            <div className="flex items-center justify-center sm:justify-start gap-2.5 mt-1">
              <button
                onClick={handlePlayAll}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="flex items-center gap-2 py-2 px-5 rounded-full font-bold text-xs shadow-md transition-transform active:scale-95 hover:scale-105"
              >
                <Play className="w-3.5 h-3.5 fill-current" /> Play All
              </button>

              <button
                onClick={handleShufflePlay}
                className="flex items-center gap-2 py-2 px-4 rounded-full bg-white/[0.06] text-[#E8E6F0] hover:bg-white/[0.12] hover:text-white border border-white/10 font-bold text-xs shadow-sm transition-all active:scale-95"
              >
                <Shuffle className="w-3.5 h-3.5" /> Shuffle
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ── Search & Track List ── */}
      {downloadedSongs.length > 0 ? (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-1">
            <div className="flex items-center gap-2">
              <h2 className="text-lg sm:text-xl font-extrabold text-white tracking-tight font-['Outfit']">
                Tracks
              </h2>
              <span className="text-xs font-bold text-[#6B6A7A] bg-white/5 px-2.5 py-0.5 rounded-full">
                {filteredSongs.length}
              </span>
            </div>

            {/* Quick Filter Search Bar */}
            <div className="relative w-full sm:w-64">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#6B6A7A]" />
              <input
                type="text"
                placeholder="Search downloaded songs..."
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                className="w-full pl-9 pr-3.5 py-1.5 rounded-xl bg-white/[0.04] border border-white/10 focus:border-[var(--vynce-primary)]/40 text-xs text-white placeholder:text-[#6B6A7A] outline-none transition-colors font-medium"
              />
            </div>
          </div>

          {/* 2-Column Dense Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
            {filteredSongs.map((song, idx) => {
              const isMissing = missingAudioIds.has(song.id);
              return (
                <div key={song.id} className="relative group">
                  <SongCard
                    song={song}
                    index={idx + 1}
                    queueContext={filteredSongs}
                  />
                  {isMissing && (
                    <div className="absolute top-2 right-12 pointer-events-none z-10">
                      <span className="text-[10px] font-bold text-amber-400/90 bg-black/80 backdrop-blur-sm border border-amber-500/20 px-2 py-0.5 rounded-full">
                        {!song.fileSize ? 'Metadata only' : 'Offline audio missing'}
                      </span>
                    </div>
                  )}
                  {!isMissing && typeof song.fileSize === 'number' && song.fileSize > 0 && (
                    <div className="absolute top-2 right-12 pointer-events-none z-10 opacity-0 group-hover:opacity-100 transition-opacity">
                      <span className="text-[10px] font-mono text-white/70 bg-black/70 backdrop-blur-sm border border-white/10 px-2 py-0.5 rounded-full">
                        {(song.fileSize / (1024 * 1024)).toFixed(1)} MB
                      </span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {filteredSongs.length === 0 && searchFilter && (
            <div className="flex flex-col items-center justify-center py-10 text-[#6B6A7A] gap-2">
              <p className="text-xs font-semibold text-white">No downloaded songs match "{searchFilter}"</p>
              <button
                onClick={() => setSearchFilter('')}
                className="text-xs text-[var(--vynce-primary)] hover:underline font-bold"
              >
                Clear filter
              </button>
            </div>
          )}
        </div>
      ) : (
        /* Empty State */
        <div className="flex flex-col items-center justify-center py-16 text-[#6B6A7A] gap-4 bg-[#121118]/60 rounded-2xl border border-white/5 p-6 text-center">
          <div className="p-4 rounded-full bg-white/[0.03] border border-white/10">
            <Download className="w-8 h-8 text-[var(--vynce-primary)]/60" />
          </div>
          <div className="flex flex-col gap-1 max-w-sm">
            <p className="text-base font-bold text-white font-['Outfit']">No Downloaded Songs Yet</p>
            <p className="text-xs text-[#8E8D9F] leading-relaxed">
              Click the download button on any song or in the player to save tracks offline for instant playback.
            </p>
          </div>
          <button
            onClick={onNavigateToExplore}
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="flex items-center gap-2 px-5 py-2.5 rounded-full text-xs font-bold shadow-md transition-transform active:scale-95 hover:scale-105 mt-1"
          >
            <Compass className="w-3.5 h-3.5" />
            <span>Discover Music</span>
          </button>
        </div>
      )}
    </div>
  );
};
