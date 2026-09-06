/**
 * Lyrics Translation Coordinator
 * 
 * Handles:
 * 1. Structural LRC timestamp isolation & reattachment
 * 2. Two-tier caching (Client-side Dexie IndexedDB + Server-side persistent cache)
 * 3. In-flight single-flight request deduplication
 * 4. Provider abstraction with IndicTrans2 as primary backend
 * 5. Graceful fallback on network/translation failures
 */

import { LyricLine, LyricsTranslation, TranslationMode, TranslationProvenance } from '../types/music';
import { db } from '../db';

export interface TranslationRequest {
  songId: string;
  lines: LyricLine[];
  sourceLanguage: string;
  targetLanguage: string;
  mode?: TranslationMode;
}

export interface TranslationResponse {
  translatedLines: LyricLine[];
  sourceLanguage: string;
  targetLanguage: string;
  mode: TranslationMode;
  isCached: boolean;
  provenance: TranslationProvenance;
}

// In-flight single-flight deduplication cache
const inFlightRequests = new Map<string, Promise<TranslationResponse>>();

/**
 * Calculates a lightweight deterministic 64-bit content hash
 */
export function calculateContentHash(lines: LyricLine[], sourceLang: string, targetLang: string, mode: string): string {
  const content = lines.map((l) => l.text).join('|') + `_${sourceLang}_${targetLang}_${mode}`;
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    const char = content.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32bit integer
  }
  return Math.abs(hash).toString(16);
}

/**
 * Strips LRC timestamps and extracts pure text lines for translation
 */
export function extractTextLines(lines: LyricLine[]): { textLines: string[]; nonTextIndices: Map<number, string> } {
  const textLines: string[] = [];
  const nonTextIndices = new Map<number, string>();

  lines.forEach((line, idx) => {
    const text = (line.text || '').trim();
    // Check if line is empty, just a musical note, or structural tag like [Verse] / [Chorus]
    if (!text || text === '♪' || /^\[(verse|chorus|bridge|intro|outro|hook|drop)[^\]]*\]$/i.test(text)) {
      nonTextIndices.set(idx, text || '♪');
    } else {
      textLines.push(text);
    }
  });

  return { textLines, nonTextIndices };
}

/**
 * Reattaches timestamps to translated text lines 1:1, preserving exact timing values
 */
export function reattachTimestamps(
  originalLines: LyricLine[],
  translatedTextLines: string[],
  nonTextIndices: Map<number, string>
): LyricLine[] {
  let translatedIndex = 0;

  return originalLines.map((orig, idx) => {
    if (nonTextIndices.has(idx)) {
      return {
        time: orig.time,
        text: nonTextIndices.get(idx) || '♪',
      };
    }

    const translated = translatedTextLines[translatedIndex] || orig.text;
    translatedIndex++;
    return {
      time: orig.time,
      text: translated,
    };
  });
}

/**
 * Primary Lyrics Translation Function
 */
export async function translateLyrics(req: TranslationRequest): Promise<TranslationResponse> {
  const mode: TranslationMode = req.mode || 'literal';
  const contentHash = calculateContentHash(req.lines, req.sourceLanguage, req.targetLanguage, mode);
  const cacheKey = `${req.songId}_${req.sourceLanguage}_${req.targetLanguage}_${mode}_${contentHash}`;

  // 1. Check Single-Flight in-flight deduplication
  if (inFlightRequests.has(cacheKey)) {
    return inFlightRequests.get(cacheKey)!;
  }

  // 2. Check Tier 1: Client-Side Dexie / IndexedDB
  try {
    const cachedEntry = await db.lyricsTranslations
      .where('[songId+sourceLanguage+targetLanguage+mode]')
      .equals([req.songId, req.sourceLanguage, req.targetLanguage, mode])
      .first();

    if (cachedEntry && cachedEntry.translatedLines && cachedEntry.translatedLines.length > 0) {
      // ponytail: validate cache isn't stale (identical to source = broken old cache entry)
      const srcSample = req.lines.slice(0, 5).map(l => l.text).join('|');
      const cachedSample = cachedEntry.translatedLines.slice(0, 5).map(l => l.text).join('|');
      if (srcSample !== cachedSample) {
        return {
          translatedLines: cachedEntry.translatedLines,
          sourceLanguage: req.sourceLanguage,
          targetLanguage: req.targetLanguage,
          mode,
          isCached: true,
          provenance: cachedEntry.provenance,
        };
      }
      // Stale entry — delete it and re-fetch
      console.warn('[Translation] Stale cache detected (translated == original), re-fetching');
      await db.lyricsTranslations.delete(cachedEntry.id!);
    }
  } catch (err) {
    console.warn('[Translation] IndexedDB cache read skipped:', err);
  }

  // 3. Execute translation request with single-flight locking
  const translationPromise = (async (): Promise<TranslationResponse> => {
    const { textLines, nonTextIndices } = extractTextLines(req.lines);

    if (textLines.length === 0) {
      return {
        translatedLines: req.lines,
        sourceLanguage: req.sourceLanguage,
        targetLanguage: req.targetLanguage,
        mode,
        isCached: false,
        provenance: {
          provider: 'passthrough',
          model: 'none',
          sourceLanguage: req.sourceLanguage,
          targetLanguage: req.targetLanguage,
          mode,
          createdAt: Date.now(),
          updatedAt: Date.now(),
        },
      };
    }

    try {
      // Server-side translation request to application API endpoint
      const response = await fetch('/api/translate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          songId: req.songId,
          contentHash,
          lines: textLines,
          sourceLanguage: req.sourceLanguage,
          targetLanguage: req.targetLanguage,
          mode,
        }),
      });

      if (!response.ok) {
        throw new Error(`Translation service returned status ${response.status}`);
      }

      const data = await response.json();
      const serverTranslatedLines: string[] = data.translatedLines || [];
      const serverProvenance: TranslationProvenance = data.provenance || {
        provider: 'indictrans2',
        model: 'ai4bharat/indictrans2-indic-en-1B',
        sourceLanguage: req.sourceLanguage,
        targetLanguage: req.targetLanguage,
        mode,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      };

      const finalReattachedLines = reattachTimestamps(req.lines, serverTranslatedLines, nonTextIndices);

      // Save to Tier 1 Client-side IndexedDB Cache
      try {
        await db.lyricsTranslations.put({
          songId: req.songId,
          contentHash,
          sourceLanguage: req.sourceLanguage,
          targetLanguage: req.targetLanguage,
          mode,
          translatedLines: finalReattachedLines,
          provenance: serverProvenance,
          createdAt: Date.now(),
        });
      } catch (dbErr) {
        console.warn('[Translation] Failed to write IndexedDB cache:', dbErr);
      }

      return {
        translatedLines: finalReattachedLines,
        sourceLanguage: req.sourceLanguage,
        targetLanguage: req.targetLanguage,
        mode,
        isCached: data.cached || false,
        provenance: serverProvenance,
      };
    } catch (err: any) {
      console.error('[Translation] Request failed:', err);
      // Graceful fallback: return original lyrics intact without breaking the viewer
      return {
        translatedLines: req.lines,
        sourceLanguage: req.sourceLanguage,
        targetLanguage: req.targetLanguage,
        mode,
        isCached: false,
        provenance: {
          provider: 'fallback_original',
          model: 'none',
          sourceLanguage: req.sourceLanguage,
          targetLanguage: req.targetLanguage,
          mode,
          createdAt: Date.now(),
          updatedAt: Date.now(),
        },
      };
    } finally {
      inFlightRequests.delete(cacheKey);
    }
  })();

  inFlightRequests.set(cacheKey, translationPromise);
  return translationPromise;
}

/** Clears all stale translation caches from IndexedDB */
export async function clearTranslationCache(): Promise<void> {
  try {
    await db.lyricsTranslations.clear();
    console.log('[Translation] IndexedDB translation cache cleared');
  } catch (err) {
    console.warn('[Translation] Failed to clear cache:', err);
  }
}
