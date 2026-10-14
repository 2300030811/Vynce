import { Song } from '../types/music';

export interface ListenTogetherPlaybackState {
  songId?: string;
  title?: string;
  artists?: string[];
  thumbnailUrl?: string;
  positionMs?: number;
  isPlaying?: boolean;
}

export interface ListenTogetherParticipant {
  id: string;
  name: string;
  avatar?: string;
  isHost: boolean;
  joinedAt?: number;
}

export interface ListenTogetherSession {
  code: string;
  participantId: string;
  joinUrl?: string;
  participants: number;
  participantList: ListenTogetherParticipant[];
  hostName: string;
  controllerId: string;
  controllerName: string;
  stateVersion: number;
  serverNow: number;
  state?: ListenTogetherPlaybackState | null;
  targetEndpoint?: string;
}

export const DEFAULT_LISTEN_TOGETHER_PORT = 8945;

/**
 * Converts a 4-octet IPv4 string (e.g. 192.168.1.45) into an 8-character uppercase hex code (e.g. C0A8012D).
 */
export function ipToRoomCode(ip: string): string {
  try {
    const parts = ip.trim().split('.').map((p) => parseInt(p, 10));
    if (parts.length === 4 && parts.every((p) => !isNaN(p) && p >= 0 && p <= 255)) {
      return parts
        .map((p) => p.toString(16).toUpperCase().padStart(2, '0'))
        .join('');
    }
  } catch {}
  return generateRandomRoomCode();
}

/**
 * Decodes an 8-character uppercase hex code into a 4-octet IPv4 string.
 */
export function roomCodeToIp(code: string): string | null {
  const clean = code.trim().toUpperCase();
  if (clean.includes('.')) return clean;
  if (clean.length === 8 && /^[0-9A-F]{8}$/.test(clean)) {
    try {
      const p0 = parseInt(clean.substring(0, 2), 16);
      const p1 = parseInt(clean.substring(2, 4), 16);
      const p2 = parseInt(clean.substring(4, 6), 16);
      const p3 = parseInt(clean.substring(6, 8), 16);
      return `${p0}.${p1}.${p2}.${p3}`;
    } catch {}
  }
  return null;
}

export function generateRandomRoomCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let result = '';
  for (let i = 0; i < 6; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

/**
 * Resolves the target base HTTP URL for a given room code or direct IP / port.
 */
export function resolveBaseUrl(code: string): string {
  const clean = code.trim();
  if (clean.startsWith('http://') || clean.startsWith('https://')) {
    return clean.replace(/\/+$/, '');
  }

  const decodedIp = roomCodeToIp(clean) || clean;
  const hostWithPort = decodedIp.includes(':') ? decodedIp : `${decodedIp}:${DEFAULT_LISTEN_TOGETHER_PORT}`;
  return `http://${hostWithPort}`;
}

export class ListenTogetherClient {
  private static endpoints: Record<string, string> = {};

  static setEndpoint(code: string, url: string) {
    this.endpoints[code.toUpperCase()] = url;
  }

  static getEndpoint(code: string): string {
    return this.endpoints[code.toUpperCase()] || resolveBaseUrl(code);
  }

  static async joinSession(code: string, displayName: string): Promise<ListenTogetherSession> {
    const cleanCode = code.trim().toUpperCase();
    const baseUrl = this.getEndpoint(cleanCode);

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4000);

    try {
      const res = await fetch(`${baseUrl}/sessions/${cleanCode}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: displayName || 'Vynce Guest' }),
        signal: controller.signal,
      });

      clearTimeout(timeout);
      if (!res.ok) {
        throw new Error(`Failed to join session (${res.status})`);
      }
      const data = await res.json();
      return {
        ...data,
        targetEndpoint: baseUrl,
      };
    } catch (err: any) {
      clearTimeout(timeout);
      throw err;
    }
  }

  static async getSession(code: string, participantId?: string): Promise<ListenTogetherSession> {
    const cleanCode = code.trim().toUpperCase();
    const baseUrl = this.getEndpoint(cleanCode);

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);

    try {
      const headers: Record<string, string> = {};
      if (participantId) {
        headers['X-Participant-Id'] = participantId;
      }

      const res = await fetch(`${baseUrl}/sessions/${cleanCode}`, {
        headers,
        signal: controller.signal,
      });

      clearTimeout(timeout);
      if (!res.ok) {
        throw new Error(`Failed to fetch session (${res.status})`);
      }
      const data = await res.json();
      return {
        ...data,
        targetEndpoint: baseUrl,
      };
    } catch (err: any) {
      clearTimeout(timeout);
      throw err;
    }
  }

  static async updateState(
    code: string,
    participantId: string,
    state: ListenTogetherPlaybackState
  ): Promise<ListenTogetherSession> {
    const cleanCode = code.trim().toUpperCase();
    const baseUrl = this.getEndpoint(cleanCode);

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);

    try {
      const res = await fetch(`${baseUrl}/sessions/${cleanCode}/state`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          participantId,
          state,
        }),
        signal: controller.signal,
      });

      clearTimeout(timeout);
      if (!res.ok) {
        throw new Error(`Failed to update state (${res.status})`);
      }
      return await res.json();
    } catch (err: any) {
      clearTimeout(timeout);
      throw err;
    }
  }

  static async leaveSession(code: string, participantId: string): Promise<void> {
    const cleanCode = code.trim().toUpperCase();
    const baseUrl = this.getEndpoint(cleanCode);

    try {
      await fetch(`${baseUrl}/sessions/${cleanCode}/leave`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ participantId }),
      });
    } catch {
      // ignore
    }
  }
}
