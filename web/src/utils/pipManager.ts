import { Song, LyricData } from '../types/music';
import { audioEngine } from '../audio/AudioEngine';
import { formatTime } from './formatters';

class PiPManager {
  private canvas: HTMLCanvasElement | null = null;
  private ctx: CanvasRenderingContext2D | null = null;
  private video: HTMLVideoElement | null = null;
  private animId: number | null = null;
  private isRunning = false;
  private currentImage: HTMLImageElement | null = null;
  private lastLoadedImageUrl: string = '';
  private imageLoadFailed = false;

  private getSongState?: () => {
    currentSong: Song | null;
    currentTime: number;
    duration: number;
    lyrics: LyricData | null;
    lyricsOffset: number;
    status: string;
  };

  public init(
    getSongState: () => {
      currentSong: Song | null;
      currentTime: number;
      duration: number;
      lyrics: LyricData | null;
      lyricsOffset: number;
      status: string;
    }
  ) {
    this.getSongState = getSongState;
  }

  private ensureElements() {
    if (!this.canvas) {
      this.canvas = document.createElement('canvas');
      // High-DPI 1280x720 resolution for Retina crispness
      this.canvas.width = 1280;
      this.canvas.height = 720;
      this.ctx = this.canvas.getContext('2d', { alpha: false });
    }

    if (!this.video) {
      this.video = document.createElement('video');
      this.video.width = 640;
      this.video.height = 360;
      this.video.muted = true;
      this.video.playsInline = true;
      this.video.setAttribute('playsinline', 'true');
      this.video.style.position = 'fixed';
      this.video.style.top = '-9999px';
      this.video.style.left = '-9999px';
      this.video.style.pointerEvents = 'none';
      this.video.style.opacity = '0';
      document.body.appendChild(this.video);

      this.video.addEventListener('leavepictureinpicture', () => {
        this.stop();
      });
    }
  }

  public isPiPSupported(): boolean {
    return (
      typeof document !== 'undefined' &&
      'pictureInPictureEnabled' in document &&
      Boolean(document.pictureInPictureEnabled)
    );
  }

  public isInPiP(): boolean {
    return Boolean(document.pictureInPictureElement && document.pictureInPictureElement === this.video);
  }

  public async togglePiP(): Promise<boolean> {
    if (!this.isPiPSupported()) return false;
    this.ensureElements();

    if (this.isInPiP()) {
      try {
        await document.exitPictureInPicture();
        this.stop();
        return false;
      } catch {
        return false;
      }
    } else {
      try {
        this.start();
        if (this.canvas && this.video) {
          const stream = (this.canvas as any).captureStream?.(30);
          if (stream) {
            this.video.srcObject = stream;
            await this.video.play();
            await this.video.requestPictureInPicture();
            return true;
          }
        }
      } catch (err) {
        this.stop();
        return false;
      }
    }
    return false;
  }

  private start() {
    if (this.isRunning) return;
    this.isRunning = true;
    this.renderLoop();
  }

  private stop() {
    this.isRunning = false;
    if (this.animId) {
      cancelAnimationFrame(this.animId);
      this.animId = null;
    }
  }

  private renderLoop = () => {
    if (!this.isRunning) return;
    this.renderFrame();
    this.animId = requestAnimationFrame(this.renderLoop);
  };

  private renderFrame() {
    if (!this.ctx || !this.canvas || !this.getSongState) return;
    const ctx = this.ctx;
    const width = 1280;
    const height = 720;
    const { currentSong, currentTime, duration, lyrics, lyricsOffset, status } = this.getSongState();

    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';

    // Load artwork safely
    if (currentSong?.image && currentSong.image !== this.lastLoadedImageUrl) {
      this.lastLoadedImageUrl = currentSong.image;
      this.imageLoadFailed = false;
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.src = currentSong.image;
      img.onload = () => {
        this.currentImage = img;
      };
      img.onerror = () => {
        this.imageLoadFailed = true;
        this.currentImage = null;
      };
    }

    // ── 1. Deep Midnight Base Background ──
    const bgGrad = ctx.createLinearGradient(0, 0, width, height);
    bgGrad.addColorStop(0, '#0D0C14');
    bgGrad.addColorStop(0.5, '#12101F');
    bgGrad.addColorStop(1, '#07070B');
    ctx.fillStyle = bgGrad;
    ctx.fillRect(0, 0, width, height);

    // Glowing Ambient Aura behind artwork
    const aura = ctx.createRadialGradient(320, 360, 20, 320, 360, 480);
    aura.addColorStop(0, 'rgba(155, 110, 234, 0.28)');
    aura.addColorStop(0.6, 'rgba(100, 60, 180, 0.08)');
    aura.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = aura;
    ctx.fillRect(0, 0, width, height);

    // Secondary Ambient Glow on right
    const rightGlow = ctx.createRadialGradient(950, 480, 10, 950, 480, 400);
    rightGlow.addColorStop(0, 'rgba(0, 245, 212, 0.12)');
    rightGlow.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = rightGlow;
    ctx.fillRect(0, 0, width, height);

    // ── 2. Left Column: Studio Album Artwork Card ──
    const artSize = 480;
    const artX = 80;
    const artY = (height - artSize) / 2;

    ctx.save();
    ctx.shadowColor = 'rgba(0, 0, 0, 0.75)';
    ctx.shadowBlur = 48;
    ctx.shadowOffsetY = 20;

    ctx.beginPath();
    ctx.roundRect(artX, artY, artSize, artSize, 40);
    ctx.fillStyle = '#1A1828';
    ctx.fill();
    ctx.clip();

    if (this.currentImage && this.currentImage.complete && !this.imageLoadFailed) {
      try {
        ctx.drawImage(this.currentImage, artX, artY, artSize, artSize);
      } catch {
        this.renderFallbackVinylArt(ctx, artX, artY, artSize, currentSong?.name || 'V');
      }
    } else {
      this.renderFallbackVinylArt(ctx, artX, artY, artSize, currentSong?.name || 'V');
    }
    ctx.restore();

    // High-precision Artwork Border
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.roundRect(artX, artY, artSize, artSize, 40);
    ctx.stroke();

    // ── 3. Right Column: Song Info & Live Karaoke Stage ──
    const rightX = 620;
    const rightWidth = width - rightX - 80;

    // Top Tag & Mini Spectrum
    const freqData = new Uint8Array(16);
    audioEngine.getFrequencyData(freqData);

    // Hi-Fi Badge Pill
    ctx.fillStyle = 'rgba(155, 110, 234, 0.18)';
    ctx.strokeStyle = 'rgba(155, 110, 234, 0.45)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.roundRect(rightX, artY + 10, 210, 38, 19);
    ctx.fill();
    ctx.stroke();

    ctx.fillStyle = '#D8B4FE';
    ctx.font = 'bold 16px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('320KBPS • VYNCE DSP', rightX + 105, artY + 29);

    // Mini Live Soundwave next to badge
    const waveX = rightX + 230;
    for (let i = 0; i < 14; i++) {
      const val = freqData[i] || 0;
      const barH = Math.max(5, (val / 255) * 32);
      ctx.fillStyle = i % 2 === 0 ? '#00F5D4' : '#B388FF';
      ctx.beginPath();
      ctx.roundRect(waveX + i * 14, artY + 29 - barH / 2, 7, barH, 3.5);
      ctx.fill();
    }

    // Song Title
    ctx.textAlign = 'left';
    ctx.textBaseline = 'alphabetic';

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 42px sans-serif';
    const title = currentSong?.name || 'Vynce Music';
    const cleanTitle = title.length > 20 ? title.substring(0, 18) + '…' : title;
    ctx.fillText(cleanTitle, rightX, artY + 115);

    // Artist Subtitle
    ctx.fillStyle = 'rgba(232, 230, 240, 0.65)';
    ctx.font = '600 24px sans-serif';
    const artist = currentSong?.primaryArtists || 'High Fidelity Audio Stream';
    const cleanArtist = artist.length > 28 ? artist.substring(0, 26) + '…' : artist;
    ctx.fillText(cleanArtist, rightX, artY + 155);

    // ── 4. Frosted Glass Synced Karaoke Box ──
    const effectiveTime = Math.max(0, currentTime - lyricsOffset / 1000);

    let activeLyric = '♪  Synced Karaoke Active  ♪';
    let nextLyric = '';

    if (lyrics?.synced && lyrics.lines && lyrics.lines.length > 0) {
      let activeIdx = -1;
      for (let i = 0; i < lyrics.lines.length; i++) {
        if (effectiveTime >= lyrics.lines[i].time) {
          activeIdx = i;
        } else {
          break;
        }
      }

      if (activeIdx >= 0) {
        activeLyric = lyrics.lines[activeIdx].text;
        nextLyric = activeIdx < lyrics.lines.length - 1 ? lyrics.lines[activeIdx + 1].text : '';
      } else {
        nextLyric = lyrics.lines[0]?.text || '';
      }
    } else if (status === 'playing') {
      activeLyric = '♪ Playing in Studio DSP Quality ♪';
    }

    const lyricBoxY = artY + 195;
    const lyricBoxH = 210;

    // Glass Card Background
    ctx.fillStyle = 'rgba(255, 255, 255, 0.035)';
    ctx.strokeStyle = 'rgba(155, 110, 234, 0.3)';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.roundRect(rightX, lyricBoxY, rightWidth, lyricBoxH, 32);
    ctx.fill();
    ctx.stroke();

    // Active Lyric (Prominent glowing text)
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    ctx.fillStyle = '#00F5D4';
    ctx.font = 'bold 30px sans-serif';
    const cleanActive = activeLyric.length > 28 ? activeLyric.substring(0, 26) + '…' : activeLyric;
    ctx.fillText(cleanActive, rightX + rightWidth / 2, lyricBoxY + (nextLyric ? 75 : 105));

    // Next Anticipation Lyric Line
    if (nextLyric) {
      ctx.fillStyle = 'rgba(232, 230, 240, 0.45)';
      ctx.font = '500 20px sans-serif';
      const cleanNext = nextLyric.length > 36 ? nextLyric.substring(0, 34) + '…' : nextLyric;
      ctx.fillText(cleanNext, rightX + rightWidth / 2, lyricBoxY + 140);
    }
  }

  private renderFallbackVinylArt(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    size: number,
    initial: string
  ) {
    const grad = ctx.createLinearGradient(x, y, x + size, y + size);
    grad.addColorStop(0, '#7C3AED');
    grad.addColorStop(0.5, '#4C1D95');
    grad.addColorStop(1, '#1E1B4B');
    ctx.fillStyle = grad;
    ctx.fillRect(x, y, size, size);

    // Concentric vinyl disc grooves
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
    ctx.lineWidth = 1.5;
    for (let r = 25; r < size / 2 - 10; r += 18) {
      ctx.beginPath();
      ctx.arc(x + size / 2, y + size / 2, r, 0, Math.PI * 2);
      ctx.stroke();
    }

    // Center badge
    ctx.fillStyle = '#0F0E17';
    ctx.beginPath();
    ctx.arc(x + size / 2, y + size / 2, 36, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#00F5D4';
    ctx.font = 'bold 36px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(initial.charAt(0).toUpperCase(), x + size / 2, y + size / 2);
  }
}

export const pipManager = new PiPManager();
