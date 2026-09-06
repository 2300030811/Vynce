import { LyricData, LyricLine, Song } from '../types/music';
import { db } from '../db';

const LRC_REGEX = /\[(\d{1,2}):(\d{2}(?:\.\d{1,3})?)\](.*)/;
const WORD_TIMING_REGEX = /<(\d{1,2}):(\d{2}(?:\.\d{1,3})?)>/g;

const USER_AGENT = 'Vynce-Music-Player/1.0.0 (https://github.com/2300030811/Vynce)';

export function decodeHtml(html: string): string {
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

/**
 * Normalizes title by removing suffixes, brackets, trending labels, movie tags etc.
 */
export function cleanSongTitle(title: string): string {
  if (!title) return '';
  let clean = decodeHtml(title);

  // Remove common bracketed metadata
  clean = clean
    .replace(/\s*[\(\[](?:from|feat|ft\.|with|official|lyric video|video|audio|remastered|remix|lofi|slowed|reverb|deluxe|bonus track|trending version|trending|version|original motion picture soundtrack|soundtrack|full song|live|hd)[^\)\]]*[\)\]]/gi, '')
    .replace(/\s*-\s*(?:remastered|remix|bonus track|from [^-\n]+|trending version|trending|lofi|slowed|speed up|reverb|version|original|official).*/gi, '')
    .replace(/\s*trending version\s*/gi, '')
    .replace(/\s*trending\s*/gi, '')
    .replace(/\s*\(from [^\)]+\)/gi, '')
    .replace(/\s*\[from [^\]]+\]/gi, '')
    .replace(/\s*from "[^"]+"/gi, '')
    .replace(/\s*from '[^']+'/gi, '')
    .trim();

  // Strip trailing punctuation
  clean = clean.replace(/[-–—:|~]+$/, '').trim();

  return clean || decodeHtml(title).trim();
}

/**
 * Extracts list of clean individual artist names
 */
export function extractArtistNames(artistStr: string): string[] {
  if (!artistStr) return [];
  const decoded = decodeHtml(artistStr);
  return decoded
    .split(/[,&/|]/)
    .map((a) =>
      a
        .replace(/\s*feat\.?.*$/i, '')
        .replace(/\s*ft\.?.*$/i, '')
        .trim()
    )
    .filter(Boolean);
}

export function cleanArtistName(artist: string): string {
  const list = extractArtistNames(artist);
  return list[0] || '';
}

export function parseLrc(lrcText: string): LyricData {
  if (!lrcText) {
    return { synced: false, lines: [] };
  }

  const lines = lrcText.split('\n');
  const parsedLines: LyricLine[] = [];

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed) continue;

    // Ignore header tags like [ti:Title], [ar:Artist], [al:Album], [length:03:45]
    if (/^\[[a-z]{2,8}:/i.test(trimmed)) continue;

    const match = LRC_REGEX.exec(trimmed);
    if (match) {
      const minutes = parseInt(match[1], 10);
      const seconds = parseFloat(match[2]);
      const time = minutes * 60 + seconds;
      // Strip word-by-word timestamps like <00:15.20> to get readable line text
      const cleanText = decodeHtml(match[3].replace(WORD_TIMING_REGEX, '').trim());
      if (cleanText) {
        parsedLines.push({ time, text: cleanText });
      }
    }
  }

  if (parsedLines.length > 0) {
    parsedLines.sort((a, b) => a.time - b.time);
    return {
      synced: true,
      lines: parsedLines,
      plainLyrics: parsedLines.map((l) => l.text).join('\n'),
    };
  }

  // Fallback plain text if no timestamps
  const plainTextLines = lines
    .map((text, idx) => ({ time: idx * 4, text: decodeHtml(text.trim()) }))
    .filter((l) => l.text);

  return {
    synced: false,
    lines: plainTextLines,
    plainLyrics: decodeHtml(lrcText),
  };
}

/**
 * Parses Apple Music TTML (Timed Text Markup Language) XML into synchronized LyricData
 */
export function parseTTML(ttmlText: string): LyricData {
  if (!ttmlText) {
    return { synced: false, lines: [] };
  }

  const parsedLines: LyricLine[] = [];
  const pRegex = /<p\b[^>]*\bbegin=["']([^"']+)["'][^>]*>(.*?)<\/p>/gis;
  let match: RegExpExecArray | null;

  while ((match = pRegex.exec(ttmlText)) !== null) {
    const beginStr = match[1];
    const rawInner = match[2];

    let time = 0;
    if (beginStr.includes(':')) {
      const parts = beginStr.split(':');
      if (parts.length === 2) {
        time = parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
      } else if (parts.length === 3) {
        time = parseFloat(parts[0]) * 3600 + parseFloat(parts[1]) * 60 + parseFloat(parts[2]);
      }
    } else {
      time = parseFloat(beginStr.replace(/s$/i, ''));
    }

    const cleanText = decodeHtml(rawInner.replace(/<[^>]+>/g, '').trim());
    if (cleanText && !isNaN(time)) {
      parsedLines.push({ time, text: cleanText });
    }
  }

  if (parsedLines.length > 0) {
    parsedLines.sort((a, b) => a.time - b.time);
    return {
      synced: true,
      lines: parsedLines,
      plainLyrics: parsedLines.map((l) => l.text).join('\n'),
    };
  }

  return { synced: false, lines: [] };
}

export const LyricsApi = {
  /**
   * Fetches lyrics with comprehensive multi-tier fallback:
   * 1. Local IndexedDB cache
   * 2. LRCLIB exact match
   * 3. LRCLIB search (Clean Title + Primary Artist)
   * 4. LRCLIB search with each collaborator/singer individual name
   * 5. LRCLIB search by Clean Title alone (with duration scoring)
   * 6. BetterLyrics / TTML API provider (Apple Music & Kugou endpoints)
   * 7. JioSaavn direct lyrics endpoint
   * 8. KuGou native lyrics API fallback
   */
  async getLyrics(
    trackName: string,
    artistName: string,
    albumName?: string,
    duration?: number,
    songId?: string
  ): Promise<LyricData | null> {
    // 1. Check local Dexie cache first
    if (songId) {
      try {
        const cached = await db.lyrics.get(songId);
        if (cached && cached.lines && cached.lines.length > 0) {
          return {
            synced: cached.synced,
            lines: cached.lines,
            plainLyrics: cached.plainLyrics,
          };
        }
      } catch {}
    }

    const cleanTitle = cleanSongTitle(trackName);
    const artistsList = extractArtistNames(artistName);
    const primaryArtist = artistsList[0] || cleanArtistName(artistName);

    const headers: HeadersInit = {
      Accept: 'application/json',
      'User-Agent': USER_AGENT,
    };

    // Helper to evaluate LRCLIB search results
    const evaluateResults = (list: any[]): LyricData | null => {
      if (!Array.isArray(list) || list.length === 0) return null;

      let best = list.find((item: any) => item.syncedLyrics);
      if (duration && duration > 0) {
        const durationMatches = list.filter(
          (item: any) => item.syncedLyrics && Math.abs(item.duration - duration) < 6
        );
        if (durationMatches.length > 0) {
          best = durationMatches[0];
        }
      }

      best = best || list[0];
      if (best?.syncedLyrics) {
        const parsed = parseLrc(best.syncedLyrics);
        if (songId) this.cacheLyrics(songId, parsed, best.syncedLyrics);
        return parsed;
      }
      if (best?.plainLyrics) {
        const parsed = { synced: false, lines: [], plainLyrics: decodeHtml(best.plainLyrics) };
        if (songId) this.cacheLyrics(songId, parsed, best.plainLyrics);
        return parsed;
      }
      return null;
    };

    // 2. Search-First Strategy: LRCLIB search with (Clean Title + Primary Artist)
    if (primaryArtist) {
      try {
        const query = encodeURIComponent(`${cleanTitle} ${primaryArtist}`.trim());
        const searchRes = await fetch(`https://lrclib.net/api/search?q=${query}`, { headers });
        if (searchRes.ok) {
          const list = await searchRes.json();
          const parsed = evaluateResults(list);
          if (parsed) return parsed;
        }
      } catch {}
    }

    // 3. LRCLIB search with other collaborator artists (e.g. KR$NA, Amit Trivedi, etc.)
    for (let i = 1; i < artistsList.length; i++) {
      const art = artistsList[i];
      try {
        const query = encodeURIComponent(`${cleanTitle} ${art}`.trim());
        const searchRes = await fetch(`https://lrclib.net/api/search?q=${query}`, { headers });
        if (searchRes.ok) {
          const list = await searchRes.json();
          const parsed = evaluateResults(list);
          if (parsed) return parsed;
        }
      } catch {}
    }

    // 4. LRCLIB search with Clean Title alone (matches tracks with unconventional artist metadata)
    try {
      const query = encodeURIComponent(cleanTitle);
      const searchRes = await fetch(`https://lrclib.net/api/search?q=${query}`, { headers });
      if (searchRes.ok) {
        const list = await searchRes.json();
        const parsed = evaluateResults(list);
        if (parsed) return parsed;
      }
    } catch {}

    // 5. Try LRCLIB exact match if search didn't resolve
    try {
      const params = new URLSearchParams({
        track_name: cleanTitle,
        artist_name: primaryArtist,
      });
      if (albumName) params.append('album_name', cleanSongTitle(albumName));
      if (duration && duration > 0) params.append('duration', String(Math.round(duration)));

      const exactRes = await fetch(`https://lrclib.net/api/get?${params.toString()}`, { headers });
      if (exactRes.ok) {
        const data = await exactRes.json();
        if (data.syncedLyrics) {
          const parsed = parseLrc(data.syncedLyrics);
          if (songId) this.cacheLyrics(songId, parsed, data.syncedLyrics);
          return parsed;
        }
        if (data.plainLyrics) {
          const parsed = { synced: false, lines: [], plainLyrics: decodeHtml(data.plainLyrics) };
          if (songId) this.cacheLyrics(songId, parsed, data.plainLyrics);
          return parsed;
        }
      }
    } catch {}

    // 6. Try BetterLyrics (Apple Music TTML / Kugou / Boidu multi-source provider)
    try {
      const boiduEndpoints = [
        `https://lyrics-api.boidu.dev/getLyrics?s=${encodeURIComponent(cleanTitle)}&a=${encodeURIComponent(primaryArtist)}`,
        `https://lyrics-api.boidu.dev/kugou/getLyrics?s=${encodeURIComponent(cleanTitle)}&a=${encodeURIComponent(primaryArtist)}`,
      ];

      for (const endpoint of boiduEndpoints) {
        try {
          const res = await fetch(endpoint);
          if (res.ok) {
            const data = await res.json();
            const rawLyrics = data?.lyrics || data?.data || data?.ttml;
            if (rawLyrics && typeof rawLyrics === 'string') {
              let parsed: LyricData | null = null;
              if (rawLyrics.includes('<tt') || rawLyrics.includes('<p begin=')) {
                parsed = parseTTML(rawLyrics);
              } else if (rawLyrics.includes('[00:') || rawLyrics.includes('[01:')) {
                parsed = parseLrc(rawLyrics);
              }
              if (parsed && parsed.lines.length > 0) {
                if (songId) this.cacheLyrics(songId, parsed, rawLyrics);
                return parsed;
              }
            }
          }
        } catch {}
      }
    } catch {}

    // 7. Try JioSaavn native lyrics (direct or proxied)
    if (songId) {
      try {
        const saavnUrls = [
          `/jiosaavn-direct/api.php?__call=lyrics.getLyrics&lyrics_id=${songId}&ctx=web6dot0&api_version=4&_format=json`,
          `https://my-repo-nine-phi.vercel.app/api/songs/${songId}`,
        ];

        for (const sUrl of saavnUrls) {
          try {
            const sRes = await fetch(sUrl);
            if (sRes.ok) {
              const sData = await sRes.json();
              const rawLyrics = sData.lyrics || sData?.data?.[0]?.lyrics || sData?.data?.lyrics;
              if (rawLyrics) {
                const plain = decodeHtml(String(rawLyrics).replace(/<br\s*[\/]?>/gi, '\n'));
                const parsed = { synced: false, lines: [], plainLyrics: plain };
                this.cacheLyrics(songId, parsed, plain);
                return parsed;
              }
            }
          } catch {}
        }
      } catch {}
    }

    // 7. Try KuGou lyrics API fallback
    try {
      const kgQuery = encodeURIComponent(`${cleanTitle} ${primaryArtist}`.trim());
      const kgRes = await fetch(
        `https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${kgQuery}`
      );
      if (kgRes.ok) {
        const kgData = await kgRes.json();
        const candidates = kgData?.candidates || [];
        if (candidates.length > 0) {
          const candidate = candidates[0];
          const dlRes = await fetch(
            `https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id=${candidate.id}&accesskey=${candidate.accesskey}`
          );
          if (dlRes.ok) {
            const dlData = await dlRes.json();
            if (dlData.content) {
              const decoded = atob(dlData.content);
              const parsed = parseLrc(decoded);
              if (parsed.lines.length > 0) {
                if (songId) this.cacheLyrics(songId, parsed, decoded);
                return parsed;
              }
            }
          }
        }
      }
    } catch {}

    return null;
  },

  /**
   * Search for alternative lyrics on LRCLIB manually
   */
  async searchLyrics(
    query: string
  ): Promise<
    Array<{
      id: number;
      trackName: string;
      artistName: string;
      albumName?: string;
      syncedLyrics?: string;
      plainLyrics?: string;
    }>
  > {
    try {
      const cleanQ = cleanSongTitle(query);
      const res = await fetch(`https://lrclib.net/api/search?q=${encodeURIComponent(cleanQ || query)}`, {
        headers: {
          Accept: 'application/json',
          'User-Agent': USER_AGENT,
        },
      });
      if (!res.ok) return [];
      const list = await res.json();
      return Array.isArray(list) ? list : [];
    } catch {
      return [];
    }
  },

  /**
   * Save lyrics to local IndexedDB
   */
  async cacheLyrics(songId: string, lyricData: LyricData, rawLrc?: string) {
    try {
      await db.lyrics.put({
        songId,
        synced: lyricData.synced,
        rawLrc,
        plainLyrics: lyricData.plainLyrics,
        lines: lyricData.lines,
        fetchedAt: Date.now(),
      });
    } catch {}
  },
};

