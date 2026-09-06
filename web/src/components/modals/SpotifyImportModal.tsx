import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { SpotifyImporter } from '../../api/spotify';
import { DbPlaylist } from '../../db';
import { X, DownloadCloud, Loader2, AlertCircle, ArrowRight } from 'lucide-react';

interface SpotifyImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (playlist: DbPlaylist) => void;
  initialUrl?: string;
}

export const SpotifyImportModal: React.FC<SpotifyImportModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  initialUrl = '',
}) => {
  const [urlInput, setUrlInput] = useState(initialUrl);
  const [isLoading, setIsLoading] = useState(false);
  const [progressMsg, setProgressMsg] = useState('');
  const [currentProgress, setCurrentProgress] = useState({ current: 0, total: 0 });
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (initialUrl) {
      setUrlInput(initialUrl);
    }
  }, [initialUrl, isOpen]);

  if (!isOpen || typeof document === 'undefined') return null;

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!urlInput.trim()) return;

    setErrorMsg(null);
    setIsLoading(true);
    setProgressMsg('Connecting to source & fetching playlist tracks...');
    setCurrentProgress({ current: 0, total: 0 });

    try {
      const result = await SpotifyImporter.importPlaylistToVynce(
        urlInput.trim(),
        (curr, tot, songName) => {
          setCurrentProgress({ current: curr, total: tot });
          setProgressMsg(`Matching (${curr}/${tot}): ${songName}`);
        }
      );

      if (!result) {
        setErrorMsg('Could not find or parse this playlist. Please check the URL or ID.');
        setIsLoading(false);
        return;
      }

      setIsLoading(false);
      onSuccess(result.playlist);
      onClose();
    } catch (err: any) {
      setErrorMsg(err.message || 'An error occurred while importing playlist.');
      setIsLoading(false);
    }
  };

  const pct = currentProgress.total > 0
    ? Math.round((currentProgress.current / currentProgress.total) * 100)
    : 0;

  return createPortal(
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-[#0E0E12] border border-white/15 rounded-3xl p-6 sm:p-7 shadow-2xl flex flex-col gap-5 overflow-hidden">
        {/* Ambient Top Glow */}
        <div
          className="absolute -top-24 left-1/2 -translate-x-1/2 w-64 h-32 blur-3xl opacity-30 pointer-events-none"
          style={{ background: 'var(--vynce-primary)' }}
        />

        {/* Header */}
        <div className="flex items-center justify-between z-10">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2.5 rounded-2xl border border-white/10"
            >
              <DownloadCloud className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white font-['Outfit']">
                Import Playlist
              </h2>
              <p className="text-xs text-[#8A8998]">
                Convert any public playlist directly into your local Vynce library
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={isLoading}
            className="p-2 rounded-full hover:bg-white/10 text-[#8A8998] hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        {!isLoading ? (
          <form onSubmit={handleImport} className="flex flex-col gap-4 z-10">
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-semibold text-[#A6A4B8]">
                Playlist Link or ID
              </label>
              <input
                type="text"
                value={urlInput}
                onChange={(e) => setUrlInput(e.target.value)}
                placeholder="https://open.spotify.com/playlist/..."
                autoFocus
                className="w-full px-4 py-3 rounded-2xl bg-white/[0.05] border border-white/10 focus:border-white/30 text-sm text-white placeholder:text-[#555462] outline-none transition-colors"
              />
              <span className="text-[11px] text-[#6B6A7A]">
                Tip: Paste any public playlist link or ID.
              </span>
            </div>

            {errorMsg && (
              <div className="flex items-center gap-2 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-medium">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2.5 rounded-full text-xs font-bold text-[#A6A4B8] hover:text-white transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!urlInput.trim()}
                style={{
                  backgroundColor: 'var(--vynce-primary-container)',
                  color: 'var(--vynce-on-primary-container)',
                }}
                className="flex items-center gap-2 px-6 py-2.5 rounded-full active:scale-95 text-xs font-bold shadow-lg transition-all disabled:opacity-50 disabled:pointer-events-none"
              >
                <span>Import Playlist</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </form>
        ) : (
          <div className="flex flex-col items-center justify-center py-6 gap-4 z-10">
            <Loader2 style={{ color: 'var(--vynce-primary)' }} className="w-10 h-10 animate-spin" />
            <div className="flex flex-col items-center text-center gap-1 w-full">
              <p className="text-sm font-bold text-white">Importing Playlist</p>
              <p className="text-xs text-[#8A8998] truncate max-w-full px-4">{progressMsg}</p>
            </div>

            {/* Progress Bar */}
            {currentProgress.total > 0 && (
              <div className="w-full flex flex-col gap-1.5 mt-2">
                <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
                  <div
                    className="h-full transition-all duration-150 ease-out"
                    style={{ width: `${pct}%`, backgroundColor: 'var(--vynce-primary)' }}
                  />
                </div>
                <div className="flex justify-between text-[10px] text-[#6B6A7A] font-semibold">
                  <span>{currentProgress.current} tracks matched</span>
                  <span>{pct}%</span>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>,
    document.body
  );
};
