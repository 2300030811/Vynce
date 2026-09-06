import React, { useEffect, useRef, useState } from 'react';
import { usePlayerStore } from '../../stores/playerStore';
import { EQ_FREQUENCIES, EQ_PRESETS, EqPreset, audioEngine } from '../../audio/AudioEngine';
import {
  Sliders,
  X,
  RotateCcw,
  Volume2,
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

export const EqualizerModal: React.FC = () => {
  const {
    isEqualizerOpen,
    toggleEqualizer,
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

  // Live Canvas Rendering: FFT Spectrum + Smooth Spline EQ Response Curve
  useEffect(() => {
    if (!isEqualizerOpen) return;

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

      // ── 1. Background Grid Lines (+12dB, +6dB, 0dB, -6dB, -12dB) ──
      ctx.lineWidth = 1;
      const dbLevels = [12, 6, 0, -6, -12];
      dbLevels.forEach((db) => {
        // Map dB (-12 to +12) to y (height to 0) with 12% padding
        const normY = 0.5 - (db / 24) * 0.78;
        const y = normY * height;

        ctx.strokeStyle = db === 0 ? 'rgba(255, 255, 255, 0.18)' : 'rgba(255, 255, 255, 0.04)';
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();

        ctx.fillStyle = db === 0 ? 'rgba(255, 255, 255, 0.5)' : 'rgba(255, 255, 255, 0.2)';
        ctx.font = '10px monospace';
        ctx.fillText(`${db > 0 ? `+${db}` : db}dB`, 12, y - 4);
      });

      // ── 2. Live Audio Spectrum Bars (FFT) ──
      const barCount = 64;
      const barWidth = width / barCount;

      for (let i = 0; i < barCount; i++) {
        const dataIdx = Math.floor((i / barCount) * (freqData.length * 0.7));
        const val = freqData[dataIdx] || 0;
        const barHeight = (val / 255) * (height * 0.7);
        const x = i * barWidth;
        const y = height - barHeight;

        const grad = ctx.createLinearGradient(0, height, 0, y);
        grad.addColorStop(0, 'rgba(155, 110, 234, 0.02)');
        grad.addColorStop(1, 'rgba(155, 110, 234, 0.22)');

        ctx.fillStyle = grad;
        ctx.fillRect(x + 1, y, barWidth - 2, barHeight);
      }

      // ── 3. Smooth Cubic Spline EQ Curve ──
      const paddingX = 48;
      const usableWidth = width - paddingX * 2;
      const stepX = usableWidth / (EQ_FREQUENCIES.length - 1);

      const points: { x: number; y: number }[] = EQ_FREQUENCIES.map((_, idx) => {
        const gain = isEqEnabled ? (eqGains[idx] || 0) : 0;
        const x = paddingX + idx * stepX;
        const normY = 0.5 - (gain / 24) * 0.78;
        const y = normY * height;
        return { x, y };
      });

      // Fill underneath the curve
      ctx.beginPath();
      ctx.moveTo(points[0].x, height);
      ctx.lineTo(points[0].x, points[0].y);

      for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[i];
        const p1 = points[i + 1];
        const cp1x = p0.x + (p1.x - p0.x) / 2;
        const cp1y = p0.y;
        const cp2x = p0.x + (p1.x - p0.x) / 2;
        const cp2y = p1.y;
        ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y);
      }

      ctx.lineTo(points[points.length - 1].x, height);
      ctx.closePath();

      const fillGrad = ctx.createLinearGradient(0, 0, 0, height);
      fillGrad.addColorStop(0, 'rgba(155, 110, 234, 0.4)');
      fillGrad.addColorStop(0.6, 'rgba(155, 110, 234, 0.12)');
      fillGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = fillGrad;
      ctx.fill();

      // Stroke the curve line
      ctx.beginPath();
      ctx.moveTo(points[0].x, points[0].y);

      for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[i];
        const p1 = points[i + 1];
        const cp1x = p0.x + (p1.x - p0.x) / 2;
        const cp1y = p0.y;
        const cp2x = p0.x + (p1.x - p0.x) / 2;
        const cp2y = p1.y;
        ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y);
      }

      ctx.strokeStyle = isEqEnabled ? '#B388FF' : 'rgba(255, 255, 255, 0.2)';
      ctx.lineWidth = 3.5;
      ctx.shadowColor = isEqEnabled ? '#9B6EEA' : 'transparent';
      ctx.shadowBlur = 14;
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Draw node points
      points.forEach((p, idx) => {
        const isDragging = draggingIdx === idx;
        ctx.beginPath();
        ctx.arc(p.x, p.y, isDragging ? 7.5 : 5.5, 0, Math.PI * 2);
        ctx.fillStyle = isDragging ? '#00F5D4' : isEqEnabled ? '#FFFFFF' : 'rgba(255, 255, 255, 0.4)';
        ctx.fill();
        ctx.strokeStyle = isDragging ? '#00F5D4' : isEqEnabled ? '#9B6EEA' : '#444';
        ctx.lineWidth = 2.5;
        ctx.stroke();
      });

      animFrameId.current = requestAnimationFrame(render);
    };

    render();

    return () => {
      if (animFrameId.current) {
        cancelAnimationFrame(animFrameId.current);
      }
    };
  }, [isEqualizerOpen, eqGains, isEqEnabled, draggingIdx]);

  // Handle direct mouse / touch drag on canvas nodes
  const handleCanvasMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const clickX = ((e.clientX - rect.left) / rect.width) * canvas.width;
    const clickY = ((e.clientY - rect.top) / rect.height) * canvas.height;

    const paddingX = 48;
    const usableWidth = canvas.width - paddingX * 2;
    const stepX = usableWidth / (EQ_FREQUENCIES.length - 1);

    // Find nearest node within hit radius
    let closestIdx: number | null = null;
    let minDistance = 35;

    EQ_FREQUENCIES.forEach((_, idx) => {
      const nodeX = paddingX + idx * stepX;
      const gain = isEqEnabled ? (eqGains[idx] || 0) : 0;
      const normY = 0.5 - (gain / 24) * 0.78;
      const nodeY = normY * canvas.height;

      const dist = Math.hypot(clickX - nodeX, clickY - nodeY);
      if (dist < minDistance) {
        minDistance = dist;
        closestIdx = idx;
      }
    });

    if (closestIdx !== null) {
      setDraggingIdx(closestIdx);
    }
  };

  const handleCanvasMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (draggingIdx === null) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mouseY = ((e.clientY - rect.top) / rect.height) * canvas.height;

    // Convert mouseY to dB (-12dB to +12dB)
    const normY = mouseY / canvas.height;
    const calculatedGain = (0.5 - normY) / 0.78 * 24;
    const clampedGain = Math.round(Math.max(-12, Math.min(12, calculatedGain)));
    setEqGain(draggingIdx, clampedGain);
  };

  const handleCanvasMouseUp = () => {
    setDraggingIdx(null);
  };

  if (!isEqualizerOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-4 md:p-6 bg-black/90 backdrop-blur-2xl animate-in fade-in duration-200 select-none">
      <div className="relative w-[98vw] max-w-[1780px] h-[92vh] max-h-[950px] bg-[#0A0A0A] rounded-[36px] p-5 sm:p-7 md:p-8 flex flex-col justify-between gap-5 border border-white/10 shadow-2xl overflow-hidden">
        {/* ── Top Header ──────────────────────────────────────── */}
        <div className="flex items-center justify-between border-b border-white/5 pb-4 shrink-0">
          <div className="flex items-center gap-3.5">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-3 rounded-2xl shadow-inner border border-white/10"
            >
              <Sliders className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2.5">
                <h2 className="text-xl sm:text-2xl font-black text-white font-['Outfit']">
                  Pro Studio Equalizer & DSP Console
                </h2>
                <span
                  style={
                    isEqEnabled
                      ? {
                          backgroundColor: 'var(--vynce-primary-container)',
                          color: 'var(--vynce-on-primary-container)',
                        }
                      : {}
                  }
                  className={`text-[10px] font-extrabold uppercase px-3 py-0.5 rounded-full border ${
                    isEqEnabled ? 'border-transparent shadow-sm' : 'bg-white/5 border-white/10 text-[#6B6A7A]'
                  }`}
                >
                  {isEqEnabled ? 'DSP Active' : 'Bypassed'}
                </span>
              </div>
              <p className="text-xs text-[#6B6A7A] mt-0.5 hidden sm:block">
                Interactive Curve Node Sculpting • 10-Band BiquadFilters • Sub-Bass Synthesis • Live FFT Spectrum
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Enable/Bypass Switch (A/B Test) */}
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
              className={`flex items-center gap-2 px-4 py-2 rounded-2xl text-xs font-bold transition-all border ${
                isEqEnabled
                  ? 'border-transparent shadow-md scale-102'
                  : 'bg-white/5 border-white/10 text-[#6B6A7A] hover:text-white'
              }`}
              title="Toggle Equalizer Bypass (A/B Comparison)"
            >
              <Power className="w-3.5 h-3.5" />
              <span>{isEqEnabled ? 'DSP Enabled' : 'Bypassed'}</span>
            </button>

            <button
              onClick={toggleEqualizer}
              className="p-2.5 rounded-full bg-white/5 hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* ── Main Panoramic 2-Column Console ─────────────────── */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 flex-1 min-h-0 items-stretch overflow-hidden">
          {/* LEFT PANEL: Interactive Curve + 10 Channel Faders (Col span 8) */}
          <div className="lg:col-span-8 flex flex-col justify-between gap-4 bg-white/[0.02] p-4 sm:p-6 rounded-[30px] border border-white/5 shadow-inner overflow-hidden">
            {/* Interactive Response Curve & FFT Visualizer Canvas */}
            <div className="relative w-full flex-1 min-h-[160px] max-h-[220px] rounded-2xl bg-black/60 border border-white/10 overflow-hidden shadow-inner flex items-center justify-center cursor-crosshair">
              <canvas
                ref={canvasRef}
                width={1400}
                height={220}
                onMouseDown={handleCanvasMouseDown}
                onMouseMove={handleCanvasMouseMove}
                onMouseUp={handleCanvasMouseUp}
                onMouseLeave={handleCanvasMouseUp}
                className="w-full h-full object-cover"
              />
              <div className="absolute top-3 right-4 flex items-center gap-2 text-[10px] font-mono font-bold text-[#6B6A7A] pointer-events-none bg-black/40 px-2.5 py-1 rounded-full backdrop-blur-md border border-white/5">
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-purple-500 animate-pulse" /> Live FFT
                </span>
                <span>•</span>
                <span>Drag nodes to sculpt curve</span>
              </div>
            </div>

            {/* 10 Studio Channel Strips */}
            <div className="grid grid-cols-10 gap-1 sm:gap-2.5 items-center justify-between py-3 px-2 sm:px-4 bg-black/35 rounded-2xl border border-white/5">
              {EQ_FREQUENCIES.map((freq, idx) => {
                const gain = eqGains[idx] || 0;
                const meta = FREQ_META[idx];

                return (
                  <div
                    key={freq}
                    className="flex flex-col items-center gap-2 justify-between group"
                  >
                    {/* Gain Display Badge */}
                    <span
                      style={gain !== 0 ? { color: 'var(--vynce-primary)' } : {}}
                      className="text-xs font-mono font-black text-[#6B6A7A] transition-colors"
                    >
                      {gain > 0 ? `+${gain}` : gain}
                    </span>

                    {/* Vertical Channel Fader */}
                    <div className="relative flex items-center justify-center h-32 sm:h-36">
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
                        className="h-32 sm:h-36 w-2.5 rounded-lg cursor-pointer [writing-mode:vertical-lr] [direction:rtl] opacity-90 group-hover:opacity-100 transition-opacity"
                      />
                    </div>

                    {/* Frequency & Role Info */}
                    <div className="flex flex-col items-center text-center">
                      <span className="text-xs font-extrabold text-white">{meta.freq}</span>
                      <span className="text-[9px] font-medium text-[#6B6A7A] hidden sm:block truncate max-w-[65px]">
                        {meta.role}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* RIGHT PANEL: Acoustic Profiles & 3 Master DSP Modules (Col span 4) */}
          <div className="lg:col-span-4 flex flex-col justify-between gap-4 bg-white/[0.02] p-4 sm:p-6 rounded-[30px] border border-white/5 shadow-inner overflow-hidden">
            {/* Presets Grid */}
            <div className="flex flex-col gap-2.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A] flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                  <span>Acoustic Profiles</span>
                </span>
                <span className="text-xs font-bold text-white bg-white/5 px-2.5 py-0.5 rounded-full">
                  {eqPreset}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-1.5">
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
                      className={`px-3 py-1.5 rounded-xl text-xs font-semibold tracking-wide transition-all border text-left truncate ${
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
            <div className="flex flex-col gap-2.5">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A] flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5 text-orange-400" />
                <span>Master DSP Enhancers</span>
              </span>

              {/* Sub-Bass Booster */}
              <div className="flex flex-col gap-1.5 p-3 rounded-2xl bg-black/40 border border-white/5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Flame className="w-3.5 h-3.5 text-orange-400" />
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
              </div>

              {/* Vocal Clarity */}
              <div className="flex flex-col gap-1.5 p-3 rounded-2xl bg-black/40 border border-white/5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Mic2 className="w-3.5 h-3.5 text-cyan-400" />
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
              </div>

              {/* Preamp Input Gain */}
              <div className="flex flex-col gap-1.5 p-3 rounded-2xl bg-black/40 border border-white/5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Gauge className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
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
              </div>
            </div>

            {/* Bottom Actions */}
            <div className="flex items-center justify-between pt-2 border-t border-white/5 shrink-0">
              <button
                onClick={() => {
                  setEqPreset('Flat');
                  setBassBoost(0);
                  setVocalClarity(0);
                  setPreampGain(0);
                }}
                className="flex items-center gap-2 text-xs font-semibold text-[#6B6A7A] hover:text-white transition-colors"
              >
                <RotateCcw className="w-3.5 h-3.5" /> Reset All
              </button>

              <button
                onClick={toggleEqualizer}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="px-6 py-2.5 rounded-2xl text-xs font-bold tracking-wide transition-all shadow-xl hover:scale-105 active:scale-95"
              >
                Apply & Close
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};



