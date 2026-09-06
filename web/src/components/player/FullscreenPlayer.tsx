import React, { useEffect, useState, useRef } from 'react';
import { usePlayerStore, FullscreenVisualMode } from '../../stores/playerStore';
import { useLibraryStore } from '../../stores/libraryStore';
import { SyncedLyrics } from './SyncedLyrics';
import { formatTime } from '../../utils/formatters';
import {
  ChevronDown,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Heart,
  Sliders,
  ListMusic,
  ListPlus,
  Image as ImageIcon,
  Mic2,
  Moon,
  X,
  PictureInPicture2,
  Users,
} from 'lucide-react';
import { AddToPlaylistModal } from '../modals/AddToPlaylistModal';
import { ListenTogetherModal } from '../modals/ListenTogetherModal';
import { useListenTogetherStore } from '../../stores/listenTogetherStore';

import { NavTab } from '../layout/Sidebar';

interface FullscreenPlayerProps {
  onTabChange?: (tab: NavTab) => void;
}

export const FullscreenPlayer: React.FC<FullscreenPlayerProps> = ({ onTabChange }) => {
  const {
    currentSong,
    status,
    currentTime,
    duration,
    repeatMode,
    isShuffled,
    isFullscreen,
    fullscreenMode,
    sleepTimerRemaining,
    toggleFullscreen,
    setFullscreenMode,
    togglePlay,
    seek,
    toggleRepeat,
    toggleShuffle,
    nextTrack,
    prevTrack,
    toggleQueue,
    toggleSleepTimer,
    togglePiP,
    playbackSpeed,
    setPlaybackSpeed,
  } = usePlayerStore();
  const { isLiked, toggleLike } = useLibraryStore();

  // Smooth Scrubber Dragging State (Declared at top level before any early returns)
  const [isDraggingProgress, setIsDraggingProgress] = useState(false);
  const [dragProgressTime, setDragProgressTime] = useState<number | null>(null);
  const [isAddToPlaylistOpen, setIsAddToPlaylistOpen] = useState(false);
  const [isListenTogetherOpen, setIsListenTogetherOpen] = useState(false);
  const { roomCode } = useListenTogetherStore();
  const progressBarRef = useRef<HTMLDivElement | null>(null);

  // Global Escape key to exit fullscreen
  useEffect(() => {
    if (!isFullscreen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        toggleFullscreen();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isFullscreen, toggleFullscreen]);

  if (!isFullscreen || !currentSong) return null;

  const isPlaying = status === 'playing';
  const liked = isLiked(currentSong.id);

  const calculateTimeFromPointer = (clientX: number) => {
    if (!progressBarRef.current || duration <= 0) return 0;
    const rect = progressBarRef.current.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    return ratio * duration;
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    setIsDraggingProgress(true);
    const newTime = calculateTimeFromPointer(e.clientX);
    setDragProgressTime(newTime);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingProgress) return;
    const newTime = calculateTimeFromPointer(e.clientX);
    setDragProgressTime(newTime);
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDraggingProgress) return;
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId);
    }
    const finalTime = calculateTimeFromPointer(e.clientX);
    seek(finalTime);
    setIsDraggingProgress(false);
    setDragProgressTime(null);
  };

  const displayTime = isDraggingProgress && dragProgressTime !== null ? dragProgressTime : currentTime;
  const pct = duration > 0 ? (displayTime / duration) * 100 : 0;

  const modes: { id: FullscreenVisualMode; label: string; icon: any }[] = [
    { id: 'artwork', label: 'Player', icon: ImageIcon },
    { id: 'karaoke', label: 'Karaoke Lyrics', icon: Mic2 },
  ];

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col justify-between h-screen w-screen overflow-hidden transition-all duration-700 select-none animate-in fade-in"
      style={{
        background: `radial-gradient(circle at 50% 30%, var(--vynce-primary) 0%, rgba(0,0,0,0.85) 60%, #000000 100%)`,
        backgroundColor: '#000000',
      }}
    >
      {/* Background Soft Ambient Light */}
      <div
        className="absolute inset-0 pointer-events-none opacity-25 blur-3xl scale-125 transition-all duration-700"
        style={{
          backgroundImage: `url(${currentSong.image})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      />
      <div className="absolute inset-0 bg-black/50 pointer-events-none" />

      {/* ── Top Bar (Sticky & Always Visible) ──────────────── */}
      <header className="relative z-30 shrink-0 flex items-center justify-between px-4 sm:px-8 py-3 md:px-12 border-b border-white/[0.06] bg-black/40 backdrop-blur-xl shadow-lg">
        <button
          onClick={toggleFullscreen}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/[0.06] hover:bg-white/[0.12] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/10"
          title="Minimize (Esc)"
        >
          <ChevronDown className="w-4 h-4" />
          <span className="hidden sm:inline">Minimize</span>
        </button>

        {/* Mode Switcher Pill (Artwork vs Karaoke) */}
        <div className="flex items-center gap-1 bg-black/60 p-1 rounded-full border border-white/15 backdrop-blur-xl shadow-2xl">
          {modes.map((m) => {
            const Icon = m.icon;
            const isActive = fullscreenMode === m.id;
            return (
              <button
                key={m.id}
                onClick={() => setFullscreenMode(m.id)}
                style={
                  isActive
                    ? {
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }
                    : {}
                }
                className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
                  isActive
                    ? 'shadow-md scale-105 font-extrabold'
                    : 'text-[#6B6A7A] hover:text-white hover:bg-white/10'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{m.label}</span>
              </button>
            );
          })}
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-1 sm:gap-2">
          <button
            onClick={() => setIsListenTogetherOpen(true)}
            style={roomCode ? { color: 'var(--vynce-primary)' } : {}}
            className="p-2.5 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors relative"
            title={roomCode ? `Listen Together (Room: ${roomCode})` : 'Listen Together (Group Sync)'}
          >
            <Users className="w-4 h-4 sm:w-5 sm:h-5" />
            {roomCode && (
              <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[var(--vynce-primary)] animate-pulse" />
            )}
          </button>
          <button
            onClick={toggleSleepTimer}
            style={sleepTimerRemaining ? { color: 'var(--vynce-primary)' } : {}}
            className="p-2.5 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors relative"
            title="Sleep Timer"
          >
            <Moon className="w-4 h-4 sm:w-5 sm:h-5" />
            {sleepTimerRemaining !== null && (
              <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[var(--vynce-primary)] animate-pulse" />
            )}
          </button>
          <button
            onClick={() => {
              toggleFullscreen();
              onTabChange?.('equalizer');
            }}
            className="p-2.5 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
            title="Equalizer & DSP Console"
          >
            <Sliders className="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
          {/* Speed Scaler */}
          <button
            onClick={() => {
              const speeds = [0.75, 1.0, 1.25, 1.5, 2.0];
              const next = speeds[(speeds.indexOf(playbackSpeed) + 1) % speeds.length];
              setPlaybackSpeed(next);
            }}
            className="px-2.5 py-1 rounded-full bg-white/[0.06] hover:bg-white/[0.12] text-xs font-mono font-bold text-[#E8E6F0] hover:text-white transition-all border border-white/10 shrink-0"
            title={`Playback Speed: ${playbackSpeed}x (Click to cycle)`}
          >
            {playbackSpeed}x
          </button>
          <button
            onClick={togglePiP}
            className="p-2.5 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
            title="Picture-in-Picture Mini Player (Live Lyrics)"
          >
            <PictureInPicture2 className="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
          <button
            onClick={toggleQueue}
            className="p-2.5 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
            title="Queue"
          >
            <ListMusic className="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
          <button
            onClick={toggleFullscreen}
            className="p-2.5 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
            title="Close Fullscreen"
          >
            <X className="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
        </div>
      </header>

      {/* ── Center Stage ───────────────────────────────────── */}
      <main className="relative z-10 flex-1 min-h-0 flex items-center justify-center px-4 sm:px-8 md:px-12 max-w-7xl mx-auto w-full overflow-hidden py-4">
        {/* MODE 1: ARTWORK (Classic Split View) */}
        {fullscreenMode === 'artwork' && (
          <div className="flex flex-col md:flex-row items-center justify-center gap-6 md:gap-14 w-full h-full animate-in fade-in duration-300">
            {/* Left: Cover Art & Song Info */}
            <div className="flex flex-col items-center md:items-start gap-4 max-w-sm md:max-w-md w-full shrink-0">
              <div className="relative aspect-square w-52 sm:w-64 md:w-80 lg:w-92 rounded-[36px] overflow-hidden shadow-[0_25px_60px_rgba(0,0,0,0.85)] border border-white/10 bg-[var(--vynce-surface-container)] group">
                <img
                  src={currentSong.image || '/favicon.svg'}
                  alt={currentSong.name}
                  className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = '/favicon.svg';
                  }}
                />
              </div>

              <div className="flex items-center justify-between w-full">
                <div className="flex flex-col min-w-0 pr-4">
                  <h1 className="text-xl sm:text-2xl md:text-3xl font-extrabold text-white truncate tracking-tight font-['Outfit']">
                    {currentSong.name}
                  </h1>
                  <p className="text-xs sm:text-sm font-semibold text-[#E8E6F0]/70 truncate mt-0.5">
                    {currentSong.primaryArtists}
                  </p>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => setIsAddToPlaylistOpen(true)}
                    className="w-11 h-11 rounded-full flex items-center justify-center transition-transform active:scale-90 bg-white/[0.06] text-[#E8E6F0] hover:bg-white/[0.12] hover:text-white"
                    title="Add to Playlist"
                  >
                    <ListPlus className="w-5 h-5" />
                  </button>

                  <button
                    onClick={() => toggleLike(currentSong)}
                    className={`w-11 h-11 rounded-full flex items-center justify-center transition-transform active:scale-90 ${
                      liked
                        ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] shadow-lg'
                        : 'bg-white/[0.06] text-[#E8E6F0] hover:bg-white/[0.1]'
                    }`}
                    title={liked ? 'Liked' : 'Like'}
                  >
                    <Heart className={`w-5 h-5 ${liked ? 'fill-current text-[var(--vynce-primary)]' : ''}`} />
                  </button>
                </div>
              </div>
            </div>

            {/* Right: Live Synced Lyrics (Clean Minimal View without bulky header box in Player mode) */}
            <div className="flex-1 w-full h-[55vh] md:h-[65vh] max-h-[580px] flex flex-col overflow-hidden">
              <SyncedLyrics hideHeader={true} />
            </div>
          </div>
        )}

        {/* MODE 2: FULL KARAOKE IMMERSION */}
        {fullscreenMode === 'karaoke' && (
          <div className="w-full h-[60vh] md:h-[70vh] max-h-[650px] flex flex-col overflow-hidden animate-in fade-in duration-300 max-w-3xl">
            <SyncedLyrics hideHeader={false} />
          </div>
        )}
      </main>

      {/* ── Bottom Controls (Sticky & Always Visible) ─────── */}
      <footer className="relative z-30 shrink-0 px-4 sm:px-8 py-4 md:px-12 max-w-3xl mx-auto w-full flex flex-col gap-3 bg-black/40 backdrop-blur-xl rounded-t-3xl border-t border-white/[0.06] shadow-2xl">
        {/* Scrubber Progress Slider with Smooth Dragging */}
        <div className="flex flex-col gap-1.5 py-1">
          <div
            ref={progressBarRef}
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
            className="group relative h-2 hover:h-3 w-full bg-white/15 rounded-full cursor-pointer transition-all flex items-center select-none touch-none"
          >
            <div
              className="absolute top-0 left-0 h-full rounded-full transition-all duration-75 ease-out"
              style={{ width: `${pct}%`, backgroundColor: 'var(--vynce-primary)' }}
            />
            {/* Draggable Glowing Handle */}
            <div
              className="absolute w-3.5 h-3.5 rounded-full bg-white shadow-[0_0_12px_var(--vynce-primary)] border-2 border-[var(--vynce-primary)] -translate-x-1/2 transition-transform duration-75 group-hover:scale-125 active:scale-140 pointer-events-none"
              style={{
                left: `${pct}%`,
                opacity: isDraggingProgress ? 1 : undefined,
              }}
            />
          </div>

          <div className="flex justify-between text-[11px] font-mono font-bold text-[#6B6A7A]">
            <span>{formatTime(displayTime)}</span>
            <span>{formatTime(duration)}</span>
          </div>
        </div>

        {/* Controls Row */}
        <div className="flex items-center justify-between">
          <button
            onClick={toggleShuffle}
            className={`p-2.5 rounded-full transition-colors ${
              isShuffled ? 'text-[var(--vynce-primary)]' : 'text-[#6B6A7A] hover:text-white'
            }`}
            title="Shuffle"
          >
            <Shuffle className="w-5 h-5" />
          </button>

          <button
            onClick={prevTrack}
            className="p-2.5 rounded-full text-[#E8E6F0] hover:text-white transition-transform active:scale-90"
            title="Previous"
          >
            <SkipBack className="w-6 h-6 fill-current" />
          </button>

          {/* Large Material 3 Squircle Play Button */}
          <button
            onClick={togglePlay}
            className="w-18 h-12 rounded-[20px] bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] flex items-center justify-center shadow-xl transition-transform active:scale-95 hover:scale-105"
            title={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? (
              <Pause className="w-6 h-6 fill-current" />
            ) : (
              <Play className="w-6 h-6 fill-current translate-x-0.5" />
            )}
          </button>

          <button
            onClick={nextTrack}
            className="p-2.5 rounded-full text-[#E8E6F0] hover:text-white transition-transform active:scale-90"
            title="Next"
          >
            <SkipForward className="w-6 h-6 fill-current" />
          </button>

          <button
            onClick={toggleRepeat}
            className={`p-2.5 rounded-full transition-colors ${
              repeatMode !== 'off' ? 'text-[var(--vynce-primary)]' : 'text-[#6B6A7A] hover:text-white'
            }`}
            title={`Repeat: ${repeatMode}`}
          >
            {repeatMode === 'one' ? <Repeat1 className="w-5 h-5" /> : <Repeat className="w-5 h-5" />}
          </button>
        </div>
      </footer>

      {/* Add to Playlist Modal */}
      <AddToPlaylistModal
        isOpen={isAddToPlaylistOpen}
        onClose={() => setIsAddToPlaylistOpen(false)}
        song={currentSong}
      />

      {/* Listen Together Modal */}
      <ListenTogetherModal
        isOpen={isListenTogetherOpen}
        onClose={() => setIsListenTogetherOpen(false)}
      />
    </div>
  );
};


