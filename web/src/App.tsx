import React, { useState } from 'react';
import { AppLayout } from './components/layout/AppLayout';
import { NavTab } from './components/layout/Sidebar';
import { HomePage } from './pages/HomePage';
import { SearchPage } from './pages/SearchPage';
import { AlbumPage } from './pages/AlbumPage';
import { ArtistPage } from './pages/ArtistPage';
import { PlaylistPage } from './pages/PlaylistPage';
import { LikedSongsPage } from './pages/LikedSongsPage';
import { LibraryPage } from './pages/LibraryPage';
import { SettingsPage } from './pages/SettingsPage';
import { EqualizerPage } from './pages/EqualizerPage';

export type CurrentView =
  | { type: 'tab'; tab: NavTab }
  | { type: 'album'; albumId: string }
  | { type: 'artist'; artistId: string }
  | { type: 'playlist'; playlistId: string };

export function App() {
  const [currentView, setCurrentView] = useState<CurrentView>({ type: 'tab', tab: 'home' });
  const [lastActiveTab, setLastActiveTab] = useState<NavTab>('home');
  const [searchQuery, setSearchQuery] = useState('');

  const handleTabChange = (tab: NavTab) => {
    setLastActiveTab(tab);
    if (tab === 'songs') {
      setSearchQuery('');
    }
    setCurrentView({ type: 'tab', tab });
  };

  const handleSearchSubmit = (query: string) => {
    setSearchQuery(query);
    setLastActiveTab('songs');
    setCurrentView({ type: 'tab', tab: 'songs' });
  };

  const handleSelectAlbum = (albumId: string) => {
    setCurrentView({ type: 'album', albumId });
  };

  const handleSelectArtist = (artistId: string) => {
    setCurrentView({ type: 'artist', artistId });
  };

  const handleSelectPlaylist = (playlistId: string) => {
    setCurrentView({ type: 'playlist', playlistId });
  };

  const handleBackToTab = () => {
    setCurrentView({ type: 'tab', tab: lastActiveTab });
  };

  const activeTab: NavTab = currentView.type === 'tab' ? currentView.tab : lastActiveTab;

  return (
    <AppLayout
      activeTab={activeTab}
      searchQuery={searchQuery}
      onTabChange={handleTabChange}
      onSearchSubmit={handleSearchSubmit}
      onPlaylistClick={handleSelectPlaylist}
      onSelectArtist={handleSelectArtist}
      onSelectAlbum={handleSelectAlbum}
    >
      {currentView.type === 'tab' && currentView.tab === 'home' && (
        <HomePage
          onSelectAlbum={handleSelectAlbum}
          onSelectPlaylist={handleSelectPlaylist}
          onSelectArtist={handleSelectArtist}
          onTabChange={handleTabChange}
        />
      )}

      {currentView.type === 'tab' && currentView.tab === 'songs' && (
        <SearchPage
          initialQuery={searchQuery}
          onClearSearch={() => setSearchQuery('')}
          onSelectAlbum={handleSelectAlbum}
          onSelectPlaylist={handleSelectPlaylist}
          onSelectArtist={handleSelectArtist}
        />
      )}

      {currentView.type === 'tab' && currentView.tab === 'liked' && (
        <LikedSongsPage
          onNavigateToExplore={() => handleTabChange('songs')}
          onSelectAlbum={handleSelectAlbum}
          onSelectArtist={handleSelectArtist}
        />
      )}

      {currentView.type === 'tab' && currentView.tab === 'library' && (
        <LibraryPage
          onSelectPlaylist={handleSelectPlaylist}
          onSelectAlbum={handleSelectAlbum}
          onNavigateToLiked={() => handleTabChange('liked')}
        />
      )}

      {currentView.type === 'tab' && currentView.tab === 'equalizer' && <EqualizerPage />}

      {currentView.type === 'tab' && currentView.tab === 'settings' && (
        <SettingsPage onNavigateToTab={handleTabChange} />
      )}

      {currentView.type === 'album' && (
        <AlbumPage
          albumId={currentView.albumId}
          onBack={handleBackToTab}
          onSelectArtist={handleSelectArtist}
        />
      )}

      {currentView.type === 'artist' && (
        <ArtistPage
          artistId={currentView.artistId}
          onBack={handleBackToTab}
          onSelectAlbum={handleSelectAlbum}
        />
      )}

      {currentView.type === 'playlist' && (
        <PlaylistPage
          playlistId={currentView.playlistId}
          onBack={handleBackToTab}
          onSelectAlbum={handleSelectAlbum}
        />
      )}
    </AppLayout>
  );
}

export default App;
