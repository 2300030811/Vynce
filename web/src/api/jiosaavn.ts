import { Song, Album, Artist, Playlist, HomeModule, TopQueryResult, GlobalSearchResult } from '../types/music';

const BASES = [
  'https://my-repo-nine-phi.vercel.app',
  'https://jiosaavn-api-pink.vercel.app',
];

let lastGoodBase = BASES[0];

function decodeHtml(html: string): string {
  if (!html) return '';
  const txt = document.createElement('textarea');
  txt.innerHTML = html;
  return txt.value
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');
}

function extractImageUrl(data: any): string {
  if (!data) return '';
  let url = '';
  if (Array.isArray(data)) {
    const last = data[data.length - 1];
    url = last?.url || last?.link || '';
  } else if (typeof data === 'string') {
    url = data;
  }
  if (!url) return '';
  url = url.replace('http://', 'https://').replace('150x150', '500x500').replace('50x50', '500x500');
  if (
    url.includes('artist-default') ||
    url.includes('default-artist') ||
    url.includes('default_artist') ||
    url.includes('artist-placeholder')
  ) {
    return '';
  }
  return url;
}

import { useSettingsStore } from '../stores/settingsStore';

function extractDownloadUrl(data: any): string {
  if (!data) return '';
  if (Array.isArray(data)) {
    const preferred = useSettingsStore.getState()?.bitrate || '320kbps';
    const match = data.find((d: any) => d.quality === preferred || d.bitrate === preferred) ||
                  data.find((d: any) => d.quality === '320kbps') ||
                  data[data.length - 1];
    const url = match?.url || match?.link || '';
    return url.replace('http://', 'https://');
  }
  if (typeof data === 'string') {
    return data.replace('http://', 'https://');
  }
  return '';
}

function parseSong(item: any): Song {
  const primaryArtists = item.primaryArtists || 
    (Array.isArray(item.artists?.primary) ? item.artists.primary.map((a: any) => a.name).join(', ') : '') || 
    item.subtitle || 
    item.artist || 
    '';

  const albumName = (typeof item.album === 'object' ? item.album?.name : item.album) || '';

  return {
    id: String(item.id || ''),
    name: decodeHtml(item.name || item.title || 'Unknown Track'),
    primaryArtists: decodeHtml(primaryArtists),
    album: decodeHtml(albumName),
    image: extractImageUrl(item.image),
    downloadUrl: extractDownloadUrl(item.downloadUrl || item.media_url),
    duration: Number(item.duration || 0),
    year: String(item.year || ''),
    language: String(item.language || ''),
    hasLyrics: Boolean(item.hasLyrics === 'true' || item.hasLyrics === true),
  };
}

async function getJson(path: string, params: Record<string, string> = {}): Promise<any> {
  const query = new URLSearchParams(params).toString();
  const urlPath = `${path}${query ? `?${query}` : ''}`;
  const basesToTry = [lastGoodBase, ...BASES.filter((b) => b !== lastGoodBase)];

  for (const base of basesToTry) {
    try {
      const res = await fetch(`${base}${urlPath}`, {
        headers: { Accept: 'application/json' },
      });
      if (!res.ok) continue;
      const json = await res.json();
      lastGoodBase = base;
      return json;
    } catch {
      // Try next base URL
    }
  }
  return null;
}

export const JioSaavnApi = {
  async getHome(languages: string = 'hindi,english', category: string = 'all'): Promise<HomeModule[]> {
    // 1. First attempt: Try direct launch data via local proxy
    try {
      const url = `/jiosaavn-direct/api.php?__call=webapi.getLaunchData&api_version=4&_format=json&_marker=0&ctx=web6dot0&language=${languages}`;
      const res = await fetch(url);
      if (res.ok) {
        const root = await res.json();
        const modulesMap = root.modules;
        if (modulesMap && Object.keys(modulesMap).length > 0) {
          const sortedModules = Object.entries(modulesMap)
            .map(([key, value]: [string, any]) => ({
              key,
              position: Number(value?.position ?? 999),
              title: decodeHtml(value?.title || ''),
              subtitle: decodeHtml(value?.subtitle || ''),
            }))
            .sort((a, b) => a.position - b.position);

          const result: HomeModule[] = [];

          for (const mod of sortedModules) {
            const array = root[mod.key];
            if (!Array.isArray(array) || array.length === 0) continue;

            const songs: Song[] = [];
            const playlists: Playlist[] = [];
            const albums: Album[] = [];

            for (const el of array) {
              const type = el.type;
              if (type === 'song') {
                songs.push({
                  id: String(el.id || ''),
                  name: decodeHtml(el.title || ''),
                  primaryArtists: decodeHtml(el.subtitle || ''),
                  album: '',
                  image: extractImageUrl(el.image),
                  downloadUrl: '',
                  duration: Number(el.more_info?.duration || 0),
                });
              } else if (type === 'playlist') {
                playlists.push({
                  id: String(el.id || ''),
                  name: decodeHtml(el.title || ''),
                  image: extractImageUrl(el.image),
                  songCount: el.more_info?.song_count || 0,
                  followerCount: el.more_info?.follower_count || '0',
                });
              } else if (type === 'album') {
                albums.push({
                  id: String(el.id || ''),
                  name: decodeHtml(el.title || ''),
                  artists: decodeHtml(el.subtitle || ''),
                  image: extractImageUrl(el.image),
                  year: String(el.more_info?.year || el.year || ''),
                  songCount: el.more_info?.song_count || 0,
                });
              }
            }

            if (songs.length > 0) {
              result.push({
                id: `song-${mod.key}`,
                title: mod.title,
                subtitle: mod.subtitle,
                type: 'songs',
                items: songs,
              });
            }
            if (playlists.length > 0) {
              result.push({
                id: `playlist-${mod.key}`,
                title: mod.title,
                subtitle: mod.subtitle,
                type: 'playlists',
                items: playlists,
              });
            }
            if (albums.length > 0) {
              result.push({
                id: `album-${mod.key}`,
                title: mod.title,
                subtitle: mod.subtitle,
                type: 'albums',
                items: albums,
              });
            }
          }

          if (result.length >= 3) {
            return result;
          }
        }
      }
    } catch {}

    // 2. High-Density Curated Discovery Architecture
    try {
      if (category === 'bollywood') {
        const [songs, albums, playlists] = await Promise.all([
          this.searchSongs('latest hindi bollywood', 24),
          this.searchAlbums('bollywood hits', 12),
          this.searchPlaylists('bollywood romantic hindi', 12),
        ]);
        return [
          { id: 'bollywood-songs', title: 'Bollywood Hotlist', subtitle: 'Trending in Mumbai & Worldwide', type: 'songs', items: songs },
          { id: 'bollywood-albums', title: 'Hot Movie Soundtracks', subtitle: 'Blockbuster Releases', type: 'albums', items: albums },
          { id: 'bollywood-playlists', title: 'Curated Bollywood Mixes', subtitle: 'Handcrafted For You', type: 'playlists', items: playlists },
        ];
      }

      if (category === 'punjabi') {
        const [songs, albums] = await Promise.all([
          this.searchSongs('punjabi hits diljit karan aujla', 24),
          this.searchAlbums('punjabi top hits', 12),
        ]);
        return [
          { id: 'punjabi-songs', title: 'Punjabi Chartbusters', subtitle: 'High Voltage Beats', type: 'songs', items: songs },
          { id: 'punjabi-albums', title: 'Top Punjabi Albums', subtitle: 'Latest Bangers', type: 'albums', items: albums },
        ];
      }

      if (category === 'global') {
        const [songs, albums] = await Promise.all([
          this.searchSongs('global top 50 english pop', 24),
          this.searchAlbums('billboard hot 100', 12),
        ]);
        return [
          { id: 'global-songs', title: 'Global Pop & Billboard Hits', subtitle: 'Worldwide Viral Sensations', type: 'songs', items: songs },
          { id: 'global-albums', title: 'Trending Global Albums', subtitle: 'Chart-Topping Releases', type: 'albums', items: albums },
        ];
      }

      if (category === 'lofi') {
        const [songs, playlists] = await Promise.all([
          this.searchSongs('lofi chill aesthetic hindi english', 24),
          this.searchPlaylists('lofi chill study beats', 12),
        ]);
        return [
          { id: 'lofi-songs', title: 'Lo-Fi & Ambient Beats', subtitle: 'Focus, Relax, & Unwind', type: 'songs', items: songs },
          { id: 'lofi-playlists', title: 'Aesthetic Chill Stations', subtitle: 'Late Night Vibes', type: 'playlists', items: playlists },
        ];
      }

      if (category === 'romantic') {
        const [songs, playlists] = await Promise.all([
          this.searchSongs('romantic hindi love songs arijit', 24),
          this.searchPlaylists('heartfelt romantic melodies', 12),
        ]);
        return [
          { id: 'romantic-songs', title: 'Romantic Melodies', subtitle: 'Soulful Love Anthems', type: 'songs', items: songs },
          { id: 'romantic-playlists', title: 'Love & Acoustic Stations', subtitle: 'Sweet Serenades', type: 'playlists', items: playlists },
        ];
      }

      // Default 'all' or 'trending': Rich Multi-Section Home Experience
      const [
        trendingSongs,
        bollywoodSongs,
        punjabiSongs,
        globalSongs,
        chillSongs,
        topAlbums,
        topPlaylists,
      ] = await Promise.all([
        this.searchSongs('trending hits 2025', 18),
        this.searchSongs('latest hindi songs', 14),
        this.searchSongs('punjabi hits', 14),
        this.searchSongs('global hits billboard', 14),
        this.searchSongs('lofi chill songs', 14),
        this.searchAlbums('trending hits', 10),
        this.searchPlaylists('top hits', 10),
      ]);

      const modules: HomeModule[] = [];

      if (trendingSongs.length > 0) {
        modules.push({
          id: 'trending-picks',
          title: 'Quick Picks & Trending Hits',
          subtitle: 'Top of the charts today',
          type: 'songs',
          items: trendingSongs,
        });
      }

      if (bollywoodSongs.length > 0) {
        modules.push({
          id: 'bollywood-vibes',
          title: 'Bollywood Blockbusters',
          subtitle: 'Chart-topping Hindi anthems',
          type: 'songs',
          items: bollywoodSongs,
        });
      }

      if (topAlbums.length > 0) {
        modules.push({
          id: 'featured-albums',
          title: 'Trending Albums',
          subtitle: 'Latest fresh studio releases',
          type: 'albums',
          items: topAlbums,
        });
      }

      if (punjabiSongs.length > 0) {
        modules.push({
          id: 'punjabi-vibes',
          title: 'Punjabi Energy',
          subtitle: 'High-octane club & street beats',
          type: 'songs',
          items: punjabiSongs,
        });
      }

      if (topPlaylists.length > 0) {
        modules.push({
          id: 'curated-playlists',
          title: 'Curated Playlists & Mixes',
          subtitle: 'Handcrafted for every mood',
          type: 'playlists',
          items: topPlaylists,
        });
      }

      if (globalSongs.length > 0) {
        modules.push({
          id: 'global-pop',
          title: 'Global Top Hits',
          subtitle: 'International viral sensations',
          type: 'songs',
          items: globalSongs,
        });
      }

      if (chillSongs.length > 0) {
        modules.push({
          id: 'lofi-chill',
          title: 'Lo-Fi Chill & Night Vibes',
          subtitle: 'Mellow grooves for focus & relaxation',
          type: 'songs',
          items: chillSongs,
        });
      }

      return modules;
    } catch {
      const fallbackSongs = await this.searchSongs('trending hits 2025');
      return [
        {
          id: 'fallback-trending',
          title: 'Trending Hits',
          subtitle: 'Top charts right now',
          type: 'songs',
          items: fallbackSongs,
        },
      ];
    }
  },

  async searchAll(query: string) {
    const [songs, albums, artists, playlists] = await Promise.all([
      this.searchSongs(query),
      this.searchAlbums(query),
      this.searchArtists(query),
      this.searchPlaylists(query),
    ]);
    return { songs, albums, artists, playlists };
  },

  async searchGlobal(query: string): Promise<GlobalSearchResult> {
    const json = await getJson('/api/search', { query });
    const data = json?.data;

    let topQuery: TopQueryResult | null = null;
    const rawTop = data?.topQuery?.results?.[0];
    if (rawTop) {
      topQuery = {
        id: String(rawTop.id || ''),
        name: decodeHtml(rawTop.title || rawTop.name || ''),
        type: (rawTop.type as any) || 'artist',
        image: extractImageUrl(rawTop.image),
        subtitle: decodeHtml(rawTop.description || rawTop.subtitle || rawTop.type || ''),
      };
    }

    const songs: Song[] = (data?.songs?.results || []).map((s: any) => parseSong(s));
    const artists: Artist[] = (data?.artists?.results || []).map((a: any) => ({
      id: String(a.id || ''),
      name: decodeHtml(a.title || a.name || ''),
      image: extractImageUrl(a.image),
      followerCount: a.description || a.followerCount || '',
    }));
    const albums: Album[] = (data?.albums?.results || []).map((al: any) => ({
      id: String(al.id || ''),
      name: decodeHtml(al.title || al.name || ''),
      artists: decodeHtml(al.artist || al.artists || al.description || ''),
      image: extractImageUrl(al.image),
      year: String(al.year || ''),
    }));
    const playlists: Playlist[] = (data?.playlists?.results || []).map((p: any) => ({
      id: String(p.id || ''),
      name: decodeHtml(p.title || p.name || ''),
      image: extractImageUrl(p.image),
      followerCount: p.description || p.followerCount || '',
    }));

    if (songs.length === 0) {
      const fallbackSongs = await this.searchSongs(query, 12);
      songs.push(...fallbackSongs);
    }
    if (artists.length === 0 && !topQuery) {
      const fallbackArtists = await this.searchArtists(query, 6);
      artists.push(...fallbackArtists);
    }

    return { topQuery, songs, artists, albums, playlists };
  },

  async searchSongs(query: string, limit: number = 20): Promise<Song[]> {
    const json = await getJson('/api/search/songs', { query, limit: String(limit) });
    const results = json?.data?.results || [];
    return results.map((item: any) => parseSong(item));
  },

  async searchAlbums(query: string, limit: number = 10): Promise<Album[]> {
    const json = await getJson('/api/search/albums', { query, limit: String(limit) });
    const results = json?.data?.results || [];
    return results.map((a: any) => ({
      id: String(a.id || ''),
      name: decodeHtml(a.name || a.title || ''),
      artists: decodeHtml(
        Array.isArray(a.artists?.primary) ? a.artists.primary.map((art: any) => art.name).join(', ') : a.artist || ''
      ),
      image: extractImageUrl(a.image),
      songCount: a.songCount || 0,
      year: String(a.year || ''),
    }));
  },

  async searchArtists(query: string, limit: number = 10): Promise<Artist[]> {
    const json = await getJson('/api/search/artists', { query, limit: String(limit) });
    const results = json?.data?.results || [];
    return results.map((a: any) => ({
      id: String(a.id || ''),
      name: decodeHtml(a.name || a.title || ''),
      image: extractImageUrl(a.image),
      followerCount: a.followerCount || '0',
    }));
  },

  async searchPlaylists(query: string, limit: number = 10): Promise<Playlist[]> {
    const json = await getJson('/api/search/playlists', { query, limit: String(limit) });
    const results = json?.data?.results || [];
    return results.map((p: any) => ({
      id: String(p.id || ''),
      name: decodeHtml(p.name || p.title || ''),
      image: extractImageUrl(p.image),
      songCount: p.songCount || 0,
      followerCount: p.followerCount || '0',
    }));
  },

  async getSong(id: string): Promise<Song | null> {
    const json = await getJson(`/api/songs/${id}`);
    const data = json?.data;
    const songObj = Array.isArray(data) ? data[0] : (data?.results ? data.results[0] : data);
    return songObj ? parseSong(songObj) : null;
  },

  async getAlbum(id: string): Promise<{ album: Album; songs: Song[] }> {
    const json = await getJson('/api/albums', { id });
    const data = json?.data;
    if (!data) return { album: { id, name: 'Album', artists: '', image: '' }, songs: [] };

    const songs = Array.isArray(data.songs) ? data.songs.map((s: any) => parseSong(s)) : [];
    const album: Album = {
      id: String(data.id || id),
      name: decodeHtml(data.name || ''),
      artists: decodeHtml(
        Array.isArray(data.artists?.primary)
          ? data.artists.primary.map((a: any) => a.name).join(', ')
          : data.artists || ''
      ),
      image: extractImageUrl(data.image),
      songCount: data.songCount || songs.length,
      year: String(data.year || ''),
      songs,
    };
    return { album, songs };
  },

  async getPlaylist(id: string): Promise<{ playlist: Playlist; songs: Song[] }> {
    const json = await getJson('/api/playlists', { id, limit: '200' });
    const data = json?.data;
    if (!data) return { playlist: { id, name: 'Playlist', image: '' }, songs: [] };

    const songs = Array.isArray(data.songs) ? data.songs.map((s: any) => parseSong(s)) : [];
    const playlist: Playlist = {
      id: String(data.id || id),
      name: decodeHtml(data.name || ''),
      image: extractImageUrl(data.image),
      songCount: data.songCount || songs.length,
      followerCount: data.followerCount || '0',
      description: decodeHtml(data.description || ''),
      songs,
    };
    return { playlist, songs };
  },

  async getArtist(id: string): Promise<{ artist: Artist; songs: Song[]; albums: Album[] }> {
    const [artistRes, songsRes, albumsRes] = await Promise.all([
      getJson(`/api/artists/${id}`),
      getJson(`/api/artists/${id}/songs`),
      getJson(`/api/artists/${id}/albums`),
    ]);

    const artistData = artistRes?.data;
    const songs = Array.isArray(songsRes?.data?.songs) ? songsRes.data.songs.map((s: any) => parseSong(s)) : [];
    const albums = Array.isArray(albumsRes?.data?.albums)
      ? albumsRes.data.albums.map((a: any) => ({
          id: String(a.id || ''),
          name: decodeHtml(a.name || ''),
          image: extractImageUrl(a.image),
          year: String(a.year || ''),
          songCount: a.songCount || 0,
          artists: artistData?.name || '',
        }))
      : [];

    const artist: Artist = {
      id: String(artistData?.id || id),
      name: decodeHtml(artistData?.name || ''),
      image: extractImageUrl(artistData?.image),
      bio: decodeHtml(artistData?.bio?.[0]?.text || ''),
      followerCount: artistData?.followerCount || '0',
      topSongs: songs,
      topAlbums: albums,
    };

    return { artist, songs, albums };
  },

  async getRecommendations(id: string): Promise<Song[]> {
    const json = await getJson(`/api/songs/${id}/suggestions`);
    const results = Array.isArray(json?.data) ? json.data : [];
    return results.map((s: any) => parseSong(s));
  },
};
