import { create } from 'zustand';
import { Song, Album, Playlist } from '../types/music';
import { db, DbLikedSong, DbPlaylist, DbHistoryEntry, DbSavedAlbum } from '../db';

export interface LibraryState {
  likedSongIds: Set<string>;
  likedSongs: DbLikedSong[];
  savedAlbumIds: Set<string>;
  savedAlbums: DbSavedAlbum[];
  playlists: DbPlaylist[];
  history: DbHistoryEntry[];
  isLoading: boolean;

  loadLibrary: () => Promise<void>;
  toggleLike: (song: Song) => Promise<boolean>;
  isLiked: (songId: string) => boolean;

  // Album Saving in Library
  toggleSaveAlbum: (album: Album, songs?: Song[]) => Promise<boolean>;
  isAlbumSaved: (albumId: string) => boolean;
  deleteSavedAlbum: (albumId: string) => Promise<void>;

  // Playlist Management & Saving
  createPlaylist: (name: string, description?: string, coverArt?: string) => Promise<string>;
  toggleSavePlaylist: (playlist: Playlist, songs: Song[]) => Promise<boolean>;
  isPlaylistSaved: (playlistId: string) => boolean;
  deletePlaylist: (id: string) => Promise<void>;
  addSongToPlaylist: (playlistId: string, song: Song) => Promise<void>;
  removeSongFromPlaylist: (playlistId: string, songId: string) => Promise<void>;
  getPlaylistSongs: (playlistId: string) => Promise<Song[]>;
  getPlaylistIdsForSong: (songId: string) => Promise<string[]>;

  // History & Backup
  recordHistory: (song: Song) => Promise<void>;
  clearHistory: () => Promise<void>;
  exportLibrary: () => Promise<string>;
  importLibrary: (jsonData: string) => Promise<{ playlistsCount: number; likesCount: number; albumsCount: number }>;
}

export const useLibraryStore = create<LibraryState>((set, get) => ({
  likedSongIds: new Set(),
  likedSongs: [],
  savedAlbumIds: new Set(),
  savedAlbums: [],
  playlists: [],
  history: [],
  isLoading: true,

  loadLibrary: async () => {
    try {
      const [likes, savedAlbums, playlists, history] = await Promise.all([
        db.likedSongs.orderBy('addedAt').reverse().toArray(),
        db.savedAlbums ? db.savedAlbums.orderBy('addedAt').reverse().toArray() : Promise.resolve([]),
        db.playlists.orderBy('createdAt').reverse().toArray(),
        db.history.orderBy('playedAt').reverse().limit(100).toArray(),
      ]);

      // Deduplicate history entries to show each unique song only once
      const uniqueHistory: DbHistoryEntry[] = [];
      const seenSongIds = new Set<string>();
      for (const h of history) {
        if (h.song && !seenSongIds.has(h.song.id)) {
          seenSongIds.add(h.song.id);
          uniqueHistory.push(h);
        }
      }

      set({
        likedSongs: likes,
        likedSongIds: new Set(likes.map((s) => s.id)),
        savedAlbums: savedAlbums || [],
        savedAlbumIds: new Set((savedAlbums || []).map((a) => a.id)),
        playlists,
        history: uniqueHistory,
        isLoading: false,
      });
    } catch {
      set({ isLoading: false });
    }
  },

  toggleLike: async (song: Song) => {
    const { likedSongIds, likedSongs } = get();
    const isCurrentlyLiked = likedSongIds.has(song.id);

    if (isCurrentlyLiked) {
      await db.likedSongs.delete(song.id);
      const nextIds = new Set(likedSongIds);
      nextIds.delete(song.id);
      set({
        likedSongIds: nextIds,
        likedSongs: likedSongs.filter((s) => s.id !== song.id),
      });
      return false;
    } else {
      const dbEntry: DbLikedSong = {
        ...song,
        addedAt: Date.now(),
      };
      await db.likedSongs.put(dbEntry);
      const nextIds = new Set(likedSongIds);
      nextIds.add(song.id);
      set({
        likedSongIds: nextIds,
        likedSongs: [dbEntry, ...likedSongs],
      });
      return true;
    }
  },

  isLiked: (songId: string) => {
    return get().likedSongIds.has(songId);
  },

  // ── Album Saving ──────────────────────────────────────────
  toggleSaveAlbum: async (album: Album, songs: Song[] = []) => {
    const { savedAlbumIds, savedAlbums } = get();
    const isSaved = savedAlbumIds.has(album.id);

    if (isSaved) {
      await db.savedAlbums.delete(album.id);
      // Clean up cached album playlist songs
      await db.playlistSongs.where('playlistId').equals(`album_${album.id}`).delete();
      const nextIds = new Set(savedAlbumIds);
      nextIds.delete(album.id);
      set({
        savedAlbumIds: nextIds,
        savedAlbums: savedAlbums.filter((a) => a.id !== album.id),
      });
      return false;
    } else {
      const entry: DbSavedAlbum = {
        id: album.id,
        name: album.name,
        artists: album.artists || '',
        image: album.image || '',
        year: album.year,
        songCount: album.songCount || songs.length,
        addedAt: Date.now(),
      };
      await db.savedAlbums.put(entry);

      // Save tracks to offline playlist table if provided
      if (songs.length > 0) {
        for (let i = 0; i < songs.length; i++) {
          await db.playlistSongs.put({
            playlistId: `album_${album.id}`,
            song: songs[i],
            addedAt: Date.now(),
            orderIndex: i,
          });
        }
      }

      const nextIds = new Set(savedAlbumIds);
      nextIds.add(album.id);
      set({
        savedAlbumIds: nextIds,
        savedAlbums: [entry, ...savedAlbums],
      });
      return true;
    }
  },

  isAlbumSaved: (albumId: string) => {
    return get().savedAlbumIds.has(albumId);
  },

  deleteSavedAlbum: async (albumId: string) => {
    await db.savedAlbums.delete(albumId);
    await db.playlistSongs.where('playlistId').equals(`album_${albumId}`).delete();
    const { savedAlbumIds, savedAlbums } = get();
    const nextIds = new Set(savedAlbumIds);
    nextIds.delete(albumId);
    set({
      savedAlbumIds: nextIds,
      savedAlbums: savedAlbums.filter((a) => a.id !== albumId),
    });
  },

  // ── Playlist Management & Saving ──────────────────────────
  createPlaylist: async (name: string, description: string = '', coverArt: string = '') => {
    const id = `playlist_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`;
    const playlist: DbPlaylist = {
      id,
      name,
      description,
      coverArt,
      createdAt: Date.now(),
    };
    await db.playlists.add(playlist);
    set((state) => ({ playlists: [playlist, ...state.playlists] }));
    return id;
  },

  toggleSavePlaylist: async (playlist: Playlist, songs: Song[]) => {
    const { playlists } = get();
    const exists = playlists.some((p) => p.id === playlist.id || p.name === playlist.name);

    if (exists) {
      const match = playlists.find((p) => p.id === playlist.id || p.name === playlist.name);
      if (match) {
        await Promise.all([
          db.playlists.delete(match.id),
          db.playlistSongs.where('playlistId').equals(match.id).delete(),
        ]);
        set((state) => ({ playlists: state.playlists.filter((p) => p.id !== match.id) }));
      }
      return false;
    } else {
      const id = playlist.id.startsWith('playlist_') ? playlist.id : `saved_pl_${playlist.id}`;
      const entry: DbPlaylist = {
        id,
        name: playlist.name,
        description: playlist.description || `${songs.length} tracks`,
        coverArt: playlist.image || '',
        createdAt: Date.now(),
      };
      await db.playlists.put(entry);

      // Save tracks to playlist table
      for (let i = 0; i < songs.length; i++) {
        await db.playlistSongs.put({
          playlistId: id,
          song: songs[i],
          addedAt: Date.now(),
          orderIndex: i,
        });
      }

      set((state) => ({ playlists: [entry, ...state.playlists] }));
      return true;
    }
  },

  isPlaylistSaved: (playlistId: string) => {
    const { playlists } = get();
    return playlists.some((p) => p.id === playlistId || p.id === `saved_pl_${playlistId}`);
  },

  deletePlaylist: async (id: string) => {
    await Promise.all([
      db.playlists.delete(id),
      db.playlistSongs.where('playlistId').equals(id).delete(),
    ]);
    set((state) => ({ playlists: state.playlists.filter((p) => p.id !== id) }));
  },

  addSongToPlaylist: async (playlistId: string, song: Song) => {
    const count = await db.playlistSongs.where('playlistId').equals(playlistId).count();
    await db.playlistSongs.add({
      playlistId,
      song,
      addedAt: Date.now(),
      orderIndex: count,
    });

    // Update cover art if empty
    const playlist = await db.playlists.get(playlistId);
    if (playlist && !playlist.coverArt && song.image) {
      await db.playlists.update(playlistId, { coverArt: song.image });
      set((state) => ({
        playlists: state.playlists.map((p) =>
          p.id === playlistId ? { ...p, coverArt: song.image } : p
        ),
      }));
    }
  },

  removeSongFromPlaylist: async (playlistId: string, songId: string) => {
    const item = await db.playlistSongs
      .where('playlistId')
      .equals(playlistId)
      .and((record) => record.song.id === songId)
      .first();

    if (item?.id) {
      await db.playlistSongs.delete(item.id);
    }
  },

  getPlaylistSongs: async (playlistId: string) => {
    const records = await db.playlistSongs
      .where('playlistId')
      .equals(playlistId)
      .sortBy('orderIndex');
    return records.map((r) => r.song);
  },

  getPlaylistIdsForSong: async (songId: string) => {
    try {
      const all = await db.playlistSongs.toArray();
      const matched = all.filter((r) => r.song && r.song.id === songId);
      return matched.map((r) => r.playlistId);
    } catch {
      return [];
    }
  },

  recordHistory: async (song: Song) => {
    const entry: DbHistoryEntry = {
      songId: song.id,
      song,
      playedAt: Date.now(),
    };
    try {
      await db.history.where('songId').equals(song.id).delete();
      await db.history.add(entry);
    } catch {
      // Fallback
    }
    set((state) => ({
      history: [entry, ...state.history.filter((h) => h.songId !== song.id)].slice(0, 100),
    }));
  },

  clearHistory: async () => {
    await db.history.clear();
    set({ history: [] });
  },

  exportLibrary: async () => {
    const [likes, savedAlbums, playlists, playlistSongs] = await Promise.all([
      db.likedSongs.toArray(),
      db.savedAlbums ? db.savedAlbums.toArray() : Promise.resolve([]),
      db.playlists.toArray(),
      db.playlistSongs.toArray(),
    ]);
    const backup = {
      version: 2,
      exportedAt: new Date().toISOString(),
      likes,
      savedAlbums,
      playlists,
      playlistSongs,
    };
    return JSON.stringify(backup, null, 2);
  },

  importLibrary: async (jsonData: string) => {
    const parsed = JSON.parse(jsonData);
    if (!parsed) throw new Error('Invalid JSON backup file');

    let likesCount = 0;
    let playlistsCount = 0;
    let albumsCount = 0;

    if (Array.isArray(parsed.likes)) {
      for (const song of parsed.likes) {
        if (song?.id) {
          await db.likedSongs.put(song);
          likesCount++;
        }
      }
    }

    if (Array.isArray(parsed.savedAlbums)) {
      for (const album of parsed.savedAlbums) {
        if (album?.id && album?.name) {
          await db.savedAlbums.put(album);
          albumsCount++;
        }
      }
    }

    if (Array.isArray(parsed.playlists)) {
      for (const pl of parsed.playlists) {
        if (pl?.id && pl?.name) {
          await db.playlists.put(pl);
          playlistsCount++;
        }
      }
    }

    if (Array.isArray(parsed.playlistSongs)) {
      for (const ps of parsed.playlistSongs) {
        if (ps?.playlistId && ps?.song) {
          await db.playlistSongs.put(ps);
        }
      }
    }

    await get().loadLibrary();
    return { playlistsCount, likesCount, albumsCount };
  },
}));
