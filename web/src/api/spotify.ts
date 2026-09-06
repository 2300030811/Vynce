import { Song, Playlist } from '../types/music';
import { DbPlaylist } from '../db';
import { JioSaavnApi } from './jiosaavn';
import { useLibraryStore } from '../stores/libraryStore';
import { usePlayerStore } from '../stores/playerStore';
import { useQueueStore } from '../stores/queueStore';

export interface SpotifyTrackInfo {
  name: string;
  artists: string;
  album: string;
  durationMs?: number;
  coverArt?: string;
  uri?: string;
}

export interface SpotifyPlaylistResult {
  id: string;
  name: string;
  description: string;
  coverArt: string;
  tracks: SpotifyTrackInfo[];
}

export type SpotifyCategoryType =
  | 'all'
  | 'charts'
  | 'mood'
  | 'focus'
  | 'party'
  | 'workout'
  | 'desi'
  | 'pop'
  | 'hiphop'
  | 'indie'
  | 'romance'
  | 'sleep';

export interface SpotifyCuratedPlaylist {
  id: string;
  name: string;
  subtitle: string;
  coverArt: string;
  category: SpotifyCategoryType;
  spotifyUrl: string;
  gradient: string;
}

export interface SpotifyCategoryMeta {
  id: SpotifyCategoryType;
  label: string;
  badge?: string;
  description: string;
}

export const SPOTIFY_CATEGORIES: SpotifyCategoryMeta[] = [
  { id: 'all', label: 'All Mixes', description: 'Curated music collections across all genres and moods' },
  { id: 'charts', label: 'Top Charts', badge: 'Hot', description: 'Global and regional top charts updated daily' },
  { id: 'desi', label: 'Desi & Bollywood', badge: 'Trending', description: 'Bollywood, Punjabi, Telugu, and Indian indie anthems' },
  { id: 'mood', label: 'Mood & Vibes', description: 'Feel-good, chill, and acoustic mood boosters' },
  { id: 'focus', label: 'Focus & Flow', description: 'Lo-fi, ambient, and peaceful piano for deep concentration' },
  { id: 'workout', label: 'Energy & Workout', description: 'High-BPM beats and motivational adrenaline' },
  { id: 'party', label: 'Dance & Club', description: 'High-energy electronic, techno, and dancefloor anthems' },
  { id: 'pop', label: 'Pop Anthems', description: 'Trending pop hits and chart-topping viral melodies' },
  { id: 'hiphop', label: 'Hip-Hop & Rap', description: 'Heavy basslines, classic 90s, and modern drill' },
  { id: 'indie', label: 'Indie & Alt', description: 'Fresh acoustic, bedroom pop, and alternative discoveries' },
  { id: 'romance', label: 'Acoustic & Romance', description: 'Soulful melodies, coffeehouse acoustics, and love songs' },
  { id: 'sleep', label: 'Ambient & Sleep', description: 'Gentle night rain, ambient frequencies, and peaceful rest' },
];

export const SPOTIFY_CURATED_PLAYLISTS: SpotifyCuratedPlaylist[] = [
  // ── CHARTS ──────────────────────────────────────────────────────────
  {
    id: '37i9dQZF1DXcBWIGoYBM5M',
    name: "Today's Top Hits",
    subtitle: 'The hottest tracks right now worldwide',
    coverArt: '',
    category: 'charts',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M',
    gradient: 'from-emerald-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZEVXbMDoHDwVN2tF',
    name: 'Top 50 - Global',
    subtitle: 'Daily update of the most played tracks worldwide',
    coverArt: '',
    category: 'charts',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZEVXbMDoHDwVN2tF',
    gradient: 'from-blue-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZEVXbLZ52XmnySJg',
    name: 'Top 50 - India',
    subtitle: 'Daily update of the most played tracks in India',
    coverArt: '',
    category: 'charts',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZEVXbLZ52XmnySJg',
    gradient: 'from-amber-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZEVXbLRQDuF5jeBp',
    name: 'Top 50 - USA',
    subtitle: 'Daily update of the most played tracks in the United States',
    coverArt: '',
    category: 'charts',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZEVXbLRQDuF5jeBp',
    gradient: 'from-cyan-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX4JAvHpjipBk',
    name: 'New Music Friday',
    subtitle: 'The best new releases of the week',
    coverArt: '',
    category: 'charts',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX4JAvHpjipBk',
    gradient: 'from-purple-950/80 via-neutral-950 to-black',
  },

  // ── DESI & BOLLYWOOD ────────────────────────────────────────────────
  {
    id: '37i9dQZF1DXdpQPPZq3F7n',
    name: 'Bollywood Mush',
    subtitle: 'Soulful Hindi melodies and heartwarming love anthems',
    coverArt: '',
    category: 'desi',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DXdpQPPZq3F7n',
    gradient: 'from-rose-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX5q67ZpWyRrZ',
    name: 'Indie India',
    subtitle: 'The finest independent artists and sounds across India',
    coverArt: '',
    category: 'desi',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX5q67ZpWyRrZ',
    gradient: 'from-teal-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX1lVhptIYRda',
    name: 'Hot Country & Acoustic',
    subtitle: 'Heartland acoustic anthems and contemporary sounds',
    coverArt: '',
    category: 'desi',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX1lVhptIYRda',
    gradient: 'from-orange-950/80 via-neutral-950 to-black',
  },

  // ── MOOD & VIBES ────────────────────────────────────────────────────
  {
    id: '37i9dQZF1DX4WYpdgoIcn6',
    name: 'Chill Hits',
    subtitle: 'Kick back to the best chill tunes and calm melodies',
    coverArt: '',
    category: 'mood',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX4WYpdgoIcn6',
    gradient: 'from-teal-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX3rxVfibe1L0',
    name: 'Mood Booster',
    subtitle: "Get happy with today's dose of feel-good anthems",
    coverArt: '',
    category: 'mood',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX3rxVfibe1L0',
    gradient: 'from-yellow-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX4sWSpwq3LiO',
    name: 'Peaceful Piano',
    subtitle: 'Relax and unwind with beautiful solo piano pieces',
    coverArt: '',
    category: 'mood',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX4sWSpwq3LiO',
    gradient: 'from-slate-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX7qK8ma5wgG1',
    name: 'Sad Songs',
    subtitle: 'Emotional acoustic and melancholic reflection',
    coverArt: '',
    category: 'mood',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX7qK8ma5wgG1',
    gradient: 'from-sky-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX3YSRoSdA634',
    name: 'Life Sucks',
    subtitle: 'When everything is too much, this is for you',
    coverArt: '',
    category: 'mood',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX3YSRoSdA634',
    gradient: 'from-zinc-950/80 via-neutral-950 to-black',
  },

  // ── FOCUS & FLOW ────────────────────────────────────────────────────
  {
    id: '37i9dQZF1DWZeKCadgRdKQ',
    name: 'Deep Focus',
    subtitle: 'Ambient electronic study beats for high productivity',
    coverArt: '',
    category: 'focus',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DWZeKCadgRdKQ',
    gradient: 'from-indigo-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DWWQRwui0ExPn',
    name: 'Lo-Fi Beats',
    subtitle: 'Warm analog beats to relax, code, and study to',
    coverArt: '',
    category: 'focus',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DWWQRwui0ExPn',
    gradient: 'from-purple-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX8Uebhn9wzrS',
    name: 'Chill Lo-Fi Study',
    subtitle: 'Soft instrumental lo-fi background beats',
    coverArt: '',
    category: 'focus',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX8Uebhn9wzrS',
    gradient: 'from-emerald-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DXdLEN7aqioXM',
    name: 'Retrowave Flow',
    subtitle: 'Retro synthwave and dreamwave drive',
    coverArt: '',
    category: 'focus',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DXdLEN7aqioXM',
    gradient: 'from-fuchsia-950/80 via-neutral-950 to-black',
  },

  // ── ENERGY & WORKOUT ────────────────────────────────────────────────
  {
    id: '37i9dQZF1DX76Wlfdnj7AP',
    name: 'Beast Mode',
    subtitle: 'Pure adrenaline and heavy bass for max performance',
    coverArt: '',
    category: 'workout',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX76Wlfdnj7AP',
    gradient: 'from-red-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX70RN3TfWWJh',
    name: 'Workout Motivation',
    subtitle: 'Upbeat pop, hip-hop, and EDM fitness fuel',
    coverArt: '',
    category: 'workout',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX70RN3TfWWJh',
    gradient: 'from-orange-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX4eRPd9frC1m',
    name: 'Pure Hype',
    subtitle: 'Unstoppable high-energy bangers',
    coverArt: '',
    category: 'workout',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX4eRPd9frC1m',
    gradient: 'from-amber-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX32NsLKyzScr',
    name: 'Power Hour',
    subtitle: 'Fast-paced rhythmic motivation',
    coverArt: '',
    category: 'workout',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX32NsLKyzScr',
    gradient: 'from-rose-950/80 via-neutral-950 to-black',
  },

  // ── DANCE & CLUB ────────────────────────────────────────────────────
  {
    id: '37i9dQZF1DX0BcQWzuB7ZO',
    name: 'Dance Hits',
    subtitle: 'Club anthems and peak-hour electronic beats',
    coverArt: '',
    category: 'party',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX0BcQWzuB7ZO',
    gradient: 'from-violet-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX6J5NfMJS675',
    name: 'Techno Bunker',
    subtitle: 'Deep underground techno and dark industrial grooves',
    coverArt: '',
    category: 'party',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX6J5NfMJS675',
    gradient: 'from-zinc-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DXaXB8fQg7xif',
    name: 'Dance Party',
    subtitle: 'Festival anthems and high-energy club tracks',
    coverArt: '',
    category: 'party',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DXaXB8fQg7xif',
    gradient: 'from-pink-950/80 via-neutral-950 to-black',
  },

  // ── POP ANTHEMS ─────────────────────────────────────────────────────
  {
    id: '37i9dQZF1DWUa8ZRTfalHk',
    name: 'Pop Rising',
    subtitle: 'The freshest pop anthems and viral hits',
    coverArt: '',
    category: 'pop',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DWUa8ZRTfalHk',
    gradient: 'from-rose-950/80 via-neutral-950 to-black',
  },

  // ── HIP-HOP & RAP ───────────────────────────────────────────────────
  {
    id: '37i9dQZF1DX0XUsuxWHRQd',
    name: 'RapCaviar',
    subtitle: 'Heavy-hitting hip-hop and trap chartbusters',
    coverArt: '',
    category: 'hiphop',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX0XUsuxWHRQd',
    gradient: 'from-neutral-950/80 via-zinc-950 to-black',
  },
  {
    id: '37i9dQZF1DWY4xHQp97fN6',
    name: 'Get Turnt',
    subtitle: 'High energy trap, drill, and heavy bars',
    coverArt: '',
    category: 'hiphop',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DWY4xHQp97fN6',
    gradient: 'from-stone-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX186v583rmzp',
    name: "90s Hip-Hop Anthems",
    subtitle: 'Golden era hip-hop classics and boom bap beats',
    coverArt: '',
    category: 'hiphop',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX186v583rmzp',
    gradient: 'from-yellow-950/80 via-neutral-950 to-black',
  },

  // ── INDIE & ALT ─────────────────────────────────────────────────────
  {
    id: '37i9dQZF1DX2Nc3B70tvx0',
    name: "Indie's Top 50",
    subtitle: 'The definitive sound of modern indie and alternative',
    coverArt: '',
    category: 'indie',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX2Nc3B70tvx0',
    gradient: 'from-blue-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DXdbXrPNafg9d',
    name: 'All New Indie',
    subtitle: 'Essential new indie rock, bedroom pop, and post-punk',
    coverArt: '',
    category: 'indie',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DXdbXrPNafg9d',
    gradient: 'from-teal-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DWWEcRhUVtL8n',
    name: 'Indie Pop',
    subtitle: 'Bright hooks, jangly guitars, and catchy melodies',
    coverArt: '',
    category: 'indie',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DWWEcRhUVtL8n',
    gradient: 'from-purple-950/80 via-neutral-950 to-black',
  },

  // ── ACOUSTIC & ROMANCE ──────────────────────────────────────────────
  {
    id: '37i9dQZF1DX4E3UdUs7fUx',
    name: 'Afternoon Acoustic',
    subtitle: 'Warm acoustic guitars, gentle vocals, and mellow vibes',
    coverArt: '',
    category: 'romance',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX4E3UdUs7fUx',
    gradient: 'from-amber-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX6ziVCJnEm59',
    name: 'Your Favorite Coffeehouse',
    subtitle: 'Catch up or slow down with acoustic singer-songwriters',
    coverArt: '',
    category: 'romance',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX6ziVCJnEm59',
    gradient: 'from-stone-950/80 via-neutral-950 to-black',
  },

  // ── AMBIENT & SLEEP ─────────────────────────────────────────────────
  {
    id: '37i9dQZF1DXbcPC6Vvqudd',
    name: 'Night Rain',
    subtitle: 'The soothing, restful sound of steady rainfall',
    coverArt: '',
    category: 'sleep',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DXbcPC6Vvqudd',
    gradient: 'from-sky-950/80 via-neutral-950 to-black',
  },
  {
    id: '37i9dQZF1DX3Ogo9pFvBkY',
    name: 'Ambient Relaxation',
    subtitle: 'Gentle soundscapes and calm textures for relaxation',
    coverArt: '',
    category: 'sleep',
    spotifyUrl: 'https://open.spotify.com/playlist/37i9dQZF1DX3Ogo9pFvBkY',
    gradient: 'from-slate-950/80 via-neutral-950 to-black',
  },
];

/**
 * Vynce Spotify Matching Engine
 * Uses string normalization, character bigram Sørensen–Dice similarity,
 * and duration tolerance scoring to find the best audio match.
 */
export class SpotifyMapper {
  private static readonly FEAT_PATTERN = /\(feat\..*?\)/gi;
  private static readonly FT_PATTERN = /\(ft\..*?\)/gi;
  private static readonly BRACKET_PATTERN = /\[.*?\]/gi;
  private static readonly REMASTER_PATTERN = /\(.*?\bremaster.*?\)/gi;
  private static readonly REMIX_PATTERN = /\(.*?\bremix.*?\)/gi;
  private static readonly NON_ALNUM_PATTERN = /[^a-z0-9\s]/gi;
  private static readonly MULTI_SPACE_PATTERN = /\s+/g;

  public static normalize(str: string): string {
    if (!str) return '';
    return str
      .toLowerCase()
      .replace(this.FEAT_PATTERN, '')
      .replace(this.FT_PATTERN, '')
      .replace(this.BRACKET_PATTERN, '')
      .replace(this.REMASTER_PATTERN, '')
      .replace(this.REMIX_PATTERN, '')
      .replace(this.NON_ALNUM_PATTERN, '')
      .replace(this.MULTI_SPACE_PATTERN, ' ')
      .trim();
  }

  public static getBigrams(str: string): Set<string> {
    const s = this.normalize(str);
    const bigrams = new Set<string>();
    if (s.length < 2) return bigrams;
    for (let i = 0; i < s.length - 1; i++) {
      bigrams.add(s.slice(i, i + 2));
    }
    return bigrams;
  }

  public static bigramSimilarity(a: string, b: string): number {
    const normA = this.normalize(a);
    const normB = this.normalize(b);
    if (normA === normB && normA.length > 0) return 1.0;
    const bigramsA = this.getBigrams(normA);
    const bigramsB = this.getBigrams(normB);
    if (bigramsA.size === 0 || bigramsB.size === 0) return 0.0;

    let intersection = 0;
    for (const bg of bigramsA) {
      if (bigramsB.has(bg)) intersection++;
    }
    return (2.0 * intersection) / (bigramsA.size + bigramsB.size);
  }

  public static durationScore(spotifyDurationMs?: number, candidateDurationSec?: number): number {
    if (!spotifyDurationMs || !candidateDurationSec || spotifyDurationMs <= 0 || candidateDurationSec <= 0) {
      return 0.5;
    }
    const spotifySec = Math.floor(spotifyDurationMs / 1000);
    const diff = Math.abs(spotifySec - candidateDurationSec);
    if (diff <= 2) return 1.0;
    if (diff <= 5) return 0.8;
    if (diff <= 10) return 0.5;
    if (diff <= 30) return 0.2;
    return 0.0;
  }

  public static matchScore(
    spotifyTrack: { name: string; artists: string; durationMs?: number },
    candidate: Song
  ): number {
    const candidateDurationSec = typeof candidate.duration === 'number'
      ? candidate.duration
      : parseInt(String(candidate.duration || 0), 10);

    const titleScore = this.bigramSimilarity(spotifyTrack.name, candidate.name);
    const artistScore = this.bigramSimilarity(spotifyTrack.artists, candidate.primaryArtists || '');
    const durScore = this.durationScore(spotifyTrack.durationMs, candidateDurationSec);

    return titleScore * 0.45 + artistScore * 0.35 + durScore * 0.20;
  }

  public static findBestMatch(
    spotifyTrack: { name: string; artists: string; durationMs?: number },
    candidates: Song[]
  ): Song | null {
    if (!candidates || candidates.length === 0) return null;

    let bestCandidate: Song | null = null;
    let bestScore = -1;

    for (const candidate of candidates) {
      const score = this.matchScore(spotifyTrack, candidate);
      if (score > bestScore) {
        bestScore = score;
        bestCandidate = candidate;
        if (score >= 0.92) {
          break; // Early exit on excellent match
        }
      }
    }

    // Minimum acceptable threshold
    return bestScore >= 0.35 ? bestCandidate : candidates[0] || null;
  }
}

export class SpotifyImporter {
  private static playlistCache = new Map<string, SpotifyPlaylistResult>();

  /**
   * Extracts playlist ID from Spotify URL or raw ID
   */
  public static extractPlaylistId(input: string): string | null {
    if (!input) return null;
    const trimmed = input.trim();

    const urlMatch = trimmed.match(/playlist\/([a-zA-Z0-9]+)/);
    if (urlMatch) return urlMatch[1];

    const uriMatch = trimmed.match(/spotify:playlist:([a-zA-Z0-9]+)/);
    if (uriMatch) return uriMatch[1];

    if (/^[a-zA-Z0-9]{15,30}$/.test(trimmed)) {
      return trimmed;
    }

    return null;
  }

  /**
   * Extracts track ID from Spotify URL or raw ID
   */
  public static extractTrackId(input: string): string | null {
    if (!input) return null;
    const trimmed = input.trim();
    const urlMatch = trimmed.match(/track\/([a-zA-Z0-9]+)/);
    if (urlMatch) return urlMatch[1];
    const uriMatch = trimmed.match(/spotify:track:([a-zA-Z0-9]+)/);
    if (uriMatch) return uriMatch[1];
    return null;
  }

  /**
   * Fetches playlist metadata and tracklist using Spotify Embed with cache
   */
  public static async fetchSpotifyPlaylist(playlistId: string): Promise<SpotifyPlaylistResult | null> {
    if (!playlistId) return null;
    if (this.playlistCache.has(playlistId)) {
      return this.playlistCache.get(playlistId)!;
    }

    try {
      // ponytail: proxy through Vite dev server to bypass CORS in web/desktop environment
      const embedRes = await fetch(`/spotify-embed/playlist/${playlistId}`);
      if (embedRes.ok) {
        const html = await embedRes.text();
        const nextDataMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([^<]+)<\/script>/);
        if (nextDataMatch) {
          const nextData = JSON.parse(nextDataMatch[1]);
          const entity = nextData?.props?.pageProps?.state?.data?.entity;
          if (entity) {
            const trackList: SpotifyTrackInfo[] = (entity.trackList || []).map((t: any) => ({
              name: t.title || t.name || '',
              artists: t.subtitle || (Array.isArray(t.artists) ? t.artists.map((a: any) => a.name).join(', ') : ''),
              album: t.album?.name || '',
              durationMs: t.duration || 0,
              coverArt: t.album?.images?.[0]?.url || t.images?.[0]?.url || t.coverArt?.sources?.[0]?.url || '',
              uri: t.uri || '',
            }));

            const result: SpotifyPlaylistResult = {
              id: entity.id || playlistId,
              name: entity.name || entity.title || 'Curated Playlist',
              description: entity.description || '',
              coverArt: entity.coverArt?.sources?.[0]?.url || entity.visualIdentity?.image?.[0]?.url || '',
              tracks: trackList,
            };

            this.playlistCache.set(playlistId, result);
            return result;
          }
        }
      }
    } catch {
      // Fallback
    }

    return null;
  }

  /**
   * Loads fresh metadata for curated playlists with optional category filter
   */
  public static async loadCuratedPlaylists(category?: SpotifyCategoryType): Promise<SpotifyCuratedPlaylist[]> {
    const list = category && category !== 'all'
      ? SPOTIFY_CURATED_PLAYLISTS.filter((p) => p.category === category)
      : [...SPOTIFY_CURATED_PLAYLISTS];

    // Hydrate in parallel (cached results resolve instantly)
    await Promise.allSettled(
      list.map(async (item) => {
        try {
          const pl = await this.fetchSpotifyPlaylist(item.id);
          if (pl) {
            if (pl.coverArt) item.coverArt = pl.coverArt;
            if (pl.name) item.name = pl.name;
            if (pl.description && pl.description.trim() && !pl.description.toLowerCase().includes('spotify')) {
              item.subtitle = pl.description;
            }
          }
        } catch {}
      })
    );
    return list;
  }

  /**
   * Returns curated playlists grouped by category
   */
  public static getPlaylistsGroupedByCategory(): Record<SpotifyCategoryType, SpotifyCuratedPlaylist[]> {
    const groups: Partial<Record<SpotifyCategoryType, SpotifyCuratedPlaylist[]>> = {};
    for (const p of SPOTIFY_CURATED_PLAYLISTS) {
      if (!groups[p.category]) groups[p.category] = [];
      groups[p.category]!.push(p);
    }
    return groups as Record<SpotifyCategoryType, SpotifyCuratedPlaylist[]>;
  }

  /**
   * Loads a Spotify playlist and converts it into a Vynce Playlist + Song[] ready for PlaylistPage
   */
  public static async fetchSpotifyPlaylistAsVynce(
    playlistId: string
  ): Promise<{ playlist: Playlist; songs: Song[] } | null> {
    const cleanId = this.extractPlaylistId(playlistId) || playlistId;
    const spotifyData = await this.fetchSpotifyPlaylist(cleanId);
    if (!spotifyData) return null;

    const songs: Song[] = spotifyData.tracks.map((t, idx) => ({
      id: `sp_${cleanId}_${idx}_${encodeURIComponent(t.name.slice(0, 30))}`,
      name: t.name,
      primaryArtists: t.artists,
      album: typeof t.album === 'string' ? t.album : (t.album as any)?.name || '',
      duration: t.durationMs ? Math.round(t.durationMs / 1000) : 180,
      image: t.coverArt || '',
      downloadUrls: [],
      // Mark as unresolved Spotify track so player can resolve on demand if clicked
      spotifyTrackQuery: `${t.name} ${t.artists}`.trim(),
    } as any));

    const playlist: Playlist = {
      id: cleanId,
      name: spotifyData.name,
      image: spotifyData.coverArt || '',
      description: spotifyData.description,
      songCount: spotifyData.tracks.length,
      songs,
    };

    return { playlist, songs };
  }

  /**
   * Imports a Spotify playlist into Vynce by resolving each track on JioSaavn using SpotifyMapper scoring
   */
  public static async importPlaylistToVynce(
    spotifyUrlOrId: string,
    onProgress?: (current: number, total: number, songName: string) => void
  ): Promise<{ playlist: DbPlaylist; matchedCount: number } | null> {
    const playlistId = this.extractPlaylistId(spotifyUrlOrId);
    if (!playlistId) return null;

    const spotifyData = await this.fetchSpotifyPlaylist(playlistId);
    if (!spotifyData || spotifyData.tracks.length === 0) return null;

    const libraryStore = useLibraryStore.getState();
    const newPlaylistId = await libraryStore.createPlaylist(
      spotifyData.name,
      spotifyData.description
    );

    let matchedCount = 0;
    const total = spotifyData.tracks.length;

    for (let i = 0; i < spotifyData.tracks.length; i++) {
      const track = spotifyData.tracks[i];
      onProgress?.(i + 1, total, `${track.name} - ${track.artists}`);

      try {
        const query = `${track.name} ${track.artists}`.trim();
        const candidates = await JioSaavnApi.searchSongs(query, 5);
        if (candidates && candidates.length > 0) {
          const bestMatch = SpotifyMapper.findBestMatch(track, candidates);
          if (bestMatch) {
            await libraryStore.addSongToPlaylist(newPlaylistId, bestMatch);
            matchedCount++;
          }
        }
      } catch {
        // Continue to next track
      }
    }

    const createdPlaylist = libraryStore.playlists.find((p) => p.id === newPlaylistId) || {
      id: newPlaylistId,
      name: spotifyData.name,
      description: spotifyData.description,
      createdAt: Date.now(),
      coverArt: spotifyData.coverArt || '',
    };

    return {
      playlist: createdPlaylist,
      matchedCount,
    };
  }

  /**
   * Fetches metadata for a single Spotify Track
   */
  public static async fetchSpotifyTrack(trackId: string): Promise<SpotifyTrackInfo | null> {
    try {
      const embedRes = await fetch(`/spotify-embed/track/${trackId}`);
      if (embedRes.ok) {
        const html = await embedRes.text();
        const nextDataMatch = html.match(/<script id="__NEXT_DATA__" type="application\/json">([^<]+)<\/script>/);
        if (nextDataMatch) {
          const nextData = JSON.parse(nextDataMatch[1]);
          const entity = nextData?.props?.pageProps?.state?.data?.entity;
          if (entity) {
            return {
              name: entity.name || entity.title || '',
              artists: (entity.artists || []).map((a: any) => a.name).join(', ') || entity.subtitle || '',
              album: entity.album?.name || '',
              durationMs: entity.duration || 0,
              coverArt: entity.coverArt?.sources?.[0]?.url || '',
              uri: entity.uri || `spotify:track:${trackId}`,
            };
          }
        }
      }
    } catch {}
    return null;
  }

  /**
   * Resolves a Spotify track into a playable JioSaavn Song using SpotifyMapper match score
   */
  public static async resolveSpotifyTrack(trackUrlOrId: string): Promise<Song | null> {
    const trackId = this.extractTrackId(trackUrlOrId);
    if (!trackId) return null;

    const track = await this.fetchSpotifyTrack(trackId);
    if (!track) return null;

    const query = `${track.name} ${track.artists}`.trim();
    const candidates = await JioSaavnApi.searchSongs(query, 5);
    return SpotifyMapper.findBestMatch(track, candidates);
  }

  /**
   * Plays a Spotify playlist directly in Vynce with fast instant playback and background stream resolution
   */
  public static async playSpotifyPlaylistDirectly(
    playlistId: string,
    onStatus?: (msg: string) => void
  ): Promise<boolean> {
    onStatus?.('Connecting to Spotify...');
    const data = await this.fetchSpotifyPlaylist(playlistId);
    if (!data || data.tracks.length === 0) return false;

    onStatus?.(`Found ${data.tracks.length} tracks. Resolving 320kbps streams...`);
    const playerStore = usePlayerStore.getState();

    // 1. Resolve first track quickly so user gets immediate playback
    let firstSong: Song | null = null;
    const initialBatch: Song[] = [];

    for (let i = 0; i < Math.min(3, data.tracks.length); i++) {
      const t = data.tracks[i];
      try {
        const candidates = await JioSaavnApi.searchSongs(`${t.name} ${t.artists}`.trim(), 3);
        const best = SpotifyMapper.findBestMatch(t, candidates);
        if (best) {
          if (!firstSong) {
            firstSong = best;
            await playerStore.playSong(firstSong, [firstSong], 0);
          }
          initialBatch.push(best);
        }
      } catch {}
    }

    if (!firstSong) return false;

    // Set initial queue
    useQueueStore.getState().setQueue(initialBatch, 0);

    // 2. Resolve remaining tracks in background batches
    const remaining = data.tracks.slice(3);
    (async () => {
      const queueStore = useQueueStore.getState();
      const batchSize = 4;
      for (let i = 0; i < remaining.length; i += batchSize) {
        const slice = remaining.slice(i, i + batchSize);
        const batchResults = await Promise.allSettled(
          slice.map(async (t) => {
            const candidates = await JioSaavnApi.searchSongs(`${t.name} ${t.artists}`.trim(), 3);
            return SpotifyMapper.findBestMatch(t, candidates);
          })
        );
        const newTracks: Song[] = [];
        for (const r of batchResults) {
          if (r.status === 'fulfilled' && r.value) {
            newTracks.push(r.value);
          }
        }
        if (newTracks.length > 0) {
          queueStore.addToQueue(newTracks);
        }
      }
    })();

    return true;
  }
}
