import React, { useState, useRef, useEffect } from 'react';
import { Song, LyricData, LyricLine } from '../../types/music';
import { extractColorsFromImage, ExtractedColors } from '../../utils/colorExtractor';
import {
  X,
  Download,
  Copy,
  Check,
  Sparkles,
  Smartphone,
  Square,
  Palette,
  Music,
  Flame,
} from 'lucide-react';

interface LyricQuoteModalProps {
  isOpen: boolean;
  onClose: () => void;
  song: Song | null;
  lyrics: LyricData | null;
}

export type AspectRatio = 'square' | 'story';
export type CardTheme = 'adaptive' | 'orange' | 'violet' | 'teal' | 'rose' | 'emerald' | 'amoled';

export const LyricQuoteModal: React.FC<LyricQuoteModalProps> = ({
  isOpen,
  onClose,
  song,
  lyrics,
}) => {
  const [selectedLines, setSelectedLines] = useState<string[]>([]);
  const [aspectRatio, setAspectRatio] = useState<AspectRatio>('square');
  const [theme, setTheme] = useState<CardTheme>('adaptive');
  const [extractedColors, setExtractedColors] = useState<ExtractedColors | null>(null);
  const [isCopied, setIsCopied] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  // Extract real dynamic dominant colors from the playing song's artwork
  useEffect(() => {
    if (!song?.image) return;
    let isMounted = true;
    extractColorsFromImage(song.image).then((colors) => {
      if (isMounted) {
        setExtractedColors(colors);
      }
    });
    return () => {
      isMounted = false;
    };
  }, [song?.image]);

  // Initialize or reset with current song's lyrics whenever song changes or modal opens
  useEffect(() => {
    if (lyrics?.lines && lyrics.lines.length > 0) {
      setSelectedLines(lyrics.lines.slice(0, 2).map((l) => l.text));
    } else {
      setSelectedLines([]);
    }
  }, [lyrics, song?.id, isOpen]);

  // Render canvas card preview
  useEffect(() => {
    if (!isOpen || !song || !canvasRef.current) return;

    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const isStory = aspectRatio === 'story';
    const width = 1080;
    const height = isStory ? 1920 : 1080;
    canvas.width = width;
    canvas.height = height;

    // 1. Background Theme Gradient
    ctx.clearRect(0, 0, width, height);

    const dominantRgb = extractedColors?.dominant || 'rgb(249, 115, 22)';
    const accentRgb = extractedColors?.accent || 'rgb(251, 146, 60)';

    const bgGrad = ctx.createLinearGradient(0, 0, width, height);
    let accentHex = 'rgba(249, 115, 22, 0.4)';
    let quoteIconColor = '#FB923C';

    if (theme === 'adaptive') {
      // Dynamic Song Artwork Palette
      bgGrad.addColorStop(0, dominantRgb);
      bgGrad.addColorStop(0.4, '#120E18');
      bgGrad.addColorStop(1, '#07060A');
      accentHex = extractedColors?.glow || 'rgba(249, 115, 22, 0.45)';
      quoteIconColor = accentRgb;
    } else if (theme === 'orange') {
      // Sunset Orange / Amber
      bgGrad.addColorStop(0, '#2D1406');
      bgGrad.addColorStop(0.5, '#170B04');
      bgGrad.addColorStop(1, '#0A0502');
      accentHex = 'rgba(249, 115, 22, 0.45)';
      quoteIconColor = '#FB923C';
    } else if (theme === 'violet') {
      // Midnight Violet
      bgGrad.addColorStop(0, '#1E1038');
      bgGrad.addColorStop(0.5, '#100C22');
      bgGrad.addColorStop(1, '#07060F');
      accentHex = 'rgba(155, 110, 234, 0.4)';
      quoteIconColor = '#B388FF';
    } else if (theme === 'teal') {
      // Cyber Neon Teal
      bgGrad.addColorStop(0, '#062624');
      bgGrad.addColorStop(0.5, '#071518');
      bgGrad.addColorStop(1, '#04090B');
      accentHex = 'rgba(0, 245, 212, 0.35)';
      quoteIconColor = '#00F5D4';
    } else if (theme === 'rose') {
      // Neon Rose
      bgGrad.addColorStop(0, '#2E0E1F');
      bgGrad.addColorStop(0.5, '#180B13');
      bgGrad.addColorStop(1, '#0A0508');
      accentHex = 'rgba(255, 107, 139, 0.35)';
      quoteIconColor = '#FF6B8B';
    } else if (theme === 'emerald') {
      // Emerald Green
      bgGrad.addColorStop(0, '#062B1D');
      bgGrad.addColorStop(0.5, '#071810');
      bgGrad.addColorStop(1, '#030A07');
      accentHex = 'rgba(16, 185, 129, 0.35)';
      quoteIconColor = '#34D399';
    } else {
      // AMOLED Dark
      bgGrad.addColorStop(0, '#101014');
      bgGrad.addColorStop(0.5, '#070709');
      bgGrad.addColorStop(1, '#000000');
      accentHex = 'rgba(255, 255, 255, 0.15)';
      quoteIconColor = '#E8E6F0';
    }

    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, width, height);

    // Glowing Ambient Aura
    const aura = ctx.createRadialGradient(
      width / 2,
      isStory ? 480 : 260,
      30,
      width / 2,
      isStory ? 480 : 260,
      isStory ? 650 : 500
    );
    aura.addColorStop(0, accentHex);
    aura.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = aura;
    ctx.fillRect(0, 0, width, height);

    // 2. Track Artwork & Header
    const artSize = isStory ? 340 : 220;
    const artX = 90;
    const artY = isStory ? 180 : 90;

    // Artwork Container with Drop Shadow
    ctx.save();
    ctx.shadowColor = 'rgba(0, 0, 0, 0.85)';
    ctx.shadowBlur = 45;
    ctx.shadowOffsetY = 22;

    ctx.beginPath();
    ctx.roundRect(artX, artY, artSize, artSize, 32);
    ctx.fillStyle = '#1A1828';
    ctx.fill();
    ctx.clip();

    if (song.image) {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.src = song.image;
      img.onload = () => {
        try {
          ctx.drawImage(img, artX, artY, artSize, artSize);
        } catch {}
      };
      if (img.complete) {
        try {
          ctx.drawImage(img, artX, artY, artSize, artSize);
        } catch {}
      }
    }
    ctx.restore();

    // Artwork Glass Border
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.22)';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.roundRect(artX, artY, artSize, artSize, 32);
    ctx.stroke();

    // 3. Track Info (Beside artwork)
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';

    const infoX = artX + artSize + 50;
    const infoY = artY + artSize / 2;

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 44px sans-serif';
    const title = song.name.length > 20 ? song.name.substring(0, 18) + '…' : song.name;
    ctx.fillText(title, infoX, infoY - 26);

    ctx.fillStyle = 'rgba(232, 230, 240, 0.70)';
    ctx.font = '600 28px sans-serif';
    const artist = song.primaryArtists.length > 28 ? song.primaryArtists.substring(0, 26) + '…' : song.primaryArtists;
    ctx.fillText(artist, infoX, infoY + 28);

    // 4. Large Glowing Lyric Quote Typography
    const quoteY = isStory ? 760 : 430;
    const linesToDraw = selectedLines.length > 0 ? selectedLines : ['♪ ' + song.name + ' ♪'];

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    // Opening Quote Icon
    ctx.fillStyle = quoteIconColor;
    ctx.font = 'bold 76px serif';
    ctx.fillText('“', width / 2, quoteY - 45);

    // Lyric Lines
    const lineHeight = 80;
    linesToDraw.forEach((line, idx) => {
      const lineY = quoteY + 48 + idx * lineHeight;

      ctx.save();
      ctx.shadowColor = accentHex;
      ctx.shadowBlur = 35;

      ctx.fillStyle = '#FFFFFF';
      ctx.font = 'bold 44px sans-serif';
      const cleanLine = line.length > 38 ? line.substring(0, 36) + '…' : line;
      ctx.fillText(cleanLine, width / 2, lineY);
      ctx.restore();
    });

    // 5. Footer Watermark & Branding
    const footerY = height - 90;

    // Vynce Logo Pill
    ctx.fillStyle = 'rgba(255, 255, 255, 0.10)';
    ctx.beginPath();
    ctx.roundRect(width / 2 - 130, footerY - 30, 260, 58, 29);
    ctx.fill();

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 24px sans-serif';
    ctx.fillText('Vynce Music', width / 2, footerY);
  }, [isOpen, song, selectedLines, aspectRatio, theme, extractedColors]);

  const handleDownload = () => {
    if (!canvasRef.current) return;
    setIsDownloading(true);
    const link = document.createElement('a');
    link.download = `${song?.name || 'vynce'}-lyric-quote.png`;
    link.href = canvasRef.current.toDataURL('image/png');
    link.click();
    setTimeout(() => setIsDownloading(false), 1000);
  };

  const handleCopy = async () => {
    if (!canvasRef.current) return;
    try {
      canvasRef.current.toBlob(async (blob) => {
        if (blob) {
          await navigator.clipboard.write([
            new ClipboardItem({ 'image/png': blob }),
          ]);
          setIsCopied(true);
          setTimeout(() => setIsCopied(false), 2000);
        }
      });
    } catch {
      alert('Clipboard copy is not supported in this browser. Please use Download.');
    }
  };

  const toggleLineSelection = (lineText: string) => {
    if (selectedLines.includes(lineText)) {
      if (selectedLines.length > 1) {
        setSelectedLines(selectedLines.filter((l) => l !== lineText));
      }
    } else {
      if (selectedLines.length < 4) {
        setSelectedLines([...selectedLines, lineText]);
      }
    }
  };

  if (!isOpen || !song) return null;

  const dynamicColorSwatch = extractedColors?.dominant || 'var(--vynce-primary)';

  const themeOptions = [
    {
      id: 'adaptive',
      label: 'Dynamic Artwork',
      isDynamic: true,
      color: dynamicColorSwatch,
    },
    { id: 'orange', label: 'Sunset Orange', color: '#F97316' },
    { id: 'violet', label: 'Violet', color: '#9B6EEA' },
    { id: 'teal', label: 'Teal', color: '#00F5D4' },
    { id: 'rose', label: 'Rose', color: '#FF6B8B' },
    { id: 'emerald', label: 'Emerald', color: '#10B981' },
    { id: 'amoled', label: 'Dark', color: '#1A1828' },
  ];

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-4xl bg-[#0F0E18] border border-white/[0.08] rounded-3xl p-6 shadow-2xl animate-in zoom-in-95 duration-200 max-h-[90vh] flex flex-col gap-5 overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-white/[0.06]">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-sm"
            >
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white tracking-wide font-['Outfit']">
                Share Lyric Quote Card
              </h2>
              <p className="text-xs text-[#6B6A7A]">
                Create aesthetic story & post images for Instagram / WhatsApp
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Split: Left Controls, Right Preview */}
        <div className="grid grid-cols-1 md:grid-cols-12 gap-6 overflow-y-auto pr-1 no-scrollbar flex-1">
          {/* Controls (5 cols) */}
          <div className="md:col-span-5 flex flex-col gap-4">
            {/* Format Picker */}
            <div className="flex flex-col gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-[#A5A3B5]">
                Format
              </span>
              <div className="grid grid-cols-2 gap-2">
                <button
                  onClick={() => setAspectRatio('square')}
                  className={`flex items-center justify-center gap-2 p-2.5 rounded-2xl border text-xs font-bold transition-all ${
                    aspectRatio === 'square'
                      ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] border-[var(--vynce-primary)] shadow-md'
                      : 'bg-white/[0.03] border-white/5 text-[#E8E6F0]/70 hover:text-white'
                  }`}
                >
                  <Square className="w-4 h-4" /> Square 1:1
                </button>
                <button
                  onClick={() => setAspectRatio('story')}
                  className={`flex items-center justify-center gap-2 p-2.5 rounded-2xl border text-xs font-bold transition-all ${
                    aspectRatio === 'story'
                      ? 'bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] border-[var(--vynce-primary)] shadow-md'
                      : 'bg-white/[0.03] border-white/5 text-[#E8E6F0]/70 hover:text-white'
                  }`}
                >
                  <Smartphone className="w-4 h-4" /> Story 9:16
                </button>
              </div>
            </div>

            {/* Theme Picker with Dynamic Album Match Default */}
            <div className="flex flex-col gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-[#A5A3B5]">
                Theme Palette
              </span>
              <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
                {themeOptions.map((t) => {
                  const isSelected = theme === t.id;
                  return (
                    <button
                      key={t.id}
                      onClick={() => setTheme(t.id as CardTheme)}
                      className={`flex flex-col items-center gap-1.5 p-2 rounded-2xl border text-[11px] font-semibold transition-all relative ${
                        isSelected
                          ? 'border-[var(--vynce-primary)] bg-white/[0.12] shadow-lg scale-102 font-bold text-white'
                          : 'border-white/5 bg-white/[0.02] text-[#A5A3B5] hover:text-white hover:bg-white/[0.06]'
                      }`}
                    >
                      {t.isDynamic ? (
                        <div
                          className="w-5 h-5 rounded-full flex items-center justify-center shadow-md ring-2 ring-white/20"
                          style={{
                            background: extractedColors?.dominant
                              ? `linear-gradient(135deg, ${extractedColors.dominant}, ${extractedColors.accent})`
                              : 'linear-gradient(135deg, #F97316, #9B6EEA)',
                          }}
                        >
                          <Sparkles className="w-2.5 h-2.5 text-white" />
                        </div>
                      ) : (
                        <span
                          className="w-4 h-4 rounded-full shadow-sm"
                          style={{ backgroundColor: t.color }}
                        />
                      )}
                      <span className="truncate max-w-[70px]">{t.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Line Selector */}
            <div className="flex flex-col gap-2 flex-1">
              <span className="text-xs font-bold uppercase tracking-wider text-[#A5A3B5]">
                Select Lines (Max 4)
              </span>
              <div className="flex flex-col gap-1 max-h-44 overflow-y-auto border border-white/5 rounded-2xl p-2 bg-black/30 no-scrollbar">
                {lyrics?.lines && lyrics.lines.length > 0 ? (
                  lyrics.lines.map((line, idx) => {
                    const isSelected = selectedLines.includes(line.text);
                    return (
                      <div
                        key={idx}
                        onClick={() => toggleLineSelection(line.text)}
                        className={`flex items-center justify-between p-2 rounded-xl text-xs cursor-pointer transition-colors ${
                          isSelected
                            ? 'bg-[var(--vynce-primary-container)]/50 text-white font-bold border border-[var(--vynce-primary)]/50 shadow-sm'
                            : 'text-[#E8E6F0]/60 hover:text-white hover:bg-white/[0.04]'
                        }`}
                      >
                        <span className="truncate mr-2">{line.text}</span>
                        {isSelected && (
                          <Check className="w-3.5 h-3.5 text-[var(--vynce-primary)] shrink-0" />
                        )}
                      </div>
                    );
                  })
                ) : (
                  <span className="text-xs text-[#6B6A7A] p-3 text-center">
                    No lyrics lines available
                  </span>
                )}
              </div>
            </div>

            {/* Download & Copy Buttons */}
            <div className="flex items-center gap-2 pt-2">
              <button
                onClick={handleDownload}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="flex-1 flex items-center justify-center gap-2 py-3 rounded-2xl font-bold text-xs shadow-lg transition-transform active:scale-95 hover:scale-102"
              >
                <Download className="w-4 h-4" /> Download Card
              </button>
              <button
                onClick={handleCopy}
                className="flex items-center justify-center gap-2 px-4 py-3 rounded-2xl bg-white/[0.06] hover:bg-white/10 text-white text-xs font-bold border border-white/10 transition-colors"
                title="Copy image to clipboard"
              >
                {isCopied ? <Check className="w-4 h-4 text-green-400" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* Live Preview Canvas (7 cols) */}
          <div className="md:col-span-7 flex items-center justify-center p-4 rounded-3xl bg-black/50 border border-white/5 overflow-hidden min-h-[360px] shadow-inner">
            <canvas
              ref={canvasRef}
              className={`rounded-2xl shadow-2xl border border-white/10 transition-all ${
                aspectRatio === 'story'
                  ? 'max-h-[460px] aspect-[9/16]'
                  : 'max-h-[380px] aspect-square'
              }`}
            />
          </div>
        </div>
      </div>
    </div>
  );
};
