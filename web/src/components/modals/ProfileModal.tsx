import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import {
  User,
  X,
  Heart,
  Disc3,
  ListMusic,
  Download,
  Settings,
  Sliders,
  Check,
  Edit2,
  Sparkles,
  ShieldCheck,
  Radio,
  Clock,
  Volume2,
  Camera,
  Layers,
} from 'lucide-react';
import { useSettingsStore } from '../../stores/settingsStore';
import { useLibraryStore } from '../../stores/libraryStore';

interface ProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigateToTab?: (tab: any) => void;
}

export const ProfileModal: React.FC<ProfileModalProps> = ({
  isOpen,
  onClose,
  onNavigateToTab,
}) => {
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
    setUserTitle,
    bitrate,
  } = useSettingsStore();

  const { likedSongs, savedAlbums, playlists, history, exportLibrary } = useLibraryStore();

  const [isEditing, setIsEditing] = useState(false);
  const [nameInput, setNameInput] = useState(userName);
  const [bioInput, setBioInput] = useState(userBio);
  const [avatarUrlInput, setAvatarUrlInput] = useState(customAvatarUrl);
  const [showUrlInput, setShowUrlInput] = useState(false);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const avatarColors: { id: string; bg: string; border: string; label: string }[] = [
    { id: 'purple', bg: 'from-purple-600 to-indigo-900', border: 'border-purple-400', label: 'Neon Purple' },
    { id: 'cyan', bg: 'from-cyan-500 to-blue-900', border: 'border-cyan-400', label: 'Electric Blue' },
    { id: 'emerald', bg: 'from-emerald-500 to-teal-900', border: 'border-emerald-400', label: 'Emerald' },
    { id: 'amber', bg: 'from-amber-500 to-orange-900', border: 'border-amber-400', label: 'Sunset Amber' },
    { id: 'rose', bg: 'from-rose-500 to-pink-900', border: 'border-rose-400', label: 'Rose Pink' },
    { id: 'gold', bg: 'from-yellow-500 to-amber-900', border: 'border-yellow-400', label: 'Royal Gold' },
  ];

  const currentAvatar = avatarColors.find((a) => a.id === userAvatar) || avatarColors[0];

  const handleSaveProfile = () => {
    if (nameInput.trim()) {
      setUserName(nameInput.trim());
      setUserBio(bioInput.trim());
      if (showUrlInput) {
        setCustomAvatarUrl(avatarUrlInput.trim());
      }
      setIsEditing(false);
      setSavedMsg('Profile updated successfully!');
      setTimeout(() => setSavedMsg(null), 2500);
    }
  };

  const handleExportBackup = async () => {
    try {
      const jsonStr = await exportLibrary();
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `vynce_backup_${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      setSavedMsg('Library backup exported!');
      setTimeout(() => setSavedMsg(null), 2500);
    } catch {
      alert('Failed to export backup.');
    }
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
              className="p-3 rounded-2xl shadow-inner"
            >
              <User className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white font-['Outfit']">
                Listener Profile
              </h2>
              <p className="text-xs text-[#6B6A7A]">
                Your Vynce identity & preferences
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

        {savedMsg && (
          <div className="mx-6 mt-4 flex items-center gap-2 px-4 py-2 rounded-2xl bg-[var(--vynce-primary-container)] text-[var(--vynce-on-primary-container)] text-xs font-bold shadow-md animate-in fade-in">
            <Check className="w-4 h-4" />
            <span>{savedMsg}</span>
          </div>
        )}

        {/* Profile Card Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Avatar & Info Banner */}
          <div className="relative overflow-hidden rounded-[28px] p-6 bg-gradient-to-br from-white/[0.04] to-white/[0.01] border border-white/10 flex flex-col sm:flex-row items-center gap-5">
            {/* Avatar Circle / Custom Image */}
            <div className="relative shrink-0">
              {customAvatarUrl ? (
                <img
                  src={customAvatarUrl}
                  alt={userName}
                  className="w-20 h-20 rounded-2xl object-cover border-2 border-[var(--vynce-primary)] shadow-2xl"
                  onError={() => setCustomAvatarUrl('')}
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

            {/* Name & Bio Area */}
            <div className="flex flex-col items-center sm:items-start text-center sm:text-left flex-1 min-w-0">
              {isEditing ? (
                <div className="flex flex-col gap-2 w-full">
                  <input
                    type="text"
                    placeholder="Your Name"
                    value={nameInput}
                    onChange={(e) => setNameInput(e.target.value)}
                    className="px-3 py-1.5 rounded-xl bg-white/[0.06] border border-white/20 text-sm font-bold text-white outline-none w-full"
                    autoFocus
                  />
                  <input
                    type="text"
                    placeholder="Status / Bio (e.g. Exploring indie acoustic)"
                    value={bioInput}
                    onChange={(e) => setBioInput(e.target.value)}
                    className="px-3 py-1.5 rounded-xl bg-white/[0.06] border border-white/20 text-xs text-[#E8E6F0] outline-none w-full"
                  />
                  {showUrlInput && (
                    <input
                      type="url"
                      placeholder="Custom Avatar Image URL (https://...)"
                      value={avatarUrlInput}
                      onChange={(e) => setAvatarUrlInput(e.target.value)}
                      className="px-3 py-1.5 rounded-xl bg-white/[0.06] border border-white/20 text-xs text-[#E8E6F0] outline-none w-full"
                    />
                  )}
                  <div className="flex items-center gap-2 mt-1">
                    <button
                      onClick={handleSaveProfile}
                      style={{
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold shadow-sm"
                    >
                      <Check className="w-3.5 h-3.5" /> Save
                    </button>
                    <button
                      onClick={() => setShowUrlInput(!showUrlInput)}
                      className="text-[11px] text-[#6B6A7A] hover:text-white px-2 py-1"
                    >
                      {showUrlInput ? 'Use Color Swatch' : 'Custom Image URL'}
                    </button>
                    <button
                      onClick={() => setIsEditing(false)}
                      className="text-[11px] text-[#6B6A7A] hover:text-white px-2 py-1"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center sm:items-start">
                  <div className="flex items-center gap-2">
                    <h3 className="text-xl font-black text-white font-['Outfit'] truncate">
                      {userName}
                    </h3>
                    <button
                      onClick={() => {
                        setNameInput(userName);
                        setBioInput(userBio);
                        setIsEditing(true);
                      }}
                      className="p-1 rounded-lg text-[#6B6A7A] hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                      title="Edit Profile"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <p className="text-xs text-[#8E8D9F] line-clamp-1 mt-0.5">
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
                    <span className="text-[11px] text-[#8E8D9F] font-medium font-mono">
                      {bitrate} Hi-Res
                    </span>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Avatar Color Picker */}
          <div className="flex flex-col gap-2.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-[#6B6A7A] uppercase tracking-wider">
                Avatar Theme Color
              </label>
              {customAvatarUrl && (
                <button
                  onClick={() => setCustomAvatarUrl('')}
                  className="text-[11px] font-bold text-[var(--vynce-primary)] hover:underline"
                >
                  Reset to Theme
                </button>
              )}
            </div>
            <div className="flex items-center gap-2.5 overflow-x-auto pb-1">
              {avatarColors.map((color) => (
                <button
                  key={color.id}
                  onClick={() => {
                    setCustomAvatarUrl('');
                    setUserAvatar(color.id);
                  }}
                  className={`w-9 h-9 rounded-xl bg-gradient-to-br ${color.bg} border-2 transition-all shrink-0 cursor-pointer ${
                    userAvatar === color.id && !customAvatarUrl
                      ? `${color.border} scale-110 shadow-lg`
                      : 'border-transparent opacity-60 hover:opacity-100'
                  }`}
                  title={color.label}
                />
              ))}
            </div>
          </div>

          {/* Collection Numbers Grid */}
          <div className="grid grid-cols-4 gap-2.5">
            <div className="p-3 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center text-center">
              <Heart className="w-3.5 h-3.5 text-[var(--vynce-primary)] mb-1" />
              <span className="text-base font-black text-white font-['Outfit']">
                {likedSongs.length}
              </span>
              <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Likes</span>
            </div>

            <div className="p-3 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center text-center">
              <Disc3 className="w-3.5 h-3.5 text-[var(--vynce-primary)] mb-1" />
              <span className="text-base font-black text-white font-['Outfit']">
                {savedAlbums.length}
              </span>
              <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Albums</span>
            </div>

            <div className="p-3 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center text-center">
              <ListMusic className="w-3.5 h-3.5 text-[var(--vynce-primary)] mb-1" />
              <span className="text-base font-black text-white font-['Outfit']">
                {playlists.length}
              </span>
              <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Playlists</span>
            </div>

            <div className="p-3 rounded-2xl bg-white/[0.03] border border-white/5 flex flex-col items-center text-center">
              <Clock className="w-3.5 h-3.5 text-[var(--vynce-primary)] mb-1" />
              <span className="text-base font-black text-white font-['Outfit']">
                {history.length}
              </span>
              <span className="text-[9px] text-[#6B6A7A] uppercase font-bold">Streamed</span>
            </div>
          </div>

          {/* Quick Action Shortcuts */}
          <div className="flex flex-col gap-2 pt-2 border-t border-white/10">
            <button
              onClick={() => {
                onClose();
                onNavigateToTab?.('equalizer');
              }}
              className="flex items-center justify-between p-3.5 rounded-2xl bg-white/[0.02] hover:bg-white/[0.06] border border-white/5 text-xs font-semibold text-[#E8E6F0] transition-colors cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <Sliders className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Equalizer & 10-Band DSP Studio</span>
              </div>
              <span className="text-[#6B6A7A]">→</span>
            </button>

            <button
              onClick={handleExportBackup}
              className="flex items-center justify-between p-3.5 rounded-2xl bg-white/[0.02] hover:bg-white/[0.06] border border-white/5 text-xs font-semibold text-[#E8E6F0] transition-colors cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <Download className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Export Library Backup (JSON)</span>
              </div>
              <span className="text-[#6B6A7A]">Download</span>
            </button>

            <button
              onClick={() => {
                onClose();
                onNavigateToTab?.('settings');
              }}
              className="flex items-center justify-between p-3.5 rounded-2xl bg-white/[0.02] hover:bg-white/[0.06] border border-white/5 text-xs font-semibold text-[#E8E6F0] transition-colors cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <Settings className="w-4 h-4 text-[var(--vynce-primary)]" />
                <span>Full Settings & Preferences</span>
              </div>
              <span className="text-[#6B6A7A]">→</span>
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
};
