import { create } from 'zustand';
import { Song } from '../types/music';
import { usePlayerStore } from './playerStore';
import { useSettingsStore } from './settingsStore';
import { JioSaavnApi } from '../api/jiosaavn';
import {
  ListenTogetherClient,
  ListenTogetherSession,
  ListenTogetherParticipant,
  ListenTogetherPlaybackState,
  generateRandomRoomCode,
  roomCodeToIp,
  ipToRoomCode,
} from '../utils/listenTogetherClient';

export interface Reaction {
  id: string;
  emoji: string;
  fromName: string;
  timestamp: number;
}

export interface ListenTogetherMessage {
  type: 'SYNC_STATE' | 'JOIN' | 'LEAVE' | 'REACTION' | 'HOST_TRANSFER' | 'REQUEST_SYNC';
  senderId: string;
  senderName: string;
  avatar: string;
  roomCode: string;
  payload?: any;
}

interface ListenTogetherState {
  roomCode: string | null;
  isHost: boolean;
  participantId: string;
  participants: ListenTogetherParticipant[];
  reactions: Reaction[];
  syncStatus: 'idle' | 'connected' | 'syncing' | 'error';
  lastSyncedAt: number | null;
  syncLatencyMs: number | null;
  hostDeviceType: 'web' | 'vynce_app' | 'local';
  errorMessage: string | null;

  // Actions
  createRoom: (hostName: string) => string;
  joinRoom: (code: string, userName: string) => Promise<boolean>;
  leaveRoom: () => void;
  sendReaction: (emoji: string) => void;
  broadcastPlayback: (song: Song | null, isPlaying: boolean, currentTime: number) => void;
  addReactionLocally: (emoji: string, fromName: string) => void;
  forceResync: () => Promise<void>;
}

// Global broadcast channel for cross-tab realtime sync
let broadcastChannel: BroadcastChannel | null = null;
if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
  broadcastChannel = new BroadcastChannel('vynce_listen_together');
}

let syncIntervalTimer: any = null;
let lastAppliedVersion = 0;
let isResolvingSong = false;

export const useListenTogetherStore = create<ListenTogetherState>()((set, get) => {
  const participantId = `usr_${Math.random().toString(36).substring(2, 9)}`;

  const stopSyncLoop = () => {
    if (syncIntervalTimer) {
      clearInterval(syncIntervalTimer);
      syncIntervalTimer = null;
    }
  };

  const startSyncLoop = (code: string) => {
    stopSyncLoop();
    syncIntervalTimer = setInterval(async () => {
      const { roomCode, isHost, hostDeviceType } = get();
      if (!roomCode || isHost || hostDeviceType !== 'vynce_app') return;

      const startPing = Date.now();
      try {
        const session = await ListenTogetherClient.getSession(roomCode, participantId);
        const latency = Date.now() - startPing;

        set({
          participants: session.participantList || [],
          lastSyncedAt: Date.now(),
          syncLatencyMs: latency,
          syncStatus: 'connected',
        });

        // Apply state if newer or if drift is significant
        if (session.state && session.stateVersion > lastAppliedVersion) {
          lastAppliedVersion = session.stateVersion;
          await applyRemotePlaybackState(session.state);
        }
      } catch (err: any) {
        // Fallback or retry
      }
    }, 800);
  };

  const applyRemotePlaybackState = async (state: ListenTogetherPlaybackState) => {
    if (!state || isResolvingSong) return;
    const player = usePlayerStore.getState();

    const targetPosSec = (state.positionMs || 0) / 1000;
    const isPlaying = state.isPlaying ?? true;

    // Check if song needs to be loaded
    const isSameSong =
      (state.songId && player.currentSong?.id === state.songId) ||
      (state.title && player.currentSong?.name.toLowerCase() === state.title.toLowerCase());

    if (!isSameSong && state.title) {
      isResolvingSong = true;
      try {
        const query = `${state.title} ${(state.artists && state.artists[0]) || ''}`.trim();
        const results = await JioSaavnApi.searchSongs(query, 5);
        if (results && results.length > 0) {
          const match = results[0];
          await player.playSong(match, results, 0);
          player.seek(targetPosSec);
          if (!isPlaying && player.status === 'playing') {
            player.togglePlay();
          }
        }
      } catch (err) {
        console.error('Failed to resolve song from sync:', err);
      } finally {
        isResolvingSong = false;
      }
    } else {
      // Song already loaded, check seek drift & play state
      if (Math.abs(player.currentTime - targetPosSec) > 1.5) {
        player.seek(targetPosSec);
      }
      if (isPlaying && player.status !== 'playing') {
        player.togglePlay();
      } else if (!isPlaying && player.status === 'playing') {
        player.togglePlay();
      }
    }
  };

  // Listen for broadcast messages across tabs / windows
  if (typeof window !== 'undefined') {
    const handleIncomingMessage = (msg: ListenTogetherMessage) => {
      const { roomCode, isHost } = get();
      if (!roomCode || msg.roomCode !== roomCode) return;
      if (msg.senderId === participantId) return; // ignore self

      if (msg.type === 'SYNC_STATE' && !isHost) {
        const { song, isPlaying, currentTime } = msg.payload || {};
        const player = usePlayerStore.getState();

        if (song) {
          if (!player.currentSong || player.currentSong.id !== song.id) {
            player.playSong(song, [song], 0);
          }
          if (typeof currentTime === 'number' && Math.abs(player.currentTime - currentTime) > 1.5) {
            player.seek(currentTime);
          }
          if (isPlaying && player.status !== 'playing') {
            player.togglePlay();
          } else if (!isPlaying && player.status === 'playing') {
            player.togglePlay();
          }
        }
        set({ lastSyncedAt: Date.now(), syncStatus: 'connected' });
      } else if (msg.type === 'JOIN') {
        set((state) => {
          const exists = state.participants.some((p) => p.id === msg.senderId);
          if (exists) return state;
          return {
            participants: [
              ...state.participants,
              {
                id: msg.senderId,
                name: msg.senderName,
                isHost: false,
              },
            ],
          };
        });

        if (isHost) {
          const player = usePlayerStore.getState();
          get().broadcastPlayback(player.currentSong, player.status === 'playing', player.currentTime);
        }
      } else if (msg.type === 'LEAVE') {
        set((state) => ({
          participants: state.participants.filter((p) => p.id !== msg.senderId),
        }));
      } else if (msg.type === 'REACTION') {
        get().addReactionLocally(msg.payload?.emoji || '❤️', msg.senderName);
      } else if (msg.type === 'REQUEST_SYNC' && isHost) {
        const player = usePlayerStore.getState();
        get().broadcastPlayback(player.currentSong, player.status === 'playing', player.currentTime);
      }
    };

    if (broadcastChannel) {
      broadcastChannel.onmessage = (event) => {
        handleIncomingMessage(event.data);
      };
    }

    window.addEventListener('storage', (e) => {
      if (e.key === 'vynce_listen_together_event' && e.newValue) {
        try {
          const data = JSON.parse(e.newValue);
          handleIncomingMessage(data);
        } catch {}
      }
    });
  }

  const broadcast = (msg: ListenTogetherMessage) => {
    if (broadcastChannel) {
      broadcastChannel.postMessage(msg);
    }
    if (typeof window !== 'undefined') {
      try {
        localStorage.setItem(
          'vynce_listen_together_event',
          JSON.stringify({ ...msg, _t: Date.now() })
        );
      } catch {}
    }
  };

  return {
    roomCode: null,
    isHost: false,
    participantId,
    participants: [],
    reactions: [],
    syncStatus: 'idle',
    lastSyncedAt: null,
    syncLatencyMs: null,
    hostDeviceType: 'local',
    errorMessage: null,

    createRoom: (hostName: string) => {
      const code = generateRandomRoomCode();
      const settings = useSettingsStore.getState();
      const host: ListenTogetherParticipant = {
        id: participantId,
        name: hostName || settings.userName,
        isHost: true,
      };

      set({
        roomCode: code,
        isHost: true,
        participants: [host],
        syncStatus: 'connected',
        lastSyncedAt: Date.now(),
        hostDeviceType: 'local',
        errorMessage: null,
      });

      broadcast({
        type: 'JOIN',
        senderId: participantId,
        senderName: host.name,
        avatar: settings.userAvatar || 'purple',
        roomCode: code,
      });

      const player = usePlayerStore.getState();
      if (player.currentSong) {
        get().broadcastPlayback(player.currentSong, player.status === 'playing', player.currentTime);
      }

      return code;
    },

    joinRoom: async (code: string, userName: string) => {
      const cleanCode = code.trim().toUpperCase();
      if (!cleanCode) return false;

      const settings = useSettingsStore.getState();
      const myName = userName || settings.userName || 'Guest';

      set({ syncStatus: 'syncing', errorMessage: null });

      // Check if this looks like a Vynce Android app host code (8 hex digits or IP:Port)
      const isIpOrHex = roomCodeToIp(cleanCode) !== null || cleanCode.includes(':') || cleanCode.includes('.');

      if (isIpOrHex) {
        try {
          const session = await ListenTogetherClient.joinSession(cleanCode, myName);
          set({
            roomCode: cleanCode,
            isHost: false,
            participants: session.participantList || [],
            syncStatus: 'connected',
            lastSyncedAt: Date.now(),
            hostDeviceType: 'vynce_app',
          });

          if (session.state) {
            lastAppliedVersion = session.stateVersion;
            await applyRemotePlaybackState(session.state);
          }

          startSyncLoop(cleanCode);
          return true;
        } catch (err: any) {
          console.warn('Direct HTTP join failed, falling back to peer sync:', err);
        }
      }

      // Default to cross-tab / peer broadcast channel sync
      const me: ListenTogetherParticipant = {
        id: participantId,
        name: myName,
        isHost: false,
      };

      set({
        roomCode: cleanCode,
        isHost: false,
        participants: [me],
        syncStatus: 'connected',
        lastSyncedAt: Date.now(),
        hostDeviceType: 'local',
      });

      broadcast({
        type: 'JOIN',
        senderId: participantId,
        senderName: me.name,
        avatar: settings.userAvatar || 'purple',
        roomCode: cleanCode,
      });

      broadcast({
        type: 'REQUEST_SYNC',
        senderId: participantId,
        senderName: me.name,
        avatar: settings.userAvatar || 'purple',
        roomCode: cleanCode,
      });

      return true;
    },

    leaveRoom: () => {
      stopSyncLoop();
      const { roomCode, isHost } = get();
      if (roomCode) {
        const settings = useSettingsStore.getState();
        broadcast({
          type: 'LEAVE',
          senderId: participantId,
          senderName: settings.userName,
          avatar: settings.userAvatar,
          roomCode,
        });

        if (get().hostDeviceType === 'vynce_app') {
          ListenTogetherClient.leaveSession(roomCode, participantId);
        }
      }

      set({
        roomCode: null,
        isHost: false,
        participants: [],
        syncStatus: 'idle',
        lastSyncedAt: null,
        syncLatencyMs: null,
        errorMessage: null,
      });
    },

    sendReaction: (emoji: string) => {
      const { roomCode } = get();
      if (!roomCode) return;
      const settings = useSettingsStore.getState();

      get().addReactionLocally(emoji, settings.userName);

      broadcast({
        type: 'REACTION',
        senderId: participantId,
        senderName: settings.userName,
        avatar: settings.userAvatar,
        roomCode,
        payload: { emoji },
      });
    },

    broadcastPlayback: (song: Song | null, isPlaying: boolean, currentTime: number) => {
      const { roomCode, isHost, hostDeviceType } = get();
      if (!roomCode || !isHost || !song) return;

      const settings = useSettingsStore.getState();

      // Broadcast to local tabs
      broadcast({
        type: 'SYNC_STATE',
        senderId: participantId,
        senderName: settings.userName,
        avatar: settings.userAvatar,
        roomCode,
        payload: {
          song,
          isPlaying,
          currentTime,
          timestamp: Date.now(),
        },
      });

      // If connected to HTTP room coordinator
      if (hostDeviceType === 'vynce_app') {
        ListenTogetherClient.updateState(roomCode, participantId, {
          songId: song.id,
          title: song.name,
          artists: song.primaryArtists ? [song.primaryArtists] : [],
          thumbnailUrl: song.image,
          positionMs: Math.floor(currentTime * 1000),
          isPlaying,
        }).catch(() => {});
      }

      set({ lastSyncedAt: Date.now() });
    },

    addReactionLocally: (emoji: string, fromName: string) => {
      const reaction: Reaction = {
        id: `${Date.now()}_${Math.random()}`,
        emoji,
        fromName,
        timestamp: Date.now(),
      };

      set((state) => ({
        reactions: [...state.reactions.slice(-15), reaction],
      }));
    },

    forceResync: async () => {
      const { roomCode, isHost, hostDeviceType } = get();
      if (!roomCode) return;

      if (isHost) {
        const player = usePlayerStore.getState();
        get().broadcastPlayback(player.currentSong, player.status === 'playing', player.currentTime);
      } else if (hostDeviceType === 'vynce_app') {
        try {
          const session = await ListenTogetherClient.getSession(roomCode, participantId);
          if (session.state) {
            await applyRemotePlaybackState(session.state);
          }
        } catch {}
      } else {
        const settings = useSettingsStore.getState();
        broadcast({
          type: 'REQUEST_SYNC',
          senderId: participantId,
          senderName: settings.userName,
          avatar: settings.userAvatar,
          roomCode,
        });
      }
    },
  };
});
