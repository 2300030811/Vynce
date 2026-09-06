import React from 'react';
import { Playlist } from '../../types/music';
import { Play, ListMusic, Bookmark, BookmarkCheck } from 'lucide-react';
import { formatNumber } from '../../utils/formatters';
import { useLibraryStore } from '../../stores/libraryStore';

interface PlaylistCardProps {
  playlist: Playlist;
  onClick?: () => void;
  onPlay?: (e: React.MouseEvent) => void;
  showSaveButton?: boolean;
}

export const PlaylistCard: React.FC<PlaylistCardProps> = ({ playlist, onClick, onPlay, showSaveButton = true }) => {
  const [imgError, setImgError] = React.useState(false);
  const { isPlaylistSaved, toggleSavePlaylist } = useLibraryStore();
  const isSaved = isPlaylistSaved(playlist.id);

  const handleSave = (e: React.MouseEvent) => {
    e.stopPropagation();
    toggleSavePlaylist(playlist, []);
  };

  return (
    <div onClick={onClick} className="group relative flex flex-col cursor-pointer transition-all duration-200">
      <div className="relative aspect-square w-full rounded-2xl overflow-hidden shadow-md bg-[var(--vynce-surface-container)] mb-2">
        {playlist.image && !imgError ? (
          <img
            src={playlist.image}
            alt={playlist.name}
            className="w-full h-full object-cover transition-transform duration-200 group-hover:scale-105"
            loading="lazy"
            onError={() => setImgError(true)}
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-[var(--vynce-surface-container)]">
            <ListMusic className="w-10 h-10 text-[#6B6A7A]/40" />
          </div>
        )}

        {/* Quick Save Bookmark button on top-right */}
        {showSaveButton && !playlist.id.startsWith('playlist_') && (
          <button
            onClick={handleSave}
            className={`absolute top-2.5 right-2.5 p-2 rounded-full backdrop-blur-md transition-all duration-200 ${
              isSaved
                ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] opacity-100'
                : 'bg-black/60 text-white/80 opacity-0 group-hover:opacity-100 hover:bg-black/80 hover:text-white'
            }`}
            title={isSaved ? 'In Library (Click to remove)' : 'Save Playlist to Library'}
          >
            {isSaved ? <BookmarkCheck className="w-3.5 h-3.5" /> : <Bookmark className="w-3.5 h-3.5" />}
          </button>
        )}

        {onPlay && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onPlay(e);
            }}
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="absolute bottom-2.5 right-2.5 p-3 rounded-full shadow-xl opacity-0 group-hover:opacity-100 transition-all duration-200 active:scale-95 hover:scale-105"
          >
            <Play className="w-4 h-4 fill-current translate-x-0.5" />
          </button>
        )}
      </div>
      <h3 className="text-sm font-semibold text-[#E8E6F0] truncate group-hover:text-[var(--vynce-primary)] transition-colors">
        {playlist.name}
      </h3>
      <p className="text-xs text-[#6B6A7A] truncate mt-0.5">
        {playlist.songCount ? `${playlist.songCount} songs` : ''}
        {playlist.followerCount && Number(playlist.followerCount) > 0
          ? ` • ${formatNumber(playlist.followerCount)} fans`
          : ''}
      </p>
    </div>
  );
};
