import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  Settings,
  X,
  Loader2,
  Music,
  Sliders,
  History,
  TrendingUp,
  User,
  Disc,
  ArrowRight,
  Sparkles,
  Mic,
  Users,
} from 'lucide-react';
import { JioSaavnApi } from '../../api/jiosaavn';
import { Song, Album, Artist, TopQueryResult } from '../../types/music';
import { usePlayerStore } from '../../stores/playerStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { useListenTogetherStore } from '../../stores/listenTogetherStore';
import { VoiceSearchModal } from '../modals/VoiceSearchModal';
import { HistoryModal } from '../modals/HistoryModal';
import { StatsModal } from '../modals/StatsModal';
import { ProfileModal } from '../modals/ProfileModal';
import { ListenTogetherModal } from '../modals/ListenTogetherModal';

interface HeaderProps {
  currentSearchQuery?: string;
  onSearchSubmit: (query: string) => void;
  onOpenSettings: () => void;
  onTabChange?: (tab: any) => void;
  onSelectArtist?: (id: string) => void;
  onSelectAlbum?: (id: string) => void;
  onSelectPlaylist?: (id: string) => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentSearchQuery = '',
  onSearchSubmit,
  onOpenSettings,
  onTabChange,
  onSelectArtist,
  onSelectAlbum,
  onSelectPlaylist,
}) => {
  const { playSong } = usePlayerStore();
  const { userName, userAvatar } = useSettingsStore();
  const [searchQuery, setSearchQuery] = useState(currentSearchQuery);
  const [isSearching, setIsSearching] = useState(false);
  const [isVoiceModalOpen, setIsVoiceModalOpen] = useState(false);
  const [isHistoryModalOpen, setIsHistoryModalOpen] = useState(false);
  const [isStatsModalOpen, setIsStatsModalOpen] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [isListenTogetherOpen, setIsListenTogetherOpen] = useState(false);
  const { roomCode } = useListenTogetherStore();
  const [topResult, setTopResult] = useState<TopQueryResult | null>(null);
  const [songSuggestions, setSongSuggestions] = useState<Song[]>([]);
  const [artistSuggestions, setArtistSuggestions] = useState<Artist[]>([]);
  const [albumSuggestions, setAlbumSuggestions] = useState<Album[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const ref = useRef<HTMLDivElement | null>(null);

  // Synchronize internal state when query changes from external sources
  useEffect(() => {
    setSearchQuery(currentSearchQuery);
    if (!currentSearchQuery) {
      setTopResult(null);
      setSongSuggestions([]);
      setArtistSuggestions([]);
      setAlbumSuggestions([]);
      setShowDropdown(false);
    }
  }, [currentSearchQuery]);

  useEffect(() => {
    if (!searchQuery.trim() || searchQuery.length < 2) {
      setTopResult(null);
      setSongSuggestions([]);
      setArtistSuggestions([]);
      setAlbumSuggestions([]);
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    const t = setTimeout(async () => {
      try {
        const res = await JioSaavnApi.searchGlobal(searchQuery.trim());
        setTopResult(res.topQuery);
        setSongSuggestions(res.songs.slice(0, 5));
        setArtistSuggestions(res.artists.slice(0, 4));
        setAlbumSuggestions(res.albums.slice(0, 3));
        setShowDropdown(true);
      } catch {
        setTopResult(null);
        setSongSuggestions([]);
      } finally {
        setIsSearching(false);
      }
    }, 180);

    return () => clearTimeout(t);
  }, [searchQuery]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setShowDropdown(false);
    onSearchSubmit(searchQuery.trim());
  };

  const handleClear = () => {
    setSearchQuery('');
    setTopResult(null);
    setSongSuggestions([]);
    setArtistSuggestions([]);
    setAlbumSuggestions([]);
    setShowDropdown(false);
    onSearchSubmit('');
  };

  const handleTopResultClick = () => {
    if (!topResult) return;
    setShowDropdown(false);

    if (topResult.type === 'artist') {
      onSelectArtist?.(topResult.id);
    } else if (topResult.type === 'album') {
      onSelectAlbum?.(topResult.id);
    } else if (topResult.type === 'playlist') {
      onSelectPlaylist?.(topResult.id);
    } else if (songSuggestions.length > 0) {
      playSong(songSuggestions[0], songSuggestions, 0);
    } else {
      onSearchSubmit(searchQuery.trim());
    }
  };

  const hasResults =
    Boolean(topResult) ||
    songSuggestions.length > 0 ||
    artistSuggestions.length > 0 ||
    albumSuggestions.length > 0;

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between gap-4 px-5 py-3 md:px-8 bg-black/80 backdrop-blur-xl border-b border-white/[0.06]">
      {/* Mobile Logo */}
      <div
        onClick={() => onTabChange?.('home')}
        className="flex items-center gap-2 md:hidden cursor-pointer shrink-0"
      >
        <img src="/favicon.svg" alt="Vynce" className="w-8 h-8 rounded-xl" />
        <span className="text-lg font-bold tracking-tight text-white font-['Outfit']">Vynce</span>
      </div>

      {/* Search Bar Container */}
      <div ref={ref} className="relative flex-1 max-w-xl mx-auto md:mx-0">
        <form
          onSubmit={handleSubmit}
          className="relative flex items-center bg-[var(--vynce-surface-container)] hover:bg-[var(--vynce-surface-container-high)] focus-within:bg-[var(--vynce-surface-container-high)] rounded-full px-4 py-2.5 transition-all border border-white/[0.06] focus-within:border-[var(--vynce-primary)]/40 shadow-inner"
        >
          <Search className="w-4 h-4 text-[#6B6A7A] mr-3 shrink-0" />
          <input
            type="text"
            placeholder="Search artists, songs, albums, playlists..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') {
                handleClear();
                (e.target as HTMLInputElement).blur();
              }
            }}
            onFocus={() => {
              if (hasResults) setShowDropdown(true);
            }}
            className="w-full bg-transparent text-sm text-[#E8E6F0] placeholder:text-[#6B6A7A] outline-none font-medium"
          />
          <button
            type="button"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setIsVoiceModalOpen(true);
            }}
            className="p-1 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-[var(--vynce-primary)] transition-colors shrink-0 mr-1"
            title="Voice Search & Commands"
          >
            <Mic className="w-4 h-4" />
          </button>
          {searchQuery && (
            <button
              type="button"
              onClick={handleClear}
              className="p-1 text-[#6B6A7A] hover:text-white transition-colors"
              title="Clear search"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
          {isSearching && (
            <Loader2
              style={{ color: 'var(--vynce-primary)' }}
              className="w-4 h-4 animate-spin ml-2 shrink-0"
            />
          )}
        </form>

        {/* Global Live Suggestion Dropdown */}
        {showDropdown && hasResults && (
          <div className="absolute top-full left-0 right-0 mt-2.5 bg-[#0F0E18]/95 backdrop-blur-2xl rounded-3xl p-3.5 shadow-2xl border border-white/[0.08] flex flex-col gap-3 z-50 animate-in fade-in zoom-in-95 duration-150 max-h-[80vh] overflow-y-auto no-scrollbar">
            {/* 1. TOP RESULT / HERO CARD */}
            {topResult && (
              <div className="flex flex-col gap-1.5 pb-2 border-b border-white/[0.06]">
                <span className="text-[10px] font-bold uppercase tracking-widest text-[#A5A3B5] px-2 flex items-center gap-1.5">
                  <TrendingUp className="w-3 h-3 text-[var(--vynce-primary)]" /> Top Result
                </span>

                <div
                  onClick={handleTopResultClick}
                  className="flex items-center gap-3.5 p-2.5 rounded-2xl bg-white/[0.03] hover:bg-[var(--vynce-primary-container)]/30 border border-white/[0.04] hover:border-[var(--vynce-primary)]/40 cursor-pointer transition-all group"
                >
                  <div
                    className={`relative overflow-hidden shrink-0 shadow-lg ${
                      topResult.type === 'artist' ? 'w-14 h-14 rounded-full' : 'w-14 h-14 rounded-2xl'
                    }`}
                  >
                    {topResult.image ? (
                      <img
                        src={topResult.image}
                        alt={topResult.name}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                        onError={(e) => {
                          (e.target as HTMLElement).style.display = 'none';
                        }}
                      />
                    ) : (
                      <div className="w-full h-full bg-purple-900/60 flex items-center justify-center text-white font-bold text-lg">
                        {topResult.name.charAt(0)}
                      </div>
                    )}
                  </div>

                  <div className="flex flex-col min-w-0 flex-1">
                    <span className="text-base font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate font-['Outfit']">
                      {topResult.name}
                    </span>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded-full bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)]">
                        {topResult.type}
                      </span>
                      <span className="text-xs text-[#A5A3B5] truncate">
                        {topResult.subtitle || 'Top Match'}
                      </span>
                    </div>
                  </div>

                  <ArrowRight className="w-4 h-4 text-[#6B6A7A] group-hover:text-white group-hover:translate-x-1 transition-all mr-2 shrink-0" />
                </div>
              </div>
            )}

            {/* 2. MATCHING SONGS */}
            {songSuggestions.length > 0 && (
              <div className="flex flex-col gap-1">
                <span className="text-[10px] font-bold uppercase tracking-wider text-[#6B6A7A] px-2 py-1">
                  Songs
                </span>
                {songSuggestions.map((song, idx) => (
                  <div
                    key={`${song.id}-${idx}`}
                    onClick={() => {
                      playSong(song, songSuggestions, idx);
                      setShowDropdown(false);
                    }}
                    className="flex items-center gap-3 p-2 rounded-2xl hover:bg-white/[0.06] cursor-pointer transition-colors group"
                  >
                    <img
                      src={song.image || '/favicon.svg'}
                      alt={song.name}
                      className="w-10 h-10 rounded-xl object-cover shadow-sm shrink-0"
                      onError={(e) => {
                        (e.target as HTMLImageElement).src = '/favicon.svg';
                      }}
                    />
                    <div className="flex flex-col min-w-0 flex-1">
                      <span className="text-sm font-semibold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate">
                        {song.name}
                      </span>
                      <span className="text-xs text-[#6B6A7A] truncate">{song.primaryArtists}</span>
                    </div>
                    <Music className="w-4 h-4 text-[#6B6A7A] group-hover:text-white transition-colors mr-1 shrink-0" />
                  </div>
                ))}
              </div>
            )}

            {/* 3. ARTISTS & ALBUMS CHIPS */}
            {(artistSuggestions.length > 0 || albumSuggestions.length > 0) && (
              <div className="flex flex-col gap-2 pt-1 border-t border-white/[0.04]">
                {artistSuggestions.length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-[#6B6A7A] px-2">
                      Artists
                    </span>
                    <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
                      {artistSuggestions.map((art) => (
                        <div
                          key={art.id}
                          onClick={() => {
                            setShowDropdown(false);
                            onSelectArtist?.(art.id);
                          }}
                          className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] border border-white/5 cursor-pointer shrink-0 transition-colors group"
                        >
                          <div className="w-5 h-5 rounded-full overflow-hidden bg-purple-900/60 shrink-0">
                            {art.image ? (
                              <img
                                src={art.image}
                                alt={art.name}
                                className="w-full h-full object-cover"
                              />
                            ) : (
                              <span className="text-[10px] text-white flex items-center justify-center h-full">
                                {art.name.charAt(0)}
                              </span>
                            )}
                          </div>
                          <span className="text-xs font-semibold text-white group-hover:text-[var(--vynce-primary)] transition-colors">
                            {art.name}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {albumSuggestions.length > 0 && (
                  <div className="flex flex-col gap-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-[#6B6A7A] px-2">
                      Albums
                    </span>
                    <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
                      {albumSuggestions.map((alb) => (
                        <div
                          key={alb.id}
                          onClick={() => {
                            setShowDropdown(false);
                            onSelectAlbum?.(alb.id);
                          }}
                          className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.08] border border-white/5 cursor-pointer shrink-0 transition-colors group"
                        >
                          <Disc className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                          <span className="text-xs font-semibold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate max-w-[140px]">
                            {alb.name}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 4. FOOTER FULL SEARCH TRIGGER */}
            <div
              onClick={handleSubmit}
              className="flex items-center justify-between p-2.5 rounded-2xl bg-white/[0.02] hover:bg-white/[0.06] border border-white/[0.04] cursor-pointer transition-colors text-xs text-[#A5A3B5] hover:text-white"
            >
              <span>
                See all results for <strong className="text-white">"{searchQuery}"</strong>
              </span>
              <kbd className="px-2 py-0.5 rounded-lg bg-black/50 text-[10px] font-mono text-[#A5A3B5] border border-white/10">
                Enter ↵
              </kbd>
            </div>
          </div>
        )}
      </div>

      {/* Right Header Utilities */}
      <div className="hidden md:flex items-center gap-1.5 shrink-0">
        {/* Listen Together Group Sync */}
        <button
          onClick={() => setIsListenTogetherOpen(true)}
          style={
            roomCode
              ? {
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }
              : {}
          }
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-all cursor-pointer ${
            roomCode
              ? 'border-transparent shadow-md font-bold'
              : 'text-[#E8E6F0]/80 hover:text-white bg-white/[0.03] hover:bg-white/[0.08] border-white/5'
          }`}
          title="Listen Together"
        >
          <Users className={`w-3.5 h-3.5 ${roomCode ? 'text-current animate-pulse' : 'text-[var(--vynce-primary)]'}`} />
          <span>{roomCode ? `Room ${roomCode}` : 'Listen Together'}</span>
        </button>

        {/* Listening History */}
        <button
          onClick={() => setIsHistoryModalOpen(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium text-[#E8E6F0]/80 hover:text-white bg-white/[0.03] hover:bg-white/[0.08] border border-white/5 transition-all cursor-pointer"
          title="Listening History"
        >
          <History className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
          <span>History</span>
        </button>

        {/* Listening Stats & Telemetry */}
        <button
          onClick={() => setIsStatsModalOpen(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium text-[#E8E6F0]/80 hover:text-white bg-white/[0.03] hover:bg-white/[0.08] border border-white/5 transition-all cursor-pointer"
          title="Listening Insights & DSP Telemetry"
        >
          <TrendingUp className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
          <span>Stats</span>
        </button>

        {/* Equalizer Quick Action */}
        <button
          onClick={() => onTabChange?.('equalizer')}
          className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.06] transition-colors cursor-pointer"
          title="Equalizer & DSP"
        >
          <Sliders className="w-4 h-4" />
        </button>

        {/* Settings */}
        <button
          onClick={onOpenSettings}
          className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/[0.06] transition-colors cursor-pointer"
          title="Settings"
        >
          <Settings className="w-4 h-4" />
        </button>

        {/* Profile Avatar Trigger */}
        <div
          onClick={() => setIsProfileModalOpen(true)}
          style={{
            backgroundColor: 'var(--vynce-primary-container)',
            color: 'var(--vynce-on-primary-container)',
          }}
          className="w-8 h-8 rounded-full flex items-center justify-center font-bold text-xs cursor-pointer hover:scale-105 transition-transform ml-1 shadow-md border border-white/10 select-none"
          title={`Profile: ${userName}`}
        >
          <span>{userName.slice(0, 2).toUpperCase()}</span>
        </div>
      </div>

      {/* Listen Together Modal */}
      <ListenTogetherModal
        isOpen={isListenTogetherOpen}
        onClose={() => setIsListenTogetherOpen(false)}
      />

      {/* Listening History Modal */}
      <HistoryModal
        isOpen={isHistoryModalOpen}
        onClose={() => setIsHistoryModalOpen(false)}
      />

      {/* Listening Stats & Telemetry Modal */}
      <StatsModal
        isOpen={isStatsModalOpen}
        onClose={() => setIsStatsModalOpen(false)}
      />

      {/* User Profile Modal */}
      <ProfileModal
        isOpen={isProfileModalOpen}
        onClose={() => setIsProfileModalOpen(false)}
        onNavigateToTab={onTabChange}
      />

      {/* Voice Assistant & Search Modal */}
      <VoiceSearchModal
        isOpen={isVoiceModalOpen}
        onClose={() => setIsVoiceModalOpen(false)}
        onSearchSubmit={onSearchSubmit}
      />
    </header>
  );
};
