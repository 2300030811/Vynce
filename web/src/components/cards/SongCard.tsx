import React, { useState, useRef, useEffect } from 'react';
import { Song } from '../../types/music';
import { usePlayerStore } from '../../stores/playerStore';
import { useLibraryStore } from '../../stores/libraryStore';
import { useQueueStore } from '../../stores/queueStore';
import { JioSaavnApi } from '../../api/jiosaavn';
import {
  Play,
  MoreVertical,
  Heart,
  ListPlus,
  Radio,
  Download,
  Share2,
  ListMusic,
  Plus,
  Check,
} from 'lucide-react';
import { AddToPlaylistModal } from '../modals/AddToPlaylistModal';

interface SongCardProps {
  song: Song;
  index?: number;
  queueContext?: Song[];
  showAlbum?: boolean;
}

export const SongCard: React.FC<SongCardProps> = ({ song, index, queueContext }) => {
  const { currentSong, status, playSong, togglePlay, startSongRadio } = usePlayerStore();
  const { isLiked, toggleLike, playlists, addSongToPlaylist } = useLibraryStore();
  const { playNext, addToQueue } = useQueueStore();

  const [showMenu, setShowMenu] = useState(false);
  const [showPlaylistsSubmenu, setShowPlaylistsSubmenu] = useState(false);
  const [showAddToPlaylistModal, setShowAddToPlaylistModal] = useState(false);
  const [copied, setCopied] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const isCurrent = currentSong?.id === song.id;
  const isPlaying = isCurrent && status === 'playing';
  const liked = isLiked(song.id);

  // Close menu on click outside
  useEffect(() => {
    if (!showMenu) return;
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setShowMenu(false);
        setShowPlaylistsSubmenu(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [showMenu]);

  const handlePlay = (e: React.MouseEvent) => {
    e.stopPropagation();
    isCurrent ? togglePlay() : playSong(song, queueContext, index);
  };

  const [feedbackToast, setFeedbackToast] = useState<string | null>(null);

  const handlePlayNext = (e: React.MouseEvent) => {
    e.stopPropagation();
    playNext(song);
    setShowMenu(false);
    setFeedbackToast('Will play next');
    setTimeout(() => setFeedbackToast(null), 1800);
  };

  const handleAddToQueue = (e: React.MouseEvent) => {
    e.stopPropagation();
    addToQueue(song);
    setShowMenu(false);
    setFeedbackToast('Added to queue');
    setTimeout(() => setFeedbackToast(null), 1800);
  };

  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMenu(false);
    let url = song.downloadUrl;
    if (!url) {
      const details = await JioSaavnApi.getSong(song.id);
      url = details?.downloadUrl || '';
    }
    if (url) {
      try {
        const response = await fetch(url);
        const blob = await response.blob();
        const blobUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = `${song.name} - ${song.primaryArtists}.mp3`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(blobUrl), 2000);
      } catch {
        const a = document.createElement('a');
        a.href = url;
        a.download = `${song.name} - ${song.primaryArtists}.mp3`;
        a.target = '_blank';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
      }
    }
  };

  const handleCopyLink = (e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(`${song.name} by ${song.primaryArtists}`);
    setCopied(true);
    setTimeout(() => {
      setCopied(false);
      setShowMenu(false);
    }, 1200);
  };

  return (
    <div
      onClick={handlePlay}
      style={
        isCurrent
          ? {
              backgroundColor: 'color-mix(in srgb, var(--vynce-primary-container) 45%, transparent)',
            }
          : {}
      }
      className={`group relative flex items-center justify-between p-2 rounded-2xl cursor-pointer transition-all duration-150 border border-transparent ${
        isCurrent
          ? 'border-[var(--vynce-primary)]/20 shadow-sm'
          : 'hover:bg-white/[0.04] active:bg-white/[0.06]'
      }`}
    >
      <div className="flex items-center gap-3 min-w-0 flex-1">
        <div className="relative w-12 h-12 rounded-xl overflow-hidden shrink-0 bg-[var(--vynce-surface-container)] shadow-sm">
          <img
            src={song.image || '/favicon.svg'}
            alt={song.name}
            className="w-full h-full object-cover transition-transform group-hover:scale-105"
            loading="lazy"
            onError={(e) => {
              (e.target as HTMLImageElement).src = '/favicon.svg';
            }}
          />
          {isCurrent && (
            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
              {isPlaying ? (
                <span className="flex items-end gap-0.5 h-3.5">
                  <span
                    style={{ backgroundColor: 'var(--vynce-primary)' }}
                    className="w-0.5 h-full animate-pulse"
                  />
                  <span
                    style={{ backgroundColor: 'var(--vynce-primary)' }}
                    className="w-0.5 h-2 animate-pulse delay-75"
                  />
                  <span
                    style={{ backgroundColor: 'var(--vynce-primary)' }}
                    className="w-0.5 h-3 animate-pulse delay-150"
                  />
                </span>
              ) : (
                <Play
                  style={{ color: 'var(--vynce-on-primary-container)' }}
                  className="w-4 h-4 fill-current"
                />
              )}
            </div>
          )}
        </div>

        <div className="flex flex-col min-w-0">
          <span
            style={isCurrent ? { color: 'var(--vynce-primary)' } : {}}
            className={`text-sm font-semibold truncate ${
              isCurrent ? 'font-bold' : 'text-[#E8E6F0]'
            }`}
          >
            {song.name}
          </span>
          <span className="text-xs text-[#6B6A7A] truncate mt-0.5">
            {song.primaryArtists}
          </span>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="flex items-center gap-1 shrink-0 ml-2">
        {feedbackToast && (
          <span className="px-2 py-0.5 rounded-lg bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] text-[10px] font-bold shadow-sm animate-in fade-in zoom-in-95">
            {feedbackToast}
          </span>
        )}

        <button
          onClick={(e) => {
            e.stopPropagation();
            toggleLike(song);
          }}
          className={`p-2 rounded-full hover:bg-white/8 transition-colors ${
            liked ? 'text-[var(--vynce-primary)]' : 'text-[#6B6A7A]/40 hover:text-[#6B6A7A]'
          }`}
          title={liked ? 'Liked' : 'Like'}
        >
          <Heart className={`w-4 h-4 ${liked ? 'fill-current' : ''}`} />
        </button>

        <div className="relative" ref={menuRef}>
          <button
            onClick={(e) => {
              e.stopPropagation();
              setShowMenu(!showMenu);
            }}
            className="p-2 rounded-full text-[#6B6A7A]/50 hover:text-white hover:bg-white/8 transition-colors"
            title="More Options"
          >
            <MoreVertical className="w-4 h-4" />
          </button>

          {/* Context Dropdown Menu */}
          {showMenu && (
            <div className="absolute right-0 top-full mt-1.5 w-52 bg-[#0A0A0A] rounded-2xl p-1.5 shadow-2xl border border-white/10 flex flex-col gap-0.5 z-50 animate-in fade-in zoom-in-95 duration-150 backdrop-blur-xl">
              <button
                onClick={handlePlayNext}
                className="flex flex-col gap-0.5 px-3 py-2 rounded-xl text-xs font-semibold text-[#E8E6F0] hover:bg-white/[0.08] transition-colors text-left"
              >
                <div className="flex items-center gap-2.5">
                  <Radio className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                  <span>Play Next</span>
                </div>
                <span className="text-[10px] text-[#6B6A7A] pl-6 font-normal">Play right after current</span>
              </button>

              <button
                onClick={handleAddToQueue}
                className="flex flex-col gap-0.5 px-3 py-2 rounded-xl text-xs font-semibold text-[#E8E6F0] hover:bg-white/[0.08] transition-colors text-left"
              >
                <div className="flex items-center gap-2.5">
                  <ListMusic className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                  <span>Add to Queue</span>
                </div>
                <span className="text-[10px] text-[#6B6A7A] pl-6 font-normal">Append to end of queue</span>
              </button>

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowMenu(false);
                  startSongRadio(song);
                }}
                className="flex flex-col gap-0.5 px-3 py-2 rounded-xl text-xs font-semibold text-[#E8E6F0] hover:bg-white/[0.08] transition-colors text-left"
              >
                <div className="flex items-center gap-2.5">
                  <Radio className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                  <span>Start Song Radio</span>
                </div>
                <span className="text-[10px] text-[#6B6A7A] pl-6 font-normal">Infinite smart autoplay mix</span>
              </button>

              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setShowMenu(false);
                  setShowAddToPlaylistModal(true);
                }}
                className="flex items-center justify-between w-full px-3 py-2 rounded-xl text-xs font-semibold text-[#E8E6F0] hover:bg-white/[0.08] transition-colors text-left"
              >
                <div className="flex items-center gap-2.5">
                  <ListPlus className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                  <span>Add to Playlist</span>
                </div>
                <Plus className="w-3 h-3 text-[#6B6A7A]" />
              </button>

              <div className="h-px bg-white/5 my-1" />

              <button
                onClick={handleDownload}
                className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-[#E8E6F0] hover:bg-white/[0.08] transition-colors text-left"
              >
                <Download className="w-3.5 h-3.5 text-[#6B6A7A]" />
                <span>Download (320kbps)</span>
              </button>

              <button
                onClick={handleCopyLink}
                className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold text-[#E8E6F0] hover:bg-white/[0.08] transition-colors text-left"
              >
                {copied ? (
                  <Check className="w-3.5 h-3.5 text-green-400" />
                ) : (
                  <Share2 className="w-3.5 h-3.5 text-[#6B6A7A]" />
                )}
                <span>{copied ? 'Copied!' : 'Share Track'}</span>
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Add to Playlist Modal */}
      <AddToPlaylistModal
        isOpen={showAddToPlaylistModal}
        onClose={() => setShowAddToPlaylistModal(false)}
        song={song}
      />
    </div>
  );
};

