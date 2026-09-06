import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import {
  History,
  Play,
  Trash2,
  X,
  Clock,
  Music,
  Search,
} from 'lucide-react';
import { useLibraryStore } from '../../stores/libraryStore';
import { usePlayerStore } from '../../stores/playerStore';
import { formatTime } from '../../utils/formatters';
import { Song } from '../../types/music';

interface HistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const HistoryModal: React.FC<HistoryModalProps> = ({ isOpen, onClose }) => {
  const { history, clearHistory } = useLibraryStore();
  const { playSong } = usePlayerStore();
  const [filterQuery, setFilterQuery] = useState('');

  const songs: Song[] = history.map((h) => h.song).filter(Boolean);

  const filteredHistory = history.filter((h) => {
    if (!filterQuery.trim()) return true;
    const q = filterQuery.toLowerCase();
    return (
      h.song.name.toLowerCase().includes(q) ||
      h.song.primaryArtists.toLowerCase().includes(q)
    );
  });

  const handlePlaySong = (song: Song, index: number) => {
    playSong(song, songs, index);
  };

  const handlePlayAll = () => {
    if (songs.length > 0) {
      playSong(songs[0], songs, 0);
    }
  };

  const formatRelativeTime = (timestamp: number) => {
    const diff = Math.floor((Date.now() - timestamp) / 1000);
    if (diff < 60) return 'Just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
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
              <History className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white font-['Outfit']">
                Listening History
              </h2>
              <p className="text-xs text-[#6B6A7A]">
                {history.length} recently played tracks
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

        {/* Action Controls & Search */}
        {history.length > 0 && (
          <div className="flex items-center justify-between gap-3 px-6 py-3 bg-white/[0.02] border-b border-white/5">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[#6B6A7A]" />
              <input
                type="text"
                placeholder="Filter history..."
                value={filterQuery}
                onChange={(e) => setFilterQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 rounded-xl bg-white/[0.04] border border-white/10 text-xs text-white placeholder:text-[#6B6A7A] outline-none focus:border-[var(--vynce-primary)]/50"
              />
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={handlePlayAll}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold shadow-md hover:scale-102 active:scale-95 transition-transform"
              >
                <Play className="w-3.5 h-3.5 fill-current" /> Play All
              </button>

              <button
                onClick={() => {
                  if (confirm('Clear entire listening history?')) {
                    clearHistory();
                  }
                }}
                className="p-2 rounded-xl text-[#6B6A7A] hover:text-red-400 hover:bg-white/[0.05] transition-colors"
                title="Clear History"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}

        {/* History Song List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-1.5 max-h-[55vh]">
          {filteredHistory.length > 0 ? (
            filteredHistory.map((entry, idx) => (
              <div
                key={`${entry.song.id}-${entry.playedAt}-${idx}`}
                onClick={() => handlePlaySong(entry.song, idx)}
                className="group flex items-center justify-between p-3 rounded-2xl bg-white/[0.02] hover:bg-white/[0.06] border border-transparent hover:border-white/10 cursor-pointer transition-all"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="relative w-11 h-11 rounded-xl overflow-hidden shrink-0 bg-white/5 border border-white/10">
                    <img
                      src={entry.song.image || '/favicon.svg'}
                      alt={entry.song.name}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                      onError={(e) => {
                        (e.target as HTMLImageElement).src = '/favicon.svg';
                      }}
                    />
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                      <Play className="w-4 h-4 text-white fill-current" />
                    </div>
                  </div>

                  <div className="flex flex-col min-w-0">
                    <span className="text-sm font-semibold text-white truncate group-hover:text-[var(--vynce-primary)] transition-colors">
                      {entry.song.name}
                    </span>
                    <span className="text-xs text-[#8E8D9F] truncate">
                      {entry.song.primaryArtists}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-3 shrink-0 ml-3">
                  <span className="text-[11px] text-[#6B6A7A] font-medium">
                    {formatRelativeTime(entry.playedAt)}
                  </span>
                  {entry.song.duration ? (
                    <span className="text-xs text-[#8E8D9F] font-mono hidden sm:inline">
                      {formatTime(entry.song.duration)}
                    </span>
                  ) : null}
                </div>
              </div>
            ))
          ) : (
            <div className="flex flex-col items-center justify-center py-16 text-[#6B6A7A] gap-3 text-center">
              <div className="p-4 rounded-full bg-white/[0.03] border border-white/10">
                <Music className="w-8 h-8 text-[#6B6A7A]" />
              </div>
              <p className="text-sm font-semibold text-white">No listening history yet</p>
              <p className="text-xs text-[#8E8D9F] max-w-xs">
                Songs you play will appear here automatically so you can jump back in anytime.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
};
