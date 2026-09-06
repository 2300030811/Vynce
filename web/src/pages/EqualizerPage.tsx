import React, { useEffect, useRef, useState } from 'react';
import { usePlayerStore } from '../stores/playerStore';
import { EQ_FREQUENCIES, EQ_PRESETS, EqPreset, audioEngine } from '../audio/AudioEngine';
import {
  Sliders,
  RotateCcw,
  Zap,
  Power,
  Flame,
  Gauge,
  Sparkles,
  Mic2,
} from 'lucide-react';

const FREQ_META = [
  { freq: '32Hz', role: 'Sub-Bass', range: '20-40Hz' },
  { freq: '64Hz', role: 'Bass Punch', range: '40-80Hz' },
  { freq: '125Hz', role: 'Upper Bass', range: '80-160Hz' },
  { freq: '250Hz', role: 'Warmth', range: '160-300Hz' },
  { freq: '500Hz', role: 'Body', range: '300-600Hz' },
  { freq: '1kHz', role: 'Vocal Core', range: '600-1.5kHz' },
  { freq: '2kHz', role: 'Clarity', range: '1.5-3kHz' },
  { freq: '4kHz', role: 'Presence', range: '3-6kHz' },
  { freq: '8kHz', role: 'Treble', range: '6-10kHz' },
  { freq: '16kHz', role: 'Air & Shimmer', range: '10-20kHz' },
];

export const EqualizerPage: React.FC = () => {
  const {
    eqPreset,
    eqGains,
    setEqPreset,
    setEqGain,
    bassBoost,
    setBassBoost,
    vocalClarity,
    setVocalClarity,
    preampGain,
    setPreampGain,
    isEqEnabled,
    toggleEqEnabled,
  } = usePlayerStore();

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animFrameId = useRef<number | null>(null);
  const [draggingIdx, setDraggingIdx] = useState<number | null>(null);
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  // Smooth Catmull-Rom Spline generator for studio-grade acoustic curves
  const drawCatmullRom = (
    ctx: CanvasRenderingContext2D,
    points: { x: number; y: number }[]
  ) => {
    if (points.length < 2) return;
    ctx.moveTo(points[0].x, points[0].y);

    for (let i = 0; i < points.length - 1; i++) {
      const p0 = i > 0 ? points[i - 1] : points[i];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = i < points.length - 2 ? points[i + 2] : p2;

      const cp1x = p1.x + (p2.x - p0.x) / 5.5;
      const cp1y = Math.max(10, Math.min(ctx.canvas.height - 10, p1.y + (p2.y - p0.y) / 5.5));
      const cp2x = p2.x - (p3.x - p1.x) / 5.5;
      const cp2y = Math.max(10, Math.min(ctx.canvas.height - 10, p2.y - (p3.y - p1.y) / 5.5));

      ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
    }
  };

  // Live Canvas Rendering: Fluid FFT Spectrum + Catmull-Rom Spline Curve
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const freqData = new Uint8Array(128);

    const render = () => {
      audioEngine.getFrequencyData(freqData);

      const width = canvas.width;
      const height = canvas.height;

      ctx.clearRect(0, 0, width, height);

      // ── 1. Background DB Reference Grid Lines ──
      ctx.lineWidth = 1;
      const dbLevels = [12, 6, 0, -6, -12];
      dbLevels.forEach((db) => {
        const normY = 0.5 - (db / 24) * 0.78;
        const y = normY * height;

        ctx.strokeStyle = db === 0 ? 'rgba(255, 255, 255, 0.22)' : 'rgba(255, 255, 255, 0.05)';
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();

        ctx.fillStyle = db === 0 ? 'rgba(255, 255, 255, 0.6)' : 'rgba(255, 255, 255, 0.25)';
        ctx.font = 'bold 11px monospace';
        ctx.fillText(`${db > 0 ? `+${db}` : db} dB`, 16, y - 5);
      });

      // ── 2. Fluid FFT Frequency Spectrum Bars ──
      const barCount = 72;
      const barWidth = width / barCount;

      for (let i = 0; i < barCount; i++) {
        const dataIdx = Math.floor((i / barCount) * (freqData.length * 0.75));
        const val = freqData[dataIdx] || 0;
        const barHeight = (val / 255) * (height * 0.72);
        const x = i * barWidth;
        const y = height - barHeight;

        const grad = ctx.createLinearGradient(0, height, 0, y);
        grad.addColorStop(0, 'rgba(155, 110, 234, 0.03)');
        grad.addColorStop(1, 'rgba(155, 110, 234, 0.28)');

        ctx.fillStyle = grad;
        ctx.fillRect(x + 1.5, y, barWidth - 3, barHeight);

        // Peak luminous tip
        if (barHeight > 6) {
          ctx.fillStyle = 'rgba(216, 180, 254, 0.6)';
          ctx.fillRect(x + 1.5, y, barWidth - 3, 2);
        }
      }

      // ── 3. Smooth All-10-Node Catmull-Rom Spline Curve ──
      const paddingX = 56;
      const usableWidth = width - paddingX * 2;
      const stepX = usableWidth / (EQ_FREQUENCIES.length - 1);

      const points: { x: number; y: number; gain: number; meta: (typeof FREQ_META)[0] }[] =
        EQ_FREQUENCIES.map((_, idx) => {
          const gain = isEqEnabled ? (eqGains[idx] || 0) : 0;
          const x = paddingX + idx * stepX;
          const normY = 0.5 - (gain / 24) * 0.78;
          const y = Math.max(16, Math.min(height - 16, normY * height));
          return { x, y, gain, meta: FREQ_META[idx] };
        });

      // Fill illuminated area underneath the curve
      ctx.beginPath();
      ctx.moveTo(points[0].x, height);
      ctx.lineTo(points[0].x, points[0].y);
      drawCatmullRom(ctx, points);
      ctx.lineTo(points[points.length - 1].x, height);
      ctx.closePath();

      const fillGrad = ctx.createLinearGradient(0, 0, 0, height);
      fillGrad.addColorStop(0, 'rgba(179, 136, 255, 0.42)');
      fillGrad.addColorStop(0.55, 'rgba(155, 110, 234, 0.12)');
      fillGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = fillGrad;
      ctx.fill();

      // Stroke glowing curve line
      ctx.beginPath();
      drawCatmullRom(ctx, points);
      ctx.strokeStyle = isEqEnabled ? '#D8B4FE' : 'rgba(255, 255, 255, 0.25)';
      ctx.lineWidth = 4;
      ctx.shadowColor = isEqEnabled ? '#9B6EEA' : 'transparent';
      ctx.shadowBlur = 16;
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Draw all 10 interactive node circles with active state glow
      points.forEach((p, idx) => {
        const isActive = draggingIdx === idx || hoveredIdx === idx;
        const radius = isActive ? 8.5 : 6;

        // Outer glow halo
        if (isActive) {
          ctx.beginPath();
          ctx.arc(p.x, p.y, radius + 7, 0, Math.PI * 2);
          ctx.fillStyle = 'rgba(0, 245, 212, 0.25)';
          ctx.fill();
        }

        // Inner node dot
        ctx.beginPath();
        ctx.arc(p.x, p.y, radius, 0, Math.PI * 2);
        ctx.fillStyle = isActive ? '#00F5D4' : isEqEnabled ? '#FFFFFF' : 'rgba(255, 255, 255, 0.5)';
        ctx.fill();
        ctx.strokeStyle = isActive ? '#00F5D4' : isEqEnabled ? '#9B6EEA' : '#444';
        ctx.lineWidth = 2.5;
        ctx.stroke();

        // Active node tooltip badge on top
        if (isActive) {
          const badgeText = `${p.meta.freq}  ${p.gain > 0 ? `+${p.gain}` : p.gain}dB (${p.meta.role})`;
          ctx.font = 'bold 11px sans-serif';
          const textWidth = ctx.measureText(badgeText).width;
          const badgeX = Math.max(10, Math.min(width - textWidth - 24, p.x - textWidth / 2 - 10));
          const badgeY = Math.max(26, p.y - 20);

          ctx.fillStyle = 'rgba(10, 10, 15, 0.9)';
          ctx.beginPath();
          ctx.roundRect(badgeX, badgeY - 14, textWidth + 20, 20, 6);
          ctx.fill();
          ctx.strokeStyle = '#00F5D4';
          ctx.lineWidth = 1;
          ctx.stroke();

          ctx.fillStyle = '#00F5D4';
          ctx.fillText(badgeText, badgeX + 10, badgeY);
        }
      });

      animFrameId.current = requestAnimationFrame(render);
    };

    render();

    return () => {
      if (animFrameId.current) {
        cancelAnimationFrame(animFrameId.current);
      }
    };
  }, [eqGains, isEqEnabled, draggingIdx, hoveredIdx]);

  // Handle global mouse move & mouse up during node drag for 100% fluid control
  useEffect(() => {
    if (draggingIdx === null) return;

    const handleWindowMouseMove = (e: MouseEvent) => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const rect = canvas.getBoundingClientRect();
      const mouseY = ((e.clientY - rect.top) / rect.height) * canvas.height;

      const normY = mouseY / canvas.height;
      const calculatedGain = ((0.5 - normY) / 0.78) * 24;
      const clampedGain = Math.round(Math.max(-12, Math.min(12, calculatedGain)));
      setEqGain(draggingIdx, clampedGain);
    };

    const handleWindowMouseUp = () => {
      setDraggingIdx(null);
    };

    window.addEventListener('mousemove', handleWindowMouseMove);
    window.addEventListener('mouseup', handleWindowMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleWindowMouseMove);
      window.removeEventListener('mouseup', handleWindowMouseUp);
    };
  }, [draggingIdx, setEqGain]);

  // Click & grab any of the 10 frequency columns on canvas
  const handleCanvasMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const clickX = ((e.clientX - rect.left) / rect.width) * canvas.width;
    const clickY = ((e.clientY - rect.top) / rect.height) * canvas.height;

    const paddingX = 56;
    const usableWidth = canvas.width - paddingX * 2;
    const stepX = usableWidth / (EQ_FREQUENCIES.length - 1);

    // Calculate nearest frequency column index (0 to 9)
    const nearestIdx = Math.round((clickX - paddingX) / stepX);
    const clampedIdx = Math.max(0, Math.min(EQ_FREQUENCIES.length - 1, nearestIdx));

    setDraggingIdx(clampedIdx);

    // Immediately update gain to mouse Y position
    const normY = clickY / canvas.height;
    const calculatedGain = ((0.5 - normY) / 0.78) * 24;
    const clampedGain = Math.round(Math.max(-12, Math.min(12, calculatedGain)));
    setEqGain(clampedIdx, clampedGain);
  };

  const handleCanvasMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mouseX = ((e.clientX - rect.left) / rect.width) * canvas.width;

    const paddingX = 56;
    const usableWidth = canvas.width - paddingX * 2;
    const stepX = usableWidth / (EQ_FREQUENCIES.length - 1);

    const nearestIdx = Math.round((mouseX - paddingX) / stepX);
    if (nearestIdx >= 0 && nearestIdx < EQ_FREQUENCIES.length) {
      setHoveredIdx(nearestIdx);
    } else {
      setHoveredIdx(null);
    }
  };

  const handleCanvasMouseLeave = () => {
    setHoveredIdx(null);
  };

  return (
    <div className="flex flex-col gap-6 max-w-[1750px] mx-auto w-full pb-16 animate-in fade-in select-none">
      {/* ── Page Header ─────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#121118]/60 p-6 md:p-8 rounded-[32px] border border-white/5 backdrop-blur-xl">
        <div className="flex items-center gap-4">
          <div
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="p-4 rounded-2xl shadow-inner border border-white/10"
          >
            <Sliders className="w-7 h-7" />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl sm:text-3xl font-extrabold text-white font-['Outfit']">
                Pro Studio Equalizer & DSP Suite
              </h1>
              <span
                style={
                  isEqEnabled
                    ? {
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }
                    : {}
                }
                className={`text-[10px] font-extrabold uppercase px-3 py-1 rounded-full border ${
                  isEqEnabled ? 'border-transparent shadow-sm' : 'bg-white/5 border-white/10 text-[#6B6A7A]'
                }`}
              >
                {isEqEnabled ? 'DSP Active' : 'Bypassed'}
              </span>
            </div>
            <p className="text-xs sm:text-sm text-[#6B6A7A] mt-1">
              Hardware-accelerated 10-Band BiquadFilters • Interactive spline curve sculpting • Sub-harmonic bass & vocal synthesis
            </p>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              setEqPreset('Flat');
              setBassBoost(0);
              setVocalClarity(0);
              setPreampGain(0);
            }}
            className="flex items-center gap-2 px-4 py-2.5 rounded-2xl text-xs font-semibold text-[#6B6A7A] hover:text-white bg-white/5 hover:bg-white/10 border border-white/5 transition-all"
          >
            <RotateCcw className="w-3.5 h-3.5" /> Reset All to Flat
          </button>

          <button
            onClick={toggleEqEnabled}
            style={
              isEqEnabled
                ? {
                    backgroundColor: 'var(--vynce-primary-container)',
                    color: 'var(--vynce-on-primary-container)',
                  }
                : {}
            }
            className={`flex items-center gap-2 px-5 py-2.5 rounded-2xl text-xs font-bold transition-all border ${
              isEqEnabled
                ? 'border-transparent shadow-md scale-102 font-extrabold'
                : 'bg-white/5 border-white/10 text-[#6B6A7A] hover:text-white'
            }`}
            title="Toggle Equalizer Bypass (A/B Comparison)"
          >
            <Power className="w-4 h-4" />
            <span>{isEqEnabled ? 'DSP Enabled' : 'Bypassed'}</span>
          </button>
        </div>
      </div>

      {/* ── Main Studio Grid ─────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* LEFT COLUMN: Response Curve Canvas + 10-Band Channel Faders (8 cols) */}
        <div className="lg:col-span-8 flex flex-col gap-6 bg-[#121118]/60 p-6 md:p-8 rounded-[36px] border border-white/5 backdrop-blur-xl shadow-2xl">
          {/* Interactive Response Curve & FFT Visualizer Canvas */}
          <div className="relative w-full h-56 sm:h-64 rounded-2xl bg-black/60 border border-white/10 overflow-hidden shadow-inner flex items-center justify-center cursor-crosshair">
            <canvas
              ref={canvasRef}
              width={1400}
              height={260}
              onMouseDown={handleCanvasMouseDown}
              onMouseMove={handleCanvasMouseMove}
              onMouseLeave={handleCanvasMouseLeave}
              className="w-full h-full object-cover"
            />
            <div className="absolute top-3 right-4 flex items-center gap-2 text-[10px] font-mono font-bold text-[#6B6A7A] pointer-events-none bg-black/50 px-3 py-1 rounded-full backdrop-blur-md border border-white/5">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-purple-500 animate-pulse" /> Live FFT Analyzer
              </span>
              <span>•</span>
              <span>Drag nodes to sculpt curve</span>
            </div>
          </div>

          {/* 10 Studio Channel Faders */}
          <div className="grid grid-cols-10 gap-1 sm:gap-3 items-center justify-between py-6 px-2 sm:px-6 bg-black/40 rounded-2xl border border-white/5 shadow-inner">
            {EQ_FREQUENCIES.map((freq, idx) => {
              const gain = eqGains[idx] || 0;
              const meta = FREQ_META[idx];

              return (
                <div
                  key={freq}
                  className="flex flex-col items-center gap-3 h-64 justify-between group"
                >
                  {/* Gain Display Badge */}
                  <span
                    style={gain !== 0 ? { color: 'var(--vynce-primary)' } : {}}
                    className="text-xs font-mono font-extrabold text-[#6B6A7A] transition-colors"
                  >
                    {gain > 0 ? `+${gain}` : gain}
                  </span>

                  {/* Vertical Channel Fader */}
                  <div className="relative flex items-center justify-center h-40">
                    {/* Center Zero Line Indicator */}
                    <div className="absolute w-5 h-[1px] bg-white/25 pointer-events-none" />

                    <input
                      type="range"
                      min={-12}
                      max={12}
                      step={1}
                      value={gain}
                      onChange={(e) => setEqGain(idx, parseFloat(e.target.value))}
                      style={{ accentColor: 'var(--vynce-primary)' }}
                      className="h-40 w-2.5 rounded-lg cursor-pointer [writing-mode:vertical-lr] [direction:rtl] opacity-90 group-hover:opacity-100 transition-opacity"
                    />
                  </div>

                  {/* Frequency & Role Info */}
                  <div className="flex flex-col items-center text-center">
                    <span className="text-xs font-extrabold text-white">{meta.freq}</span>
                    <span className="text-[10px] font-medium text-[#6B6A7A] hidden sm:block truncate max-w-[68px]">
                      {meta.role}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* RIGHT COLUMN: Acoustic Presets & 3 Master DSP Modules (4 cols) */}
        <div className="lg:col-span-4 flex flex-col justify-between gap-6 bg-[#121118]/60 p-6 md:p-8 rounded-[36px] border border-white/5 backdrop-blur-xl shadow-2xl">
          {/* Presets Grid */}
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A] flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Acoustic Profiles</span>
              </span>
              <span className="text-xs font-bold text-white bg-white/5 px-3 py-0.5 rounded-full border border-white/5">
                {eqPreset}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-2">
              {(Object.keys(EQ_PRESETS) as EqPreset[]).map((preset) => {
                const isActive = eqPreset === preset;
                return (
                  <button
                    key={preset}
                    onClick={() => setEqPreset(preset)}
                    style={
                      isActive
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                            borderColor: 'var(--vynce-primary)',
                          }
                        : {}
                    }
                    className={`px-3.5 py-2.5 rounded-2xl text-xs font-semibold tracking-wide transition-all border text-left truncate ${
                      isActive
                        ? 'shadow-md font-extrabold scale-102'
                        : 'bg-white/[0.03] border-white/5 text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.06]'
                    }`}
                  >
                    {preset}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Master DSP Modules */}
          <div className="flex flex-col gap-3.5">
            <span className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A] flex items-center gap-1.5">
              <Zap className="w-4 h-4 text-orange-400" />
              <span>Master DSP Enhancers</span>
            </span>

            {/* Sub-Bass Booster */}
            <div className="flex flex-col gap-2 p-4 rounded-2xl bg-black/40 border border-white/5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Flame className="w-4 h-4 text-orange-400" />
                  <span className="text-xs font-bold text-white">Sub-Bass Harmonic Booster</span>
                </div>
                <span className="text-xs font-mono font-bold text-orange-400">
                  {bassBoost > 0 ? `+${bassBoost}%` : 'Off'}
                </span>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={bassBoost}
                onChange={(e) => setBassBoost(parseInt(e.target.value, 10))}
                style={{ accentColor: '#FB923C' }}
                className="w-full h-1.5 bg-white/10 rounded-full cursor-pointer"
              />
              <span className="text-[10px] text-[#6B6A7A]">55Hz resonant low-shelf for club bass punch</span>
            </div>

            {/* Vocal Clarity */}
            <div className="flex flex-col gap-2 p-4 rounded-2xl bg-black/40 border border-white/5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Mic2 className="w-4 h-4 text-cyan-400" />
                  <span className="text-xs font-bold text-white">Vocal Brilliance & Presence</span>
                </div>
                <span className="text-xs font-mono font-bold text-cyan-400">
                  {vocalClarity > 0 ? `+${vocalClarity}%` : 'Off'}
                </span>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={vocalClarity}
                onChange={(e) => setVocalClarity(parseInt(e.target.value, 10))}
                style={{ accentColor: '#22D3EE' }}
                className="w-full h-1.5 bg-white/10 rounded-full cursor-pointer"
              />
              <span className="text-[10px] text-[#6B6A7A]">2.8kHz vocal clarity & speech presence</span>
            </div>

            {/* Preamp Input Gain */}
            <div className="flex flex-col gap-2 p-4 rounded-2xl bg-black/40 border border-white/5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Gauge className="w-4 h-4 text-[var(--vynce-primary)]" />
                  <span className="text-xs font-bold text-white">Preamp Headroom Gain</span>
                </div>
                <span className="text-xs font-mono font-bold text-[var(--vynce-primary)]">
                  {preampGain > 0 ? `+${preampGain}dB` : `${preampGain}dB`}
                </span>
              </div>
              <input
                type="range"
                min="-6"
                max="6"
                step="0.5"
                value={preampGain}
                onChange={(e) => setPreampGain(parseFloat(e.target.value))}
                style={{ accentColor: 'var(--vynce-primary)' }}
                className="w-full h-1.5 bg-white/10 rounded-full cursor-pointer"
              />
              <span className="text-[10px] text-[#6B6A7A]">Input gain staging to prevent clipping distortion</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
