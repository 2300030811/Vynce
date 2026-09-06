export interface Song {
  id: string;
  name: string;
  primaryArtists: string;
  album: string;
  image: string;
  downloadUrl: string;
  duration: number; // in seconds
  year?: string;
  language?: string;
  hasLyrics?: boolean;
}

export interface Album {
  id: string;
  name: string;
  artists: string;
  image: string;
  songCount?: number | string;
  year?: string;
  songs?: Song[];
}

export interface Artist {
  id: string;
  name: string;
  image: string;
  followerCount?: string | number;
  bio?: string;
  topSongs?: Song[];
  topAlbums?: Album[];
}

export interface Playlist {
  id: string;
  name: string;
  image: string;
  songCount?: number | string;
  followerCount?: string | number;
  description?: string;
  songs?: Song[];
}

export interface HomeModule {
  id: string;
  title: string;
  subtitle?: string;
  type: 'songs' | 'playlists' | 'albums';
  items: (Song | Playlist | Album)[];
}

export interface LyricLine {
  time: number; // in seconds
  text: string;
}

export interface LyricData {
  synced: boolean;
  lines: LyricLine[];
  plainLyrics?: string;
}

export interface UserPlaylist {
  id: string;
  name: string;
  description?: string;
  createdAt: number;
  coverArt?: string;
  songIds: string[];
}

export interface ListeningHistoryEntry {
  song: Song;
  playedAt: number;
  durationListened: number;
}

export interface TopQueryResult {
  id: string;
  name: string;
  type: 'artist' | 'album' | 'song' | 'playlist';
  image: string;
  subtitle: string;
}

export interface GlobalSearchResult {
  topQuery: TopQueryResult | null;
  songs: Song[];
  artists: Artist[];
  albums: Album[];
  playlists: Playlist[];
}

export type TranslationMode = 'literal' | 'lyrical';

export interface TranslationProvenance {
  provider: string;
  model: string;
  modelVersion?: string;
  sourceLanguage: string;
  targetLanguage: string;
  mode: TranslationMode;
  createdAt: number;
  updatedAt: number;
}

export interface LyricsTranslation {
  id?: number;
  songId: string;
  contentHash: string;
  sourceLanguage: string;
  targetLanguage: string;
  mode: TranslationMode;
  translatedLines: LyricLine[];
  translatedPlain?: string;
  provenance: TranslationProvenance;
  createdAt: number;
}

export interface DetectedLanguage {
  script: string;
  language: string;
  code: string;
  confidence: number;
  isRomanized: boolean;
  isAmbiguous: boolean;
}

export interface LyricsDisplayData {
  original: LyricLine[];
  romanized?: LyricLine[];
  translated?: LyricLine[];
  sourceLanguage?: string;
  targetLanguage?: string;
  translationMode?: TranslationMode;
  detectedLanguage?: DetectedLanguage;
}

