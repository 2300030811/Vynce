import React, { useState, useRef } from 'react';
import { useLibraryStore } from '../stores/libraryStore';
import { usePlayerStore } from '../stores/playerStore';
import { SongCard } from '../components/cards/SongCard';
import { PlaylistCard } from '../components/cards/PlaylistCard';
import { AlbumCard } from '../components/cards/AlbumCard';
import { SpotifyImportModal } from '../components/modals/SpotifyImportModal';
import { Heart, Download, Upload, ArrowDown, ListPlus, Trash2, Play, Check, DownloadCloud, Disc3 } from 'lucide-react';

interface LibraryPageProps {
  onSelectPlaylist: (id: string) => void;
  onSelectAlbum?: (id: string) => void;
  onNavigateToLiked?: () => void;
}

type LibraryFilter = 'all' | 'playlists' | 'albums' | 'offline';

export const LibraryPage: React.FC<LibraryPageProps> = ({
  onSelectPlaylist,
  onSelectAlbum,
  onNavigateToLiked,
}) => {
  const {
    likedSongs,
    savedAlbums,
    deleteSavedAlbum,
    playlists,
    createPlaylist,
    deletePlaylist,
    exportLibrary,
    importLibrary,
  } = useLibraryStore();
  const { playSong } = usePlayerStore();
  const [filter, setFilter] = useState<LibraryFilter>('all');
  const [showLikedSongsList, setShowLikedSongsList] = useState(false);
  const [isSpotifyModalOpen, setIsSpotifyModalOpen] = useState(false);
  const [backupMsg, setBackupMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleCreatePlaylist = async () => {
    const name = prompt('Enter playlist name:');
    if (name && name.trim()) {
      await createPlaylist(name.trim());
    }
  };

  const handleExport = async () => {
    try {
      const jsonStr = await exportLibrary();
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `vynce_backup_${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      setBackupMsg('Library exported successfully!');
      setTimeout(() => setBackupMsg(null), 3000);
    } catch {
      alert('Failed to export library.');
    }
  };

  const handleImportFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const text = await file.text();
      const res = await importLibrary(text);
      setBackupMsg(`Imported ${res.playlistsCount} playlists, ${res.albumsCount || 0} albums & ${res.likesCount} liked songs!`);
      setTimeout(() => setBackupMsg(null), 4000);
    } catch (err: any) {
      alert('Failed to import backup: ' + (err.message || 'Invalid format'));
    }
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const filterLabels: { id: LibraryFilter; label: string }[] = [
    { id: 'all', label: 'All Items' },
    { id: 'playlists', label: 'Playlists' },
    { id: 'albums', label: 'Saved Albums' },
    { id: 'offline', label: 'Offline Cache' },
  ];

  return (
    <div className="flex flex-col gap-8 w-full pb-36 animate-in fade-in duration-300">
      {/* Top Header & Filter Chips */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
          {filterLabels.map((f) => (
            <button
              key={f.id}
              onClick={() => setFilter(f.id)}
              style={
                filter === f.id
                  ? {
                      backgroundColor: 'var(--vynce-primary-container)',
                      color: 'var(--vynce-on-primary-container)',
                    }
                  : {}
              }
              className={`px-4 py-2 rounded-full text-xs font-bold tracking-wide capitalize transition-all border ${
                filter === f.id
                  ? 'border-transparent shadow-sm'
                  : 'bg-transparent border-white/10 text-[#E8E6F0]/70 hover:text-white'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          {/* Export JSON */}
          <button
            onClick={handleExport}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0]/80 hover:text-white text-xs font-semibold border border-white/5 transition-colors"
            title="Export Library Backup (JSON)"
          >
            <Download className="w-3.5 h-3.5" /> Backup
          </button>

          {/* Import JSON */}
          <button
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0]/80 hover:text-white text-xs font-semibold border border-white/5 transition-colors"
            title="Import Library Backup (JSON)"
          >
            <Upload className="w-3.5 h-3.5" /> Restore
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json,application/json"
            onChange={handleImportFile}
            className="hidden"
          />

          {/* Import Playlist */}
          <button
            onClick={() => setIsSpotifyModalOpen(true)}
            className="flex items-center gap-1.5 px-4 py-2 rounded-full bg-white/[0.05] hover:bg-white/[0.1] text-white text-xs font-bold border border-white/10 shadow-sm transition-all active:scale-95 hover:scale-105"
            title="Import playlist directly from public link or ID"
          >
            <DownloadCloud className="w-3.5 h-3.5" /> Import Playlist
          </button>

          <button
            onClick={handleCreatePlaylist}
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-full font-bold text-xs shadow-md transition-transform active:scale-95 hover:scale-105"
          >
            <ListPlus className="w-4 h-4" /> New Playlist
          </button>
        </div>
      </div>

      {backupMsg && (
        <div className="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-[var(--vynce-primary-container)]/80 text-[var(--vynce-on-primary-container)] text-xs font-bold shadow-lg animate-in fade-in">
          <Check className="w-4 h-4" />
          <span>{backupMsg}</span>
        </div>
      )}

      {/* Primary Auto Playlists Grid */}
      {!showLikedSongsList ? (
        <div className="flex flex-col gap-8">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {/* Quick Create Playlist Card */}
            {(filter === 'all' || filter === 'playlists') && (
              <div
                onClick={handleCreatePlaylist}
                className="group relative flex flex-col justify-between p-6 rounded-[28px] border-2 border-dashed border-white/15 hover:border-[var(--vynce-primary)]/60 bg-white/[0.02] hover:bg-white/[0.05] cursor-pointer transition-all duration-300 min-h-[220px]"
              >
                <div className="flex flex-col gap-2">
                  <div
                    style={{
                      backgroundColor: 'var(--vynce-primary-container)',
                      color: 'var(--vynce-on-primary-container)',
                    }}
                    className="w-12 h-12 rounded-2xl flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform"
                  >
                    <ListPlus className="w-6 h-6" />
                  </div>
                  <span className="text-base font-bold text-white font-['Outfit'] mt-2">
                    Create Playlist
                  </span>
                  <p className="text-xs text-[#8E8D9F] leading-relaxed">
                    Build a custom mix and add your favorite tracks anytime.
                  </p>
                </div>
                <div className="flex items-center gap-1.5 text-xs font-bold text-[var(--vynce-primary)] mt-4">
                  <span>Start a mix</span>
                  <span>→</span>
                </div>
              </div>
            )}

            {/* Liked Songs Big Card */}
            {(filter === 'all') && (
              <div
                onClick={() => {
                  if (onNavigateToLiked) {
                    onNavigateToLiked();
                  } else {
                    setShowLikedSongsList(true);
                  }
                }}
                className="group relative flex flex-col cursor-pointer transition-all"
              >
                <div
                  style={{
                    background:
                      'radial-gradient(circle at 50% 40%, var(--vynce-primary-container) 0%, rgba(0,0,0,0.85) 100%)',
                  }}
                  className="relative aspect-square w-full rounded-[28px] flex items-center justify-center transition-all shadow-xl border border-white/5 group-hover:border-[var(--vynce-primary)]/40"
                >
                  <Heart
                    style={{ color: 'var(--vynce-primary)' }}
                    className="w-24 h-24 fill-current drop-shadow-lg transition-transform group-hover:scale-110"
                  />
                  {likedSongs.length > 0 && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        playSong(likedSongs[0], likedSongs, 0);
                      }}
                      style={{
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }}
                      className="absolute bottom-4 right-4 p-4 rounded-full shadow-2xl opacity-0 group-hover:opacity-100 transition-all duration-200 active:scale-95 hover:scale-105"
                      title="Play Liked Songs"
                    >
                      <Play className="w-5 h-5 fill-current translate-x-0.5" />
                    </button>
                  )}
                </div>
                <div className="flex flex-col px-2 mt-3">
                  <span className="text-base font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors font-['Outfit']">
                    Liked songs
                  </span>
                  <span className="text-xs text-[#6B6A7A]">
                    Auto playlist • {likedSongs.length} songs
                  </span>
                </div>
              </div>
            )}

            {/* Downloaded Songs Big Card */}
            {(filter === 'all' || filter === 'offline') && (
              <div
                onClick={() => {
                  if (likedSongs.length > 0) {
                    playSong(likedSongs[0], likedSongs, 0);
                  }
                }}
                className="group relative flex flex-col cursor-pointer transition-all"
              >
                <div className="relative aspect-square w-full rounded-[28px] bg-[var(--vynce-surface-container)] flex items-center justify-center transition-all shadow-xl border border-white/5 group-hover:border-white/20">
                  <Download className="w-24 h-24 text-[#E8E6F0] transition-transform group-hover:scale-110" />
                </div>
                <div className="flex flex-col px-2 mt-3">
                  <span className="text-base font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors font-['Outfit']">
                    Downloaded songs
                  </span>
                  <span className="text-xs text-[#6B6A7A]">
                    Auto playlist • Offline Cache
                  </span>
                </div>
              </div>
            )}

            {/* Saved Albums in Grid */}
            {(filter === 'all' || filter === 'albums') &&
              savedAlbums.map((alb) => (
                <div key={alb.id} className="relative group">
                  <AlbumCard
                    album={{
                      id: alb.id,
                      name: alb.name,
                      artists: alb.artists,
                      image: alb.image,
                      year: alb.year,
                      songCount: alb.songCount,
                    }}
                    onClick={() => onSelectAlbum?.(alb.id)}
                  />
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      if (confirm(`Remove "${alb.name}" from your Library?`)) {
                        deleteSavedAlbum(alb.id);
                      }
                    }}
                    className="absolute top-3 right-3 p-2.5 rounded-full bg-black/80 text-[#6B6A7A] opacity-0 group-hover:opacity-100 hover:text-red-400 transition-opacity z-10"
                    title="Remove from Library"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}

            {/* Custom User Playlists in Grid */}
            {(filter === 'all' || filter === 'playlists') &&
              playlists.map((pl) => (
                <div key={pl.id} className="relative group">
                  <PlaylistCard
                    playlist={{
                      id: pl.id,
                      name: pl.name,
                      image: pl.coverArt,
                      description: pl.description,
                    }}
                    onClick={() => onSelectPlaylist(pl.id)}
                  />
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      if (confirm(`Delete "${pl.name}"?`)) {
                        deletePlaylist(pl.id);
                      }
                    }}
                    className="absolute top-3 right-3 p-2.5 rounded-full bg-black/80 text-[#6B6A7A] opacity-0 group-hover:opacity-100 hover:text-red-400 transition-opacity z-10"
                    title="Delete playlist"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
          </div>
        </div>
      ) : (
        /* Expanded Liked Songs Desktop List */
        <div className="flex flex-col gap-6">
          <div className="flex items-center justify-between">
            <button
              onClick={() => setShowLikedSongsList(false)}
              style={{ color: 'var(--vynce-primary)' }}
              className="text-sm font-bold hover:underline flex items-center gap-1"
            >
              ← Back to Collection
            </button>
            {likedSongs.length > 0 && (
              <button
                onClick={() => playSong(likedSongs[0], likedSongs, 0)}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="px-6 py-2.5 rounded-full font-bold text-xs shadow-md transition-transform active:scale-95 hover:scale-105 flex items-center gap-2"
              >
                <Play className="w-4 h-4 fill-current" /> Play All ({likedSongs.length})
              </button>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
            {likedSongs.map((song, idx) => (
              <SongCard
                key={song.id}
                song={song}
                index={idx}
                queueContext={likedSongs}
                showAlbum={false}
              />
            ))}
          </div>
        </div>
      )}

      {/* Spotify Import Modal */}
      <SpotifyImportModal
        isOpen={isSpotifyModalOpen}
        onClose={() => setIsSpotifyModalOpen(false)}
        onSuccess={(pl) => {
          setBackupMsg(`Spotify playlist "${pl.name}" imported successfully!`);
          setTimeout(() => setBackupMsg(null), 4000);
          onSelectPlaylist(pl.id);
        }}
      />
    </div>
  );
};
