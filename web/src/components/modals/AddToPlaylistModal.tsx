import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  ListPlus,
  Plus,
  Check,
  X,
  Music,
  FolderPlus,
  Sparkles,
} from 'lucide-react';
import { useLibraryStore } from '../../stores/libraryStore';
import { Song } from '../../types/music';

interface AddToPlaylistModalProps {
  isOpen: boolean;
  onClose: () => void;
  song: Song | null;
}

export const AddToPlaylistModal: React.FC<AddToPlaylistModalProps> = ({
  isOpen,
  onClose,
  song,
}) => {
  const {
    playlists,
    createPlaylist,
    addSongToPlaylist,
    removeSongFromPlaylist,
    getPlaylistIdsForSong,
  } = useLibraryStore();

  const [activePlaylistIds, setActivePlaylistIds] = useState<Set<string>>(new Set());
  const [newPlaylistName, setNewPlaylistName] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Sync playlists containing this song
  useEffect(() => {
    if (!song || !isOpen) return;

    let isMounted = true;
    getPlaylistIdsForSong(song.id).then((ids) => {
      if (isMounted) {
        setActivePlaylistIds(new Set(ids));
      }
    });

    return () => {
      isMounted = false;
    };
  }, [song, isOpen, getPlaylistIdsForSong]);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((prev) => (prev === msg ? null : prev));
    }, 2400);
  };

  const handleTogglePlaylist = async (playlistId: string, playlistName: string) => {
    if (!song) return;

    const isAlreadyIn = activePlaylistIds.has(playlistId);
    if (isAlreadyIn) {
      await removeSongFromPlaylist(playlistId, song.id);
      setActivePlaylistIds((prev) => {
        const next = new Set(prev);
        next.delete(playlistId);
        return next;
      });
      showToast(`Removed from "${playlistName}"`);
    } else {
      await addSongToPlaylist(playlistId, song);
      setActivePlaylistIds((prev) => new Set(prev).add(playlistId));
      showToast(`Added to "${playlistName}"`);
    }
  };

  const handleCreateAndAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPlaylistName.trim() || !song) return;

    try {
      const newId = await createPlaylist(newPlaylistName.trim(), '', song.image);
      await addSongToPlaylist(newId, song);
      setActivePlaylistIds((prev) => new Set(prev).add(newId));
      showToast(`Created & added to "${newPlaylistName.trim()}"`);
      setNewPlaylistName('');
      setIsCreating(false);
    } catch {
      alert('Failed to create playlist.');
    }
  };

  if (!isOpen || !song) return null;

  return createPortal(
    <div
      onClick={onClose}
      className="fixed inset-0 z-[120] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
    >
      <div
        className="relative w-full max-w-md max-h-[85vh] bg-[#121118] border border-white/10 rounded-[32px] shadow-2xl flex flex-col overflow-hidden animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-inner"
            >
              <ListPlus className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Add to Playlist
              </h2>
              <p className="text-xs text-[#6B6A7A]">
                Select playlists to save this track
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Selected Song Preview Banner */}
        <div className="flex items-center gap-3 px-5 py-3 bg-white/[0.02] border-b border-white/5">
          <div className="w-11 h-11 rounded-xl overflow-hidden bg-white/5 border border-white/10 shrink-0">
            <img
              src={song.image || '/favicon.svg'}
              alt={song.name}
              className="w-full h-full object-cover"
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/favicon.svg';
              }}
            />
          </div>
          <div className="flex flex-col min-w-0 flex-1">
            <span className="text-xs font-bold text-white truncate">
              {song.name}
            </span>
            <span className="text-[11px] text-[#8E8D9F] truncate">
              {song.primaryArtists}
            </span>
          </div>
        </div>

        {/* Quick Toast Notification */}
        {toastMessage && (
          <div className="mx-5 mt-3 flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] text-xs font-bold shadow-md animate-in fade-in">
            <Check className="w-4 h-4 shrink-0" />
            <span className="truncate">{toastMessage}</span>
          </div>
        )}

        {/* Playlists List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-1.5 max-h-[45vh]">
          {/* Create New Playlist Button / Inline Form */}
          {isCreating ? (
            <form
              onSubmit={handleCreateAndAdd}
              className="p-3 rounded-2xl bg-white/[0.04] border border-[var(--vynce-primary)]/40 flex flex-col gap-2.5 mb-2 animate-in fade-in"
            >
              <label className="text-xs font-bold text-white">
                New Playlist Name
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder="e.g. My Favorites 2026"
                  value={newPlaylistName}
                  onChange={(e) => setNewPlaylistName(e.target.value)}
                  className="flex-1 px-3 py-2 rounded-xl bg-white/[0.06] border border-white/10 text-xs text-white placeholder:text-[#6B6A7A] outline-none focus:border-[var(--vynce-primary)]"
                  autoFocus
                />
                <button
                  type="submit"
                  disabled={!newPlaylistName.trim()}
                  style={{
                    backgroundColor: 'var(--vynce-primary-container)',
                    color: 'var(--vynce-on-primary-container)',
                  }}
                  className="px-3 py-2 rounded-xl text-xs font-bold shadow-sm disabled:opacity-40 cursor-pointer"
                >
                  Create & Add
                </button>
                <button
                  type="button"
                  onClick={() => setIsCreating(false)}
                  className="p-2 rounded-xl text-[#6B6A7A] hover:text-white transition-colors cursor-pointer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </form>
          ) : (
            <button
              onClick={() => setIsCreating(true)}
              className="w-full flex items-center gap-3 p-3 rounded-2xl border border-dashed border-white/15 hover:border-[var(--vynce-primary)]/50 hover:bg-white/[0.03] text-left transition-all group mb-2 cursor-pointer"
            >
              <div className="w-10 h-10 rounded-xl bg-white/[0.04] group-hover:bg-[var(--vynce-primary-container)] group-hover:text-[var(--vynce-on-primary-container)] text-[var(--vynce-primary)] flex items-center justify-center transition-colors">
                <Plus className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors">
                  Create New Playlist
                </span>
                <span className="text-[10px] text-[#6B6A7A]">
                  Add "{song.name.slice(0, 20)}..." to a new list
                </span>
              </div>
            </button>
          )}

          {/* User's Existing Playlists */}
          {playlists.length > 0 ? (
            playlists.map((pl) => {
              const isAdded = activePlaylistIds.has(pl.id);
              return (
                <div
                  key={pl.id}
                  onClick={() => handleTogglePlaylist(pl.id, pl.name)}
                  className={`group flex items-center justify-between p-2.5 rounded-2xl border transition-all cursor-pointer ${
                    isAdded
                      ? 'bg-white/[0.06] border-[var(--vynce-primary)]/40 shadow-sm'
                      : 'bg-white/[0.02] border-transparent hover:bg-white/[0.05] hover:border-white/10'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-10 h-10 rounded-xl overflow-hidden bg-white/5 border border-white/10 shrink-0 flex items-center justify-center">
                      {pl.coverArt ? (
                        <img
                          src={pl.coverArt}
                          alt={pl.name}
                          className="w-full h-full object-cover"
                          onError={(e) => {
                            (e.target as HTMLImageElement).src = '/favicon.svg';
                          }}
                        />
                      ) : (
                        <Music className="w-4 h-4 text-[#6B6A7A]" />
                      )}
                    </div>

                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-bold text-white truncate group-hover:text-[var(--vynce-primary)] transition-colors">
                        {pl.name}
                      </span>
                      <span className="text-[10px] text-[#6B6A7A]">
                        {isAdded ? 'Added to playlist' : 'Tap to add'}
                      </span>
                    </div>
                  </div>

                  <div
                    style={
                      isAdded
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                          }
                        : {}
                    }
                    className={`w-7 h-7 rounded-full flex items-center justify-center transition-all ${
                      isAdded
                        ? 'shadow-md scale-105'
                        : 'border border-white/20 text-[#6B6A7A] group-hover:text-white group-hover:border-white/40'
                    }`}
                  >
                    {isAdded ? (
                      <Check className="w-3.5 h-3.5 stroke-[3]" />
                    ) : (
                      <Plus className="w-3.5 h-3.5" />
                    )}
                  </div>
                </div>
              );
            })
          ) : (
            !isCreating && (
              <div className="flex flex-col items-center justify-center py-8 text-center text-[#6B6A7A] gap-2">
                <FolderPlus className="w-7 h-7 text-[#6B6A7A]" />
                <p className="text-xs text-[#8E8D9F]">No playlists created yet</p>
              </div>
            )
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/10 flex justify-end">
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-2xl bg-white/[0.06] hover:bg-white/[0.1] text-xs font-bold text-white transition-colors cursor-pointer"
          >
            Done
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
};
