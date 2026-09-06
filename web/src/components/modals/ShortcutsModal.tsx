import React from 'react';
import { X, Keyboard, Play, Volume2, Music, Sparkles, Sliders, Moon } from 'lucide-react';

interface ShortcutsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface ShortcutItem {
  keys: string[];
  description: string;
}

interface ShortcutGroup {
  title: string;
  icon: React.ReactNode;
  shortcuts: ShortcutItem[];
}

const SHORTCUT_GROUPS: ShortcutGroup[] = [
  {
    title: 'Playback Controls',
    icon: <Play className="w-4 h-4 text-[var(--vynce-primary)]" />,
    shortcuts: [
      { keys: ['Space'], description: 'Play / Pause toggle' },
      { keys: ['Shift', '→'], description: 'Skip to Next Track' },
      { keys: ['Shift', '←'], description: 'Skip to Previous Track' },
      { keys: ['→'], description: 'Seek forward 5 seconds' },
      { keys: ['←'], description: 'Seek backward 5 seconds' },
    ],
  },
  {
    title: 'Volume & Audio',
    icon: <Volume2 className="w-4 h-4 text-cyan-400" />,
    shortcuts: [
      { keys: ['↑'], description: 'Increase volume (+5%)' },
      { keys: ['↓'], description: 'Decrease volume (-5%)' },
      { keys: ['M'], description: 'Mute / Unmute audio' },
    ],
  },
  {
    title: 'Panels & Views',
    icon: <Music className="w-4 h-4 text-purple-400" />,
    shortcuts: [
      { keys: ['F'], description: 'Toggle Fullscreen Player' },
      { keys: ['L'], description: 'Toggle Synced Lyrics' },
      { keys: ['Q'], description: 'Toggle Queue Drawer' },
      { keys: ['S'], description: 'Toggle Sleep Timer' },
      { keys: ['?'], description: 'Open Keyboard Shortcuts' },
    ],
  },
];

export const ShortcutsModal: React.FC<ShortcutsModalProps> = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in duration-200"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-lg bg-[#0F0E18] border border-white/[0.08] rounded-3xl p-6 shadow-2xl animate-in zoom-in-95 duration-200 overflow-hidden"
      >
        {/* Ambient Top Glow */}
        <div className="absolute -top-24 left-1/2 -translate-x-1/2 w-64 h-32 bg-[var(--vynce-primary)]/20 blur-3xl pointer-events-none" />

        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/[0.06] mb-5">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-sm"
            >
              <Keyboard className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white tracking-wide">Keyboard Shortcuts</h2>
              <p className="text-xs text-[#6B6A7A]">Quick commands for effortless playback</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Groups */}
        <div className="flex flex-col gap-5 max-h-[60vh] overflow-y-auto pr-1 no-scrollbar">
          {SHORTCUT_GROUPS.map((group) => (
            <div key={group.title} className="flex flex-col gap-2.5">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-[#A5A3B5]">
                {group.icon}
                <span>{group.title}</span>
              </div>

              <div className="flex flex-col gap-1.5 bg-white/[0.02] border border-white/[0.04] rounded-2xl p-2.5">
                {group.shortcuts.map((sc) => (
                  <div
                    key={sc.description}
                    className="flex items-center justify-between py-1.5 px-2 rounded-xl hover:bg-white/[0.04] transition-colors"
                  >
                    <span className="text-xs font-medium text-[#D0CEE0]">{sc.description}</span>
                    <div className="flex items-center gap-1">
                      {sc.keys.map((k) => (
                        <kbd
                          key={k}
                          className="px-2.5 py-1 text-[11px] font-mono font-bold bg-[#1C1A2B] text-white rounded-lg border border-white/10 shadow-sm"
                        >
                          {k}
                        </kbd>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>

        {/* Footer */}
        <div className="mt-5 pt-3 border-t border-white/[0.06] text-center">
          <p className="text-[11px] text-[#6B6A7A]">
            Press <kbd className="px-1.5 py-0.5 text-[10px] font-mono bg-[#1C1A2B] text-white rounded border border-white/10">?</kbd> anywhere to open this menu
          </p>
        </div>
      </div>
    </div>
  );
};
