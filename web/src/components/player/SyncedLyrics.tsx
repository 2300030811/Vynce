import React, { useEffect, useRef, useState, useMemo } from 'react';
import { usePlayerStore } from '../../stores/playerStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { LyricsApi, parseLrc } from '../../api/lyrics';
import { LyricLine, TranslationMode } from '../../types/music';
import {
  transliterateText,
  transliterateToRoman,
  hasNonLatinCharacters,
} from '../../utils/transliteration';
import {
  detectLanguage,
  SUPPORTED_LANGUAGES,
} from '../../utils/languageDetection';
import { translateLyrics } from '../../utils/lyricsTranslation';
import { LyricQuoteModal } from '../modals/LyricQuoteModal';
import {
  Mic2,
  Loader2,
  Music,
  Sliders,
  Search,
  Copy,
  Check,
  RotateCcw,
  Plus,
  Minus,
  ArrowDownCircle,
  Type,
  X,
  Sparkles,
  Languages,
  ChevronDown,
  Globe,
  Zap,
} from 'lucide-react';

export type LyricsViewMode = 'original' | 'romanized' | 'translated' | 'dual';

export interface SyncedLyricsProps {
  hideHeader?: boolean;
}

export const SyncedLyrics: React.FC<SyncedLyricsProps> = ({ hideHeader = false }) => {
  const {
    lyrics,
    isLyricsLoading,
    currentTime,
    seek,
    currentSong,
    lyricsOffset,
    setLyricsOffset,
    setCustomLyrics,
    fetchLyrics,
  } = usePlayerStore();

  const containerRef = useRef<HTMLDivElement | null>(null);
  const activeLineRef = useRef<HTMLDivElement | null>(null);

  // View & Translation States
  const [viewMode, setViewMode] = useState<LyricsViewMode>('original');
  const [targetLanguage, setTargetLanguage] = useState<string>('eng_Latn');
  const [manualSourceLanguage, setManualSourceLanguage] = useState<string | null>(null);
  const [isTranslating, setIsTranslating] = useState(false);
  const [isCachedTranslation, setIsCachedTranslation] = useState(false);
  const [translatedLines, setTranslatedLines] = useState<LyricLine[]>([]);
  const [translationError, setTranslationError] = useState<string | null>(null);

  // UI Control states
  const [isUserScrolling, setIsUserScrolling] = useState(false);
  const userScrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [fontSizeLevel, setFontSizeLevel] = useState<'sm' | 'md' | 'lg'>('md');
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [showSearchModal, setShowSearchModal] = useState(false);
  const [showQuoteModal, setShowQuoteModal] = useState(false);
  const [showLangModal, setShowLangModal] = useState<'target' | 'source' | null>(null);
  const [langSearchFilter, setLangSearchFilter] = useState('');
  const [copied, setCopied] = useState(false);

  // Manual search state
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // Calculate adjusted current time with offset
  const effectiveTime = Math.max(0, currentTime - lyricsOffset / 1000);

  // Detect source language of active lyrics sample
  const detectedSource = useMemo(() => {
    if (!lyrics) return null;
    const sampleText = lyrics.lines?.length
      ? lyrics.lines.slice(0, 10).map((l) => l.text).join(' ')
      : lyrics.plainLyrics || '';
    return detectLanguage(sampleText);
  }, [lyrics]);

  const effectiveSourceLanguage = manualSourceLanguage || detectedSource?.code || 'hin_Deva';

  // Handle translation when viewMode changes to 'translated' or 'dual', or targetLanguage changes
  useEffect(() => {
    if (!lyrics || !lyrics.lines || lyrics.lines.length === 0) return;
    if (viewMode !== 'translated' && viewMode !== 'dual') return;

    let isMounted = true;
    setIsTranslating(true);
    setTranslationError(null);

    translateLyrics({
      songId: currentSong?.id || 'unknown_song',
      lines: lyrics.lines,
      sourceLanguage: effectiveSourceLanguage,
      targetLanguage,
      mode: 'literal',
    })
      .then((res) => {
        if (!isMounted) return;
        setTranslatedLines(res.translatedLines);
        setIsCachedTranslation(res.isCached);
        setIsTranslating(false);
      })
      .catch((err) => {
        if (!isMounted) return;
        setTranslationError('Translation service unavailable. Showing original lyrics.');
        setIsTranslating(false);
      });

    return () => {
      isMounted = false;
    };
  }, [viewMode, targetLanguage, effectiveSourceLanguage, lyrics, currentSong?.id]);

  // Determine active lyric line index
  const activeIndex = useMemo(() => {
    if (!lyrics?.synced || !lyrics.lines || lyrics.lines.length === 0) return -1;
    let found = -1;
    for (let i = 0; i < lyrics.lines.length; i++) {
      if (effectiveTime >= lyrics.lines[i].time) {
        found = i;
      } else {
        break;
      }
    }
    return found;
  }, [lyrics, effectiveTime]);

  const { autoScrollLyrics } = useSettingsStore();
  const isAutoScrollingRef = useRef(false);

  // Smooth scroll active line to center — scoped to lyrics container only (no page shift)
  useEffect(() => {
    if (autoScrollLyrics && !isUserScrolling && activeLineRef.current && containerRef.current) {
      const container = containerRef.current;
      const el = activeLineRef.current;
      const containerRect = container.getBoundingClientRect();
      const elRect = el.getBoundingClientRect();
      const offsetTop = elRect.top - containerRect.top + container.scrollTop;
      const targetScroll = offsetTop - containerRect.height / 2 + elRect.height / 2;
      
      isAutoScrollingRef.current = true;
      container.scrollTo({ top: Math.max(0, targetScroll), behavior: 'smooth' });
      setTimeout(() => {
        isAutoScrollingRef.current = false;
      }, 700);
    }
  }, [activeIndex, isUserScrolling, autoScrollLyrics]);

  // Handle user manual scroll detection (only triggers for real human interactions)
  const handleUserInteraction = () => {
    if (isAutoScrollingRef.current) return;
    setIsUserScrolling(true);
    if (userScrollTimeoutRef.current) clearTimeout(userScrollTimeoutRef.current);
    userScrollTimeoutRef.current = setTimeout(() => {
      setIsUserScrolling(false);
    }, 4500);
  };

  const handleResumeAutoScroll = () => {
    setIsUserScrolling(false);
    if (userScrollTimeoutRef.current) clearTimeout(userScrollTimeoutRef.current);
    if (activeLineRef.current && containerRef.current) {
      const container = containerRef.current;
      const el = activeLineRef.current;
      const containerRect = container.getBoundingClientRect();
      const elRect = el.getBoundingClientRect();
      const offsetTop = elRect.top - containerRect.top + container.scrollTop;
      const targetScroll = offsetTop - containerRect.height / 2 + elRect.height / 2;
      isAutoScrollingRef.current = true;
      container.scrollTo({ top: Math.max(0, targetScroll), behavior: 'smooth' });
      setTimeout(() => {
        isAutoScrollingRef.current = false;
      }, 700);
    }
  };

  const handleCopyLyrics = async () => {
    if (!lyrics) return;
    const textToCopy =
      lyrics.plainLyrics || lyrics.lines.map((l) => l.text).join('\n');
    try {
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {}
  };

  const handleManualSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setIsSearching(true);
    try {
      const results = await LyricsApi.searchLyrics(searchQuery.trim());
      setSearchResults(results);
    } catch {
      setSearchResults([]);
    } finally {
      setIsSearching(false);
    }
  };

  const handleSelectSearchResult = (result: any) => {
    if (result.syncedLyrics) {
      const parsed = parseLrc(result.syncedLyrics);
      if (parsed && parsed.lines && parsed.lines.length > 0) {
        setCustomLyrics(parsed);
        setShowSearchModal(false);
        return;
      }
    }
    if (result.plainLyrics) {
      setCustomLyrics({ synced: false, plainLyrics: result.plainLyrics, lines: [] });
      setShowSearchModal(false);
    }
  };

  // Font size configuration
  const fontClasses = useMemo(() => {
    switch (fontSizeLevel) {
      case 'sm':
        return {
          active: 'text-xl md:text-2xl font-bold tracking-tight',
          inactive: 'text-base md:text-lg font-medium opacity-50',
        };
      case 'lg':
        return {
          active: 'text-3xl md:text-4xl font-extrabold tracking-tight',
          inactive: 'text-xl md:text-2xl font-semibold opacity-40',
        };
      case 'md':
      default:
        return {
          active: 'text-2xl md:text-3xl font-bold tracking-tight',
          inactive: 'text-lg md:text-xl font-semibold opacity-45',
        };
    }
  }, [fontSizeLevel]);

  // Filtered languages for modal
  const filteredLanguages = useMemo(() => {
    if (!langSearchFilter.trim()) return SUPPORTED_LANGUAGES;
    const q = langSearchFilter.toLowerCase();
    return SUPPORTED_LANGUAGES.filter(
      (l) => l.name.toLowerCase().includes(q) || l.native.toLowerCase().includes(q)
    );
  }, [langSearchFilter]);

  // ── Loading Skeleton ────────────────────────────────────
  if (isLyricsLoading) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-[#6B6A7A] gap-4">
        <Loader2 style={{ color: 'var(--vynce-primary)' }} className="w-8 h-8 animate-spin" />
        <div className="flex flex-col items-center gap-1">
          <p className="text-sm font-semibold text-[#E8E6F0]">Synchronizing Lyrics...</p>
          <p className="text-xs text-[#6B6A7A]">Connecting to LRC library & LRCLIB</p>
        </div>
      </div>
    );
  }

  // ── Empty / Not Found State ─────────────────────────────
  if (!lyrics || (!lyrics.synced && !lyrics.plainLyrics)) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center px-6 gap-4">
        <div className="w-16 h-16 rounded-full bg-white/5 flex items-center justify-center border border-white/10">
          <Mic2 className="w-8 h-8 text-[#6B6A7A]" />
        </div>
        <div className="flex flex-col gap-1 items-center">
          <p className="text-base font-bold text-[#E8E6F0] font-['Outfit']">
            No lyrics available
          </p>
          <p className="text-xs text-[#6B6A7A] max-w-xs">
            We couldn't automatically match lyrics for "{currentSong?.name}".
          </p>
        </div>

        <div className="flex items-center gap-2 mt-2">
          <button
            onClick={() => {
              setSearchQuery(
                `${currentSong?.name || ''} ${currentSong?.primaryArtists || ''}`.trim()
              );
              setShowSearchModal(true);
            }}
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-full text-xs font-bold shadow-md hover:scale-105 transition-transform"
          >
            <Search className="w-3.5 h-3.5" /> Search Lyrics Online
          </button>
          <button
            onClick={fetchLyrics}
            className="flex items-center gap-1.5 px-3 py-2 rounded-full bg-white/5 text-[#E8E6F0] text-xs font-semibold hover:bg-white/10 transition-colors"
            title="Retry"
          >
            <RotateCcw className="w-3.5 h-3.5" /> Retry
          </button>
        </div>

        {showSearchModal && renderSearchModal()}
      </div>
    );
  }

  // ── Plain Lyrics Mode ───────────────────────────────────
  if (!lyrics.synced && lyrics.plainLyrics) {
    let plainText = lyrics.plainLyrics;
    if (viewMode === 'romanized') {
      plainText = transliterateToRoman(lyrics.plainLyrics);
    }

    return (
      <div className="relative h-full flex flex-col overflow-hidden">
        {/* Floating Utilities Bar */}
        <div className="flex items-center justify-between px-6 py-2 border-b border-white/5 text-xs text-[#6B6A7A] shrink-0">
          <span className="flex items-center gap-1.5 font-bold uppercase tracking-wider text-[11px] text-[#9B6EEA]">
            <Music className="w-3.5 h-3.5" /> Plain Lyrics
          </span>
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 bg-white/[0.04] p-1 rounded-xl border border-white/5">
              <button
                onClick={() => setViewMode('original')}
                className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-colors ${
                  viewMode === 'original'
                    ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)]'
                    : 'text-[#A5A3B5] hover:text-white'
                }`}
              >
                Original
              </button>
              <button
                onClick={() => setViewMode('romanized')}
                className={`px-2.5 py-1 rounded-lg text-xs font-bold transition-colors ${
                  viewMode === 'romanized'
                    ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)]'
                    : 'text-[#A5A3B5] hover:text-white'
                }`}
              >
                Romanized (Aa)
              </button>
            </div>

            <button
              onClick={() => setShowQuoteModal(true)}
              className="p-1.5 rounded-lg hover:bg-white/10 text-[#E8E6F0] transition-colors flex items-center gap-1"
              title="Share Lyric Quote Card"
            >
              <Sparkles className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
              <span>Quote</span>
            </button>

            <button
              onClick={handleCopyLyrics}
              className="p-1.5 rounded-lg hover:bg-white/10 text-[#E8E6F0] transition-colors flex items-center gap-1"
              title="Copy"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-green-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>
        </div>

        <div className="h-full overflow-y-auto px-6 py-8 text-center text-[#E8E6F0] font-semibold leading-relaxed whitespace-pre-line text-lg select-text no-scrollbar">
          {plainText}
        </div>

        {showSearchModal && renderSearchModal()}
        <LyricQuoteModal
          isOpen={showQuoteModal}
          onClose={() => setShowQuoteModal(false)}
          song={currentSong}
          lyrics={lyrics}
        />
      </div>
    );
  }

  // ── Synced Interactive Lyrics Mode ──────────────────────
  return (
    <div className="relative h-full w-full flex flex-col overflow-hidden select-none">
      {/* ── Top Multi-Language Floating Capsule Control Bar ── */}
      {!hideHeader && (
        <div className="relative z-20 shrink-0 w-full px-4 pt-2 pb-3 flex flex-col items-center gap-2 backdrop-blur-xl bg-black/40 border-b border-white/5">
        {/* Main 4 Mode Pills Capsule */}
        <div className="flex flex-wrap items-center justify-center gap-1 bg-[#12111F]/90 p-1 rounded-full border border-white/10 shadow-xl max-w-full">
          <button
            onClick={() => setViewMode('original')}
            className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
              viewMode === 'original'
                ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] shadow-md scale-102'
                : 'text-[#A5A3B5] hover:text-white hover:bg-white/[0.06]'
            }`}
          >
            Original
          </button>

          <button
            onClick={() => setViewMode('romanized')}
            className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
              viewMode === 'romanized'
                ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] shadow-md scale-102'
                : 'text-[#A5A3B5] hover:text-white hover:bg-white/[0.06]'
            }`}
          >
            Romanized (Aa)
          </button>

          <button
            onClick={() => setViewMode('translated')}
            className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
              viewMode === 'translated'
                ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] shadow-md scale-102'
                : 'text-[#A5A3B5] hover:text-white hover:bg-white/[0.06]'
            }`}
          >
            <Globe className="w-3.5 h-3.5" />
            <span>Translated</span>
          </button>

          <button
            onClick={() => setViewMode('dual')}
            className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-bold transition-all ${
              viewMode === 'dual'
                ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] shadow-md scale-102'
                : 'text-[#A5A3B5] hover:text-white hover:bg-white/[0.06]'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            <span>Dual Subtitled</span>
          </button>
        </div>

        {/* Secondary Context Bar: Language Selector & Utilities */}
        <div className="flex flex-wrap items-center justify-center gap-2 text-xs text-[#A5A3B5]">
          {/* Target Language Modal Trigger */}
          {(viewMode === 'translated' || viewMode === 'dual') && (
            <div className="flex items-center gap-2 animate-in fade-in duration-200">
              <button
                onClick={() => setShowLangModal('target')}
                className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/[0.08] hover:bg-white/[0.14] text-white font-bold text-xs border border-white/10 transition-colors shadow-sm"
              >
                <Languages className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                <span>Translate to: <strong>{SUPPORTED_LANGUAGES.find((l) => l.code === targetLanguage)?.name || 'English'}</strong></span>
                <ChevronDown className="w-3 h-3 opacity-60" />
              </button>

              <button
                onClick={() => setShowLangModal('source')}
                className="px-2.5 py-1 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-[11px] text-[#A5A3B5] border border-white/5 transition-colors flex items-center gap-1"
                title="Click to override source language"
              >
                <span>From: <strong className="text-white">{SUPPORTED_LANGUAGES.find((l) => l.code === effectiveSourceLanguage)?.name || 'Hindi'}</strong></span>
                <ChevronDown className="w-2.5 h-2.5 opacity-50" />
              </button>

              {isTranslating ? (
                <span className="flex items-center gap-1 text-[11px] text-[var(--vynce-primary)] font-bold animate-pulse px-2.5 py-0.5 rounded-full bg-[var(--vynce-primary-container)]/30 border border-[var(--vynce-primary)]/40">
                  <Loader2 className="w-3 h-3 animate-spin" /> Translating
                </span>
              ) : isCachedTranslation ? (
                <span className="flex items-center gap-1 text-[11px] text-cyan-400 font-bold px-2.5 py-0.5 rounded-full bg-cyan-950/40 border border-cyan-800/40">
                  <Zap className="w-3 h-3" /> Near-instant cache
                </span>
              ) : (
                <span className="text-[11px] text-green-400 font-bold px-2.5 py-0.5 rounded-full bg-green-950/40 border border-green-800/40">
                  ✓ Translated
                </span>
              )}
            </div>
          )}

          {/* Quick Utility Icon Group */}
          <div className="flex items-center gap-1 bg-white/[0.04] p-1 rounded-full border border-white/5">
            <button
              onClick={() => setShowQuoteModal(true)}
              className="flex items-center gap-1 px-2.5 py-1 rounded-full hover:bg-white/10 text-[#E8E6F0] transition-colors text-xs font-semibold"
              title="Create Visual Quote Card"
            >
              <Sparkles className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
              <span className="hidden sm:inline">Quote</span>
            </button>

            <button
              onClick={() => {
                setFontSizeLevel((prev) =>
                  prev === 'sm' ? 'md' : prev === 'md' ? 'lg' : 'sm'
                );
              }}
              className="p-1.5 rounded-full hover:bg-white/10 text-[#E8E6F0] transition-colors"
              title="Adjust Font Size"
            >
              <Type className="w-3.5 h-3.5" />
            </button>

            <button
              onClick={() => setShowSyncDialog(!showSyncDialog)}
              style={showSyncDialog ? { color: 'var(--vynce-primary)' } : {}}
              className="p-1.5 rounded-full hover:bg-white/10 text-[#E8E6F0] transition-colors"
              title="Sync Offset Calibration"
            >
              <Sliders className="w-3.5 h-3.5" />
            </button>

            <button
              onClick={() => {
                setSearchQuery(
                  `${currentSong?.name || ''} ${currentSong?.primaryArtists || ''}`.trim()
                );
                setShowSearchModal(true);
              }}
              className="p-1.5 rounded-full hover:bg-white/10 text-[#E8E6F0] transition-colors"
              title="Search Other Lyrics"
            >
              <Search className="w-3.5 h-3.5" />
            </button>

            <button
              onClick={handleCopyLyrics}
              className="p-1.5 rounded-full hover:bg-white/10 text-[#E8E6F0] transition-colors"
              title="Copy Lyrics"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-green-400" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>
        </div>
      </div>
      )}

      {/* Sync Offset Calibration Bar */}
      {showSyncDialog && (
        <div className="flex items-center justify-between px-6 py-2 bg-[var(--vynce-surface-container)] border-b border-white/10 z-20 animate-in slide-in-from-top-2 duration-150">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-white">Sync Offset:</span>
            <span className="font-mono text-xs font-bold text-[var(--vynce-primary)]">
              {lyricsOffset > 0 ? `+${lyricsOffset}ms` : `${lyricsOffset}ms`}
            </span>
          </div>

          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setLyricsOffset(lyricsOffset - 500)}
              className="px-2 py-1 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs font-mono font-bold"
            >
              -0.5s
            </button>
            <button
              onClick={() => setLyricsOffset(lyricsOffset - 100)}
              className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs"
            >
              <Minus className="w-3 h-3" />
            </button>
            <button
              onClick={() => setLyricsOffset(0)}
              className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs"
            >
              <RotateCcw className="w-3 h-3" />
            </button>
            <button
              onClick={() => setLyricsOffset(lyricsOffset + 100)}
              className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs"
            >
              <Plus className="w-3 h-3" />
            </button>
            <button
              onClick={() => setLyricsOffset(lyricsOffset + 500)}
              className="px-2 py-1 rounded-lg bg-white/10 hover:bg-white/20 text-white text-xs font-mono font-bold"
            >
              +0.5s
            </button>
          </div>
        </div>
      )}

      {/* Main Lyrics Display Container */}
      <div
        ref={containerRef}
        onWheel={handleUserInteraction}
        onTouchMove={handleUserInteraction}
        onPointerDown={handleUserInteraction}
        style={{
          maskImage:
            'linear-gradient(to bottom, transparent 0%, black 12%, black 88%, transparent 100%)',
          WebkitMaskImage:
            'linear-gradient(to bottom, transparent 0%, black 12%, black 88%, transparent 100%)',
          overscrollBehavior: 'contain',
        }}
        className="flex-1 min-h-0 w-full overflow-y-auto px-6 py-12 text-center flex flex-col items-center gap-6 no-scrollbar select-none"
      >
        {lyrics.lines.map((line: LyricLine, idx: number) => {
          const isActive = idx === activeIndex;
          const isPast = idx < activeIndex;

          let primaryText = line.text || '♪';
          let subtitleText: string | null = null;

          if (viewMode === 'romanized') {
            primaryText = transliterateToRoman(line.text);
          } else if (viewMode === 'translated') {
            if (translatedLines.length > idx && translatedLines[idx]?.text) {
              primaryText = translatedLines[idx].text;
            }
          } else if (viewMode === 'dual') {
            // Dual: Primary is original Native script; Subtitle is translated text or Romanization
            primaryText = line.text;
            if (translatedLines.length > idx && translatedLines[idx]?.text && translatedLines[idx].text !== line.text) {
              subtitleText = translatedLines[idx].text;
            } else {
              subtitleText = transliterateToRoman(line.text);
            }
          }

          return (
            <div
              key={`${line.time}-${idx}`}
              ref={isActive ? activeLineRef : null}
              onClick={() => seek(line.time + lyricsOffset / 1000)}
              style={
                isActive
                  ? {
                      transform: 'scale(1.04)',
                    }
                  : {}
              }
              className="cursor-pointer transition-all duration-300 select-none px-4 py-1.5 rounded-2xl flex flex-col items-center gap-1.5 group max-w-2xl"
            >
              <p
                style={
                  isActive
                    ? {
                        color: '#FFFFFF',
                        textShadow: '0 0 35px var(--vynce-primary)',
                      }
                    : isPast
                    ? { color: 'rgba(232, 230, 240, 0.45)' }
                    : { color: 'rgba(107, 106, 122, 0.38)' }
                }
                className={`transition-colors ${
                  isActive ? fontClasses.active : fontClasses.inactive
                } group-hover:text-white group-hover:opacity-100`}
              >
                {primaryText}
              </p>

              {/* Dual Mode Subtitle */}
              {subtitleText && subtitleText !== primaryText && (
                <span
                  style={
                    isActive
                      ? { color: 'var(--vynce-primary)', opacity: 0.95 }
                      : { color: 'rgba(232, 230, 240, 0.40)' }
                  }
                  className="text-xs sm:text-sm font-semibold tracking-wide italic"
                >
                  {subtitleText}
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Floating "Resume Auto-scroll" Button */}
      {isUserScrolling && activeIndex !== -1 && (
        <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-30 animate-in fade-in slide-in-from-bottom-2 duration-200">
          <button
            onClick={handleResumeAutoScroll}
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-full text-xs font-bold shadow-2xl backdrop-blur-md border border-white/10 hover:scale-105 active:scale-95 transition-transform"
          >
            <ArrowDownCircle className="w-4 h-4" /> Resume Auto-scroll
          </button>
        </div>
      )}

      {/* ── Centered Glass Language Selector Modal (Immune to CSS clipping!) ── */}
      {showLangModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200">
          <div className="relative w-full max-w-md bg-[#12111E]/95 rounded-3xl p-6 border border-white/15 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Globe className="w-5 h-5 text-[var(--vynce-primary)]" />
                <h3 className="text-base font-bold text-white font-['Outfit']">
                  {showLangModal === 'target' ? 'Select Target Translation Language' : 'Select Source Language'}
                </h3>
              </div>
              <button
                onClick={() => {
                  setShowLangModal(null);
                  setLangSearchFilter('');
                }}
                className="p-1.5 rounded-full text-[#6B6A7A] hover:text-white transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Search filter input */}
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-[#6B6A7A]" />
              <input
                type="text"
                placeholder="Search language (Hindi, Telugu, Tamil, English...)"
                value={langSearchFilter}
                onChange={(e) => setLangSearchFilter(e.target.value)}
                className="w-full pl-10 pr-4 py-2.5 bg-white/5 border border-white/10 rounded-2xl text-xs text-white outline-none focus:border-[var(--vynce-primary)]"
                autoFocus
              />
            </div>

            {/* Grid of languages */}
            <div className="grid grid-cols-2 gap-2 max-h-72 overflow-y-auto no-scrollbar pt-1">
              {filteredLanguages.map((lang) => {
                const isSelected =
                  showLangModal === 'target'
                    ? targetLanguage === lang.code
                    : effectiveSourceLanguage === lang.code;

                return (
                  <button
                    key={lang.code}
                    onClick={() => {
                      if (showLangModal === 'target') {
                        setTargetLanguage(lang.code);
                      } else {
                        setManualSourceLanguage(lang.code);
                      }
                      setShowLangModal(null);
                      setLangSearchFilter('');
                    }}
                    style={
                      isSelected
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                          }
                        : {}
                    }
                    className={`flex items-center justify-between p-3 rounded-2xl border text-left transition-all ${
                      isSelected
                        ? 'border-[var(--vynce-primary)] shadow-md font-bold'
                        : 'bg-white/[0.04] border-white/5 text-[#E8E6F0] hover:bg-white/[0.08]'
                    }`}
                  >
                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-bold truncate">{lang.name}</span>
                      <span className="text-[10px] opacity-65 truncate">{lang.native}</span>
                    </div>
                    {isSelected && <Check className="w-4 h-4 shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {showSearchModal && renderSearchModal()}
      <LyricQuoteModal
        isOpen={showQuoteModal}
        onClose={() => setShowQuoteModal(false)}
        song={currentSong}
        lyrics={lyrics}
      />
    </div>
  );

  // ── Alternative Lyrics Search Modal ──────────────────────
  function renderSearchModal() {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200">
        <div className="relative w-full max-w-lg bg-[#12111E] rounded-3xl p-6 border border-white/10 flex flex-col gap-4 shadow-2xl">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Search className="w-5 h-5 text-[var(--vynce-primary)]" />
              <h2 className="text-lg font-bold text-white">Search Alternative Lyrics</h2>
            </div>
            <button
              onClick={() => setShowSearchModal(false)}
              className="p-1 rounded-full text-[#6B6A7A] hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <form onSubmit={handleManualSearch} className="flex gap-2">
            <input
              type="text"
              placeholder="Search by Track name, artist..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="flex-1 bg-white/5 border border-white/10 rounded-2xl px-4 py-2 text-sm text-white outline-none focus:border-[var(--vynce-primary)]"
            />
            <button
              type="submit"
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="px-4 py-2 rounded-2xl font-bold text-xs hover:scale-105 transition-transform"
            >
              {isSearching ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Search'}
            </button>
          </form>

          <div className="max-h-64 overflow-y-auto flex flex-col gap-2 no-scrollbar">
            {searchResults.map((res: any, idx: number) => (
              <div
                key={idx}
                onClick={() => handleSelectSearchResult(res)}
                className="p-3 rounded-2xl bg-white/[0.03] hover:bg-white/[0.08] border border-white/5 cursor-pointer flex items-center justify-between transition-colors"
              >
                <div className="flex flex-col min-w-0">
                  <span className="text-sm font-bold text-white truncate">{res.trackName}</span>
                  <span className="text-xs text-[#6B6A7A] truncate">
                    {res.artistName} • {res.albumName}
                  </span>
                </div>
                {res.syncedLyrics ? (
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-green-500/20 text-green-400">
                    Synced
                  </span>
                ) : (
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-white/10 text-[#6B6A7A]">
                    Plain
                  </span>
                )}
              </div>
            ))}
            {!isSearching && searchResults.length === 0 && searchQuery && (
              <p className="text-xs text-center text-[#6B6A7A] py-6">No matching lyrics found</p>
            )}
          </div>
        </div>
      </div>
    );
  }
};
