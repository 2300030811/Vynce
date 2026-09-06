import { create } from 'zustand';
import { Song } from '../types/music';

export interface QueueState {
  queue: Song[];
  currentIndex: number;
  originalQueue: Song[]; // Used when un-shuffling

  setQueue: (songs: Song[], startIndex?: number) => void;
  addToQueue: (song: Song | Song[]) => void;
  playNext: (song: Song) => void;
  removeFromQueue: (index: number) => void;
  reorderQueue: (fromIndex: number, toIndex: number) => void;
  clearQueue: () => void;
  getCurrentSong: () => Song | null;
  getNextSong: () => Song | null;
  getPrevSong: () => Song | null;
  advance: () => Song | null;
  rewind: () => Song | null;
  shuffleQueue: (isShuffled: boolean) => void;
}

export const useQueueStore = create<QueueState>((set, get) => ({
  queue: [],
  currentIndex: 0,
  originalQueue: [],

  setQueue: (songs: Song[], startIndex: number = 0) => {
    set({
      queue: songs,
      originalQueue: [...songs],
      currentIndex: Math.max(0, Math.min(startIndex, songs.length - 1)),
    });
  },

  addToQueue: (songInput: Song | Song[]) => {
    const { queue, originalQueue } = get();
    const toAdd = Array.isArray(songInput) ? songInput : [songInput];
    set({
      queue: [...queue, ...toAdd],
      originalQueue: [...originalQueue, ...toAdd],
    });
  },

  playNext: (song: Song) => {
    const { queue, currentIndex } = get();
    if (queue.length === 0) {
      set({ queue: [song], originalQueue: [song], currentIndex: 0 });
      return;
    }
    const nextQueue = [...queue];
    nextQueue.splice(currentIndex + 1, 0, song);
    set({ queue: nextQueue });
  },

  removeFromQueue: (index: number) => {
    const { queue, currentIndex } = get();
    if (index < 0 || index >= queue.length) return;

    const nextQueue = queue.filter((_, i) => i !== index);
    let nextIndex = currentIndex;
    if (index < currentIndex) {
      nextIndex = Math.max(0, currentIndex - 1);
    } else if (index === currentIndex && index >= nextQueue.length) {
      nextIndex = Math.max(0, nextQueue.length - 1);
    }

    set({ queue: nextQueue, currentIndex: nextIndex });
  },

  reorderQueue: (fromIndex: number, toIndex: number) => {
    const { queue, currentIndex } = get();
    if (fromIndex < 0 || fromIndex >= queue.length || toIndex < 0 || toIndex >= queue.length) return;

    const currentTrack = queue[currentIndex];
    const newQueue = [...queue];
    const [movedItem] = newQueue.splice(fromIndex, 1);
    newQueue.splice(toIndex, 0, movedItem);

    // Update currentIndex to follow the currently playing track
    const nextIndex = newQueue.findIndex((s) => s.id === currentTrack?.id);

    set({ queue: newQueue, currentIndex: nextIndex !== -1 ? nextIndex : currentIndex });
  },

  clearQueue: () => {
    set({ queue: [], originalQueue: [], currentIndex: 0 });
  },

  getCurrentSong: () => {
    const { queue, currentIndex } = get();
    return queue[currentIndex] || null;
  },

  getNextSong: () => {
    const { queue, currentIndex } = get();
    if (currentIndex + 1 < queue.length) {
      return queue[currentIndex + 1];
    }
    return null;
  },

  getPrevSong: () => {
    const { queue, currentIndex } = get();
    if (currentIndex > 0) {
      return queue[currentIndex - 1];
    }
    return null;
  },

  advance: () => {
    const { queue, currentIndex } = get();
    if (currentIndex + 1 < queue.length) {
      const nextIndex = currentIndex + 1;
      set({ currentIndex: nextIndex });
      return queue[nextIndex];
    }
    return null;
  },

  rewind: () => {
    const { queue, currentIndex } = get();
    if (currentIndex > 0) {
      const prevIndex = currentIndex - 1;
      set({ currentIndex: prevIndex });
      return queue[prevIndex];
    }
    return null;
  },

  shuffleQueue: (isShuffled: boolean) => {
    const { queue, originalQueue, currentIndex } = get();
    if (queue.length <= 1) return;

    const currentTrack = queue[currentIndex];

    if (isShuffled) {
      const otherTracks = queue.filter((_, idx) => idx !== currentIndex);
      // Fisher-Yates shuffle
      for (let i = otherTracks.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [otherTracks[i], otherTracks[j]] = [otherTracks[j], otherTracks[i]];
      }
      set({
        queue: [currentTrack, ...otherTracks],
        currentIndex: 0,
      });
    } else {
      const originalIdx = originalQueue.findIndex((s) => s.id === currentTrack?.id);
      set({
        queue: [...originalQueue],
        currentIndex: originalIdx !== -1 ? originalIdx : 0,
      });
    }
  },
}));
