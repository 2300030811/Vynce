import React, { useState } from 'react';
import { usePlayerStore } from '../../stores/playerStore';
import { useLibraryStore } from '../../stores/libraryStore';
import { formatTime } from '../../utils/formatters';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Heart,
  ListPlus,
  Sliders,
  ListMusic,
  Maximize2,
  Mic2,
  Volume2,
  VolumeX,
  Moon,
  PictureInPicture2,
} from 'lucide-react';
import { AddToPlaylistModal } from '../modals/AddToPlaylistModal';

import { NavTab } from '../layout/Sidebar';

interface BottomPlayerProps {
  onTabChange?: (tab: NavTab) => void;
}

export const BottomPlayer: React.FC<BottomPlayerProps> = ({ onTabChange }) => {
  const {
    currentSong,
    status,
    currentTime,
    duration,
    volume,
    isMuted,
    repeatMode,
    isShuffled,
    sleepTimerRemaining,
    togglePlay,
    seek,
    setVolume,
    toggleMute,
    toggleRepeat,
    toggleShuffle,
    nextTrack,
    prevTrack,
    toggleFullscreen,
    toggleQueue,
    toggleSleepTimer,
    togglePiP,
  } = usePlayerStore();
  const { isLiked, toggleLike } = useLibraryStore();
  const [isAddToPlaylistOpen, setIsAddToPlaylistOpen] = useState(false);

  if (!currentSong) return null;

  const isPlaying = status === 'playing';
  const liked = isLiked(currentSong.id);
  const pct = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <footer className="fixed bottom-14 md:bottom-0 left-0 right-0 z-40 bg-[#0A0A0A]/95 backdrop-blur-2xl border-t border-white/[0.08] px-3 py-2 md:px-8 md:py-3 shadow-[0_-8px_30px_rgba(0,0,0,0.7)] select-none">
      {/* Mobile top thin progress indicator */}
      <div className="md:hidden absolute top-0 left-0 right-0 h-[2px] bg-white/10 overflow-hidden">
        <div
          className="h-full transition-all duration-100 ease-out"
          style={{ width: `${pct}%`, backgroundColor: 'var(--vynce-primary)' }}
        />
      </div>

      <div className="max-w-[1920px] mx-auto flex items-center justify-between gap-2 sm:gap-4">
        {/* LEFT: Track Info & Artwork */}
        <div className="flex items-center gap-3 min-w-0 w-48 sm:w-64 md:w-80 shrink-0">
          <div
            onClick={toggleFullscreen}
            className="relative w-12 h-12 rounded-2xl overflow-hidden bg-white/5 border border-white/10 shrink-0 cursor-pointer group shadow-md"
          >
            <img
              src={currentSong.image || '/favicon.svg'}
              alt={currentSong.name}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform"
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/favicon.svg';
              }}
            />
            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
              <Maximize2 className="w-4 h-4 text-white" />
            </div>
          </div>

          <div className="flex flex-col min-w-0 flex-1">
            <h4
              onClick={toggleFullscreen}
              className="text-sm font-bold text-white truncate tracking-tight hover:text-[var(--vynce-primary)] cursor-pointer transition-colors"
            >
              {currentSong.name}
            </h4>
            <p className="text-xs text-[#6B6A7A] truncate mt-0.5">
              {currentSong.primaryArtists}
            </p>
          </div>

          <button
            onClick={() => toggleLike(currentSong)}
            className={`p-2 rounded-full hover:bg-white/8 transition-colors hidden sm:flex shrink-0 ${
              liked ? 'text-[var(--vynce-primary)]' : 'text-[#6B6A7A] hover:text-white'
            }`}
            title={liked ? 'Liked' : 'Like'}
          >
            <Heart className={`w-4 h-4 ${liked ? 'fill-current' : ''}`} />
          </button>

          <button
            onClick={() => setIsAddToPlaylistOpen(true)}
            className="p-2 rounded-full hover:bg-white/8 text-[#6B6A7A] hover:text-white transition-colors hidden sm:flex shrink-0"
            title="Add to Playlist"
          >
            <ListPlus className="w-4 h-4" />
          </button>

          <button
            onClick={toggleFullscreen}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/8 transition-colors hidden sm:flex shrink-0"
            title="Lyrics & Visualizer"
          >
            <Mic2 className="w-4 h-4" />
          </button>
        </div>

        {/* CENTER: Transport Controls & Scrubber */}
        <div className="flex flex-col items-center gap-1.5 flex-1 max-w-xl">
          <div className="flex items-center gap-4 md:gap-5">
            <button
              onClick={toggleShuffle}
              className={`p-1.5 rounded-full transition-colors hidden md:block ${
                isShuffled ? 'text-[var(--vynce-primary)]' : 'text-[#6B6A7A] hover:text-white'
              }`}
              title="Shuffle"
            >
              <Shuffle className="w-4 h-4" />
            </button>

            <button
              onClick={prevTrack}
              className="p-1.5 text-[#E8E6F0] hover:text-white transition-transform active:scale-90"
              title="Previous"
            >
              <SkipBack className="w-5 h-5 fill-current" />
            </button>

            {/* Signature Material 3 Squircle Play Button dynamically tinted */}
            <button
              onClick={togglePlay}
              className="w-10 h-10 md:w-11 md:h-11 rounded-[16px] bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] flex items-center justify-center shadow-lg transition-transform active:scale-95 hover:scale-105"
              title={isPlaying ? 'Pause' : 'Play'}
            >
              {isPlaying ? (
                <Pause className="w-5 h-5 fill-current" />
              ) : (
                <Play className="w-5 h-5 fill-current translate-x-0.5" />
              )}
            </button>

            <button
              onClick={nextTrack}
              className="p-1.5 text-[#E8E6F0] hover:text-white transition-transform active:scale-90"
              title="Next"
            >
              <SkipForward className="w-5 h-5 fill-current" />
            </button>

            <button
              onClick={toggleRepeat}
              className={`p-1.5 rounded-full transition-colors hidden md:block ${
                repeatMode !== 'off' ? 'text-[var(--vynce-primary)]' : 'text-[#6B6A7A] hover:text-white'
              }`}
              title={`Repeat: ${repeatMode}`}
            >
              {repeatMode === 'one' ? <Repeat1 className="w-4 h-4" /> : <Repeat className="w-4 h-4" />}
            </button>
          </div>

          {/* Desktop Scrubber */}
          <div className="hidden md:flex items-center gap-3 w-full">
            <span className="text-[11px] font-mono font-semibold text-[#6B6A7A] w-9 text-right">
              {formatTime(currentTime)}
            </span>

            <div
              className="group relative flex-1 h-1.5 bg-white/15 rounded-full overflow-hidden cursor-pointer hover:h-2 transition-all"
              onClick={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                seek(Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width)) * duration);
              }}
            >
              <div
                className="h-full rounded-full transition-all duration-75"
                style={{ width: `${pct}%`, backgroundColor: 'var(--vynce-primary)' }}
              />
            </div>

            <span className="text-[11px] font-mono font-semibold text-[#6B6A7A] w-9">
              {formatTime(duration)}
            </span>
          </div>
        </div>

        {/* RIGHT: Audio Utilities & Volume */}
        <div className="flex items-center justify-end gap-3 w-1/4">
          <button
            onClick={toggleSleepTimer}
            style={sleepTimerRemaining ? { color: 'var(--vynce-primary)' } : {}}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.04] transition-colors relative hidden lg:block"
            title="Sleep Timer"
          >
            <Moon className="w-4 h-4" />
            {sleepTimerRemaining !== null && (
              <span className="absolute top-1 right-1 w-1.5 h-1.5 rounded-full bg-[var(--vynce-primary)] animate-pulse" />
            )}
          </button>

          <button
            onClick={() => onTabChange?.('equalizer')}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.04] transition-colors hidden lg:block"
            title="10-Band Equalizer & DSP"
          >
            <Sliders className="w-4 h-4" />
          </button>

          <button
            onClick={togglePiP}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.04] transition-colors hidden sm:block"
            title="Picture-in-Picture Floating Mini Player (Live Lyrics)"
          >
            <PictureInPicture2 className="w-4 h-4" />
          </button>

          <button
            onClick={toggleQueue}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.04] transition-colors hidden sm:block"
            title="Queue"
          >
            <ListMusic className="w-4 h-4" />
          </button>

          <div className="hidden md:flex items-center gap-2">
            <button
              onClick={toggleMute}
              className="p-1.5 text-[#6B6A7A] hover:text-white transition-colors"
              title={isMuted ? 'Unmute' : 'Mute'}
            >
              {isMuted || volume === 0 ? (
                <VolumeX className="w-4 h-4 text-red-400" />
              ) : (
                <Volume2 className="w-4 h-4" />
              )}
            </button>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={isMuted ? 0 : volume}
              onChange={(e) => setVolume(parseFloat(e.target.value))}
              style={{ accentColor: 'var(--vynce-primary)' }}
              className="w-20 h-1 bg-white/15 rounded-full appearance-none cursor-pointer"
            />
          </div>

          <button
            onClick={toggleFullscreen}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.04] transition-colors hidden sm:block"
            title="Fullscreen Immersion"
          >
            <Maximize2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Add to Playlist Modal */}
      <AddToPlaylistModal
        isOpen={isAddToPlaylistOpen}
        onClose={() => setIsAddToPlaylistOpen(false)}
        song={currentSong}
      />
    </footer>
  );
};
