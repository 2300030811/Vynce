import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { audioEngine } from '../audio/AudioEngine';

export interface SettingsState {
  // User Profile
  userName: string;
  userBio: string;
  userAvatar: string;
  customAvatarUrl: string;
  userTitle: string;
  setUserName: (name: string) => void;
  setUserBio: (bio: string) => void;
  setUserAvatar: (avatar: string) => void;
  setCustomAvatarUrl: (url: string) => void;
  setUserTitle: (title: string) => void;

  // Audio & Playback
  bitrate: '320kbps' | '160kbps' | '96kbps';
  languages: string[];
  theme: 'dynamic' | 'dark' | 'amoled';
  accentColor: string;
  visualizerStyle: 'bars' | 'wave' | 'circle';
  crossfadeSec: number;
  showLyricsByDefault: boolean;
  ambientGlow: boolean;
  autoScrollLyrics: boolean;
  loudnessNormalization: boolean;
  autoplaySimilar: boolean;
  explicitContent: boolean;
  lyricsTransliteration: boolean;
  
  setBitrate: (bitrate: '320kbps' | '160kbps' | '96kbps') => void;
  toggleLanguage: (lang: string) => void;
  setLanguages: (languages: string[]) => void;
  setTheme: (theme: 'dynamic' | 'dark' | 'amoled') => void;
  setAccentColor: (color: string) => void;
  setVisualizerStyle: (style: 'bars' | 'wave' | 'circle') => void;
  setCrossfadeSec: (sec: number) => void;
  setAmbientGlow: (enabled: boolean) => void;
  setAutoScrollLyrics: (enabled: boolean) => void;
  setLoudnessNormalization: (enabled: boolean) => void;
  setAutoplaySimilar: (enabled: boolean) => void;
  setExplicitContent: (enabled: boolean) => void;
  setLyricsTransliteration: (enabled: boolean) => void;
}

export const AVAILABLE_LANGUAGES = [
  { id: 'hindi', label: 'Hindi', native: 'हिन्दी' },
  { id: 'english', label: 'English', native: 'English' },
  { id: 'punjabi', label: 'Punjabi', native: 'ਪੰਜਾਬੀ' },
  { id: 'telugu', label: 'Telugu', native: 'తెలుగు' },
  { id: 'tamil', label: 'Tamil', native: 'தமிழ்' },
  { id: 'bhojpuri', label: 'Bhojpuri', native: 'भोजपुरी' },
  { id: 'bengali', label: 'Bengali', native: 'বাংলা' },
  { id: 'marathi', label: 'Marathi', native: 'मराठी' },
  { id: 'kannada', label: 'Kannada', native: 'ಕನ್ನಡ' },
  { id: 'malayalam', label: 'Malayalam', native: 'മലയാളം' },
  { id: 'gujarati', label: 'Gujarati', native: 'ગુજરાતી' },
];

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set, get) => ({
      // Profile State
      userName: 'Vynce Listener',
      userBio: 'Music is life • High-Res streaming on Vynce',
      userAvatar: 'purple',
      customAvatarUrl: '',
      userTitle: 'Audiophile Member',
      setUserName: (userName) => set({ userName }),
      setUserBio: (userBio) => set({ userBio }),
      setUserAvatar: (userAvatar) => set({ userAvatar }),
      setCustomAvatarUrl: (customAvatarUrl) => set({ customAvatarUrl }),
      setUserTitle: (userTitle) => set({ userTitle }),

      // Audio & Theme State
      bitrate: '320kbps',
      languages: ['hindi', 'english', 'punjabi'],
      theme: 'dynamic',
      accentColor: 'dynamic',
      visualizerStyle: 'bars',
      crossfadeSec: 0,
      showLyricsByDefault: true,
      ambientGlow: true,
      autoScrollLyrics: true,
      loudnessNormalization: false,
      autoplaySimilar: true,
      explicitContent: true,
      lyricsTransliteration: false,

      setBitrate: (bitrate) => set({ bitrate }),
      toggleLanguage: (lang) => {
        const { languages } = get();
        if (languages.includes(lang)) {
          if (languages.length > 1) {
            set({ languages: languages.filter((l) => l !== lang) });
          }
        } else {
          set({ languages: [...languages, lang] });
        }
      },
      setLanguages: (languages) => set({ languages }),
      setTheme: (theme) => set({ theme }),
      setAccentColor: (accentColor) => set({ accentColor }),
      setVisualizerStyle: (visualizerStyle) => set({ visualizerStyle }),
      setCrossfadeSec: (crossfadeSec) => set({ crossfadeSec }),
      setAmbientGlow: (ambientGlow) => set({ ambientGlow }),
      setAutoScrollLyrics: (autoScrollLyrics) => set({ autoScrollLyrics }),
      setLoudnessNormalization: (loudnessNormalization) => {
        set({ loudnessNormalization });
        audioEngine.setLoudnessNormalization(loudnessNormalization);
      },
      setAutoplaySimilar: (autoplaySimilar) => set({ autoplaySimilar }),
      setExplicitContent: (explicitContent) => set({ explicitContent }),
      setLyricsTransliteration: (lyricsTransliteration) => set({ lyricsTransliteration }),
    }),
    {
      name: 'vynce_settings',
    }
  )
);
