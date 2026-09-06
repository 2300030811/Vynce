import React, { useState } from 'react';
import { usePlayerStore } from '../../stores/playerStore';
import { Moon, X, Clock, Check, BellOff } from 'lucide-react';
import { formatTime } from '../../utils/formatters';

export const SleepTimerModal: React.FC = () => {
  const {
    isSleepTimerOpen,
    toggleSleepTimer,
    sleepTimerRemaining,
    setSleepTimer,
  } = usePlayerStore();

  const [customMins, setCustomMins] = useState('');

  if (!isSleepTimerOpen) return null;

  const presets = [
    { label: '5 minutes', value: 5 },
    { label: '10 minutes', value: 10 },
    { label: '15 minutes', value: 15 },
    { label: '30 minutes', value: 30 },
    { label: '45 minutes', value: 45 },
    { label: '60 minutes (1 hr)', value: 60 },
    { label: '90 minutes', value: 90 },
  ];

  const handleCustomSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const num = parseInt(customMins, 10);
    if (num && num > 0) {
      setSleepTimer(num);
      setCustomMins('');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-sm bg-[#0A0A0A] rounded-3xl p-6 border border-white/10 flex flex-col gap-5 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-xl shadow-inner"
            >
              <Moon className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white font-['Outfit']">
                Sleep Timer
              </h3>
              <p className="text-[11px] text-[#6B6A7A]">
                Fade out audio & pause music automatically
              </p>
            </div>
          </div>
          <button
            onClick={toggleSleepTimer}
            className="p-2 rounded-full hover:bg-white/10 text-[#6B6A7A] hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Active Timer Indicator */}
        {sleepTimerRemaining !== null && sleepTimerRemaining > 0 && (
          <div
            style={{
              backgroundColor: 'color-mix(in srgb, var(--vynce-primary-container) 40%, transparent)',
              borderColor: 'var(--vynce-primary)',
            }}
            className="flex items-center justify-between p-3.5 rounded-2xl border animate-pulse"
          >
            <div className="flex items-center gap-2.5">
              <Clock className="w-4 h-4 text-[var(--vynce-primary)]" />
              <span className="text-xs font-semibold text-white">
                Timer active:
              </span>
            </div>
            <span className="font-mono text-sm font-extrabold text-[var(--vynce-primary)]">
              {formatTime(sleepTimerRemaining)}
            </span>
          </div>
        )}

        {/* Presets Grid */}
        <div className="grid grid-cols-2 gap-2">
          {presets.map((p) => (
            <button
              key={p.value}
              onClick={() => setSleepTimer(p.value)}
              className="flex items-center justify-between p-3 rounded-2xl bg-white/[0.04] hover:bg-white/[0.08] hover:border-[var(--vynce-primary)]/40 border border-white/5 text-xs font-semibold text-white transition-all text-left group"
            >
              <span>{p.label}</span>
              <Check className="w-3.5 h-3.5 text-[var(--vynce-primary)] opacity-0 group-hover:opacity-100 transition-opacity" />
            </button>
          ))}
        </div>

        {/* Custom Minutes Input */}
        <form onSubmit={handleCustomSubmit} className="flex gap-2">
          <input
            type="number"
            min="1"
            max="300"
            placeholder="Custom mins (e.g. 20)"
            value={customMins}
            onChange={(e) => setCustomMins(e.target.value)}
            className="flex-1 px-4 py-2 rounded-2xl bg-white/5 border border-white/10 text-xs text-white placeholder:text-[#6B6A7A] outline-none focus:border-[var(--vynce-primary)]"
          />
          <button
            type="submit"
            style={{
              backgroundColor: 'var(--vynce-primary-container)',
              color: 'var(--vynce-on-primary-container)',
            }}
            className="px-4 py-2 rounded-2xl text-xs font-bold transition-transform active:scale-95 shadow-md shrink-0"
          >
            Set
          </button>
        </form>

        {/* Cancel Timer Button */}
        {sleepTimerRemaining !== null && (
          <button
            onClick={() => setSleepTimer(null)}
            className="flex items-center justify-center gap-2 w-full py-2.5 rounded-2xl bg-red-500/10 hover:bg-red-500/20 text-red-400 font-bold text-xs transition-colors border border-red-500/20"
          >
            <BellOff className="w-3.5 h-3.5" /> Turn Off Timer
          </button>
        )}
      </div>
    </div>
  );
};
