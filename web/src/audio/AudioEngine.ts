export const EQ_FREQUENCIES = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000] as const;

export type EqPreset =
  | 'Flat'
  | 'Bass Boost'
  | 'Bass Extreme'
  | 'Club / EDM'
  | 'Vocal Pop'
  | 'Treble Boost'
  | 'Rock & Metal'
  | 'Acoustic'
  | 'Jazz & Soul'
  | 'Hip-Hop / R&B'
  | 'Classical'
  | 'Custom';

export const EQ_PRESETS: Record<EqPreset, number[]> = {
  Flat: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  'Bass Boost': [7, 6, 4, 2, 0, 0, 0, 0, 0, 0],
  'Bass Extreme': [11, 9, 6, 3, 0, -1, 0, 0, 1, 2],
  'Club / EDM': [6, 5, 2, 0, -1, 2, 3, 4, 5, 5],
  'Vocal Pop': [-2, -1, 0, 2, 4, 5, 4, 2, 1, 0],
  'Treble Boost': [0, 0, 0, 0, 0, 1, 3, 5, 7, 8],
  'Rock & Metal': [5, 4, 2, -1, -2, 1, 3, 4, 5, 5],
  Acoustic: [3, 2, 1, 1, 2, 2, 3, 3, 2, 1],
  'Jazz & Soul': [4, 3, 1, 2, -1, -1, 1, 2, 3, 4],
  'Hip-Hop / R&B': [8, 7, 5, 1, -1, 0, 2, 3, 4, 3],
  Classical: [4, 3, 2, 1, -1, -1, 0, 2, 3, 3],
  Custom: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
};

class WebAudioEngine {
  private audio: HTMLAudioElement;
  private audioCtx: AudioContext | null = null;
  private sourceNode: MediaElementAudioSourceNode | null = null;
  private analyser: AnalyserNode | null = null;
  private gainNode: GainNode | null = null;
  private preampGainNode: GainNode | null = null;
  private bassBoostFilter: BiquadFilterNode | null = null;
  private vocalClarityFilter: BiquadFilterNode | null = null;
  private eqFilters: BiquadFilterNode[] = [];
  private compressorNode: DynamicsCompressorNode | null = null;
  private isConnectedToWebAudio = false;
  private isEqBypassed = false;
  private isLoudnessNormalized = false;
  private currentGains: number[] = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
  private preampDb = 0;
  private bassBoostPercent = 0;
  private vocalClarityPercent = 0;

  private onTimeUpdateCallback?: (currentTime: number, duration: number) => void;
  private onEndedCallback?: () => void;
  private onStatusChangeCallback?: (status: 'playing' | 'paused' | 'buffering' | 'stopped') => void;
  private onErrorCallback?: (err: string) => void;

  constructor() {
    this.audio = new Audio();
    this.audio.crossOrigin = 'anonymous';
    this.audio.preload = 'auto';

    this.audio.addEventListener('timeupdate', () => {
      if (this.onTimeUpdateCallback) {
        this.onTimeUpdateCallback(this.audio.currentTime, this.audio.duration || 0);
      }
    });

    this.audio.addEventListener('play', () => {
      this.onStatusChangeCallback?.('playing');
    });

    this.audio.addEventListener('pause', () => {
      if (this.audio.currentTime >= this.audio.duration && this.audio.duration > 0) {
        // ended handled separately
      } else {
        this.onStatusChangeCallback?.('paused');
      }
    });

    this.audio.addEventListener('waiting', () => {
      this.onStatusChangeCallback?.('buffering');
    });

    this.audio.addEventListener('playing', () => {
      this.onStatusChangeCallback?.('playing');
    });

    this.audio.addEventListener('ended', () => {
      this.onStatusChangeCallback?.('stopped');
      this.onEndedCallback?.();
    });

    this.audio.addEventListener('error', () => {
      const errCode = this.audio.error?.code;
      const errMsg = this.audio.error?.message || `Playback error code ${errCode}`;
      this.onErrorCallback?.(errMsg);
    });
  }

  private initAudioContext() {
    if (this.audioCtx) return;
    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      this.audioCtx = new AudioCtxClass();

      this.preampGainNode = this.audioCtx.createGain();
      this.gainNode = this.audioCtx.createGain();
      this.analyser = this.audioCtx.createAnalyser();
      this.analyser.fftSize = 256;
      this.analyser.smoothingTimeConstant = 0.82;

      // Studio Dynamics Compressor for Loudness Normalization / ReplayGain
      this.compressorNode = this.audioCtx.createDynamicsCompressor();
      this.compressorNode.threshold.setValueAtTime(-24, this.audioCtx.currentTime);
      this.compressorNode.knee.setValueAtTime(30, this.audioCtx.currentTime);
      this.compressorNode.ratio.setValueAtTime(12, this.audioCtx.currentTime);
      this.compressorNode.attack.setValueAtTime(0.003, this.audioCtx.currentTime);
      this.compressorNode.release.setValueAtTime(0.25, this.audioCtx.currentTime);

      // Bass Sub-Harmonic Booster (55Hz resonant low-shelf)
      this.bassBoostFilter = this.audioCtx.createBiquadFilter();
      this.bassBoostFilter.type = 'lowshelf';
      this.bassBoostFilter.frequency.value = 55;
      this.bassBoostFilter.gain.value = 0;

      // Vocal Clarity & Presence Enhancer (2.8kHz peaking filter)
      this.vocalClarityFilter = this.audioCtx.createBiquadFilter();
      this.vocalClarityFilter.type = 'peaking';
      this.vocalClarityFilter.frequency.value = 2800;
      this.vocalClarityFilter.Q.value = 1.2;
      this.vocalClarityFilter.gain.value = 0;

      // 10 band equalizer
      this.eqFilters = EQ_FREQUENCIES.map((freq, idx) => {
        const filter = this.audioCtx!.createBiquadFilter();
        filter.frequency.value = freq;
        if (idx === 0) {
          filter.type = 'lowshelf';
        } else if (idx === EQ_FREQUENCIES.length - 1) {
          filter.type = 'highshelf';
        } else {
          filter.type = 'peaking';
          filter.Q.value = 1.4;
        }
        filter.gain.value = this.isEqBypassed ? 0 : this.currentGains[idx] || 0;
        return filter;
      });

      // Chain: Preamp -> BassBoost -> VocalClarity -> EQ Filters[0..9] -> Master Gain -> (Compressor) -> Analyser -> Destination
      this.preampGainNode.connect(this.bassBoostFilter);
      this.bassBoostFilter.connect(this.vocalClarityFilter);
      this.vocalClarityFilter.connect(this.eqFilters[0]);

      for (let i = 0; i < this.eqFilters.length - 1; i++) {
        this.eqFilters[i].connect(this.eqFilters[i + 1]);
      }

      this.eqFilters[this.eqFilters.length - 1].connect(this.gainNode);

      if (this.isLoudnessNormalized) {
        this.gainNode.connect(this.compressorNode);
        this.compressorNode.connect(this.analyser);
      } else {
        this.gainNode.connect(this.analyser);
      }
      this.analyser.connect(this.audioCtx.destination);

      try {
        this.sourceNode = this.audioCtx.createMediaElementSource(this.audio);
        this.sourceNode.connect(this.preampGainNode);
        this.isConnectedToWebAudio = true;
      } catch {
        // Safe fallback if CORS blocks MediaElementAudioSourceNode on certain CDNs
        this.isConnectedToWebAudio = false;
      }
    } catch {
      this.isConnectedToWebAudio = false;
    }
  }

  public setLoudnessNormalization(enabled: boolean) {
    this.isLoudnessNormalized = enabled;
    if (this.audioCtx && this.gainNode && this.compressorNode && this.analyser) {
      try {
        this.gainNode.disconnect();
        this.compressorNode.disconnect();

        if (enabled) {
          this.gainNode.connect(this.compressorNode);
          this.compressorNode.connect(this.analyser);
        } else {
          this.gainNode.connect(this.analyser);
        }
      } catch {}
    }
  }

  public setPlaybackRate(rate: number) {
    if (this.audio) {
      this.audio.playbackRate = Math.max(0.5, Math.min(2.5, rate));
      (this.audio as any).preservesPitch = true;
      (this.audio as any).mozPreservesPitch = true;
      (this.audio as any).webkitPreservesPitch = true;
    }
  }

  public async setSource(url: string, autoplay = true, crossfadeDuration = 0) {
    if (!url) return;

    // If crossfade enabled and currently playing, do smooth audio fade-out and fade-in
    if (crossfadeDuration > 0 && !this.audio.paused && this.audio.currentTime > 0) {
      const origVolume = this.audio.volume;
      const fadeStep = 50; // 50ms interval
      const steps = Math.max(4, Math.floor((crossfadeDuration * 500) / fadeStep));
      const volDelta = origVolume / steps;

      for (let i = 0; i < steps; i++) {
        this.audio.volume = Math.max(0, this.audio.volume - volDelta);
        await new Promise((r) => setTimeout(r, fadeStep));
      }

      this.audio.src = url;
      this.audio.load();

      if (autoplay) {
        await this.play();
        for (let i = 0; i < steps; i++) {
          this.audio.volume = Math.min(origVolume, this.audio.volume + volDelta);
          await new Promise((r) => setTimeout(r, fadeStep));
        }
        this.audio.volume = origVolume;
      }
    } else {
      this.audio.src = url;
      this.audio.load();
      if (autoplay) {
        await this.play();
      }
    }
  }

  public async play() {
    try {
      this.initAudioContext();
      if (this.audioCtx && this.audioCtx.state === 'suspended') {
        await this.audioCtx.resume();
      }
      await this.audio.play();
    } catch (e: any) {
      this.onStatusChangeCallback?.('paused');
      throw e;
    }
  }

  public pause() {
    this.audio.pause();
  }

  public toggle() {
    if (this.audio.paused) {
      this.play();
    } else {
      this.pause();
    }
  }

  public seek(seconds: number) {
    if (Number.isFinite(seconds) && seconds >= 0) {
      this.audio.currentTime = seconds;
    }
  }

  public setVolume(volume: number) {
    // volume between 0 and 1
    const clamped = Math.max(0, Math.min(1, volume));
    this.audio.volume = clamped;
  }

  public setEqGain(bandIndex: number, gainDb: number) {
    this.currentGains[bandIndex] = gainDb;
    if (this.eqFilters[bandIndex]) {
      this.eqFilters[bandIndex].gain.value = this.isEqBypassed ? 0 : gainDb;
    }
  }

  public applyEqPreset(preset: EqPreset, customGains?: number[]) {
    const gains = customGains || EQ_PRESETS[preset] || EQ_PRESETS.Flat;
    gains.forEach((gain, idx) => {
      this.setEqGain(idx, gain);
    });
  }

  public setBassBoost(percent: number) {
    // 0 to 100% maps to 0 to +12dB boost on 55Hz sub-shelf
    this.bassBoostPercent = Math.max(0, Math.min(100, percent));
    const gainDb = (this.bassBoostPercent / 100) * 12;
    if (this.bassBoostFilter) {
      this.bassBoostFilter.gain.value = this.isEqBypassed ? 0 : gainDb;
    }
  }

  public setVocalClarity(percent: number) {
    // 0 to 100% maps to 0 to +9dB boost on 2.8kHz vocal clarity peaking band
    this.vocalClarityPercent = Math.max(0, Math.min(100, percent));
    const gainDb = (this.vocalClarityPercent / 100) * 9;
    if (this.vocalClarityFilter) {
      this.vocalClarityFilter.gain.value = this.isEqBypassed ? 0 : gainDb;
    }
  }

  public setPreamp(gainDb: number) {
    // -6dB to +6dB gain adjustment
    this.preampDb = Math.max(-6, Math.min(6, gainDb));
    if (this.preampGainNode) {
      const linear = Math.pow(10, this.preampDb / 20);
      this.preampGainNode.gain.value = this.isEqBypassed ? 1 : linear;
    }
  }

  public setEqBypassed(bypassed: boolean) {
    this.isEqBypassed = bypassed;
    this.eqFilters.forEach((filter, idx) => {
      filter.gain.value = bypassed ? 0 : (this.currentGains[idx] || 0);
    });
    if (this.bassBoostFilter) {
      const gainDb = (this.bassBoostPercent / 100) * 12;
      this.bassBoostFilter.gain.value = bypassed ? 0 : gainDb;
    }
    if (this.vocalClarityFilter) {
      const gainDb = (this.vocalClarityPercent / 100) * 9;
      this.vocalClarityFilter.gain.value = bypassed ? 0 : gainDb;
    }
    if (this.preampGainNode) {
      const linear = Math.pow(10, this.preampDb / 20);
      this.preampGainNode.gain.value = bypassed ? 1 : linear;
    }
  }

  public isBypassed(): boolean {
    return this.isEqBypassed;
  }

  public getFrequencyData(array: Uint8Array): void {
    if (this.analyser && this.isConnectedToWebAudio) {
      (this.analyser as any).getByteFrequencyData(array);
    } else {
      // If Web Audio is disconnected, generate simulated live frequency response based on playback
      if (!this.audio.paused) {
        for (let i = 0; i < array.length; i++) {
          const t = Date.now() / 150;
          array[i] = Math.floor(60 + 50 * Math.sin(t + i * 0.4) + Math.random() * 40);
        }
      } else {
        array.fill(0);
      }
    }
  }

  public onTimeUpdate(cb: (currentTime: number, duration: number) => void) {
    this.onTimeUpdateCallback = cb;
  }

  public onEnded(cb: () => void) {
    this.onEndedCallback = cb;
  }

  public onStatusChange(cb: (status: 'playing' | 'paused' | 'buffering' | 'stopped') => void) {
    this.onStatusChangeCallback = cb;
  }

  public onError(cb: (err: string) => void) {
    this.onErrorCallback = cb;
  }

  public getCurrentTime(): number {
    return this.audio.currentTime;
  }

  public getDuration(): number {
    return this.audio.duration || 0;
  }

  public isPaused(): boolean {
    return this.audio.paused;
  }
}

export const audioEngine = new WebAudioEngine();
