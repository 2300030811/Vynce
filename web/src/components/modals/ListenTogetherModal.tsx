import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Users,
  Radio,
  Copy,
  Check,
  X,
  Play,
  Pause,
  Crown,
  Heart,
  Flame,
  Music,
  Rocket,
  Sparkles,
  Share2,
  LogOut,
  RefreshCw,
  Zap,
  Wifi,
  Smartphone,
  Globe,
  Activity,
} from 'lucide-react';
import { useListenTogetherStore } from '../../stores/listenTogetherStore';
import { usePlayerStore } from '../../stores/playerStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { APP_ICONS, handleImageFallback } from '../../utils/assets';

interface ListenTogetherModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ListenTogetherModal: React.FC<ListenTogetherModalProps> = ({
  isOpen,
  onClose,
}) => {
  const {
    roomCode,
    isHost,
    participants,
    reactions,
    syncStatus,
    syncLatencyMs,
    hostDeviceType,
    createRoom,
    joinRoom,
    leaveRoom,
    sendReaction,
    broadcastPlayback,
    forceResync,
  } = useListenTogetherStore();

  const { currentSong, status, currentTime } = usePlayerStore();
  const { userName } = useSettingsStore();

  const [joinCodeInput, setJoinCodeInput] = useState('');
  const [displayNameInput, setDisplayNameInput] = useState(userName || '');
  const [copied, setCopied] = useState(false);
  const [isJoining, setIsJoining] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const emojis = ['❤️', '🔥', '🎵', '👏', '🚀', '🎉'];

  const handleStartSession = () => {
    const code = createRoom(displayNameInput || userName);
    if (currentSong) {
      broadcastPlayback(currentSong, status === 'playing', currentTime);
    }
  };

  const handleJoinSession = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    if (!joinCodeInput.trim()) {
      setErrorMsg('Please enter a room code or Vynce app IP hex code');
      return;
    }

    setIsJoining(true);
    try {
      const success = await joinRoom(joinCodeInput.trim(), displayNameInput || userName);
      if (!success) {
        setErrorMsg('Could not connect to session. Check code or IP.');
      }
    } catch {
      setErrorMsg('Failed to join room.');
    } finally {
      setIsJoining(false);
    }
  };

  const handleCopyCode = () => {
    if (!roomCode) return;
    navigator.clipboard.writeText(roomCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleCopyInviteLink = () => {
    if (!roomCode) return;
    const url = `https://vynce.app/together/${roomCode}`;
    navigator.clipboard.writeText(`Join my Vynce Listen Together session: ${url} (Room Code: ${roomCode})`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!isOpen) return null;

  return createPortal(
    <div
      onClick={onClose}
      className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
    >
      <div
        className="relative w-full max-w-lg max-h-[85vh] bg-[#121118] border border-white/10 rounded-[32px] shadow-2xl flex flex-col overflow-hidden animate-in zoom-in-95 duration-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-3 rounded-2xl shadow-inner relative"
            >
              <Users className="w-5 h-5" />
              {roomCode && (
                <span className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-green-500 animate-pulse border-2 border-[#121118]" />
              )}
            </div>
            <div>
              <h2 className="text-xl font-bold text-white font-['Outfit'] flex items-center gap-2">
                <span>Listen Together</span>
                {roomCode && (
                  <span className="text-[10px] font-mono font-bold bg-green-500/20 text-green-400 px-2 py-0.5 rounded-full border border-green-500/30 flex items-center gap-1">
                    <Activity className="w-2.5 h-2.5 animate-pulse" /> Live Sync
                  </span>
                )}
              </h2>
              <p className="text-xs text-[#6B6A7A]">
                {roomCode
                  ? `Room ${roomCode} • ${participants.length} listener${participants.length === 1 ? '' : 's'}`
                  : 'Stream music in sync across devices & Wi-Fi'}
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-full text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {!roomCode ? (
            /* ── STATE 1: CREATE OR JOIN ROOM ── */
            <div className="flex flex-col gap-6">
              {/* Feature Showcase Banner */}
              <div className="p-4 rounded-2xl bg-gradient-to-br from-[var(--vynce-primary-container)]/30 to-white/[0.02] border border-[var(--vynce-primary)]/20 flex flex-col gap-2">
                <div className="flex items-center gap-2 text-[var(--vynce-primary)] font-bold text-xs">
                  <Radio className="w-4 h-4 animate-pulse" />
                  <span>Real-time Zero-Latency Audio Sync</span>
                </div>
                <p className="text-xs text-[#E8E6F0]/80 leading-relaxed">
                  Start a room or enter a code to sync with your phone or friends. Compatible with both <strong>Vynce Android App</strong> on Wi-Fi and browser sessions!
                </p>
              </div>

              {/* Display Name Input */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
                  Your Display Name
                </label>
                <input
                  type="text"
                  value={displayNameInput}
                  onChange={(e) => setDisplayNameInput(e.target.value)}
                  placeholder="e.g. Alex"
                  className="px-4 py-2.5 rounded-2xl bg-white/[0.04] border border-white/10 text-sm font-semibold text-white outline-none focus:border-[var(--vynce-primary)]"
                />
              </div>

              {/* Host Action */}
              <div className="flex flex-col gap-2.5">
                <button
                  onClick={handleStartSession}
                  style={{
                    backgroundColor: 'var(--vynce-primary-container)',
                    color: 'var(--vynce-on-primary-container)',
                  }}
                  className="flex items-center justify-center gap-2.5 py-3.5 px-6 rounded-2xl font-bold text-sm shadow-xl transition-transform active:scale-95 hover:scale-102 cursor-pointer"
                >
                  <Radio className="w-4 h-4" />
                  <span>Host New Session (Room Leader)</span>
                </button>
              </div>

              <div className="flex items-center gap-3">
                <div className="h-px bg-white/10 flex-1" />
                <span className="text-[11px] font-bold text-[#6B6A7A] uppercase">Or Join An Existing Room</span>
                <div className="h-px bg-white/10 flex-1" />
              </div>

              {/* Join Form */}
              <form onSubmit={handleJoinSession} className="flex flex-col gap-3">
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="Enter Code (e.g. C0A8012D or VYN842)"
                    value={joinCodeInput}
                    onChange={(e) => setJoinCodeInput(e.target.value.toUpperCase())}
                    className="flex-1 px-4 py-2.5 rounded-2xl bg-white/[0.04] border border-white/10 text-sm font-mono font-bold text-white placeholder:text-[#6B6A7A] uppercase outline-none focus:border-[var(--vynce-primary)] tracking-wider"
                  />
                  <button
                    type="submit"
                    disabled={isJoining}
                    className="px-5 py-2.5 rounded-2xl bg-white/[0.06] hover:bg-white/[0.12] text-white font-bold text-xs border border-white/10 transition-colors cursor-pointer disabled:opacity-50"
                  >
                    {isJoining ? 'Connecting...' : 'Join'}
                  </button>
                </div>
                {errorMsg && (
                  <p className="text-xs text-red-400 font-semibold">{errorMsg}</p>
                )}
                <div className="flex items-center gap-2 text-[11px] text-[#6B6A7A]">
                  <Wifi className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  <span>Supports 6-letter web codes and 8-hex Wi-Fi codes from the Vynce App</span>
                </div>
              </form>
            </div>
          ) : (
            /* ── STATE 2: ACTIVE SESSION IN PROGRESS ── */
            <div className="flex flex-col gap-5">
              {/* Room Code Card */}
              <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/10 flex items-center justify-between">
                <div className="flex flex-col">
                  <span className="text-[10px] uppercase font-bold text-[#6B6A7A] tracking-wider flex items-center gap-1.5">
                    {hostDeviceType === 'vynce_app' ? (
                      <span className="flex items-center gap-1 text-cyan-400">
                        <Smartphone className="w-3 h-3" /> Vynce App Host
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 text-[var(--vynce-primary)]">
                        <Globe className="w-3 h-3" /> Shared Web Room
                      </span>
                    )}
                  </span>
                  <span className="text-2xl font-black font-mono text-white tracking-widest mt-0.5">
                    {roomCode}
                  </span>
                  {syncLatencyMs !== null && (
                    <span className="text-[10px] font-mono text-green-400 mt-0.5">
                      Ping: {syncLatencyMs}ms • Millisecond Synced
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={handleCopyCode}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-xs font-bold text-white transition-colors cursor-pointer"
                    title="Copy Code"
                  >
                    {copied ? <Check className="w-3.5 h-3.5 text-green-400" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copied ? 'Copied' : 'Copy'}</span>
                  </button>

                  <button
                    onClick={handleCopyInviteLink}
                    className="p-2 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-white transition-colors cursor-pointer"
                    title="Share Invite"
                  >
                    <Share2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* Currently Playing Track */}
              {currentSong ? (
                <div className="p-3.5 rounded-2xl bg-gradient-to-r from-white/[0.04] to-white/[0.01] border border-white/10 flex items-center gap-3.5">
                  <div className="relative w-12 h-12 rounded-xl overflow-hidden bg-white/5 border border-white/10 shrink-0 shadow-md">
                    <img
                      src={currentSong.image || APP_ICONS.favicon}
                      alt={currentSong.name}
                      className="w-full h-full object-cover"
                      onError={handleImageFallback}
                    />
                  </div>
                  <div className="flex flex-col min-w-0 flex-1">
                    <span className="text-xs font-bold text-white truncate">
                      {currentSong.name}
                    </span>
                    <span className="text-[11px] text-[#8E8D9F] truncate">
                      {currentSong.primaryArtists}
                    </span>
                  </div>

                  <button
                    onClick={forceResync}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-[#E8E6F0] hover:text-white text-xs font-semibold transition-colors cursor-pointer"
                    title="Force Resync Playback"
                  >
                    <RefreshCw className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
                    <span>Resync</span>
                  </button>
                </div>
              ) : (
                <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/5 text-center text-xs text-[#6B6A7A]">
                  Play any song to broadcast to room participants.
                </div>
              )}

              {/* Reaction Bar */}
              <div className="flex flex-col gap-2">
                <span className="text-[10px] uppercase font-bold text-[#6B6A7A] tracking-wider">
                  Live Reactions
                </span>
                <div className="flex items-center gap-2 justify-between">
                  {emojis.map((emoji) => (
                    <button
                      key={emoji}
                      onClick={() => sendReaction(emoji)}
                      className="text-xl p-2 rounded-2xl bg-white/[0.03] hover:bg-white/[0.08] hover:scale-125 active:scale-90 transition-all cursor-pointer shadow-sm"
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
              </div>

              {/* Live Floating Reactions Feed */}
              {reactions.length > 0 && (
                <div className="flex items-center gap-1.5 overflow-x-auto py-1">
                  {reactions.slice(-6).map((r) => (
                    <span
                      key={r.id}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white/[0.06] border border-white/10 text-xs text-white animate-in zoom-in-50 duration-200"
                    >
                      <span>{r.emoji}</span>
                      <span className="text-[10px] text-[#8E8D9F]">{r.fromName}</span>
                    </span>
                  ))}
                </div>
              )}

              {/* Connected Participants List */}
              <div className="flex flex-col gap-2.5">
                <span className="text-[10px] uppercase font-bold text-[#6B6A7A] tracking-wider">
                  Participants ({participants.length})
                </span>
                <div className="flex flex-col gap-1.5 max-h-36 overflow-y-auto">
                  {participants.map((p) => (
                    <div
                      key={p.id}
                      className="flex items-center justify-between p-2.5 rounded-2xl bg-white/[0.02] border border-white/5"
                    >
                      <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 rounded-xl bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] flex items-center justify-center font-bold text-xs shadow-sm">
                          {p.name.slice(0, 1).toUpperCase()}
                        </div>
                        <span className="text-xs font-semibold text-white">
                          {p.name}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        {p.isHost ? (
                          <span className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-400 bg-amber-400/10 px-2 py-0.5 rounded-full border border-amber-400/20">
                            <Crown className="w-3 h-3" /> Host
                          </span>
                        ) : (
                          <span className="text-[10px] text-[#6B6A7A] font-medium">
                            Listener
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Room Actions */}
              <div className="flex items-center gap-2 pt-2 border-t border-white/10">
                <button
                  onClick={leaveRoom}
                  className="flex-1 flex items-center justify-center gap-2 py-2.5 rounded-2xl bg-red-500/10 hover:bg-red-500/20 text-red-400 font-bold text-xs transition-colors border border-red-500/20 cursor-pointer"
                >
                  <LogOut className="w-4 h-4" />
                  <span>{isHost ? 'End Session' : 'Leave Room'}</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
};
