import React, { useEffect, useState, useRef } from 'react';
import { createPortal } from 'react-dom';
import { WebVoiceRecognition, VoiceCommandParser, VoiceCommand } from '../../utils/voiceAssistant';
import { usePlayerStore } from '../../stores/playerStore';
import { useLibraryStore } from '../../stores/libraryStore';
import { JioSaavnApi } from '../../api/jiosaavn';
import { Mic, MicOff, X, Sparkles, Volume2, Music, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';

interface VoiceSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSearchSubmit: (query: string) => void;
}

export const VoiceSearchModal: React.FC<VoiceSearchModalProps> = ({
  isOpen,
  onClose,
  onSearchSubmit,
}) => {
  const {
    playSong,
    togglePlay,
    nextTrack,
    prevTrack,
    setVolume,
    toggleMute,
    toggleShuffle,
    toggleLyrics,
    status,
  } = usePlayerStore();
  const { likedSongs, history } = useLibraryStore();

  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [feedbackMsg, setFeedbackMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const stopListenerRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    if (isOpen) {
      startListening();
      const onKeyDown = (e: KeyboardEvent) => {
        if (e.key === 'Escape') {
          onClose();
        }
      };
      window.addEventListener('keydown', onKeyDown);
      return () => {
        window.removeEventListener('keydown', onKeyDown);
        cleanup();
      };
    } else {
      cleanup();
    }
  }, [isOpen]);

  const cleanup = () => {
    if (stopListenerRef.current) {
      stopListenerRef.current();
      stopListenerRef.current = null;
    }
    setIsListening(false);
  };

  const startListening = () => {
    cleanup();
    setTranscript('');
    setFeedbackMsg(null);
    setErrorMsg(null);
    setIsProcessing(false);
    setIsListening(true);

    const listener = WebVoiceRecognition.startListening(
      (text, isFinal) => {
        setTranscript(text);
        if (isFinal) {
          handleExecuteCommand(text);
        }
      },
      (err) => {
        setErrorMsg(err === 'no-speech' ? 'No speech detected. Try speaking again.' : err);
        setIsListening(false);
      },
      () => {
        setIsListening(false);
      }
    );

    stopListenerRef.current = listener.stop;
  };

  const handleExecuteCommand = async (rawText: string) => {
    setIsProcessing(true);
    const cmd = VoiceCommandParser.parse(rawText);

    switch (cmd.type) {
      case 'play_liked':
        if (likedSongs.length > 0) {
          setFeedbackMsg(`Playing your Liked Songs (${likedSongs.length} tracks)...`);
          setTimeout(() => {
            playSong(likedSongs[0], likedSongs, 0);
            onClose();
          }, 800);
        } else {
          setErrorMsg('No liked songs in your library.');
          setIsProcessing(false);
        }
        break;

      case 'play_history':
        if (history.length > 0) {
          const songs = history.map((h) => h.song);
          setFeedbackMsg('Playing recently played songs...');
          setTimeout(() => {
            playSong(songs[0], songs, 0);
            onClose();
          }, 800);
        } else {
          setErrorMsg('Listening history is empty.');
          setIsProcessing(false);
        }
        break;

      case 'pause':
        if (status === 'playing') {
          togglePlay();
          setFeedbackMsg('Playback paused.');
        } else {
          setFeedbackMsg('Already paused.');
        }
        setTimeout(onClose, 800);
        break;

      case 'resume':
        if (status !== 'playing') {
          togglePlay();
          setFeedbackMsg('Resuming playback.');
        } else {
          setFeedbackMsg('Already playing.');
        }
        setTimeout(onClose, 800);
        break;

      case 'next':
        setFeedbackMsg('Skipping to next track...');
        await nextTrack();
        setTimeout(onClose, 800);
        break;

      case 'previous':
        setFeedbackMsg('Playing previous track...');
        await prevTrack();
        setTimeout(onClose, 800);
        break;

      case 'set_volume':
        setVolume(cmd.percent / 100);
        setFeedbackMsg(`Volume set to ${cmd.percent}%`);
        setTimeout(onClose, 800);
        break;

      case 'toggle_mute':
        toggleMute();
        setFeedbackMsg('Mute toggled.');
        setTimeout(onClose, 800);
        break;

      case 'toggle_shuffle':
        if (cmd.enabled !== undefined) {
          toggleShuffle();
          setFeedbackMsg(`Shuffle ${cmd.enabled ? 'enabled' : 'disabled'}.`);
        } else {
          toggleShuffle();
          setFeedbackMsg('Shuffle toggled.');
        }
        setTimeout(onClose, 800);
        break;

      case 'toggle_lyrics':
        toggleLyrics();
        setFeedbackMsg('Lyrics toggled.');
        setTimeout(onClose, 800);
        break;

      case 'search':
        setFeedbackMsg(`Searching for "${cmd.query}"...`);
        setTimeout(() => {
          onSearchSubmit(cmd.query);
          onClose();
        }, 800);
        break;

      case 'play_song': {
        const query = cmd.query;
        setFeedbackMsg(`Finding and playing "${query}"...`);
        try {
          const songs = await JioSaavnApi.searchSongs(query, 10);
          if (songs && songs.length > 0) {
            playSong(songs[0], songs, 0);
            setTimeout(onClose, 1000);
          } else {
            onSearchSubmit(query);
            onClose();
          }
        } catch {
          onSearchSubmit(query);
          onClose();
        }
        break;
      }

      default: {
        const query = rawText;
        setFeedbackMsg(`Searching for "${query}"...`);
        try {
          const songs = await JioSaavnApi.searchSongs(query, 10);
          if (songs && songs.length > 0) {
            playSong(songs[0], songs, 0);
            setTimeout(onClose, 1000);
          } else {
            onSearchSubmit(query);
            onClose();
          }
        } catch {
          onSearchSubmit(query);
          onClose();
        }
        break;
      }
    }
  };

  if (!isOpen || typeof document === 'undefined') return null;

  return createPortal(
    <div
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-in fade-in duration-200"
    >
      <div className="relative w-full max-w-md bg-[#0D0C13] border border-white/15 rounded-[32px] p-7 shadow-2xl flex flex-col items-center text-center gap-6 overflow-hidden">
        {/* Dynamic Glowing Aura */}
        <div
          className="absolute -top-32 left-1/2 -translate-x-1/2 w-72 h-44 blur-3xl opacity-50 pointer-events-none transition-all duration-700"
          style={{ background: 'var(--vynce-primary)' }}
        />

        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-5 right-5 p-2 rounded-full hover:bg-white/10 text-[#8A8998] hover:text-white transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Animated Mic Sphere */}
        <div className="relative flex items-center justify-center mt-3">
          {isListening && (
            <>
              <div
                style={{ borderColor: 'var(--vynce-primary)' }}
                className="absolute w-28 h-28 rounded-full border border-dashed opacity-40 animate-spin-slow"
              />
              <div
                style={{ backgroundColor: 'var(--vynce-primary)' }}
                className="absolute w-24 h-24 rounded-full opacity-20 animate-ping"
              />
              <div
                style={{ backgroundColor: 'var(--vynce-primary)' }}
                className="absolute w-20 h-20 rounded-full opacity-30 animate-pulse"
              />
            </>
          )}

          <button
            onClick={isListening ? cleanup : startListening}
            style={{
              backgroundColor: isListening
                ? 'var(--vynce-primary)'
                : 'var(--vynce-surface-container-highest)',
              color: isListening ? '#000' : 'var(--vynce-primary)',
            }}
            className="relative z-10 w-20 h-20 rounded-full flex items-center justify-center shadow-2xl transition-all active:scale-95 hover:scale-105"
          >
            {isProcessing ? (
              <Loader2 className="w-8 h-8 animate-spin" />
            ) : isListening ? (
              <Mic className="w-8 h-8 animate-pulse" />
            ) : (
              <MicOff className="w-8 h-8" />
            )}
          </button>
        </div>

        {/* Title & Speech State */}
        <div className="flex flex-col items-center gap-1 z-10">
          <h2 className="text-xl font-bold text-white font-['Outfit']">
            {isProcessing
              ? 'Executing command...'
              : isListening
              ? 'Listening...'
              : 'Voice Assistant'}
          </h2>
          <p className="text-xs text-[#8A8998]">
            {isListening
              ? 'Say "Play [song]", "Next track", "Pause", or "Set volume 80%"'
              : 'Tap microphone to speak'}
          </p>
        </div>

        {/* Live Transcript / Feedback */}
        <div className="w-full min-h-[64px] flex items-center justify-center p-3.5 rounded-2xl bg-white/[0.03] border border-white/[0.08] z-10">
          {feedbackMsg ? (
            <div className="flex items-center gap-2 text-xs font-bold text-green-400">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{feedbackMsg}</span>
            </div>
          ) : errorMsg ? (
            <div className="flex items-center gap-2 text-xs font-semibold text-red-400">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMsg}</span>
            </div>
          ) : transcript ? (
            <p className="text-sm font-semibold text-white italic">"{transcript}"</p>
          ) : (
            <p className="text-xs text-[#555462]">Listening for voice commands...</p>
          )}
        </div>

        {/* Quick Voice Chips */}
        <div className="flex flex-wrap items-center justify-center gap-1.5 z-10">
          {['Play Kesariya', 'Next song', 'Play my liked songs', 'Volume 80%'].map((example) => (
            <button
              key={example}
              onClick={() => handleExecuteCommand(example)}
              className="px-3 py-1 rounded-full bg-white/[0.04] hover:bg-white/[0.08] border border-white/5 text-[11px] text-[#A6A4B8] hover:text-white transition-colors"
            >
              {example}
            </button>
          ))}
        </div>
      </div>
    </div>,
    document.body
  );
};
