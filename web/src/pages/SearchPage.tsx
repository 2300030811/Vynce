import React, { useState, useEffect, useMemo } from 'react';
import { JioSaavnApi } from '../api/jiosaavn';
import { Song, Album, Artist, Playlist, TopQueryResult } from '../types/music';
import { usePlayerStore } from '../stores/playerStore';
import { SongCard } from '../components/cards/SongCard';
import { AlbumCard } from '../components/cards/AlbumCard';
import { PlaylistCard } from '../components/cards/PlaylistCard';
import { ArtistCard } from '../components/cards/ArtistCard';
import {
  Search,
  Loader2,
  Music,
  Disc,
  User,
  ListMusic,
  Compass,
  Play,
  DownloadCloud,
  X,
  TrendingUp,
  Flame,
  Radio,
  ArrowRight,
  ArrowLeft,
} from 'lucide-react';
import {
  SpotifyImporter,
  SPOTIFY_CURATED_PLAYLISTS,
  SPOTIFY_CATEGORIES,
  SpotifyCuratedPlaylist,
  SpotifyCategoryType,
  SpotifyPlaylistResult,
} from '../api/spotify';
import { SpotifyImportModal } from '../components/modals/SpotifyImportModal';

interface SearchPageProps {
  initialQuery?: string;
  onClearSearch?: () => void;
  onSelectAlbum: (id: string) => void;
  onSelectPlaylist: (id: string) => void;
  onSelectArtist: (id: string) => void;
}

type SearchTab = 'all' | 'songs' | 'albums' | 'artists' | 'playlists';

/**
 * Clean Vynce Artwork Tile with graceful error handling & Material You theme gradient fallback
 */
const VynceArtworkTile: React.FC<{
  url?: string;
  name: string;
  gradient: string;
  size?: string;
}> = ({ url, name, gradient, size = 'w-full aspect-square' }) => {
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    setHasError(false);
  }, [url]);

  if (url && !hasError) {
    return (
      <div className={`relative ${size} rounded-2xl overflow-hidden shadow-lg shrink-0 border border-white/10 bg-[#15141E] group-hover:border-white/20 transition-all duration-300`}>
        <img
          src={url}
          alt={name}
          onError={() => setHasError(true)}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
          loading="lazy"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
      </div>
    );
  }

  return (
    <div
      className={`relative ${size} rounded-2xl overflow-hidden shadow-lg shrink-0 border border-white/10 bg-gradient-to-br ${gradient} flex flex-col items-center justify-center p-4 text-center group-hover:scale-102 transition-all duration-300`}
    >
      <div className="p-3.5 rounded-full bg-white/[0.08] backdrop-blur-md border border-white/10 shadow-xl mb-2">
        <Music className="w-6 h-6 text-white/80" />
      </div>
      <span className="text-[11px] font-bold text-white/80 line-clamp-1">{name}</span>
    </div>
  );
};

export const SearchPage: React.FC<SearchPageProps> = ({
  initialQuery = '',
  onClearSearch,
  onSelectAlbum,
  onSelectPlaylist,
  onSelectArtist,
}) => {
  const { playSong } = usePlayerStore();
  const [query, setQuery] = useState(initialQuery);
  const [activeTab, setActiveTab] = useState<SearchTab>('all');
  const [selectedCategory, setSelectedCategory] = useState<SpotifyCategoryType>('all');
  const [isLoading, setIsLoading] = useState(false);

  // Search Results
  const [topResult, setTopResult] = useState<TopQueryResult | null>(null);
  const [songs, setSongs] = useState<Song[]>([]);
  const [albums, setAlbums] = useState<Album[]>([]);
  const [artists, setArtists] = useState<Artist[]>([]);
  const [playlists, setPlaylists] = useState<Playlist[]>([]);

  const handleClearSearch = () => {
    setQuery('');
    setTopResult(null);
    setSongs([]);
    setAlbums([]);
    setArtists([]);
    setPlaylists([]);
    setMatchedPlaylistResult(null);
    onClearSearch?.();
  };

  // Curated Catalog State
  const [curatedPlaylists, setCuratedPlaylists] = useState<SpotifyCuratedPlaylist[]>(SPOTIFY_CURATED_PLAYLISTS);
  const [statusMsg, setStatusMsg] = useState<string | null>(null);
  const [selectedImportUrl, setSelectedImportUrl] = useState<string>('');
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [streamingPlaylistId, setStreamingPlaylistId] = useState<string | null>(null);
  const [matchedPlaylistResult, setMatchedPlaylistResult] = useState<SpotifyPlaylistResult | null>(null);

  // Hydrate top categories on load
  useEffect(() => {
    SpotifyImporter.loadCuratedPlaylists('charts').then((updated) => {
      setCuratedPlaylists((prev) => {
        const next = [...prev];
        for (const item of updated) {
          const idx = next.findIndex((p) => p.id === item.id);
          if (idx !== -1) next[idx] = item;
        }
        return next;
      });
    });

    const timer = setTimeout(() => {
      SpotifyImporter.loadCuratedPlaylists().then((allUpdated) => {
        setCuratedPlaylists([...allUpdated]);
      });
    }, 600);

    return () => clearTimeout(timer);
  }, []);

  // Filtered playlists according to selected category
  const filteredPlaylists = useMemo(() => {
    if (selectedCategory === 'all') return curatedPlaylists;
    return curatedPlaylists.filter((p) => p.category === selectedCategory);
  }, [curatedPlaylists, selectedCategory]);

  // Grouped for the "all" view
  const categorySections = useMemo(() => {
    return SPOTIFY_CATEGORIES.filter((c) => c.id !== 'all').map((cat) => ({
      category: cat,
      playlists: curatedPlaylists.filter((p) => p.category === cat.id),
    })).filter((sec) => sec.playlists.length > 0);
  }, [curatedPlaylists]);

  const performSearch = async (searchTerm: string) => {
    if (!searchTerm.trim()) return;
    setIsLoading(true);
    setMatchedPlaylistResult(null);

    // 1. Check if input is a Spotify Track link or URI
    const trackId = SpotifyImporter.extractTrackId(searchTerm);
    if (trackId) {
      try {
        setStatusMsg('Resolving audio stream...');
        const resolvedSong = await SpotifyImporter.resolveSpotifyTrack(searchTerm);
        if (resolvedSong) {
          setTopResult({
            id: resolvedSong.id,
            name: resolvedSong.name,
            type: 'song',
            image: resolvedSong.image,
            subtitle: `Matched Track • ${resolvedSong.primaryArtists}`,
          });
          setSongs([resolvedSong]);
          setAlbums([]);
          setArtists([]);
          setPlaylists([]);
          setIsLoading(false);
          setStatusMsg(null);
          return;
        }
      } catch {
        setStatusMsg(null);
      }
    }

    // 2. Check if input is a Spotify Playlist link or URI
    const playlistId = SpotifyImporter.extractPlaylistId(searchTerm);
    if (playlistId) {
      try {
        setStatusMsg('Loading playlist tracks...');
        const spData = await SpotifyImporter.fetchSpotifyPlaylist(playlistId);
        if (spData) {
          setMatchedPlaylistResult(spData);
          setTopResult({
            id: spData.id,
            name: spData.name,
            type: 'playlist',
            image: spData.coverArt,
            subtitle: `Curated Playlist • ${spData.tracks.length} tracks`,
          });
        }
        setStatusMsg(null);
      } catch {
        setStatusMsg(null);
      }
    }

    // 3. Search via JioSaavn catalog
    try {
      const data = await JioSaavnApi.searchGlobal(searchTerm);
      if (!topResult) setTopResult(data.topQuery);
      if (songs.length === 0) setSongs(data.songs);
      setAlbums(data.albums);
      setArtists(data.artists);
      setPlaylists(data.playlists);
    } catch {
      if (songs.length === 0) setSongs([]);
      if (!topResult) setTopResult(null);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    setQuery(initialQuery);
    if (initialQuery) {
      performSearch(initialQuery);
    } else {
      setTopResult(null);
      setSongs([]);
      setAlbums([]);
      setArtists([]);
      setPlaylists([]);
      setMatchedPlaylistResult(null);
    }
  }, [initialQuery]);

  const handleStreamPlaylist = async (playlist: SpotifyCuratedPlaylist, e: React.MouseEvent) => {
    e.stopPropagation();
    setStreamingPlaylistId(playlist.id);
    setStatusMsg(`Streaming "${playlist.name}"...`);
    const success = await SpotifyImporter.playSpotifyPlaylistDirectly(
      playlist.id,
      (msg) => setStatusMsg(msg)
    );
    setStreamingPlaylistId(null);
    if (success) {
      setTimeout(() => setStatusMsg(null), 3000);
    } else {
      setStatusMsg(`Could not stream "${playlist.name}". Opening playlist...`);
      setTimeout(() => setStatusMsg(null), 3000);
      onSelectPlaylist(playlist.id);
    }
  };

  const tabs: { id: SearchTab; label: string; icon: any }[] = [
    { id: 'all', label: 'All Results', icon: Search },
    { id: 'songs', label: 'Songs', icon: Music },
    { id: 'albums', label: 'Albums', icon: Disc },
    { id: 'artists', label: 'Artists', icon: User },
    { id: 'playlists', label: 'Playlists', icon: ListMusic },
  ];

  return (
    <div className="flex flex-col gap-8 pb-36 animate-in fade-in duration-300 w-full max-w-[1750px] mx-auto">
      {/* ── Status Toast ── */}
      {statusMsg && (
        <div
          style={{
            backgroundColor: 'var(--vynce-primary-container)',
            color: 'var(--vynce-on-primary-container)',
          }}
          className="flex items-center gap-3 px-5 py-3.5 rounded-2xl border border-white/10 text-xs font-bold shadow-2xl backdrop-blur-xl animate-in fade-in slide-in-from-top-2"
        >
          <Loader2 className="w-4 h-4 animate-spin shrink-0" />
          <span>{statusMsg}</span>
        </div>
      )}

      {/* ── SEARCH RESULTS VIEW (When search query active from header) ── */}
      {query ? (
        <div className="flex flex-col gap-6">
          {/* Top Back Navigation to Explore */}
          <div className="flex items-center justify-between pt-1">
            <button
              onClick={handleClearSearch}
              className="flex items-center gap-2 px-4 py-2 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[#E8E6F0] hover:text-white transition-all text-xs font-bold border border-white/5 shadow-sm active:scale-95 cursor-pointer"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Back to Explore</span>
            </button>

            <button
              onClick={handleClearSearch}
              className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-white/[0.03] hover:bg-white/[0.07] text-[#8E8D9F] hover:text-white transition-all text-xs font-semibold border border-white/5 cursor-pointer"
            >
              <X className="w-3.5 h-3.5" />
              <span>Clear Search</span>
            </button>
          </div>

          {/* Search Result Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#121118]/80 p-5 md:p-6 rounded-[28px] border border-white/5 backdrop-blur-xl">
            <div className="flex items-center gap-3 min-w-0">
              <div
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="p-3 rounded-2xl shadow-inner border border-white/10 shrink-0"
              >
                <Search className="w-5 h-5" />
              </div>
              <div className="flex flex-col min-w-0">
                <span className="text-[11px] font-bold text-[#6B6A7A] uppercase tracking-wider">
                  Search Results For
                </span>
                <div className="flex items-center gap-2">
                  <h1 className="text-xl sm:text-2xl font-extrabold text-white font-['Outfit'] truncate">
                    "{query}"
                  </h1>
                  <button
                    onClick={handleClearSearch}
                    className="p-1 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors"
                    title="Clear query"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>

            {/* Filter Tabs */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 no-scrollbar">
              {tabs.map((tab) => {
                const Icon = tab.icon;
                const isActive = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    style={
                      isActive
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                          }
                        : {}
                    }
                    className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-bold tracking-wide transition-all border ${
                      isActive
                        ? 'border-transparent shadow-sm scale-102'
                        : 'bg-white/[0.03] border-white/5 text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.06]'
                    }`}
                  >
                    <Icon className="w-3.5 h-3.5" />
                    <span>{tab.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Loading Indicator */}
          {isLoading && (
            <div className="flex flex-col items-center justify-center py-20 text-[#6B6A7A] gap-3">
              <Loader2 style={{ color: 'var(--vynce-primary)' }} className="w-8 h-8 animate-spin" />
              <p className="text-sm font-semibold">Searching library...</p>
            </div>
          )}

          {/* Results Content */}
          {!isLoading && (
            <div className="flex flex-col gap-8">
              {/* Matched Playlist Preview (If user searched a playlist link) */}
              {matchedPlaylistResult && (
                <div
                  onClick={() => onSelectPlaylist(matchedPlaylistResult.id)}
                  className="flex flex-col md:flex-row items-center justify-between gap-6 p-6 rounded-[28px] bg-gradient-to-r from-white/[0.04] via-[#151420] to-[#121118] border border-white/10 hover:border-white/20 transition-all cursor-pointer shadow-2xl group"
                >
                  <div className="flex items-center gap-4">
                    <VynceArtworkTile
                      url={matchedPlaylistResult.coverArt}
                      name={matchedPlaylistResult.name}
                      gradient="from-purple-950 to-black"
                      size="w-20 h-20"
                    />
                    <div className="flex flex-col gap-1">
                      <span
                        style={{
                          backgroundColor: 'var(--vynce-primary-container)',
                          color: 'var(--vynce-on-primary-container)',
                        }}
                        className="px-2 py-0.5 rounded-md text-[10px] font-extrabold uppercase w-fit"
                      >
                        Playlist Matched
                      </span>
                      <h2 className="text-xl font-bold text-white font-['Outfit'] group-hover:text-[var(--vynce-primary)] transition-colors">
                        {matchedPlaylistResult.name}
                      </h2>
                      <p className="text-xs text-[#8E8D9F]">
                        {matchedPlaylistResult.tracks.length} Tracks ready for high-fidelity playback
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 shrink-0">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelectPlaylist(matchedPlaylistResult.id);
                      }}
                      style={{
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }}
                      className="flex items-center gap-2 px-5 py-3 rounded-2xl text-xs font-bold shadow-lg transition-all active:scale-95 hover:scale-102"
                    >
                      <Play className="w-4 h-4 fill-current" />
                      <span>Open Playlist</span>
                    </button>
                  </div>
                </div>
              )}

              {/* Top Result + Top Songs Split Section for 'all' Tab */}
              {activeTab === 'all' && (topResult || songs.length > 0) && (
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                  {/* Top Result Card */}
                  {topResult && (
                    <div className="lg:col-span-5 flex flex-col gap-3">
                      <h2 style={{ color: 'var(--vynce-primary)' }} className="text-xl font-bold font-['Outfit'] flex items-center gap-2">
                        <TrendingUp className="w-4 h-4" /> Top Result
                      </h2>
                      <div
                        onClick={() => {
                          if (topResult.type === 'artist') onSelectArtist(topResult.id);
                          else if (topResult.type === 'album') onSelectAlbum(topResult.id);
                          else if (topResult.type === 'playlist') onSelectPlaylist(topResult.id);
                          else if (songs.length > 0) playSong(songs[0], songs, 0);
                        }}
                        className="relative flex flex-col justify-between p-6 rounded-[28px] bg-gradient-to-br from-white/[0.06] via-[#151420] to-black/80 border border-white/10 hover:border-white/20 cursor-pointer transition-all shadow-xl group hover:scale-101"
                      >
                        <div className="flex items-center gap-5">
                          <div className={`relative overflow-hidden shrink-0 shadow-2xl ${topResult.type === 'artist' ? 'w-24 h-24 sm:w-28 sm:h-28 rounded-full' : 'w-24 h-24 sm:w-28 sm:h-28 rounded-2xl'}`}>
                            {topResult.image ? (
                              <img
                                src={topResult.image}
                                alt={topResult.name}
                                className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                              />
                            ) : (
                              <div className="w-full h-full bg-[#201F2D] flex items-center justify-center">
                                <Music className="w-8 h-8 text-white/40" />
                              </div>
                            )}
                          </div>

                          <div className="flex flex-col gap-1 min-w-0">
                            <span style={{ color: 'var(--vynce-primary)' }} className="text-[11px] font-extrabold uppercase tracking-wider">
                              {topResult.type}
                            </span>
                            <h3 className="text-xl sm:text-2xl font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate">
                              {topResult.name}
                            </h3>
                            {topResult.subtitle && (
                              <p className="text-xs text-[#8E8D9F] line-clamp-1">
                                {topResult.subtitle}
                              </p>
                            )}
                          </div>
                        </div>

                        <div className="flex items-center justify-end mt-4">
                          <div
                            style={{
                              backgroundColor: 'var(--vynce-primary-container)',
                              color: 'var(--vynce-on-primary-container)',
                            }}
                            className="p-3.5 rounded-full shadow-xl group-hover:scale-110 transition-transform"
                          >
                            <Play className="w-5 h-5 fill-current ml-0.5" />
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Top Songs Quick List */}
                  {songs.length > 0 && (
                    <div className={`${topResult ? 'lg:col-span-7' : 'lg:col-span-12'} flex flex-col gap-3`}>
                      <div className="flex items-center justify-between">
                        <h2 className="text-xl font-bold text-white font-['Outfit'] flex items-center gap-2">
                          <Music className="w-4 h-4 text-[var(--vynce-primary)]" /> Songs
                        </h2>
                        {songs.length > 4 && (
                          <button
                            onClick={() => setActiveTab('songs')}
                            className="text-xs font-bold text-[var(--vynce-primary)] hover:underline"
                          >
                            See All
                          </button>
                        )}
                      </div>
                      <div className="flex flex-col gap-1">
                        {songs.slice(0, 4).map((song, idx) => (
                          <SongCard
                            key={song.id}
                            song={song}
                            index={idx + 1}
                            queueContext={songs}
                          />
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Songs Tab */}
              {activeTab === 'songs' && songs.length > 0 && (
                <div className="flex flex-col gap-2">
                  <h2 className="text-xl font-bold text-white font-['Outfit'] mb-2">All Songs</h2>
                  {songs.map((song, idx) => (
                    <SongCard
                      key={song.id}
                      song={song}
                      index={idx + 1}
                      queueContext={songs}
                    />
                  ))}
                </div>
              )}

              {/* Albums Section */}
              {(activeTab === 'all' || activeTab === 'albums') && albums.length > 0 && (
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-xl font-bold text-white font-['Outfit'] flex items-center gap-2">
                      <Disc className="w-4 h-4 text-[var(--vynce-primary)]" /> Albums
                    </h2>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                    {albums.map((album) => (
                      <AlbumCard
                        key={album.id}
                        album={album}
                        onClick={() => onSelectAlbum(album.id)}
                      />
                    ))}
                  </div>
                </div>
              )}

              {/* Artists Section */}
              {(activeTab === 'all' || activeTab === 'artists') && artists.length > 0 && (
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-xl font-bold text-white font-['Outfit'] flex items-center gap-2">
                      <User className="w-4 h-4 text-[var(--vynce-primary)]" /> Artists
                    </h2>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                    {artists.map((artist) => (
                      <ArtistCard
                        key={artist.id}
                        artist={artist}
                        onClick={() => onSelectArtist(artist.id)}
                      />
                    ))}
                  </div>
                </div>
              )}

              {/* Playlists Section */}
              {(activeTab === 'all' || activeTab === 'playlists') && playlists.length > 0 && (
                <div className="flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-xl font-bold text-white font-['Outfit'] flex items-center gap-2">
                      <ListMusic className="w-4 h-4 text-[var(--vynce-primary)]" /> Playlists
                    </h2>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                    {playlists.map((playlist) => (
                      <PlaylistCard
                        key={playlist.id}
                        playlist={playlist}
                        onClick={() => onSelectPlaylist(playlist.id)}
                      />
                    ))}
                  </div>
                </div>
              )}

              {/* Empty State */}
              {songs.length === 0 &&
                albums.length === 0 &&
                artists.length === 0 &&
                playlists.length === 0 &&
                !matchedPlaylistResult && (
                  <div className="flex flex-col items-center justify-center py-20 text-[#6B6A7A] gap-3">
                    <div className="p-4 rounded-3xl bg-white/[0.03] border border-white/10">
                      <Music className="w-8 h-8 text-white/40" />
                    </div>
                    <p className="text-lg font-bold text-white">No results found for "{query}"</p>
                    <p className="text-xs max-w-sm text-center">
                      Try searching with different keywords or paste a playlist link in the search bar above.
                    </p>
                  </div>
                )}
            </div>
          )}
        </div>
      ) : (
        /* ── EXPLORE UNIVERSE DISCOVERY VIEW (Default Landing) ── */
        <div className="flex flex-col gap-8">
          {/* Hero Banner with Native Vynce Styling */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6 bg-gradient-to-r from-[#121118] via-[#181622] to-[#121118] p-6 md:p-8 rounded-[32px] border border-white/5 shadow-2xl">
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-2 text-xs font-extrabold text-[var(--vynce-primary)] uppercase tracking-wider">
                <Compass className="w-4 h-4" />
                <span>Curated Music Universe</span>
              </div>
              <h1 className="text-3xl sm:text-4xl font-extrabold text-white font-['Outfit'] tracking-tight">
                Explore Universe
              </h1>
              <p className="text-sm text-[#8E8D9F] max-w-xl">
                Browse top charts, viral tracks, moods, and handpicked soundscapes. Click any mix to view full tracks or stream instantly.
              </p>
            </div>

            <div className="flex items-center gap-3 shrink-0">
              <button
                onClick={() => {
                  setSelectedImportUrl('');
                  setIsImportModalOpen(true);
                }}
                className="flex items-center gap-2 px-5 py-3 rounded-2xl bg-white/[0.05] hover:bg-white/[0.1] border border-white/10 text-white text-xs font-bold transition-all shadow-md active:scale-95"
              >
                <DownloadCloud className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Import Playlist</span>
              </button>
            </div>
          </div>

          {/* Category Filter Pills (No Emojis, Native Pill Design) */}
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A] flex items-center gap-1.5">
                <Compass className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                <span>Browse Categories</span>
              </span>
              <span className="text-[11px] text-[#6B6A7A] font-semibold">
                {filteredPlaylists.length} Playlists
              </span>
            </div>

            <div className="flex items-center gap-2 overflow-x-auto pb-2 no-scrollbar">
              {SPOTIFY_CATEGORIES.map((cat) => {
                const isActive = selectedCategory === cat.id;
                return (
                  <button
                    key={cat.id}
                    onClick={() => setSelectedCategory(cat.id)}
                    style={
                      isActive
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                          }
                        : {}
                    }
                    className={`flex items-center gap-2 px-4 py-2 rounded-full text-xs font-bold tracking-wide shrink-0 transition-all border ${
                      isActive
                        ? 'border-transparent shadow-lg scale-102'
                        : 'bg-[#121118]/80 border-white/5 text-[#E8E6F0]/80 hover:text-white hover:bg-white/[0.06]'
                    }`}
                  >
                    <span>{cat.label}</span>
                    {cat.badge && (
                      <span
                        style={
                          isActive
                            ? {
                                backgroundColor: 'var(--vynce-on-primary-container)',
                                color: 'var(--vynce-primary-container)',
                              }
                            : {
                                backgroundColor: 'var(--vynce-primary-container)',
                                color: 'var(--vynce-on-primary-container)',
                              }
                        }
                        className="text-[9px] px-1.5 py-0.2 rounded-full font-extrabold"
                      >
                        {cat.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          {/* ── Category Selected View ── */}
          {selectedCategory !== 'all' ? (
            <div className="flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <div className="flex flex-col">
                  <h2 className="text-xl font-extrabold text-white font-['Outfit']">
                    {SPOTIFY_CATEGORIES.find((c) => c.id === selectedCategory)?.label}
                  </h2>
                  <p className="text-xs text-[#6B6A7A]">
                    {SPOTIFY_CATEGORIES.find((c) => c.id === selectedCategory)?.description}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
                {filteredPlaylists.map((pl) => (
                  <div
                    key={pl.id}
                    onClick={() => onSelectPlaylist(pl.id)}
                    className="group relative flex flex-col justify-between p-4 rounded-3xl bg-[#121118]/80 hover:bg-[#181624] border border-white/5 hover:border-white/15 transition-all duration-300 shadow-xl hover:scale-[1.02] cursor-pointer overflow-hidden"
                  >
                    <div className="flex flex-col gap-3.5">
                      <VynceArtworkTile
                        url={pl.coverArt}
                        name={pl.name}
                        gradient={pl.gradient}
                      />

                      <div className="flex flex-col gap-1 min-w-0">
                        <h3 className="text-base font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate">
                          {pl.name}
                        </h3>
                        <p className="text-xs text-[#8E8D9F] line-clamp-2 leading-relaxed">
                          {pl.subtitle}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 mt-5 pt-3.5 border-t border-white/5">
                      <button
                        onClick={(e) => handleStreamPlaylist(pl, e)}
                        disabled={streamingPlaylistId === pl.id}
                        style={{
                          backgroundColor: 'var(--vynce-primary-container)',
                          color: 'var(--vynce-on-primary-container)',
                        }}
                        className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-xl text-xs font-bold shadow-md transition-all active:scale-95 hover:scale-102 disabled:opacity-50"
                      >
                        {streamingPlaylistId === pl.id ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <Play className="w-3.5 h-3.5 fill-current" />
                        )}
                        <span>Stream Mix</span>
                      </button>

                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedImportUrl(pl.spotifyUrl);
                          setIsImportModalOpen(true);
                        }}
                        className="px-3.5 py-2.5 rounded-xl bg-white/[0.05] hover:bg-white/[0.1] text-white text-xs font-semibold border border-white/10 transition-colors"
                        title="Import to Library"
                      >
                        <DownloadCloud className="w-4 h-4 text-white/80" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            /* ── All Categories Shelves View ── */
            <div className="flex flex-col gap-10">
              {categorySections.map((sec) => (
                <div key={sec.category.id} className="flex flex-col gap-4">
                  <div className="flex items-center justify-between">
                    <div className="flex flex-col">
                      <h2 className="text-lg font-extrabold text-white font-['Outfit'] leading-tight">
                        {sec.category.label}
                      </h2>
                      <span className="text-[11px] text-[#6B6A7A]">
                        {sec.category.description}
                      </span>
                    </div>
                    <button
                      onClick={() => setSelectedCategory(sec.category.id)}
                      className="text-xs font-bold text-[var(--vynce-primary)] hover:underline flex items-center gap-1"
                    >
                      <span>See All</span>
                      <ArrowRight className="w-3 h-3" />
                    </button>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
                    {sec.playlists.map((pl) => (
                      <div
                        key={pl.id}
                        onClick={() => onSelectPlaylist(pl.id)}
                        className="group relative flex flex-col justify-between p-4 rounded-3xl bg-[#121118]/80 hover:bg-[#181624] border border-white/5 hover:border-white/15 transition-all duration-300 shadow-xl hover:scale-[1.02] cursor-pointer overflow-hidden"
                      >
                        <div className="flex flex-col gap-3.5">
                          <VynceArtworkTile
                            url={pl.coverArt}
                            name={pl.name}
                            gradient={pl.gradient}
                          />

                          <div className="flex flex-col gap-1 min-w-0">
                            <h3 className="text-sm font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate">
                              {pl.name}
                            </h3>
                            <p className="text-[11px] text-[#8E8D9F] line-clamp-1">
                              {pl.subtitle}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 mt-4 pt-3 border-t border-white/5">
                          <button
                            onClick={(e) => handleStreamPlaylist(pl, e)}
                            disabled={streamingPlaylistId === pl.id}
                            style={{
                              backgroundColor: 'var(--vynce-primary-container)',
                              color: 'var(--vynce-on-primary-container)',
                            }}
                            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-xl text-xs font-bold shadow-md transition-all active:scale-95 hover:scale-102 disabled:opacity-50"
                          >
                            {streamingPlaylistId === pl.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <Play className="w-3.5 h-3.5 fill-current" />
                            )}
                            <span>Stream Mix</span>
                          </button>

                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedImportUrl(pl.spotifyUrl);
                              setIsImportModalOpen(true);
                            }}
                            className="px-3 py-2 rounded-xl bg-white/[0.05] hover:bg-white/[0.1] text-white text-xs font-semibold border border-white/10 transition-colors"
                            title="Import to Library"
                          >
                            <DownloadCloud className="w-3.5 h-3.5 text-white/80" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Playlist Import Modal */}
      <SpotifyImportModal
        isOpen={isImportModalOpen}
        initialUrl={selectedImportUrl}
        onClose={() => {
          setIsImportModalOpen(false);
          setSelectedImportUrl('');
        }}
        onSuccess={(importedPl) => {
          setStatusMsg(`Playlist "${importedPl.name}" imported to your library!`);
          setTimeout(() => setStatusMsg(null), 4000);
          onSelectPlaylist(importedPl.id);
        }}
      />
    </div>
  );
};
