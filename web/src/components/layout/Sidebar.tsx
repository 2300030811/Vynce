import React from 'react';
import {
  Home,
  Music,
  Heart,
  Library,
  Settings,
  ListPlus,
  Sliders,
} from 'lucide-react';
import { useLibraryStore } from '../../stores/libraryStore';
import { usePlayerStore } from '../../stores/playerStore';

export type NavTab = 'home' | 'songs' | 'liked' | 'library' | 'equalizer' | 'settings';

interface SidebarProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  onPlaylistClick?: (playlistId: string) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeTab,
  onTabChange,
  onPlaylistClick,
}) => {
  const { playlists, likedSongs, createPlaylist } = useLibraryStore();

  const handleCreatePlaylist = async () => {
    const name = prompt('Enter new playlist name:');
    if (name?.trim()) await createPlaylist(name.trim());
  };

  const navItems: { id: NavTab; label: string; icon: typeof Home }[] = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'songs', label: 'Explore & Search', icon: Music },
  ];

  return (
    <aside className="w-64 h-full bg-[#0A0A0A] border-r border-white/[0.06] flex flex-col justify-between py-5 px-4 shrink-0 hidden md:flex select-none">
      <div className="flex flex-col gap-5">
        {/* Vynce Branding */}
        <div
          onClick={() => onTabChange('home')}
          className="flex items-center gap-3 px-2 py-1 cursor-pointer group"
        >
          <img
            src="/favicon.svg"
            alt="Vynce"
            className="w-9 h-9 rounded-2xl shadow-md transition-transform group-hover:scale-105"
          />
          <div className="flex flex-col">
            <div className="flex items-center gap-1.5">
              <span className="text-xl font-extrabold tracking-tight text-white font-['Outfit']">
                Vynce
              </span>
              <span
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="text-[10px] px-1.5 py-0.5 rounded-full font-bold"
              >
                Web
              </span>
            </div>
            <span className="text-[10px] text-[#6B6A7A] tracking-wide font-medium">
              Material You Edition
            </span>
          </div>
        </div>

        {/* Discover */}
        <div className="flex flex-col gap-0.5">
          <span className="text-[10px] font-bold uppercase tracking-wider text-[#6B6A7A] px-3 mb-1">
            Discover
          </span>
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onTabChange(item.id)}
                style={
                  isActive
                    ? {
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }
                    : {}
                }
                className={`flex items-center gap-3 px-3 py-2.5 rounded-2xl text-sm font-semibold transition-all ${
                  isActive
                    ? 'shadow-sm font-bold'
                    : 'text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.04]'
                }`}
              >
                <Icon
                  style={isActive ? { color: 'var(--vynce-on-primary-container)' } : {}}
                  className={`w-[18px] h-[18px] ${isActive ? '' : 'text-[#6B6A7A]'}`}
                />
                {item.label}
              </button>
            );
          })}
        </div>

        {/* My Collection */}
        <div className="flex flex-col gap-0.5">
          <span className="text-[10px] font-bold uppercase tracking-wider text-[#6B6A7A] px-3 mb-1">
            My Collection
          </span>

          {/* Liked Songs Tab */}
          <button
            onClick={() => onTabChange('liked')}
            style={
              activeTab === 'liked'
                ? {
                    backgroundColor: 'var(--vynce-primary-container)',
                    color: 'var(--vynce-on-primary-container)',
                  }
                : {}
            }
            className={`flex items-center justify-between px-3 py-2.5 rounded-2xl text-sm font-semibold transition-all ${
              activeTab === 'liked'
                ? 'shadow-sm font-bold'
                : 'text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.04]'
            }`}
          >
            <div className="flex items-center gap-3">
              <Heart
                style={activeTab === 'liked' ? { color: 'var(--vynce-primary)' } : {}}
                className={`w-[18px] h-[18px] ${activeTab === 'liked' ? 'fill-current' : 'text-[#6B6A7A]'}`}
              />
              <span>Liked Songs</span>
            </div>
            {likedSongs.length > 0 && (
              <span
                style={
                  activeTab === 'liked'
                    ? {
                        backgroundColor: 'var(--vynce-on-primary-container)',
                        color: 'var(--vynce-primary-container)',
                      }
                    : {}
                }
                className="text-[11px] px-2 py-0.5 rounded-full bg-white/8 text-[#E8E6F0]/80 font-mono font-bold"
              >
                {likedSongs.length}
              </span>
            )}
          </button>

          {/* Library Hub Tab */}
          <button
            onClick={() => onTabChange('library')}
            style={
              activeTab === 'library'
                ? {
                    backgroundColor: 'var(--vynce-primary-container)',
                    color: 'var(--vynce-on-primary-container)',
                  }
                : {}
            }
            className={`flex items-center justify-between px-3 py-2.5 rounded-2xl text-sm font-semibold transition-all ${
              activeTab === 'library'
                ? 'shadow-sm font-bold'
                : 'text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.04]'
            }`}
          >
            <div className="flex items-center gap-3">
              <Library
                style={activeTab === 'library' ? { color: 'var(--vynce-on-primary-container)' } : {}}
                className={`w-[18px] h-[18px] ${activeTab === 'library' ? '' : 'text-[#6B6A7A]'}`}
              />
              <span>Library Hub</span>
            </div>
            {playlists.length > 0 && (
              <span
                style={
                  activeTab === 'library'
                    ? {
                        backgroundColor: 'var(--vynce-on-primary-container)',
                        color: 'var(--vynce-primary-container)',
                      }
                    : {}
                }
                className="text-[11px] px-2 py-0.5 rounded-full bg-white/8 text-[#E8E6F0]/80 font-mono font-bold"
              >
                {playlists.length}
              </span>
            )}
          </button>
        </div>

        {/* Playlists Section */}
        <div className="flex flex-col gap-1 pt-2 border-t border-white/[0.06]">
          <div className="flex items-center justify-between px-3">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[#6B6A7A]">
              Playlists
            </span>
            <button
              onClick={handleCreatePlaylist}
              className="p-1 rounded-lg text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors"
              title="Create Playlist"
            >
              <ListPlus className="w-4 h-4" />
            </button>
          </div>
          <div className="flex flex-col gap-0.5 max-h-44 overflow-y-auto pr-1 no-scrollbar">
            {playlists.map((pl) => (
              <button
                key={pl.id}
                onClick={() => onPlaylistClick?.(pl.id)}
                className="flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-medium text-[#E8E6F0]/80 hover:text-white hover:bg-white/[0.06] group truncate text-left transition-all"
              >
                <div className="w-5 h-5 rounded-lg overflow-hidden shrink-0 bg-white/5 border border-white/10 flex items-center justify-center">
                  {pl.coverArt ? (
                    <img src={pl.coverArt} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <Music className="w-3 h-3 text-[var(--vynce-primary)]" />
                  )}
                </div>
                <span className="truncate flex-1 font-semibold">{pl.name}</span>
                {(pl as any).songCount !== undefined ? (
                  <span className="text-[10px] text-[#6B6A7A] group-hover:text-[#E8E6F0]/60">
                    {(pl as any).songCount}
                  </span>
                ) : (pl as any).songs?.length ? (
                  <span className="text-[10px] text-[#6B6A7A] group-hover:text-[#E8E6F0]/60">
                    {(pl as any).songs.length}
                  </span>
                ) : null}
              </button>
            ))}
            {playlists.length === 0 && (
              <p className="text-[11px] text-[#6B6A7A]/50 px-3 py-1.5 italic">
                No custom playlists yet
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="flex flex-col gap-1 pt-3 border-t border-white/[0.06]">
        <button
          onClick={() => onTabChange('equalizer')}
          style={
            activeTab === 'equalizer'
              ? {
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }
              : {}
          }
          className={`flex items-center gap-3 px-3 py-2.5 rounded-2xl text-xs font-semibold transition-all ${
            activeTab === 'equalizer'
              ? 'shadow-sm font-bold'
              : 'text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.04]'
          }`}
        >
          <Sliders
            style={activeTab === 'equalizer' ? { color: 'var(--vynce-on-primary-container)' } : { color: 'var(--vynce-primary)' }}
            className="w-[18px] h-[18px]"
          />
          <span>Equalizer & DSP</span>
        </button>

        <button
          onClick={() => onTabChange('settings')}
          style={
            activeTab === 'settings'
              ? {
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }
              : {}
          }
          className={`flex items-center gap-3 px-3 py-2.5 rounded-2xl text-xs font-semibold transition-all ${
            activeTab === 'settings'
              ? 'shadow-sm font-bold'
              : 'text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.04]'
          }`}
        >
          <Settings
            style={activeTab === 'settings' ? { color: 'var(--vynce-on-primary-container)' } : {}}
            className={`w-[18px] h-[18px] ${activeTab === 'settings' ? '' : 'text-[#6B6A7A]'}`}
          />
          <span>Settings</span>
        </button>
      </div>
    </aside>
  );
};
