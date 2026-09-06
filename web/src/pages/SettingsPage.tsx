import React, { useState, useRef } from 'react';
import { useSettingsStore, AVAILABLE_LANGUAGES } from '../stores/settingsStore';
import { useLibraryStore } from '../stores/libraryStore';
import { usePlayerStore } from '../stores/playerStore';
import { db } from '../db';
import { generateVynceTheme, applyVynceTheme, extractSeedColorFromImage, DEFAULT_VYNCE_SEED } from '../utils/themeGenerator';
import { AudioCacheManager } from '../utils/audioCache';
import {
  Sliders,
  Play,
  Languages,
  Palette,
  Mic2,
  HardDrive,
  Keyboard,
  Info,
  Check,
  Download,
  Upload,
  Trash2,
  Sparkles,
  RefreshCw,
  ExternalLink,
  Volume2,
  Zap,
  User,
  Radio,
  ShieldCheck,
  Heart,
  Disc3,
  ListMusic,
  Clock,
  Edit2,
  Users,
  Monitor,
  CheckCircle2,
  ArrowUpCircle,
  AlertCircle,
} from 'lucide-react';
import { ListenTogetherModal } from '../components/modals/ListenTogetherModal';
import { useListenTogetherStore } from '../stores/listenTogetherStore';

import { NavTab } from '../components/layout/Sidebar';

const THEME_PRESETS = [
  { id: 'dynamic', name: 'Dynamic Material 3', color: '#9B6EEA', desc: 'Adapts to song cover' },
  { id: '#9B6EEA', name: 'Midnight Violet', color: '#9B6EEA', desc: 'Signature Vynce purple' },
  { id: '#00F5D4', name: 'Cyber Neon Teal', color: '#00F5D4', desc: 'Vibrant futuristic teal' },
  { id: '#FF6B8B', name: 'Neon Rose', color: '#FF6B8B', desc: 'Punchy vivid rose' },
  { id: '#10B981', name: 'Emerald Deep', color: '#10B981', desc: 'Organic lush green' },
  { id: '#F59E0B', name: 'Sunset Amber', color: '#F59E0B', desc: 'Warm dusk radiance' },
  { id: '#3B82F6', name: 'Electric Blue', color: '#3B82F6', desc: 'Deep acoustic blue' },
];

interface SettingsPageProps {
  onNavigateToTab?: (tab: NavTab) => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({ onNavigateToTab }) => {
  const {
    userName,
    userBio,
    userAvatar,
    userTitle,
    customAvatarUrl,
    setUserName,
    setUserBio,
    setUserAvatar,
    setCustomAvatarUrl,
    bitrate,
    setBitrate,
    languages,
    toggleLanguage,
    setLanguages,
    accentColor,
    setAccentColor,
    crossfadeSec,
    setCrossfadeSec,
    visualizerStyle,
    setVisualizerStyle,
    ambientGlow,
    setAmbientGlow,
    autoScrollLyrics,
    setAutoScrollLyrics,
    loudnessNormalization,
    setLoudnessNormalization,
    autoplaySimilar,
    setAutoplaySimilar,
    explicitContent,
    setExplicitContent,
    lyricsTransliteration,
    setLyricsTransliteration,
  } = useSettingsStore();

  const { clearHistory, history, likedSongs, playlists, savedAlbums, loadLibrary } = useLibraryStore();
  const { toggleEqualizer, lyricsOffset, setLyricsOffset, currentSong } = usePlayerStore();

  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [isListenTogetherOpen, setIsListenTogetherOpen] = useState(false);
  const { roomCode, participants } = useListenTogetherStore();
  const [profileNameInput, setProfileNameInput] = useState(userName);
  const [profileBioInput, setProfileBioInput] = useState(userBio);
  const [exportSuccess, setExportSuccess] = useState(false);
  const [importSuccess, setImportSuccess] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Desktop Auto-Updater State
  const isElectron = typeof window !== 'undefined' && Boolean(window.electronAPI?.isElectron);
  const [appVersion, setAppVersion] = useState<string>('1.0.0');
  const [updateStatus, setUpdateStatus] = useState<string>('idle');
  const [updateMsg, setUpdateMsg] = useState<string>('');
  const [downloadProgress, setDownloadProgress] = useState<number>(0);

  React.useEffect(() => {
    if (isElectron && window.electronAPI) {
      window.electronAPI.getAppVersion().then(setAppVersion).catch(() => {});

      const unsubscribe = window.electronAPI.onUpdaterEvent((event) => {
        if (event.status === 'checking') {
          setUpdateStatus('checking');
          setUpdateMsg('Checking GitHub Releases for updates...');
        } else if (event.status === 'available') {
          setUpdateStatus('available');
          setUpdateMsg(`Version ${event.version || ''} is available!`);
        } else if (event.status === 'not-available') {
          setUpdateStatus('up-to-date');
          setUpdateMsg('Vynce is up to date.');
        } else if (event.status === 'downloading') {
          setUpdateStatus('downloading');
          setDownloadProgress(Math.round(event.percent || 0));
          setUpdateMsg(`Downloading update... ${Math.round(event.percent || 0)}%`);
        } else if (event.status === 'downloaded') {
          setUpdateStatus('downloaded');
          setUpdateMsg('Update downloaded! Restart Vynce to apply.');
        } else if (event.status === 'error') {
          setUpdateStatus('error');
          setUpdateMsg(event.error || 'Failed to check updates');
        } else if (event.status === 'dev-mode') {
          setUpdateStatus('dev-mode');
          setUpdateMsg(event.message || 'Auto-updater is inactive in development mode.');
        }
      });

      return () => unsubscribe?.();
    }
  }, [isElectron]);

  const handleCheckUpdates = async () => {
    if (!window.electronAPI) return;
    setUpdateStatus('checking');
    setUpdateMsg('Checking for updates...');
    try {
      const res = await window.electronAPI.checkForUpdates();
      if (res?.status === 'dev-mode') {
        setUpdateStatus('dev-mode');
        setUpdateMsg(res.message || 'Auto-updater is disabled in dev mode.');
      }
    } catch (err: any) {
      setUpdateStatus('error');
      setUpdateMsg(err?.message || 'Update check failed.');
    }
  };

  const handleDownloadUpdate = async () => {
    if (!window.electronAPI) return;
    setUpdateStatus('downloading');
    await window.electronAPI.downloadUpdate();
  };

  const handleInstallUpdate = async () => {
    if (!window.electronAPI) return;
    await window.electronAPI.quitAndInstall();
  };

  const avatarColors: { id: string; bg: string; border: string; label: string }[] = [
    { id: 'purple', bg: 'from-purple-600 to-indigo-900', border: 'border-purple-400', label: 'Neon Purple' },
    { id: 'cyan', bg: 'from-cyan-500 to-blue-900', border: 'border-cyan-400', label: 'Electric Blue' },
    { id: 'emerald', bg: 'from-emerald-500 to-teal-900', border: 'border-emerald-400', label: 'Emerald' },
    { id: 'amber', bg: 'from-amber-500 to-orange-900', border: 'border-amber-400', label: 'Sunset Amber' },
    { id: 'rose', bg: 'from-rose-500 to-pink-900', border: 'border-rose-400', label: 'Rose Pink' },
    { id: 'gold', bg: 'from-yellow-500 to-amber-900', border: 'border-yellow-400', label: 'Royal Gold' },
  ];

  const currentAvatar = avatarColors.find((a) => a.id === userAvatar) || avatarColors[0];

  const handleSaveProfileInline = () => {
    if (profileNameInput.trim()) {
      setUserName(profileNameInput.trim());
      setUserBio(profileBioInput.trim());
      setIsEditingProfile(false);
      setToastMsg('Profile updated!');
      setTimeout(() => setToastMsg(null), 2500);
    }
  };

  const handleSelectAccent = async (hex: string) => {
    setAccentColor(hex);
    if (hex === 'dynamic') {
      if (currentSong?.image) {
        const seed = await extractSeedColorFromImage(currentSong.image);
        const theme = generateVynceTheme(seed);
        applyVynceTheme(theme);
      } else {
        const theme = generateVynceTheme(DEFAULT_VYNCE_SEED);
        applyVynceTheme(theme);
      }
    } else {
      const theme = generateVynceTheme(hex);
      applyVynceTheme(theme);
    }
  };

  const handleExportBackup = () => {
    const backupData = {
      version: '1.2.0',
      exportedAt: new Date().toISOString(),
      playlists,
      likedSongs,
      savedAlbums,
      history,
    };
    const blob = new Blob([JSON.stringify(backupData, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `vynce-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    setExportSuccess(true);
    setTimeout(() => setExportSuccess(false), 2000);
  };

  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        if (json.playlists && Array.isArray(json.playlists)) {
          for (const pl of json.playlists) {
            await db.playlists.put(pl);
          }
        }
        if (json.likedSongs && Array.isArray(json.likedSongs)) {
          for (const s of json.likedSongs) {
            await db.likedSongs.put(s);
          }
        }
        if (json.savedAlbums && Array.isArray(json.savedAlbums)) {
          for (const alb of json.savedAlbums) {
            await db.savedAlbums.put(alb);
          }
        }
        await loadLibrary();
        setImportSuccess(true);
        setTimeout(() => setImportSuccess(false), 2000);
      } catch {
        alert('Invalid backup JSON file.');
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex flex-col gap-8 pb-36 w-full animate-in fade-in duration-300">
      {/* ── Page Header ───────────────────────────────────── */}
      <div className="flex flex-col gap-1 pt-1">
        <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white font-['Outfit']">
          Settings & Control Center
        </h1>
        <p className="text-sm text-[#6B6A7A] font-medium">
          Personalize audio fidelity, discovery languages, interface aesthetics, and account preferences
        </p>
      </div>

      {toastMsg && (
        <div className="flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] text-xs font-bold shadow-lg animate-in fade-in">
          <Check className="w-4 h-4" />
          <span>{toastMsg}</span>
        </div>
      )}

      {/* ── Top Hero: Account & Listener Profile Banner ───── */}
      <section className="bg-[#0A0A0A] rounded-[36px] p-6 md:p-8 border border-white/10 shadow-2xl flex flex-col md:flex-row items-center justify-between gap-6">
        <div className="flex flex-col md:flex-row items-center gap-5 w-full md:w-auto">
          {/* Avatar Preview */}
          <div className="relative shrink-0">
            {customAvatarUrl ? (
              <img
                src={customAvatarUrl}
                alt={userName}
                className="w-20 h-20 rounded-2xl object-cover border-2 border-[var(--vynce-primary)] shadow-2xl"
              />
            ) : (
              <div
                className={`w-20 h-20 rounded-2xl bg-gradient-to-br ${currentAvatar.bg} border-2 ${currentAvatar.border} shadow-2xl flex items-center justify-center`}
              >
                <span className="text-2xl font-black text-white font-['Outfit']">
                  {userName.slice(0, 2).toUpperCase()}
                </span>
              </div>
            )}
            <div className="absolute -bottom-1 -right-1 p-1 rounded-full bg-black/80 border border-white/20">
              <ShieldCheck className="w-3.5 h-3.5 text-[var(--vynce-primary)]" />
            </div>
          </div>

          {/* Name & Details */}
          <div className="flex flex-col items-center md:items-start text-center md:text-left min-w-0">
            {isEditingProfile ? (
              <div className="flex flex-col gap-2 w-full max-w-sm">
                <input
                  type="text"
                  value={profileNameInput}
                  onChange={(e) => setProfileNameInput(e.target.value)}
                  placeholder="Your Name"
                  className="px-3 py-1.5 rounded-xl bg-white/[0.06] border border-white/20 text-sm font-bold text-white outline-none w-full"
                  autoFocus
                />
                <input
                  type="text"
                  value={profileBioInput}
                  onChange={(e) => setProfileBioInput(e.target.value)}
                  placeholder="Bio / Tagline"
                  className="px-3 py-1.5 rounded-xl bg-white/[0.06] border border-white/20 text-xs text-[#E8E6F0] outline-none w-full"
                />
                <div className="flex items-center gap-2 mt-1">
                  <button
                    onClick={handleSaveProfileInline}
                    style={{
                      backgroundColor: 'var(--vynce-primary-container)',
                      color: 'var(--vynce-on-primary-container)',
                    }}
                    className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold shadow-sm"
                  >
                    <Check className="w-3.5 h-3.5" /> Save
                  </button>
                  <button
                    onClick={() => setIsEditingProfile(false)}
                    className="text-xs text-[#6B6A7A] hover:text-white px-2 py-1"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex items-center gap-2">
                  <h2 className="text-2xl font-black text-white font-['Outfit'] truncate">
                    {userName}
                  </h2>
                  <button
                    onClick={() => {
                      setProfileNameInput(userName);
                      setProfileBioInput(userBio);
                      setIsEditingProfile(true);
                    }}
                    className="p-1 rounded-lg text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors"
                    title="Edit Name & Bio"
                  >
                    <Edit2 className="w-3.5 h-3.5" />
                  </button>
                </div>
                <p className="text-xs text-[#8E8D9F] mt-0.5 max-w-md">
                  {userBio}
                </p>
                <div className="flex items-center gap-2 mt-2">
                  <span
                    style={{
                      backgroundColor: 'var(--vynce-primary-container)',
                      color: 'var(--vynce-on-primary-container)',
                    }}
                    className="text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase tracking-wider shadow-sm"
                  >
                    {userTitle}
                  </span>
                  <span className="text-[11px] text-[#8E8D9F] font-mono">
                    {bitrate} Studio Stream
                  </span>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Quick Collection Counters */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center min-w-[70px] text-center">
            <Heart className="w-4 h-4 text-[var(--vynce-primary)] mb-1" />
            <span className="text-base font-black text-white font-['Outfit']">{likedSongs.length}</span>
            <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Likes</span>
          </div>

          <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center min-w-[70px] text-center">
            <Disc3 className="w-4 h-4 text-[var(--vynce-primary)] mb-1" />
            <span className="text-base font-black text-white font-['Outfit']">{savedAlbums.length}</span>
            <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Albums</span>
          </div>

          <div className="p-3.5 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center min-w-[70px] text-center">
            <ListMusic className="w-4 h-4 text-[var(--vynce-primary)] mb-1" />
            <span className="text-base font-black text-white font-['Outfit']">{playlists.length}</span>
            <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Playlists</span>
          </div>
        </div>
      </section>

      {/* ── 2-Column Responsive Bento Grid ────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        {/* CARD 1: 🎧 AUDIO FIDELITY & DSP ENGINE */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-inner"
            >
              <Volume2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Audio Stream & Fidelity
              </h2>
              <p className="text-xs text-[#6B6A7A]">High-resolution audio streaming parameters</p>
            </div>
          </div>

          {/* Audio Bitrate Selector */}
          <div className="flex flex-col gap-2">
            <label className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
              Streaming Bitrate
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: '320kbps', label: '320 kbps', badge: 'High Fidelity' },
                { id: '160kbps', label: '160 kbps', badge: 'Standard' },
                { id: '96kbps', label: '96 kbps', badge: 'Data Saver' },
              ].map((opt) => {
                const isActive = bitrate === opt.id;
                return (
                  <button
                    key={opt.id}
                    onClick={() => setBitrate(opt.id as any)}
                    style={
                      isActive
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                            borderColor: 'var(--vynce-primary)',
                          }
                        : {}
                    }
                    className={`flex flex-col items-center justify-center p-3 rounded-2xl text-center border transition-all cursor-pointer ${
                      isActive
                        ? 'shadow-md scale-102 font-bold'
                        : 'bg-white/[0.03] border-white/5 text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.06]'
                    }`}
                  >
                    <span className="text-xs font-extrabold">{opt.label}</span>
                    <span className="text-[10px] opacity-70 mt-0.5">{opt.badge}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Crossfade Duration Slider */}
          <div className="flex flex-col gap-2 pt-1">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
                Gapless Crossfade
              </label>
              <span className="text-xs font-mono font-bold text-[var(--vynce-primary)]">
                {crossfadeSec === 0 ? 'Off (0s)' : `${crossfadeSec}s`}
              </span>
            </div>
            <input
              type="range"
              min="0"
              max="12"
              step="1"
              value={crossfadeSec}
              onChange={(e) => setCrossfadeSec(parseInt(e.target.value, 10))}
              style={{ accentColor: 'var(--vynce-primary)' }}
              className="w-full h-1.5 bg-white/10 rounded-full cursor-pointer"
            />
          </div>

          {/* Loudness Normalization (ReplayGain) Toggle */}
          <div className="flex items-center justify-between p-4 rounded-2xl bg-white/[0.03] border border-white/5">
            <div className="flex items-center gap-3">
              <Zap className="w-5 h-5 text-cyan-400" />
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white">Loudness Normalization (ReplayGain)</span>
                <span className="text-[11px] text-[#6B6A7A]">Uniform volume level across soft & loud tracks</span>
              </div>
            </div>
            <button
              onClick={() => setLoudnessNormalization(!loudnessNormalization)}
              style={loudnessNormalization ? { backgroundColor: 'var(--vynce-primary)' } : {}}
              className={`w-11 h-6 rounded-full p-1 transition-colors cursor-pointer ${
                loudnessNormalization ? '' : 'bg-white/10'
              }`}
            >
              <div
                className={`w-4 h-4 rounded-full bg-white transition-transform ${
                  loudnessNormalization ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Smart Autoplay Similar Songs */}
          <div className="flex items-center justify-between p-4 rounded-2xl bg-white/[0.03] border border-white/5">
            <div className="flex items-center gap-3">
              <Radio className="w-5 h-5 text-[var(--vynce-primary)]" />
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white">Infinite Smart Autoplay</span>
                <span className="text-[11px] text-[#6B6A7A]">Auto-play similar recommendations when queue ends</span>
              </div>
            </div>
            <button
              onClick={() => setAutoplaySimilar(!autoplaySimilar)}
              style={autoplaySimilar ? { backgroundColor: 'var(--vynce-primary)' } : {}}
              className={`w-11 h-6 rounded-full p-1 transition-colors cursor-pointer ${
                autoplaySimilar ? '' : 'bg-white/10'
              }`}
            >
              <div
                className={`w-4 h-4 rounded-full bg-white transition-transform ${
                  autoplaySimilar ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          {/* Listen Together Quick Launch Banner */}
          <div className="flex items-center justify-between p-4 rounded-2xl bg-gradient-to-r from-[var(--vynce-primary-container)]/20 to-white/[0.02] border border-[var(--vynce-primary)]/20">
            <div className="flex items-center gap-3">
              <Users className="w-5 h-5 text-[var(--vynce-primary)]" />
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white flex items-center gap-2">
                  <span>Listen Together (Group Sync)</span>
                  {roomCode && (
                    <span className="text-[10px] font-mono font-bold bg-green-500/20 text-green-400 px-2 py-0.5 rounded-full border border-green-500/30">
                      Room {roomCode} ({participants.length})
                    </span>
                  )}
                </span>
                <span className="text-[11px] text-[#6B6A7A]">Real-time cross-device listening party with live reactions</span>
              </div>
            </div>
            <button
              onClick={() => setIsListenTogetherOpen(true)}
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="px-4 py-2 rounded-xl text-xs font-bold transition-transform active:scale-95 shadow-md hover:scale-105 cursor-pointer shrink-0"
            >
              {roomCode ? 'Manage Room' : 'Start / Join'}
            </button>
          </div>

          {/* Equalizer Quick Launch Banner */}
          <div className="flex items-center justify-between p-4 rounded-2xl bg-white/[0.03] border border-white/5">
            <div className="flex items-center gap-3">
              <Sliders className="w-5 h-5 text-[var(--vynce-primary)]" />
              <div className="flex flex-col">
                <span className="text-xs font-bold text-white">10-Band Acoustic Equalizer</span>
                <span className="text-[11px] text-[#6B6A7A]">Hardware Web Audio DSP filter chain</span>
              </div>
            </div>
            <button
              onClick={() => {
                if (onNavigateToTab) {
                  onNavigateToTab('equalizer');
                } else {
                  toggleEqualizer();
                }
              }}
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="px-4 py-2 rounded-xl text-xs font-bold transition-transform active:scale-95 shadow-md hover:scale-105 cursor-pointer"
            >
              Open EQ & DSP
            </button>
          </div>
        </section>

        {/* CARD 2: 🎨 APPEARANCE & ACCENT PALETTE */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-inner"
            >
              <Palette className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Appearance & Theme Palette
              </h2>
              <p className="text-xs text-[#6B6A7A]">Material You 3 dynamic color generation</p>
            </div>
          </div>

          {/* Preset Swatches */}
          <div className="flex flex-col gap-2">
            <label className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
              Color Accent Mode
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {THEME_PRESETS.map((preset) => {
                const isActive = accentColor === preset.id;
                return (
                  <button
                    key={preset.id}
                    onClick={() => handleSelectAccent(preset.id)}
                    className={`flex items-center gap-2.5 p-3 rounded-2xl border text-left transition-all cursor-pointer ${
                      isActive
                        ? 'border-[var(--vynce-primary)] bg-white/[0.08] shadow-md scale-102'
                        : 'border-white/5 bg-white/[0.02] hover:bg-white/[0.05]'
                    }`}
                  >
                    <span
                      style={{ backgroundColor: preset.color }}
                      className="w-4 h-4 rounded-full shrink-0 shadow-sm"
                    />
                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-bold text-white truncate">{preset.name}</span>
                      <span className="text-[10px] text-[#6B6A7A] truncate">{preset.desc}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Visualizer Style */}
          <div className="flex flex-col gap-2 pt-1">
            <label className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
              Audio Visualizer Style
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'bars', label: 'Frequency Bars' },
                { id: 'wave', label: 'Waveform' },
                { id: 'circle', label: 'Circular Ring' },
              ].map((style) => {
                const isActive = visualizerStyle === style.id;
                return (
                  <button
                    key={style.id}
                    onClick={() => setVisualizerStyle(style.id as any)}
                    style={
                      isActive
                        ? {
                            backgroundColor: 'var(--vynce-primary-container)',
                            color: 'var(--vynce-on-primary-container)',
                          }
                        : {}
                    }
                    className={`py-2.5 px-3 rounded-2xl text-xs font-bold text-center border transition-all cursor-pointer ${
                      isActive
                        ? 'border-transparent shadow-sm'
                        : 'border-white/5 bg-white/[0.02] text-[#E8E6F0]/70 hover:text-white'
                    }`}
                  >
                    {style.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Ambient Glow Toggle */}
          <div className="flex items-center justify-between p-3 rounded-2xl bg-white/[0.02] border border-white/5">
            <div className="flex flex-col">
              <span className="text-xs font-bold text-white">Dynamic Ambient Aura Lighting</span>
              <span className="text-[11px] text-[#6B6A7A]">
                Smooth fluid glow behind header and player
              </span>
            </div>
            <button
              onClick={() => setAmbientGlow(!ambientGlow)}
              style={ambientGlow ? { backgroundColor: 'var(--vynce-primary)' } : {}}
              className={`w-11 h-6 rounded-full transition-colors relative p-0.5 cursor-pointer ${
                ambientGlow ? '' : 'bg-white/20'
              }`}
            >
              <div
                className={`w-5 h-5 rounded-full bg-white transition-transform ${
                  ambientGlow ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
        </section>

        {/* CARD 3: 🌐 MUSIC & LANGUAGE DISCOVERY */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="p-2.5 rounded-2xl shadow-inner"
              >
                <Languages className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white font-['Outfit']">
                  Language & Discovery
                </h2>
                <p className="text-xs text-[#6B6A7A]">
                  Curate your home feed & search recommendations
                </p>
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              <button
                onClick={() => setLanguages(AVAILABLE_LANGUAGES.map((l) => l.id))}
                className="text-[11px] font-bold text-[var(--vynce-primary)] hover:underline cursor-pointer"
              >
                Select All
              </button>
              <span className="text-xs text-[#6B6A7A]">•</span>
              <button
                onClick={() => setLanguages(['hindi', 'english', 'punjabi'])}
                className="text-[11px] font-bold text-[#6B6A7A] hover:text-white cursor-pointer"
              >
                Default
              </button>
            </div>
          </div>

          {/* Interactive Language Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {AVAILABLE_LANGUAGES.map((lang) => {
              const isSelected = languages.includes(lang.id);
              return (
                <button
                  key={lang.id}
                  onClick={() => toggleLanguage(lang.id)}
                  style={
                    isSelected
                      ? {
                          backgroundColor: 'var(--vynce-primary-container)',
                          color: 'var(--vynce-on-primary-container)',
                          borderColor: 'var(--vynce-primary)',
                        }
                      : {}
                  }
                  className={`flex items-center justify-between p-3 rounded-2xl text-xs font-semibold border transition-all text-left cursor-pointer ${
                    isSelected
                      ? 'shadow-md font-bold scale-102'
                      : 'bg-white/[0.02] border-white/5 text-[#E8E6F0]/70 hover:text-white hover:bg-white/[0.06]'
                  }`}
                >
                  <div className="flex flex-col">
                    <span>{lang.label}</span>
                    <span className="text-[10px] opacity-70">{lang.native}</span>
                  </div>
                  {isSelected && <Check className="w-4 h-4 shrink-0" />}
                </button>
              );
            })}
          </div>
        </section>

        {/* CARD 4: 🎤 SYNCED KARAOKE & LYRICS CALIBRATION */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-inner"
            >
              <Mic2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Synced Lyrics Calibration
              </h2>
              <p className="text-xs text-[#6B6A7A]">Fine-tune lyric timestamps & auto-scroll</p>
            </div>
          </div>

          {/* Timing Calibration Slider */}
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
                Timestamp Offset Calibration
              </label>
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono font-bold text-[var(--vynce-primary)]">
                  {lyricsOffset > 0 ? `+${lyricsOffset}ms` : `${lyricsOffset}ms`}
                </span>
                {lyricsOffset !== 0 && (
                  <button
                    onClick={() => setLyricsOffset(0)}
                    className="text-[10px] font-bold text-[#6B6A7A] hover:text-white cursor-pointer"
                  >
                    (Reset)
                  </button>
                )}
              </div>
            </div>
            <input
              type="range"
              min="-1000"
              max="1000"
              step="50"
              value={lyricsOffset}
              onChange={(e) => setLyricsOffset(parseInt(e.target.value, 10))}
              style={{ accentColor: 'var(--vynce-primary)' }}
              className="w-full h-1.5 bg-white/10 rounded-full cursor-pointer"
            />
            <div className="flex justify-between text-[10px] text-[#6B6A7A] font-semibold font-mono">
              <span>-1000ms (Earlier)</span>
              <span>0ms</span>
              <span>+1000ms (Later)</span>
            </div>
          </div>

          {/* Auto Scroll Lyrics Toggle */}
          <div className="flex items-center justify-between p-3 rounded-2xl bg-white/[0.02] border border-white/5">
            <div className="flex flex-col">
              <span className="text-xs font-bold text-white">Auto-Scroll Karaoke Lyrics</span>
              <span className="text-[11px] text-[#6B6A7A]">
                Automatically scrolls to the active sung lyric line
              </span>
            </div>
            <button
              onClick={() => setAutoScrollLyrics(!autoScrollLyrics)}
              style={autoScrollLyrics ? { backgroundColor: 'var(--vynce-primary)' } : {}}
              className={`w-11 h-6 rounded-full transition-colors relative p-0.5 cursor-pointer ${
                autoScrollLyrics ? '' : 'bg-white/20'
              }`}
            >
              <div
                className={`w-5 h-5 rounded-full bg-white transition-transform ${
                  autoScrollLyrics ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
        </section>

        {/* CARD 5: 💾 STORAGE, BACKUP & DATA RESTORE */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-inner"
            >
              <HardDrive className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Storage & Backup Management
              </h2>
              <p className="text-xs text-[#6B6A7A]">Export playlists, library, and manage cache</p>
            </div>
          </div>

          {/* Local Storage Stats */}
          <div className="grid grid-cols-4 gap-2">
            <div className="p-3 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
              <span className="text-base font-extrabold text-white font-['Outfit']">
                {likedSongs.length}
              </span>
              <p className="text-[9px] text-[#6B6A7A] uppercase font-bold">Likes</p>
            </div>
            <div className="p-3 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
              <span className="text-base font-extrabold text-white font-['Outfit']">
                {savedAlbums.length}
              </span>
              <p className="text-[9px] text-[#6B6A7A] uppercase font-bold">Albums</p>
            </div>
            <div className="p-3 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
              <span className="text-base font-extrabold text-white font-['Outfit']">
                {playlists.length}
              </span>
              <p className="text-[9px] text-[#6B6A7A] uppercase font-bold">Playlists</p>
            </div>
            <div className="p-3 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
              <span className="text-base font-extrabold text-white font-['Outfit']">
                {history.length}
              </span>
              <p className="text-[9px] text-[#6B6A7A] uppercase font-bold">History</p>
            </div>
          </div>

          {/* Backup Actions */}
          <div className="grid grid-cols-2 gap-2 pt-1">
            <button
              onClick={handleExportBackup}
              className="flex items-center justify-center gap-2 p-3.5 rounded-2xl bg-white/[0.04] hover:bg-white/[0.08] text-white font-bold text-xs border border-white/10 transition-all active:scale-95 cursor-pointer"
            >
              {exportSuccess ? <Check className="w-4 h-4 text-green-400" /> : <Download className="w-4 h-4" />}
              <span>{exportSuccess ? 'Exported!' : 'Export Backup'}</span>
            </button>

            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center justify-center gap-2 p-3.5 rounded-2xl bg-white/[0.04] hover:bg-white/[0.08] text-white font-bold text-xs border border-white/10 transition-all active:scale-95 cursor-pointer"
            >
              {importSuccess ? <Check className="w-4 h-4 text-green-400" /> : <Upload className="w-4 h-4" />}
              <span>{importSuccess ? 'Restored!' : 'Import Backup'}</span>
            </button>
            <input
              type="file"
              accept=".json"
              ref={fileInputRef}
              onChange={handleImportBackup}
              className="hidden"
            />
          </div>

          {/* Clear Cache */}
          <button
            onClick={async () => {
              if (confirm('Clear playback history, audio stream cache, and lyrics cache? Liked songs will remain safe.')) {
                await clearHistory();
                await db.lyrics.clear();
                await db.lyricsTranslations.clear();
                await AudioCacheManager.clearCache();
                alert('Listening history, audio stream cache, and lyrics cache cleared successfully.');
              }
            }}
            className="flex items-center justify-center gap-2 w-full py-2.5 rounded-2xl bg-red-500/10 hover:bg-red-500/20 text-red-400 font-bold text-xs transition-colors border border-red-500/20 cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" /> Clear Listening History & Audio Cache
          </button>
        </section>

        {/* CARD 6: 💻 DESKTOP APP & AUTO-UPDATES (Electron only or cross-platform display) */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="p-2.5 rounded-2xl shadow-inner"
              >
                <Monitor className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-white font-['Outfit']">
                  Desktop App & Updates
                </h2>
                <p className="text-xs text-[#6B6A7A]">
                  {isElectron ? 'Manage auto-updates and desktop releases' : 'Web & Cross-Platform Client'}
                </p>
              </div>
            </div>

            <span className="px-3 py-1 rounded-full text-xs font-mono font-bold bg-white/5 border border-white/10 text-purple-300">
              v{appVersion}
            </span>
          </div>

          <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/5 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-300 font-semibold">Update Status</span>
              {updateStatus === 'checking' && (
                <span className="flex items-center gap-1 text-xs text-purple-400 font-medium">
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" /> Checking...
                </span>
              )}
              {updateStatus === 'up-to-date' && (
                <span className="flex items-center gap-1 text-xs text-emerald-400 font-medium">
                  <CheckCircle2 className="w-3.5 h-3.5" /> Up to date
                </span>
              )}
              {updateStatus === 'available' && (
                <span className="flex items-center gap-1 text-xs text-amber-400 font-medium">
                  <Sparkles className="w-3.5 h-3.5" /> New update found
                </span>
              )}
              {updateStatus === 'downloading' && (
                <span className="flex items-center gap-1 text-xs text-purple-400 font-medium">
                  <Download className="w-3.5 h-3.5 animate-bounce" /> {downloadProgress}%
                </span>
              )}
              {updateStatus === 'downloaded' && (
                <span className="flex items-center gap-1 text-xs text-emerald-400 font-medium">
                  <ArrowUpCircle className="w-3.5 h-3.5" /> Ready to install
                </span>
              )}
              {updateStatus === 'error' && (
                <span className="flex items-center gap-1 text-xs text-rose-400 font-medium">
                  <AlertCircle className="w-3.5 h-3.5" /> Check error
                </span>
              )}
              {updateStatus === 'idle' && (
                <span className="text-xs text-slate-500">Auto-check enabled</span>
              )}
              {updateStatus === 'dev-mode' && (
                <span className="text-xs text-slate-500">Dev Mode</span>
              )}
            </div>

            {updateMsg && (
              <p className="text-xs text-slate-400 bg-white/[0.02] p-2.5 rounded-xl border border-white/5">
                {updateMsg}
              </p>
            )}

            {updateStatus === 'downloading' && (
              <div className="w-full bg-white/10 rounded-full h-1.5 overflow-hidden mt-1">
                <div
                  className="bg-purple-500 h-full rounded-full transition-all duration-300"
                  style={{ width: `${downloadProgress}%` }}
                />
              </div>
            )}

            <div className="flex items-center gap-2 mt-1">
              {updateStatus === 'available' ? (
                <button
                  onClick={handleDownloadUpdate}
                  className="flex-1 py-2.5 px-4 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs flex items-center justify-center gap-2 transition-all shadow-lg shadow-purple-600/30 cursor-pointer"
                >
                  <Download className="w-3.5 h-3.5" /> Download & Install Update
                </button>
              ) : updateStatus === 'downloaded' ? (
                <button
                  onClick={handleInstallUpdate}
                  className="flex-1 py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs flex items-center justify-center gap-2 transition-all shadow-lg shadow-emerald-600/30 cursor-pointer"
                >
                  <ArrowUpCircle className="w-3.5 h-3.5" /> Restart & Apply Update
                </button>
              ) : (
                <button
                  onClick={handleCheckUpdates}
                  disabled={updateStatus === 'checking'}
                  className="flex-1 py-2.5 px-4 rounded-xl bg-white/[0.04] hover:bg-white/[0.08] text-white font-bold text-xs flex items-center justify-center gap-2 transition-all border border-white/10 cursor-pointer disabled:opacity-50"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${updateStatus === 'checking' ? 'animate-spin' : ''}`} />
                  Check for Updates
                </button>
              )}
            </div>
          </div>
        </section>

        {/* CARD 6: ⌨️ KEYBOARD SHORTCUTS CHEATSHEET */}
        <section className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-7 border border-white/10 shadow-xl flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl shadow-inner"
            >
              <Keyboard className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Keyboard Shortcuts
              </h2>
              <p className="text-xs text-[#6B6A7A]">Quick navigation & playback shortcuts</p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2">
            {[
              { key: 'Space', desc: 'Play / Pause' },
              { key: '→ / ←', desc: 'Seek ±5 seconds' },
              { key: 'Shift + →', desc: 'Next track' },
              { key: 'Shift + ←', desc: 'Previous track' },
              { key: '↑ / ↓', desc: 'Volume ±5%' },
              { key: 'M', desc: 'Mute / Unmute' },
              { key: 'L', desc: 'Toggle Synced Lyrics' },
              { key: 'F', desc: 'Toggle Fullscreen' },
              { key: 'Q', desc: 'Toggle Queue' },
              { key: 'S', desc: 'Toggle Sleep Timer' },
            ].map((sc) => (
              <div
                key={sc.key}
                className="flex items-center justify-between p-2.5 rounded-2xl bg-white/[0.02] border border-white/5"
              >
                <span className="text-xs text-[#E8E6F0]/80 font-medium">{sc.desc}</span>
                <kbd className="px-2 py-0.5 rounded-lg bg-white/10 text-white font-mono text-[11px] font-bold border border-white/15">
                  {sc.key}
                </kbd>
              </div>
            ))}
          </div>
        </section>
      </div>

      {/* ── CARD 7: ℹ️ ABOUT & SYSTEM FOOTER ───────────────── */}
      <footer className="bg-[#0A0A0A] rounded-[32px] p-6 md:p-8 border border-white/10 shadow-xl flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <img src="/favicon.svg" alt="Vynce Logo" className="w-8 h-8 rounded-xl shadow-md" />
          <div className="flex flex-col">
            <span className="text-sm font-extrabold text-white font-['Outfit']">
              Vynce Music • Material You 3 Web Edition
            </span>
            <span className="text-xs text-[#6B6A7A]">
              Version 1.2.0 • 100M+ Tracks • High Fidelity Web Audio DSP
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-xs font-mono font-bold text-[#6B6A7A] px-3 py-1 rounded-full bg-white/5 border border-white/5">
            GPL-3.0 License
          </span>
          <a
            href="https://github.com/2300030811/Vynce"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1.5 px-4 py-2 rounded-full bg-white/[0.04] hover:bg-white/[0.08] text-xs font-bold text-white transition-colors border border-white/10"
          >
            <span>GitHub</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </div>
      </footer>

      {/* Listen Together Modal */}
      <ListenTogetherModal
        isOpen={isListenTogetherOpen}
        onClose={() => setIsListenTogetherOpen(false)}
      />
    </div>
  );
};
