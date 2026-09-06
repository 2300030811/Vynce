import React, { useEffect, useRef } from 'react';
import { audioEngine } from '../../audio/AudioEngine';

interface AudioVisualizerProps {
  style?: 'bars' | 'wave' | 'circle';
  color?: string;
  height?: number;
  barCount?: number;
  className?: string;
}

export const AudioVisualizer: React.FC<AudioVisualizerProps> = ({
  style = 'bars',
  color = '#9B6EEA',
  height = 80,
  barCount = 48,
  className = '',
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    const freqData = new Uint8Array(128);

    const render = () => {
      animId = requestAnimationFrame(render);
      audioEngine.getFrequencyData(freqData);

      const width = canvas.width;
      const h = canvas.height;
      ctx.clearRect(0, 0, width, h);

      // Get dynamically resolved primary color from CSS variable if available
      const resolvedColor =
        getComputedStyle(document.documentElement).getPropertyValue('--vynce-primary').trim() || color;

      if (style === 'bars') {
        const step = Math.floor(freqData.length / barCount);
        const barWidth = (width / barCount) * 0.65;
        const gap = (width / barCount) * 0.35;

        for (let i = 0; i < barCount; i++) {
          const val = freqData[i * step] || 0;
          const percent = val / 255;
          const barHeight = Math.max(3, percent * h * 0.9);

          const x = i * (barWidth + gap);
          const y = h - barHeight;

          const grad = ctx.createLinearGradient(0, y, 0, h);
          grad.addColorStop(0, resolvedColor);
          grad.addColorStop(1, 'rgba(0, 0, 0, 0.2)');

          ctx.fillStyle = grad;
          ctx.beginPath();
          ctx.roundRect(x, y, barWidth, barHeight, [4, 4, 0, 0]);
          ctx.fill();
        }
      } else if (style === 'wave') {
        ctx.beginPath();
        ctx.lineWidth = 3;
        ctx.strokeStyle = resolvedColor;
        ctx.shadowColor = resolvedColor;
        ctx.shadowBlur = 10;

        const sliceWidth = width / freqData.length;
        let x = 0;

        for (let i = 0; i < freqData.length; i++) {
          const v = freqData[i] / 255.0;
          const y = h / 2 + (v - 0.5) * h * 0.8;

          if (i === 0) {
            ctx.moveTo(x, y);
          } else {
            ctx.lineTo(x, y);
          }
          x += sliceWidth;
        }

        ctx.lineTo(width, h / 2);
        ctx.stroke();
      }
    };

    render();

    return () => {
      cancelAnimationFrame(animId);
    };
  }, [style, color, barCount]);

  return (
    <canvas
      ref={canvasRef}
      width={600}
      height={height}
      className={`w-full pointer-events-none ${className}`}
      style={{ height }}
    />
  );
};
