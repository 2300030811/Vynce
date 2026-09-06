import React, { useEffect, useState } from 'react';
import { Sparkles, Download, RefreshCw, X, ArrowUpCircle, CheckCircle2, AlertCircle } from 'lucide-react';
import { UpdateEvent } from '../../types/electron';

export const DesktopUpdateBanner: React.FC = () => {
  const isElectron = typeof window !== 'undefined' && Boolean(window.electronAPI?.isElectron);
  const [updateInfo, setUpdateInfo] = useState<UpdateEvent | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [isActionLoading, setIsActionLoading] = useState(false);

  useEffect(() => {
    if (!isElectron || !window.electronAPI) return;

    const unsubscribe = window.electronAPI.onUpdaterEvent((event) => {
      setUpdateInfo(event);
      if (event.status === 'available' || event.status === 'downloaded') {
        setDismissed(false);
      }
    });

    return () => {
      unsubscribe?.();
    };
  }, [isElectron]);

  if (!isElectron || dismissed || !updateInfo) {
    return null;
  }

  // Only render banner if there is an active event worth displaying
  if (!['available', 'downloading', 'downloaded', 'error'].includes(updateInfo.status)) {
    return null;
  }

  const handleDownload = async () => {
    if (!window.electronAPI) return;
    setIsActionLoading(true);
    try {
      await window.electronAPI.downloadUpdate();
    } catch (e) {
      console.error('Failed to download update:', e);
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleInstall = async () => {
    if (!window.electronAPI) return;
    await window.electronAPI.quitAndInstall();
  };

  return (
    <div className="fixed bottom-24 right-6 z-50 max-w-sm w-full bg-[#12131C]/95 backdrop-blur-xl border border-purple-500/30 shadow-2xl shadow-purple-950/50 rounded-2xl p-4 text-white animate-in fade-in slide-in-from-bottom-4 duration-300">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-purple-500/20 border border-purple-500/40 flex items-center justify-center text-purple-300 shrink-0">
            {updateInfo.status === 'downloaded' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            ) : updateInfo.status === 'error' ? (
              <AlertCircle className="w-4 h-4 text-rose-400" />
            ) : updateInfo.status === 'downloading' ? (
              <RefreshCw className="w-4 h-4 text-purple-300 animate-spin" />
            ) : (
              <Sparkles className="w-4 h-4 text-purple-300" />
            )}
          </div>
          <div>
            <h4 className="font-semibold text-sm tracking-tight text-slate-100">
              {updateInfo.status === 'available' && `Update v${updateInfo.version || ''} Available`}
              {updateInfo.status === 'downloading' && 'Downloading Update...'}
              {updateInfo.status === 'downloaded' && 'Update Ready to Install'}
              {updateInfo.status === 'error' && 'Update Check Failed'}
            </h4>
            <p className="text-xs text-slate-400 mt-0.5">
              {updateInfo.status === 'available' && 'A new version of Vynce is ready to download.'}
              {updateInfo.status === 'downloading' && `${Math.round(updateInfo.percent || 0)}% completed`}
              {updateInfo.status === 'downloaded' && 'Restart Vynce now to apply the new update.'}
              {updateInfo.status === 'error' && (updateInfo.error || 'Could not connect to update server.')}
            </p>
          </div>
        </div>

        <button
          onClick={() => setDismissed(true)}
          className="text-slate-500 hover:text-slate-300 p-1 rounded-lg hover:bg-white/5 transition-colors"
          aria-label="Dismiss notification"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* Progress Bar when downloading */}
      {updateInfo.status === 'downloading' && (
        <div className="mt-3">
          <div className="w-full bg-white/10 rounded-full h-1.5 overflow-hidden">
            <div
              className="bg-gradient-to-r from-purple-500 to-indigo-500 h-full transition-all duration-300 rounded-full"
              style={{ width: `${Math.max(5, updateInfo.percent || 0)}%` }}
            />
          </div>
          <div className="flex justify-between text-[10px] text-slate-400 mt-1 font-mono">
            <span>{Math.round(updateInfo.percent || 0)}%</span>
            {updateInfo.bytesPerSecond && (
              <span>{(updateInfo.bytesPerSecond / 1024 / 1024).toFixed(1)} MB/s</span>
            )}
          </div>
        </div>
      )}

      {/* Action Buttons */}
      <div className="mt-3 flex items-center gap-2">
        {updateInfo.status === 'available' && (
          <button
            onClick={handleDownload}
            disabled={isActionLoading}
            className="flex-1 bg-purple-600 hover:bg-purple-500 active:scale-[0.98] text-white font-medium text-xs py-2 px-3 rounded-xl flex items-center justify-center gap-1.5 transition-all shadow-lg shadow-purple-600/30"
          >
            {isActionLoading ? (
              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Download className="w-3.5 h-3.5" />
            )}
            <span>Download Update</span>
          </button>
        )}

        {updateInfo.status === 'downloaded' && (
          <button
            onClick={handleInstall}
            className="flex-1 bg-emerald-600 hover:bg-emerald-500 active:scale-[0.98] text-white font-medium text-xs py-2 px-3 rounded-xl flex items-center justify-center gap-1.5 transition-all shadow-lg shadow-emerald-600/30"
          >
            <ArrowUpCircle className="w-3.5 h-3.5" />
            <span>Restart & Install</span>
          </button>
        )}

        <button
          onClick={() => setDismissed(true)}
          className="bg-white/5 hover:bg-white/10 text-slate-300 text-xs py-2 px-3 rounded-xl transition-colors font-medium"
        >
          Later
        </button>
      </div>
    </div>
  );
};
