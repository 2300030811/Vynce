import React, { useEffect, useState } from 'react';
import { JioSaavnApi } from '../api/jiosaavn';
import { Album, Song } from '../types/music';
import { usePlayerStore } from '../stores/playerStore';
import { useLibraryStore } from '../stores/libraryStore';
import { SongCard } from '../components/cards/SongCard';
import {
  ArrowLeft,
  Play,
  Shuffle,
  Heart,
  Share2,
  Loader2,
  Clock,
  Disc3,
  Check,
  Bookmark,
  BookmarkCheck,
} from 'lucide-react';
import { formatTime } from '../utils/formatters';

interface AlbumPageProps {
  albumId: string;
  onBack: () => void;
  onSelectArtist: (id: string) => void;
}

export const AlbumPage: React.FC<AlbumPageProps> = ({
  albumId,
  onBack,
  onSelectArtist,
}) => {
  const { playSong } = usePlayerStore();
  const { isAlbumSaved, toggleSaveAlbum } = useLibraryStore();

  const [album, setAlbum] = useState<Album | null>(null);
  const [songs, setSongs] = useState<Song[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [copied, setCopied] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const isSaved = album ? isAlbumSaved(album.id) : false;

  useEffect(() => {
    const fetchAlbum = async () => {
      setIsLoading(true);
      try {
        const { album: albumData, songs: songsData } = await JioSaavnApi.getAlbum(albumId);
        setAlbum(albumData);
        setSongs(songsData || []);
      } catch {
        setAlbum(null);
      } finally {
        setIsLoading(false);
      }
    };

    fetchAlbum();
  }, [albumId]);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] text-[#6B6A7A] gap-3">
        <Loader2 style={{ color: 'var(--vynce-primary)' }} className="w-8 h-8 animate-spin" />
        <p className="text-sm font-semibold">Loading album details...</p>
      </div>
    );
  }

  if (!album) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] text-[#6B6A7A] gap-4">
        <p className="text-base font-bold text-white">Album not found</p>
        <button
          onClick={onBack}
          style={{
            backgroundColor: 'var(--vynce-primary-container)',
            color: 'var(--vynce-on-primary-container)',
          }}
          className="px-6 py-2.5 rounded-full text-xs font-bold shadow-md"
        >
          Go Back
        </button>
      </div>
    );
  }

  const handlePlayAll = () => {
    if (songs.length > 0) {
      playSong(songs[0], songs, 0);
    }
  };

  const handleShufflePlay = () => {
    if (songs.length > 0) {
      const shuffled = [...songs].sort(() => Math.random() - 0.5);
      playSong(shuffled[0], shuffled, 0);
    }
  };

  const handleToggleSave = async () => {
    if (!album) return;
    const added = await toggleSaveAlbum(album, songs);
    setToastMsg(added ? 'Album saved to your Library!' : 'Album removed from Library');
    setTimeout(() => setToastMsg(null), 3000);
  };

  const handleShare = () => {
    navigator.clipboard.writeText(`${album.name} - Album on Vynce`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  const totalDuration = songs.reduce((acc, s) => acc + (s.duration || 0), 0);

  return (
    <div className="flex flex-col gap-8 pb-36 w-full animate-in fade-in duration-300">
      {/* ── Top Back Button & Breadcrumbs ──────────────────── */}
      <div className="flex items-center justify-between pt-1">
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/5 shadow-sm"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back</span>
        </button>

        <div className="flex items-center gap-2">
          <button
            onClick={handleToggleSave}
            className={`p-2.5 rounded-full transition-colors border ${
              isSaved
                ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] border-transparent'
                : 'bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white border-white/5'
            }`}
            title={isSaved ? 'Remove from Library' : 'Save Album to Library'}
          >
            {isSaved ? <BookmarkCheck className="w-4 h-4" /> : <Bookmark className="w-4 h-4" />}
          </button>

          <button
            onClick={handleShare}
            className="p-2.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-colors border border-white/5"
            title="Share Album"
          >
            {copied ? <Check className="w-4 h-4 text-green-400" /> : <Share2 className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {toastMsg && (
        <div className="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] text-xs font-bold shadow-lg animate-in fade-in">
          <Check className="w-4 h-4" />
          <span>{toastMsg}</span>
        </div>
      )}

      {/* ── Cinematic Full-Width Album Header Banner ──────── */}
      <div
        style={{
          background:
            'radial-gradient(ellipse at 80% 20%, var(--vynce-primary-container) 0%, rgba(18,16,24,0.95) 60%, #0A0A0A 100%)',
        }}
        className="relative overflow-hidden rounded-[36px] p-6 md:p-10 border border-white/10 shadow-2xl flex flex-col md:flex-row items-center md:items-end gap-8 group"
      >
        {/* Dynamic backdrop ambient aura */}
        <div
          className="absolute right-0 top-0 w-96 h-96 pointer-events-none opacity-30 blur-3xl scale-125 transition-all duration-700"
          style={{
            backgroundImage: `url(${album.image})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
          }}
        />

        {/* Artwork with Vinyl Sleeve Effect */}
        <div className="relative shrink-0 flex items-center justify-center">
          <div className="relative aspect-square w-48 sm:w-56 md:w-64 rounded-[28px] overflow-hidden shadow-2xl bg-black z-10 border border-white/15">
            <img
              src={album.image || '/favicon.svg'}
              alt={album.name}
              className="w-full h-full object-cover"
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/favicon.svg';
              }}
            />
          </div>
        </div>

        {/* Album Meta Information */}
        <div className="relative z-10 flex flex-col gap-3 min-w-0 flex-1 text-center md:text-left">
          <div className="flex items-center justify-center md:justify-start gap-2">
            <span
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="text-[11px] font-extrabold px-3 py-1 rounded-full uppercase tracking-wider shadow-sm"
            >
              Album {album.year ? `• ${album.year}` : ''}
            </span>
          </div>

          <h1 className="text-3xl sm:text-4xl md:text-5xl lg:text-6xl font-black text-white tracking-tight font-['Outfit'] drop-shadow-md">
            {album.name}
          </h1>

          <p className="text-sm md:text-base font-semibold text-[#E8E6F0]/80">
            {album.artists}
          </p>

          <div className="flex items-center justify-center md:justify-start gap-3 text-xs font-semibold text-[#6B6A7A] mt-1">
            <span>{songs.length} tracks</span>
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

          {/* Action Buttons Row */}
          <div className="flex items-center justify-center md:justify-start gap-3 mt-3 flex-wrap">
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

            {/* Save to Library Button */}
            <button
              onClick={handleToggleSave}
              className={`flex items-center gap-2.5 py-3 px-6 rounded-full font-bold text-sm shadow-md transition-all active:scale-95 border ${
                isSaved
                  ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] border-transparent'
                  : 'bg-white/[0.06] text-[#E8E6F0] hover:bg-white/[0.12] hover:text-white border-white/10'
              }`}
            >
              {isSaved ? (
                <>
                  <BookmarkCheck className="w-4 h-4" />
                  <span>Saved in Library</span>
                </>
              ) : (
                <>
                  <Bookmark className="w-4 h-4" />
                  <span>Save Album</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* ── High-Density 2-Column Responsive Track Grid ────── */}
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between px-1">
          <h2 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight font-['Outfit'] flex items-center gap-2">
            <span>Album Tracks</span>
            <span className="text-xs font-bold text-[#6B6A7A] bg-white/5 px-2.5 py-0.5 rounded-full">
              {songs.length}
            </span>
          </h2>
        </div>

        {/* 2-Column dense layout on desktop */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
          {songs.map((song, idx) => (
            <SongCard
              key={song.id}
              song={song}
              index={idx}
              queueContext={songs}
              showAlbum={false}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

