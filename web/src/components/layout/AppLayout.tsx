import React, { useEffect } from 'react';
import { Sidebar, NavTab } from './Sidebar';
import { Header } from './Header';
import { DesktopTitleBar } from '../desktop/DesktopTitleBar';
import { DesktopUpdateBanner } from '../desktop/DesktopUpdateBanner';
import { BottomPlayer } from '../player/BottomPlayer';
import { FullscreenPlayer } from '../player/FullscreenPlayer';
import { QueueDrawer } from './QueueDrawer';
import { SleepTimerModal } from '../player/SleepTimerModal';
import { ShortcutsModal } from '../modals/ShortcutsModal';
import { usePlayerStore } from '../../stores/playerStore';
import { useLibraryStore } from '../../stores/libraryStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { Home, Compass, Heart, Library, Settings } from 'lucide-react';

interface AppLayoutProps {
  children: React.ReactNode;
  activeTab: NavTab;
  searchQuery?: string;
  onTabChange: (tab: NavTab) => void;
  onSearchSubmit: (query: string) => void;
  onPlaylistClick?: (id: string) => void;
  onSelectArtist?: (id: string) => void;
  onSelectAlbum?: (id: string) => void;
}

export const AppLayout: React.FC<AppLayoutProps> = ({
  children,
  activeTab,
  searchQuery = '',
  onTabChange,
  onSearchSubmit,
  onPlaylistClick,
  onSelectArtist,
  onSelectAlbum,
}) => {
  const {
    togglePlay,
    seek,
    currentTime,
    duration,
    volume,
    setVolume,
    toggleMute,
    nextTrack,
    prevTrack,
    toggleFullscreen,
    toggleLyrics,
    toggleQueue,
    toggleSleepTimer,
  } = usePlayerStore();
  const { loadLibrary } = useLibraryStore();
  const { ambientGlow } = useSettingsStore();
  const [isShortcutsOpen, setIsShortcutsOpen] = React.useState(false);

  useEffect(() => {
    loadLibrary();
  }, [loadLibrary]);

  // Global Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable) return;

      if (e.key === '?' || (e.shiftKey && e.code === 'Slash')) {
        e.preventDefault();
        setIsShortcutsOpen((prev) => !prev);
        return;
      }

      switch (e.code) {
        case 'Space':
          e.preventDefault();
          togglePlay();
          break;
        case 'ArrowRight':
          e.preventDefault();
          if (e.shiftKey) nextTrack();
          else seek(Math.min(duration, currentTime + 5));
          break;
        case 'ArrowLeft':
          e.preventDefault();
          if (e.shiftKey) prevTrack();
          else seek(Math.max(0, currentTime - 5));
          break;
        case 'ArrowUp':
          e.preventDefault();
          setVolume(Math.min(1, volume + 0.05));
          break;
        case 'ArrowDown':
          e.preventDefault();
          setVolume(Math.max(0, volume - 0.05));
          break;
        case 'KeyM':
          e.preventDefault();
          toggleMute();
          break;
        case 'KeyL':
          e.preventDefault();
          toggleLyrics();
          break;
        case 'KeyF':
          e.preventDefault();
          toggleFullscreen();
          break;
        case 'KeyQ':
          e.preventDefault();
          toggleQueue();
          break;
        case 'KeyS':
          e.preventDefault();
          toggleSleepTimer();
          break;
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    togglePlay,
    seek,
    currentTime,
    duration,
    volume,
    setVolume,
    toggleMute,
    nextTrack,
    prevTrack,
    toggleFullscreen,
    toggleLyrics,
    toggleQueue,
    toggleSleepTimer,
  ]);

  const mobileNavItems = [
    { id: 'home' as NavTab, label: 'Home', icon: Home },
    { id: 'songs' as NavTab, label: 'Explore', icon: Compass },
    { id: 'liked' as NavTab, label: 'Liked', icon: Heart },
    { id: 'library' as NavTab, label: 'Library', icon: Library },
    { id: 'settings' as NavTab, label: 'Settings', icon: Settings },
  ];

  return (
    <div className="relative flex flex-col h-screen w-screen overflow-hidden bg-[#000000] text-[#E8E6F0]">
      {/* Desktop Frameless Titlebar (shown only in Electron) */}
      <DesktopTitleBar />

      <div className="relative flex flex-1 h-full w-full overflow-hidden">
        {/* Desktop Sidebar (Material 3 Navigation Drawer) */}
        <Sidebar activeTab={activeTab} onTabChange={onTabChange} onPlaylistClick={onPlaylistClick} />

      {/* Main Viewport Content Area */}
      <div className="relative z-10 flex-1 flex flex-col h-full min-w-0 overflow-hidden">
        {/* Soft dynamic ambient glow banner behind header */}
        {ambientGlow && (
          <div
            className="absolute top-0 left-0 right-0 h-96 pointer-events-none opacity-40 transition-all duration-700 blur-3xl -z-10"
            style={{ background: 'var(--vynce-gradient)' }}
          />
        )}

        <Header
          currentSearchQuery={searchQuery}
          onSearchSubmit={onSearchSubmit}
          onOpenSettings={() => onTabChange('settings')}
          onTabChange={onTabChange}
          onSelectArtist={onSelectArtist}
          onSelectAlbum={onSelectAlbum}
          onSelectPlaylist={onPlaylistClick}
        />

        {/* Scrollable View Container - Full Width utilization */}
        <main className="flex-1 overflow-y-auto px-4 py-4 md:px-8 md:py-6 no-scrollbar pb-32">
          <div className="w-full max-w-[1750px] mx-auto">{children}</div>
        </main>
      </div>

      {/* Mobile Bottom Navigation */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-30 bg-[#0A0A0A] border-t border-white/[0.06] flex items-center justify-around py-2 px-2">
        {mobileNavItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onTabChange(item.id)}
              className="flex flex-col items-center gap-1 py-1 px-3 transition-all"
            >
              <div
                style={
                  isActive
                    ? {
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }
                    : {}
                }
                className={`flex items-center justify-center px-4 py-1 rounded-full transition-all ${
                  isActive ? '' : 'text-[#6B6A7A]'
                }`}
              >
                <Icon className="w-5 h-5" />
              </div>
              <span
                style={isActive ? { color: 'var(--vynce-primary)' } : {}}
                className={`text-[11px] font-semibold ${isActive ? '' : 'text-[#6B6A7A]'}`}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>

      {/* Player Modals & Drawers */}
      <BottomPlayer onTabChange={onTabChange} />
      <FullscreenPlayer onTabChange={onTabChange} />
      <QueueDrawer />
      <SleepTimerModal />
      <ShortcutsModal isOpen={isShortcutsOpen} onClose={() => setIsShortcutsOpen(false)} />
      <DesktopUpdateBanner />
      </div>
    </div>
  );
};
