import React, { useState, useMemo } from 'react';
import { useLibraryStore } from '../stores/libraryStore';
import { usePlayerStore } from '../stores/playerStore';
import { SongCard } from '../components/cards/SongCard';
import {
  Heart,
  Play,
  Shuffle,
  Clock,
  Search,
  Music,
  Compass,
  ArrowLeft,
  Share2,
  Check,
} from 'lucide-react';
import { formatTime } from '../utils/formatters';

interface LikedSongsPageProps {
  onNavigateToExplore: () => void;
  onSelectAlbum?: (id: string) => void;
  onSelectArtist?: (id: string) => void;
}

export const LikedSongsPage: React.FC<LikedSongsPageProps> = ({
  onNavigateToExplore,
  onSelectAlbum,
  onSelectArtist,
}) => {
  const { likedSongs } = useLibraryStore();
  const { playSong } = usePlayerStore();
  const [searchFilter, setSearchFilter] = useState('');
  const [copied, setCopied] = useState(false);

  const filteredSongs = useMemo(() => {
    if (!searchFilter.trim()) return likedSongs;
    const q = searchFilter.toLowerCase();
    return likedSongs.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.primaryArtists.toLowerCase().includes(q) ||
        (s.album && (typeof s.album === 'string' ? s.album : (s.album as any).name || '').toLowerCase().includes(q))
    );
  }, [likedSongs, searchFilter]);

  const totalDuration = useMemo(() => {
    return likedSongs.reduce((acc, s) => acc + (s.duration || 0), 0);
  }, [likedSongs]);

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

  const handleShare = () => {
    navigator.clipboard.writeText(`Liked Songs Collection on Vynce (${likedSongs.length} tracks)`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="flex flex-col gap-8 pb-36 w-full animate-in fade-in duration-300">
      {/* ── Top Header Controls ── */}
      <div className="flex items-center justify-between pt-1">
        <button
          onClick={onNavigateToExplore}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/5 shadow-sm"
        >
          <Compass className="w-4 h-4 text-[var(--vynce-primary)]" />
          <span>Explore More Music</span>
        </button>

        <div className="flex items-center gap-2">
          <button
            onClick={handleShare}
            className="p-2.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-colors border border-white/5"
            title="Share Collection Info"
          >
            {copied ? <Check className="w-4 h-4 text-green-400" /> : <Share2 className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {/* ── Cinematic Hero Banner ── */}
      <div
        style={{
          background:
            'radial-gradient(ellipse at 80% 20%, var(--vynce-primary-container) 0%, rgba(20,16,30,0.95) 60%, #0A0A0A 100%)',
        }}
        className="relative overflow-hidden rounded-[36px] p-6 md:p-10 border border-white/10 shadow-2xl flex flex-col md:flex-row items-center md:items-end gap-8 group"
      >
        {/* Ambient Top Glow */}
        <div
          className="absolute right-0 top-0 w-96 h-96 pointer-events-none opacity-30 blur-3xl scale-125 transition-all duration-700"
          style={{ background: 'var(--vynce-primary)' }}
        />

        {/* Liked Songs Big Cover Art Icon */}
        <div className="relative shrink-0 flex items-center justify-center">
          <div
            style={{
              background:
                'radial-gradient(circle at 40% 35%, var(--vynce-primary-container) 0%, rgba(15,13,24,0.95) 100%)',
            }}
            className="relative aspect-square w-48 sm:w-56 md:w-64 rounded-[28px] overflow-hidden shadow-2xl z-10 border border-white/15 flex items-center justify-center group-hover:scale-102 transition-transform duration-500"
          >
            <Heart
              style={{ color: 'var(--vynce-primary)' }}
              className="w-28 h-28 sm:w-32 sm:h-32 fill-current drop-shadow-2xl animate-pulse"
            />
          </div>
        </div>

        {/* Metadata Info */}
        <div className="relative z-10 flex flex-col gap-3 min-w-0 flex-1 text-center md:text-left">
          <div className="flex items-center justify-center md:justify-start gap-2">
            <span
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="text-[11px] font-extrabold px-3 py-1 rounded-full uppercase tracking-wider shadow-sm"
            >
              Auto Collection
            </span>
          </div>

          <h1 className="text-3xl sm:text-4xl md:text-6xl font-black text-white tracking-tight font-['Outfit'] drop-shadow-md">
            Liked Songs
          </h1>

          <p className="text-xs sm:text-sm font-medium text-[#E8E6F0]/80 line-clamp-2">
            Your personal collection of favorite tracks, auto-saved whenever you tap the heart icon across Vynce.
          </p>

          <div className="flex items-center justify-center md:justify-start gap-3 text-xs font-semibold text-[#8E8D9F] mt-1">
            <span>{likedSongs.length} saved songs</span>
            {totalDuration > 0 && (
              <>
                <span>•</span>
                <span className="flex items-center gap-1">
                  <Clock className="w-3.5 h-3.5" />
                  {formatTime(totalDuration)}
                </span>
              </>
            )}
          </div>

          {/* Action Buttons */}
          {likedSongs.length > 0 && (
            <div className="flex items-center justify-center md:justify-start gap-3 mt-3">
              <button
                onClick={handlePlayAll}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="flex items-center gap-2.5 py-3 px-7 rounded-full font-bold text-sm shadow-xl transition-transform active:scale-95 hover:scale-105"
              >
                <Play className="w-4 h-4 fill-current" /> Play All
              </button>

              <button
                onClick={handleShufflePlay}
                className="flex items-center gap-2.5 py-3 px-6 rounded-full bg-white/[0.06] text-[#E8E6F0] hover:bg-white/[0.12] hover:text-white border border-white/10 font-bold text-sm shadow-md transition-all active:scale-95"
              >
                <Shuffle className="w-4 h-4" /> Shuffle
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ── Search Within Liked Songs + Track List ── */}
      {likedSongs.length > 0 ? (
        <div className="flex flex-col gap-5">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 px-1">
            <div className="flex items-center gap-2">
              <h2 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight font-['Outfit']">
                Tracks
              </h2>
              <span className="text-xs font-bold text-[#6B6A7A] bg-white/5 px-2.5 py-0.5 rounded-full">
                {filteredSongs.length}
              </span>
            </div>

            {/* Quick Filter Search Bar */}
            <div className="relative w-full sm:w-72">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[#6B6A7A]" />
              <input
                type="text"
                placeholder="Filter saved songs..."
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                className="w-full pl-10 pr-4 py-2 rounded-2xl bg-white/[0.04] border border-white/10 focus:border-[var(--vynce-primary)]/40 text-xs text-white placeholder:text-[#6B6A7A] outline-none transition-colors font-medium"
              />
            </div>
          </div>

          {/* 2-Column Dense Grid on Desktop */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
            {filteredSongs.map((song, idx) => (
              <SongCard
                key={song.id}
                song={song as any}
                index={idx + 1}
                queueContext={filteredSongs as any}
              />
            ))}
          </div>

          {filteredSongs.length === 0 && searchFilter && (
            <div className="flex flex-col items-center justify-center py-12 text-[#6B6A7A] gap-2">
              <p className="text-sm font-semibold text-white">No songs match "{searchFilter}"</p>
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
        <div className="flex flex-col items-center justify-center py-20 text-[#6B6A7A] gap-4 bg-[#121118]/60 rounded-3xl border border-white/5 p-8 text-center">
          <div className="p-5 rounded-full bg-white/[0.03] border border-white/10">
            <Heart className="w-10 h-10 text-[var(--vynce-primary)]/60" />
          </div>
          <div className="flex flex-col gap-1 max-w-sm">
            <p className="text-lg font-bold text-white font-['Outfit']">No Liked Songs Yet</p>
            <p className="text-xs text-[#8E8D9F] leading-relaxed">
              Tap the heart icon on any song while listening or browsing to save your favorite music here.
            </p>
          </div>
          <button
            onClick={onNavigateToExplore}
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="flex items-center gap-2 px-6 py-3 rounded-full text-xs font-bold shadow-lg transition-transform active:scale-95 hover:scale-105 mt-2"
          >
            <Compass className="w-4 h-4" />
            <span>Discover Music</span>
          </button>
        </div>
      )}
    </div>
  );
};
