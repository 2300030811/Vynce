import Dexie, { Table } from 'dexie';
import { Song, LyricLine, LyricsTranslation } from '../types/music';

export interface DbLikedSong extends Song {
  addedAt: number;
}

export interface DbPlaylist {
  id: string;
  name: string;
  description: string;
  coverArt: string;
  createdAt: number;
}

export interface DbPlaylistSong {
  id?: number;
  playlistId: string;
  song: Song;
  addedAt: number;
  orderIndex: number;
}

export interface DbHistoryEntry {
  id?: number;
  songId: string;
  song: Song;
  playedAt: number;
}

export interface DbSavedAlbum {
  id: string;
  name: string;
  artists: string;
  image: string;
  year?: string;
  songCount?: number | string;
  addedAt: number;
}

export interface DbLyricsEntry {
  songId: string;
  synced: boolean;
  rawLrc?: string;
  plainLyrics?: string;
  lines: LyricLine[];
  fetchedAt: number;
}

export class VynceDatabase extends Dexie {
  likedSongs!: Table<DbLikedSong, string>;
  savedAlbums!: Table<DbSavedAlbum, string>;
  playlists!: Table<DbPlaylist, string>;
  playlistSongs!: Table<DbPlaylistSong, number>;
  history!: Table<DbHistoryEntry, number>;
  lyrics!: Table<DbLyricsEntry, string>;
  lyricsTranslations!: Table<LyricsTranslation, number>;

  constructor() {
    super('VynceDB');
    this.version(1).stores({
      likedSongs: 'id, name, primaryArtists, addedAt',
      playlists: 'id, name, createdAt',
      playlistSongs: '++id, playlistId, [playlistId+orderIndex], addedAt',
      history: '++id, songId, playedAt',
      lyrics: 'songId, fetchedAt',
    });
    this.version(2).stores({
      likedSongs: 'id, name, primaryArtists, addedAt',
      playlists: 'id, name, createdAt',
      playlistSongs: '++id, playlistId, [playlistId+orderIndex], addedAt',
      history: '++id, songId, playedAt',
      lyrics: 'songId, fetchedAt',
    });
    this.version(3).stores({
      likedSongs: 'id, name, primaryArtists, addedAt',
      playlists: 'id, name, createdAt',
      playlistSongs: '++id, playlistId, [playlistId+orderIndex], addedAt',
      history: '++id, songId, playedAt',
      lyrics: 'songId, fetchedAt',
      lyricsTranslations: '++id, songId, contentHash, [songId+sourceLanguage+targetLanguage+mode], [contentHash+targetLanguage+mode], createdAt',
    });
    this.version(4).stores({
      likedSongs: 'id, name, primaryArtists, addedAt',
      savedAlbums: 'id, name, artists, addedAt',
      playlists: 'id, name, createdAt',
      playlistSongs: '++id, playlistId, [playlistId+orderIndex], addedAt',
      history: '++id, songId, playedAt',
      lyrics: 'songId, fetchedAt',
      lyricsTranslations: '++id, songId, contentHash, [songId+sourceLanguage+targetLanguage+mode], [contentHash+targetLanguage+mode], createdAt',
    });
  }
}

export const db = new VynceDatabase();
