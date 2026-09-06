import React from 'react';
import { Disc3 } from 'lucide-react';

interface VinylDiscProps {
  image: string;
  isPlaying: boolean;
  size?: number; // size in px (e.g. 320)
}

export const VinylDisc: React.FC<VinylDiscProps> = ({ image, isPlaying, size = 300 }) => {
  return (
    <div
      className="relative flex items-center justify-center select-none"
      style={{ width: size, height: size }}
    >
      {/* Vinyl Disc Container with 3D drop-shadow */}
      <div
        className={`relative rounded-full transition-transform duration-700 ease-out shadow-[0_20px_50px_rgba(0,0,0,0.8)] border border-white/10 ${
          isPlaying ? 'animate-spin-slow' : 'animate-spin-paused'
        }`}
        style={{
          width: size,
          height: size,
          background: 'radial-gradient(circle, #1a1b24 0%, #0d0e15 70%, #050508 100%)',
        }}
      >
        {/* Vinyl Grooves concentric rings */}
        <div className="absolute inset-2 rounded-full border border-white/[0.04]" />
        <div className="absolute inset-5 rounded-full border border-white/[0.06]" />
        <div className="absolute inset-8 rounded-full border border-white/[0.04]" />
        <div className="absolute inset-11 rounded-full border border-white/[0.07]" />
        <div className="absolute inset-14 rounded-full border border-white/[0.04]" />
        <div className="absolute inset-18 rounded-full border border-white/[0.08]" />

        {/* Vinyl Glossy Reflection Highlight */}
        <div
          className="absolute inset-0 rounded-full pointer-events-none opacity-40"
          style={{
            background:
              'conic-gradient(from 45deg at 50% 50%, rgba(255,255,255,0.15) 0deg, transparent 60deg, rgba(255,255,255,0.1) 180deg, transparent 240deg, rgba(255,255,255,0.15) 360deg)',
          }}
        />

        {/* Center Artwork Label */}
        <div className="absolute inset-0 m-auto w-[42%] h-[42%] rounded-full overflow-hidden border-4 border-[#0c0d13] shadow-inner flex items-center justify-center bg-black/60">
          {image ? (
            <img
              src={image}
              alt="Center label"
              className="w-full h-full object-cover"
              crossOrigin="anonymous"
            />
          ) : (
            <Disc3 className="w-12 h-12 text-purple-400/60" />
          )}

          {/* Center spindle hole */}
          <div className="absolute w-4 h-4 rounded-full bg-[#08090d] border-2 border-white/20 shadow-md" />
        </div>
      </div>
    </div>
  );
};
