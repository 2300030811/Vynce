import React, { useEffect, useState } from 'react';
import { Minus, Square, Copy, X } from 'lucide-react';
import { usePlayerStore } from '../../stores/playerStore';

export const DesktopTitleBar: React.FC = () => {
  const isElectron = typeof window !== 'undefined' && Boolean(window.electronAPI?.isElectron);
  const [isMaximized, setIsMaximized] = useState(false);

  const { togglePlay, nextTrack, prevTrack } = usePlayerStore();

  useEffect(() => {
    if (!isElectron || !window.electronAPI) return;

    // Check initial state
    window.electronAPI.isMaximized().then(setIsMaximized).catch(() => {});

    // Listen for maximize change events
    const unsubscribeMax = window.electronAPI.onMaximizeChange((max) => {
      setIsMaximized(max);
    });

    // Listen for tray media events
    const unsubscribeTray = window.electronAPI.onTrayAction((action) => {
      if (action === 'play-pause') togglePlay();
      else if (action === 'next') nextTrack();
      else if (action === 'prev') prevTrack();
    });

    return () => {
      unsubscribeMax?.();
      unsubscribeTray?.();
    };
  }, [isElectron, togglePlay, nextTrack, prevTrack]);

  if (!isElectron || !window.electronAPI) {
    return null;
  }

  const handleMinimize = () => {
    window.electronAPI?.minimize();
  };

  const handleMaximize = () => {
    window.electronAPI?.maximize();
  };

  const handleClose = () => {
    window.electronAPI?.close();
  };

  return (
    <div
      className="flex items-center justify-between h-8 w-full bg-[#050608]/90 backdrop-blur-md border-b border-white/[0.04] select-none z-50 text-slate-400 text-xs px-2"
      style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
    >
      {/* Brand / Logo */}
      <div className="flex items-center gap-2 px-1">
        <img src="/vynce_logo.png" alt="Vynce" className="w-4 h-4 rounded-full object-cover" />
        <span className="font-bold tracking-wider text-[11px] text-white/80">VYNCE</span>
      </div>

      {/* Center Drag Area */}
      <div className="flex-1 h-full flex items-center justify-center pointer-events-none">
        <span className="text-[10px] text-slate-500 font-medium tracking-wide">Desktop Audio Player</span>
      </div>

      {/* Window Controls */}
      <div
        className="flex items-center h-full"
        style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
      >
        <button
          onClick={handleMinimize}
          className="h-full px-3 flex items-center justify-center hover:bg-white/[0.08] hover:text-white transition-colors"
          title="Minimize"
          aria-label="Minimize"
        >
          <Minus className="w-3 h-3" />
        </button>

        <button
          onClick={handleMaximize}
          className="h-full px-3 flex items-center justify-center hover:bg-white/[0.08] hover:text-white transition-colors"
          title={isMaximized ? 'Restore' : 'Maximize'}
          aria-label={isMaximized ? 'Restore' : 'Maximize'}
        >
          {isMaximized ? <Copy className="w-2.5 h-2.5" /> : <Square className="w-2.5 h-2.5" />}
        </button>

        <button
          onClick={handleClose}
          className="h-full px-3 flex items-center justify-center hover:bg-red-600 hover:text-white transition-colors"
          title="Close"
          aria-label="Close"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
};
