import React from 'react';
import { useQueueStore } from '../../stores/queueStore';
import { usePlayerStore } from '../../stores/playerStore';
import { SongCard } from '../cards/SongCard';
import { ListMusic, X, Trash2, Shuffle, ChevronUp, ChevronDown } from 'lucide-react';

export const QueueDrawer: React.FC = () => {
  const { isQueueOpen, toggleQueue, isShuffled, toggleShuffle } = usePlayerStore();
  const { queue, currentIndex, clearQueue, removeFromQueue, reorderQueue } = useQueueStore();

  if (!isQueueOpen) return null;

  const currentTrack = queue[currentIndex];
  const upNextTracks = queue.slice(currentIndex + 1);

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md h-full bg-[#0A0A0A] flex flex-col border-l border-white/[0.06] shadow-2xl animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-white/[0.06]">
          <div className="flex items-center gap-3">
            <div
              style={{
                backgroundColor: 'var(--vynce-primary-container)',
                color: 'var(--vynce-on-primary-container)',
              }}
              className="p-2 rounded-xl"
            >
              <ListMusic className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white tracking-wide">Play Queue</h2>
              <p className="text-xs text-[#6B6A7A]">{queue.length} tracks in queue</p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={clearQueue}
              className="p-2 rounded-full hover:bg-white/8 text-[#6B6A7A] hover:text-red-400 transition-colors"
              title="Clear Queue"
            >
              <Trash2 className="w-4 h-4" />
            </button>
            <button
              onClick={toggleQueue}
              className="p-2 rounded-full hover:bg-white/8 text-[#6B6A7A] hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-6">
          {currentTrack && (
            <div className="flex flex-col gap-2">
              <span
                style={{ color: 'var(--vynce-primary)' }}
                className="text-xs font-bold uppercase tracking-wider px-1"
              >
                Now Playing
              </span>
              <div
                style={{
                  backgroundColor: 'color-mix(in srgb, var(--vynce-primary-container) 30%, transparent)',
                  borderColor: 'color-mix(in srgb, var(--vynce-primary) 30%, transparent)',
                }}
                className="border rounded-2xl p-1 shadow-sm"
              >
                <SongCard song={currentTrack} showAlbum={false} />
              </div>
            </div>
          )}

          <div className="flex flex-col gap-2 flex-1">
            <div className="flex items-center justify-between px-1">
              <span className="text-xs font-bold uppercase tracking-wider text-[#6B6A7A]">
                Up Next ({upNextTracks.length})
              </span>
              <button
                onClick={toggleShuffle}
                style={
                  isShuffled
                    ? {
                        backgroundColor: 'var(--vynce-primary-container)',
                        color: 'var(--vynce-on-primary-container)',
                      }
                    : {}
                }
                className={`flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full transition-colors ${
                  isShuffled ? '' : 'text-[#6B6A7A] hover:text-white hover:bg-white/[0.04]'
                }`}
              >
                <Shuffle className="w-3 h-3" /> Shuffle
              </button>
            </div>

            {upNextTracks.length > 0 ? (
              <div className="flex flex-col gap-1.5">
                {upNextTracks.map((song, idx) => {
                  const globalIdx = currentIndex + 1 + idx;
                  return (
                    <div key={`${song.id}-${globalIdx}`} className="group relative flex items-center bg-white/[0.015] hover:bg-white/[0.04] rounded-2xl border border-white/[0.03] transition-colors pr-2">
                      <div className="flex-1 min-w-0">
                        <SongCard
                          song={song}
                          index={globalIdx}
                          queueContext={queue}
                          showAlbum={false}
                        />
                      </div>
                      
                      {/* Reorder & Action buttons */}
                      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity bg-black/60 rounded-xl p-1 shrink-0 ml-1 border border-white/5">
                        <button
                          disabled={idx === 0}
                          onClick={(e) => {
                            e.stopPropagation();
                            reorderQueue(globalIdx, globalIdx - 1);
                          }}
                          className={`p-1 rounded-lg transition-colors ${
                            idx === 0
                              ? 'text-white/20 cursor-not-allowed'
                              : 'text-[#6B6A7A] hover:text-white hover:bg-white/10'
                          }`}
                          title="Move Up"
                        >
                          <ChevronUp className="w-3.5 h-3.5" />
                        </button>
                        <button
                          disabled={idx === upNextTracks.length - 1}
                          onClick={(e) => {
                            e.stopPropagation();
                            reorderQueue(globalIdx, globalIdx + 1);
                          }}
                          className={`p-1 rounded-lg transition-colors ${
                            idx === upNextTracks.length - 1
                              ? 'text-white/20 cursor-not-allowed'
                              : 'text-[#6B6A7A] hover:text-white hover:bg-white/10'
                          }`}
                          title="Move Down"
                        >
                          <ChevronDown className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            removeFromQueue(globalIdx);
                          }}
                          className="p-1 rounded-lg text-[#6B6A7A] hover:text-red-400 hover:bg-white/10 transition-colors"
                          title="Remove from Queue"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-12 text-[#6B6A7A] text-center gap-2">
                <ListMusic className="w-8 h-8 opacity-40" />
                <p className="text-xs">No upcoming tracks</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
