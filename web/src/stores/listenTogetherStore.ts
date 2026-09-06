import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Song } from '../types/music';
import { usePlayerStore } from './playerStore';
import { useSettingsStore } from './settingsStore';

export interface Participant {
  id: string;
  name: string;
  avatar: string;
  isHost: boolean;
  joinedAt: number;
}

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
  participants: Participant[];
  reactions: Reaction[];
  syncStatus: 'idle' | 'connected' | 'syncing';
  lastSyncedAt: number | null;

  // Actions
  createRoom: (hostName: string) => string;
  joinRoom: (code: string, userName: string) => boolean;
  leaveRoom: () => void;
  sendReaction: (emoji: string) => void;
  broadcastPlayback: (song: Song | null, isPlaying: boolean, currentTime: number) => void;
  addReactionLocally: (emoji: string, fromName: string) => void;
}

// Global broadcast channel for cross-tab realtime sync
let broadcastChannel: BroadcastChannel | null = null;
if (typeof window !== 'undefined' && 'BroadcastChannel' in window) {
  broadcastChannel = new BroadcastChannel('vynce_listen_together');
}

const generateRoomCode = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
};

export const useListenTogetherStore = create<ListenTogetherState>()((set, get) => {
  // Generate random client ID for this session
  const participantId = `usr_${Math.random().toString(36).substring(2, 9)}`;

  // Listen for broadcast messages across tabs / windows
  if (typeof window !== 'undefined') {
    const handleIncomingMessage = (msg: ListenTogetherMessage) => {
      const { roomCode, isHost } = get();
      if (!roomCode || msg.roomCode !== roomCode) return;
      if (msg.senderId === participantId) return; // ignore self

      if (msg.type === 'SYNC_STATE' && !isHost) {
        // Non-hosts receive playback updates from the host
        const { song, isPlaying, currentTime } = msg.payload || {};
        const player = usePlayerStore.getState();

        if (song) {
          if (!player.currentSong || player.currentSong.id !== song.id) {
            player.playSong(song, [song], 0);
          }
          if (typeof currentTime === 'number' && Math.abs(player.currentTime - currentTime) > 2) {
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
        // Add new participant
        set((state) => {
          const exists = state.participants.some((p) => p.id === msg.senderId);
          if (exists) return state;
          return {
            participants: [
              ...state.participants,
              {
                id: msg.senderId,
                name: msg.senderName,
                avatar: msg.avatar || 'purple',
                isHost: false,
                joinedAt: Date.now(),
              },
            ],
          };
        });

        // If we are host, reply with our current state
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

    // Also support storage event for broader cross-context sync
    window.addEventListener('storage', (e) => {
      if (e.key === 'vynce_listen_together_event' && e.newValue) {
        try {
          const data = JSON.parse(e.newValue);
          handleIncomingMessage(data);
        } catch {
          // ignore
        }
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
      } catch {
        // ignore
      }
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

    createRoom: (hostName: string) => {
      const code = generateRoomCode();
      const settings = useSettingsStore.getState();
      const host: Participant = {
        id: participantId,
        name: hostName || settings.userName,
        avatar: settings.userAvatar || 'purple',
        isHost: true,
        joinedAt: Date.now(),
      };

      set({
        roomCode: code,
        isHost: true,
        participants: [host],
        syncStatus: 'connected',
        lastSyncedAt: Date.now(),
      });

      // Broadcast room creation
      broadcast({
        type: 'JOIN',
        senderId: participantId,
        senderName: host.name,
        avatar: host.avatar,
        roomCode: code,
      });

      // Initial state sync
      const player = usePlayerStore.getState();
      if (player.currentSong) {
        get().broadcastPlayback(player.currentSong, player.status === 'playing', player.currentTime);
      }

      return code;
    },

    joinRoom: (code: string, userName: string) => {
      const cleanCode = code.trim().toUpperCase();
      if (!cleanCode) return false;

      const settings = useSettingsStore.getState();
      const me: Participant = {
        id: participantId,
        name: userName || settings.userName,
        avatar: settings.userAvatar || 'purple',
        isHost: false,
        joinedAt: Date.now(),
      };

      set({
        roomCode: cleanCode,
        isHost: false,
        participants: [me],
        syncStatus: 'syncing',
        lastSyncedAt: Date.now(),
      });

      // Announce join
      broadcast({
        type: 'JOIN',
        senderId: participantId,
        senderName: me.name,
        avatar: me.avatar,
        roomCode: cleanCode,
      });

      // Request host to send current track
      broadcast({
        type: 'REQUEST_SYNC',
        senderId: participantId,
        senderName: me.name,
        avatar: me.avatar,
        roomCode: cleanCode,
      });

      return true;
    },

    leaveRoom: () => {
      const { roomCode, participants } = get();
      if (roomCode) {
        const settings = useSettingsStore.getState();
        broadcast({
          type: 'LEAVE',
          senderId: participantId,
          senderName: settings.userName,
          avatar: settings.userAvatar,
          roomCode,
        });
      }

      set({
        roomCode: null,
        isHost: false,
        participants: [],
        syncStatus: 'idle',
        lastSyncedAt: null,
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
      const { roomCode, isHost } = get();
      if (!roomCode || !isHost || !song) return;

      const settings = useSettingsStore.getState();
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
  };
});
