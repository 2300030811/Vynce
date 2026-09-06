/**
 * Voice Assistant & Natural Language Command Parser
 * Adapted for Vynce Web from AirBeats VoiceCommandParser
 */

export type VoiceCommand =
  | { type: 'play_song'; query: string }
  | { type: 'play_liked' }
  | { type: 'play_history' }
  | { type: 'pause' }
  | { type: 'resume' }
  | { type: 'next' }
  | { type: 'previous' }
  | { type: 'set_volume'; percent: number }
  | { type: 'toggle_mute' }
  | { type: 'toggle_shuffle'; enabled?: boolean }
  | { type: 'toggle_lyrics' }
  | { type: 'search'; query: string }
  | { type: 'unknown'; raw: string };

export class VoiceCommandParser {
  public static parse(rawTranscript: string): VoiceCommand {
    const text = rawTranscript
      .trim()
      .toLowerCase()
      .replace(/[.,!?;:]/g, '')
      .replace(/^(hey|hi|ok|hello|yo|vynce)\s+/i, '')
      .trim();

    if (!text) {
      return { type: 'unknown', raw: rawTranscript };
    }

    // 1. Play Liked / Favorite Songs
    if (
      /^(play|listen to)?\s*(my\s+)?(liked|favorites?|favourites?|loved)\s*(songs?|tracks?|music)?$/i.test(
        text
      ) ||
      /^(play|listen to)\s*(songs?|music)\s+from\s+(my\s+)?(favorites?|favourites?|liked)$/i.test(
        text
      )
    ) {
      return { type: 'play_liked' };
    }

    // 2. Play History / Recent
    if (
      /^(play|listen to)?\s*(my\s+)?(history|recent|recently played)\s*(songs?|tracks?|music)?$/i.test(
        text
      )
    ) {
      return { type: 'play_history' };
    }

    // 3. Pause / Stop
    if (/^(pause|stop|halt|freeze)(\s+music|\s+song|\s+playback)?$/i.test(text)) {
      return { type: 'pause' };
    }

    // 4. Resume / Play current
    if (/^(resume|continue|unpause)(\s+music|\s+song|\s+playback)?$/i.test(text)) {
      return { type: 'resume' };
    }

    // 5. Next Track / Skip
    if (/^(next|skip|next song|next track|skip song|play next)$/i.test(text)) {
      return { type: 'next' };
    }

    // 6. Previous Track / Go back
    if (/^(previous|prev|back|go back|previous song|last song)$/i.test(text)) {
      return { type: 'previous' };
    }

    // 7. Volume percentage commands
    const volMatch = text.match(
      /(?:set\s+|change\s+|turn\s+)?volume(?:\s+to|\s+at)?\s+(\d{1,3})(?:\s*%)?|(\d{1,3})(?:\s*%|\s+percent)(?:\s+volume)?/i
    );
    if (volMatch) {
      const numStr = volMatch[1] || volMatch[2];
      const percent = Math.min(100, Math.max(0, parseInt(numStr, 10)));
      return { type: 'set_volume', percent };
    }

    // 8. Mute / Unmute
    if (/^(mute|unmute|toggle mute|silence)$/i.test(text)) {
      return { type: 'toggle_mute' };
    }

    // 9. Shuffle
    if (/^(turn on shuffle|enable shuffle|shuffle on|shuffle music)$/i.test(text)) {
      return { type: 'toggle_shuffle', enabled: true };
    }
    if (/^(turn off shuffle|disable shuffle|shuffle off|unshuffle)$/i.test(text)) {
      return { type: 'toggle_shuffle', enabled: false };
    }

    // 10. Lyrics
    if (/^(show|open|toggle|hide|close)\s+lyrics$/i.test(text) || text === 'lyrics') {
      return { type: 'toggle_lyrics' };
    }

    // 11. Explicit Play [Song / Artist]
    const playMatch = text.match(
      /^(?:play|listen to|put on|stream|start)(?:\s+the\s+song|\s+the\s+track|\s+song|\s+track)?(?:\s+called)?\s+(.+)$/i
    );
    if (playMatch && playMatch[1]) {
      return { type: 'play_song', query: playMatch[1].trim() };
    }

    // 12. Explicit Search [Query]
    const searchMatch = text.match(/^(?:search|find|look for)\s+(.+)$/i);
    if (searchMatch && searchMatch[1]) {
      return { type: 'search', query: searchMatch[1].trim() };
    }

    // 13. Implicit Song Name (Fallback to playing query if multi-word)
    return { type: 'play_song', query: text };
  }
}

export class WebVoiceRecognition {
  private static recognition: any = null;

  public static isSupported(): boolean {
    if (typeof window === 'undefined') return false;
    return !!((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition);
  }

  public static startListening(
    onResult: (transcript: string, isFinal: boolean) => void,
    onError: (error: string) => void,
    onEnd: () => void
  ): { stop: () => void } {
    if (!this.isSupported()) {
      onError('Speech recognition is not supported on this browser.');
      return { stop: () => {} };
    }

    try {
      const SpeechRecognitionClass =
        (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      const recognition = new SpeechRecognitionClass();
      this.recognition = recognition;

      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.lang = 'en-US';

      recognition.onresult = (event: any) => {
        let interim = '';
        let final = '';

        for (let i = event.resultIndex; i < event.results.length; i++) {
          const trans = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            final += trans;
          } else {
            interim += trans;
          }
        }

        onResult(final || interim, !!final);
      };

      recognition.onerror = (event: any) => {
        onError(event.error || 'Speech recognition error');
      };

      recognition.onend = () => {
        onEnd();
      };

      recognition.start();

      return {
        stop: () => {
          try {
            recognition.stop();
          } catch {}
        },
      };
    } catch (err: any) {
      onError(err.message || 'Failed to start microphone');
      return { stop: () => {} };
    }
  }
}
