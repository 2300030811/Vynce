import React, { useEffect, useState } from 'react';
import { JioSaavnApi } from '../api/jiosaavn';
import { Song, Album, Artist } from '../types/music';
import { usePlayerStore } from '../stores/playerStore';
import { SongCard } from '../components/cards/SongCard';
import { AlbumCard } from '../components/cards/AlbumCard';
import {
  ArrowLeft,
  Share2,
  Shuffle,
  Radio,
  Loader2,
  ChevronRight,
  Check,
} from 'lucide-react';

interface ArtistPageProps {
  artistId: string;
  onBack: () => void;
  onSelectAlbum: (id: string) => void;
}

export const ArtistPage: React.FC<ArtistPageProps> = ({
  artistId,
  onBack,
  onSelectAlbum,
}) => {
  const { playSong } = usePlayerStore();
  const [artistDetails, setArtistDetails] = useState<Artist | null>(null);
  const [topSongs, setTopSongs] = useState<Song[]>([]);
  const [topAlbums, setTopAlbums] = useState<Album[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [copied, setCopied] = useState(false);
  const [imgError, setImgError] = useState(false);

  useEffect(() => {
    const fetchArtist = async () => {
      setIsLoading(true);
      try {
        const { artist, songs, albums } = await JioSaavnApi.getArtist(artistId);
        setArtistDetails(artist);
        setTopSongs(songs || []);
        setTopAlbums(albums || []);
      } catch {
        setArtistDetails(null);
      } finally {
        setIsLoading(false);
      }
    };

    fetchArtist();
  }, [artistId]);

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] text-[#6B6A7A] gap-3">
        <Loader2 style={{ color: 'var(--vynce-primary)' }} className="w-8 h-8 animate-spin" />
        <p className="text-sm font-semibold">Loading artist details...</p>
      </div>
    );
  }

  if (!artistDetails) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] text-[#6B6A7A] gap-4">
        <p className="text-base font-bold text-white">Artist not found</p>
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

  const handleShufflePlay = () => {
    if (topSongs.length > 0) {
      const shuffled = [...topSongs].sort(() => Math.random() - 0.5);
      playSong(shuffled[0], shuffled, 0);
    }
  };

  const handleRadioPlay = () => {
    if (topSongs.length > 0) {
      playSong(topSongs[0], topSongs, 0);
    }
  };

  const handleShare = () => {
    navigator.clipboard.writeText(`${artistDetails.name} on Vynce`);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="flex flex-col gap-8 pb-36 w-full animate-in fade-in duration-300">
      {/* ── Top Floating Action Icons ───────────────────────── */}
      <div className="flex items-center justify-between pt-1">
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/5 shadow-sm"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back</span>
        </button>
        <button
          onClick={handleShare}
          className="p-2.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-colors border border-white/5"
          title="Share Artist"
        >
          {copied ? <Check className="w-4 h-4 text-green-400" /> : <Share2 className="w-4 h-4" />}
        </button>
      </div>

      {/* ── Full-Width Artist Hero Banner (Uncropped Portrait + Ambient Aura) ── */}
      <div
        style={{
          background:
            'radial-gradient(ellipse at 80% 20%, var(--vynce-primary-container) 0%, rgba(18,16,24,0.95) 60%, #0A0A0A 100%)',
        }}
        className="relative overflow-hidden rounded-[36px] p-6 md:p-10 border border-white/10 shadow-2xl flex flex-col md:flex-row items-center md:items-end gap-8 group"
      >
        {/* Dynamic backdrop ambient aura */}
        {artistDetails.image && (
          <div
            className="absolute right-0 top-0 w-96 h-96 pointer-events-none opacity-35 blur-3xl scale-125 transition-all duration-700"
            style={{
              backgroundImage: `url(${artistDetails.image})`,
              backgroundSize: 'cover',
              backgroundPosition: 'center',
            }}
          />
        )}

        {/* Complete Uncropped Artist Portrait */}
        <div className="relative shrink-0 flex items-center justify-center">
          <div className="relative aspect-square w-48 sm:w-56 md:w-64 rounded-full md:rounded-[36px] overflow-hidden shadow-2xl bg-black z-10 border-4 border-white/15 ring-4 ring-black/40 group-hover:scale-102 transition-transform duration-500">
            {artistDetails.image && !imgError ? (
              <img
                src={artistDetails.image}
                alt={artistDetails.name}
                className="w-full h-full object-cover object-center"
                onError={() => setImgError(true)}
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-purple-700 to-indigo-900 text-white font-extrabold text-5xl font-['Outfit']">
                {artistDetails.name.charAt(0)}
              </div>
            )}
          </div>
        </div>

        {/* Artist Meta Information & Actions */}
        <div className="relative z-10 flex flex-col gap-3 min-w-0 flex-1 text-center md:text-left">
          <div className="flex items-center justify-center md:justify-start gap-2">
            <span
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="text-[11px] font-extrabold px-3 py-1 rounded-full uppercase tracking-wider shadow-sm"
            >
              Verified Artist
            </span>
            {artistDetails.followerCount && (
              <span className="text-xs font-semibold text-[#6B6A7A]">
                • {artistDetails.followerCount} listeners
              </span>
            )}
          </div>

          <h1 className="text-3xl sm:text-4xl md:text-6xl font-black text-white tracking-tight font-['Outfit'] line-clamp-2 drop-shadow-md">
            {artistDetails.name}
          </h1>

          {/* Action Buttons Row */}
          <div className="flex items-center justify-center md:justify-start gap-3 mt-3">
            <button
              onClick={handleRadioPlay}
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="flex items-center gap-2.5 py-3 px-7 rounded-full font-bold text-sm shadow-xl transition-transform active:scale-95 hover:scale-105"
            >
              <Radio className="w-4 h-4" /> Play Mix
            </button>

            <button
              onClick={handleShufflePlay}
              className="flex items-center gap-2.5 py-3 px-6 rounded-full bg-white/[0.06] text-[#E8E6F0] hover:bg-white/[0.12] hover:text-white border border-white/10 font-bold text-sm shadow-md transition-all active:scale-95"
            >
              <Shuffle className="w-4 h-4" /> Shuffle
            </button>
          </div>
        </div>
      </div>

      {/* ── Top Songs List (2-Column Dense Grid) ────────────── */}
      {topSongs.length > 0 && (
        <section className="flex flex-col gap-4">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight font-['Outfit']">
              Popular Tracks
            </h2>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
            {topSongs.map((song, idx) => (
              <SongCard
                key={song.id}
                song={song}
                index={idx}
                queueContext={topSongs}
                showAlbum={false}
              />
            ))}
          </div>
        </section>
      )}

      {/* ── Top Albums Row ──────────────────────────────────── */}
      {topAlbums.length > 0 && (
        <section className="flex flex-col gap-4 pt-2">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-xl sm:text-2xl font-extrabold text-white tracking-tight font-['Outfit']">
              Discography & Releases
            </h2>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {topAlbums.map((album) => (
              <AlbumCard
                key={album.id}
                album={album}
                onClick={() => onSelectAlbum(album.id)}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
};

