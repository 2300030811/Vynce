import { Song } from '../types/music';

export function updateMediaSession(
  song: Song,
  handlers: {
    onPlay: () => void;
    onPause: () => void;
    onNext: () => void;
    onPrev: () => void;
    onSeek: (details: MediaSessionActionDetails) => void;
  }
) {
  if (!('mediaSession' in navigator)) return;

  try {
    navigator.mediaSession.metadata = new window.MediaMetadata({
      title: song.name,
      artist: song.primaryArtists,
      album: song.album || 'Single',
      artwork: [
        { src: song.image, sizes: '96x96', type: 'image/jpeg' },
        { src: song.image, sizes: '128x128', type: 'image/jpeg' },
        { src: song.image, sizes: '256x256', type: 'image/jpeg' },
        { src: song.image, sizes: '512x512', type: 'image/jpeg' },
      ],
    });

    navigator.mediaSession.setActionHandler('play', handlers.onPlay);
    navigator.mediaSession.setActionHandler('pause', handlers.onPause);
    navigator.mediaSession.setActionHandler('nexttrack', handlers.onNext);
    navigator.mediaSession.setActionHandler('previoustrack', handlers.onPrev);

    try {
      navigator.mediaSession.setActionHandler('seekto', (details) => {
        handlers.onSeek(details);
      });
    } catch {}
  } catch {}
}

export function updateMediaSessionPlaybackState(state: 'playing' | 'paused' | 'none') {
  if (!('mediaSession' in navigator)) return;
  try {
    navigator.mediaSession.playbackState = state;
  } catch {}
}
