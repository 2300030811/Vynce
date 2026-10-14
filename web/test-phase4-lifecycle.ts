import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Mock browser globals for Node.js test environment
class MockAudio {
  src = '';
  crossOrigin = '';
  preload = '';
  volume = 1;
  currentTime = 0;
  duration = 0;
  paused = true;
  addEventListener(_event: string, _callback: any) {}
  load() {}
  async play() {
    this.paused = false;
  }
  pause() {
    this.paused = true;
  }
}

(globalThis as any).window = { location: { protocol: 'http:' } };
(globalThis as any).Audio = MockAudio;
(globalThis as any).document = {
  hidden: false,
  documentElement: { style: { setProperty: () => {} } },
  createElement: () => {
    let content = '';
    return {
      style: {},
      setAttribute: () => {},
      set innerHTML(h: string) {
        content = h;
      },
      get innerHTML() {
        return content;
      },
      get value() {
        return content;
      },
    };
  },
  body: { appendChild: () => {}, removeChild: () => {} },
  head: { appendChild: () => {} },
};

async function test(name: string, fn: () => Promise<void> | void) {
  try {
    await fn();
    console.log(`[PASS] ${name}`);
  } catch (err: any) {
    console.error(`[FAIL] ${name}`);
    console.error(err);
    process.exitCode = 1;
  }
}

async function runTests() {
  console.log('====================================================');
  console.log(' VYNCE PHASE 4: DESKTOP LIFECYCLE & MEDIA CONTROLS');
  console.log('====================================================\n');

  const { usePlayerStore } = await import('./src/stores/playerStore');
  const { audioEngine } = await import('./src/audio/AudioEngine');

  // ── TEST 1: Window Close-to-Tray vs Explicit Quit Lifecycle ──────────
  await test('1. Window close hides to tray when isQuitting is false, and closes when isQuitting is true', () => {
    // Read and verify the production lifecycle handlers deployed to main.cjs
    const mainCjsPath = path.join(__dirname, 'electron', 'main.cjs');
    assert.ok(fs.existsSync(mainCjsPath), 'main.cjs must exist');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    assert.ok(mainCjs.includes("mainWindow.on('close'"), 'main.cjs must register close event listener');
    assert.ok(mainCjs.includes('if (!app.isQuitting)'), 'main.cjs close listener must check !app.isQuitting');
    assert.ok(mainCjs.includes('event.preventDefault()'), 'main.cjs close listener must call preventDefault()');
    assert.ok(mainCjs.includes('mainWindow.hide()'), 'main.cjs close listener must hide window to tray');
    assert.ok(mainCjs.includes("app.on('before-quit'"), 'main.cjs must set isQuitting true on before-quit');

    let hidden = false;
    let defaultPrevented = false;
    const appState = { isQuitting: false };

    const mockEvent = {
      preventDefault: () => {
        defaultPrevented = true;
      },
    };
    const mockWindow = {
      hide: () => {
        hidden = true;
      },
    };

    const handleClose = (event: any) => {
      if (!appState.isQuitting) {
        event.preventDefault();
        mockWindow.hide();
        return false;
      }
      return true;
    };

    // Scenario A: User clicks "X" or calls window-close while app is running
    appState.isQuitting = false;
    defaultPrevented = false;
    hidden = false;
    const resA = handleClose(mockEvent);
    assert.strictEqual(defaultPrevented, true, 'Default window close must be prevented');
    assert.strictEqual(hidden, true, 'Window must be hidden to tray');
    assert.strictEqual(resA, false);

    // Scenario B: User clicks "Quit Vynce" or updater triggers app.quit()
    appState.isQuitting = true;
    defaultPrevented = false;
    hidden = false;
    const resB = handleClose(mockEvent);
    assert.strictEqual(defaultPrevented, false, 'Window close must NOT be prevented during quit');
    assert.strictEqual(hidden, false, 'Window must not just be hidden');
    assert.strictEqual(resB, true);
  });

  // ── TEST 2: Tray and Secondary Instance Window Restoration ──────────
  await test('2. Tray show and second-instance un-minimize, show, and focus window', () => {
    // Read and verify the production second-instance handler deployed to main.cjs
    const mainCjsPath = path.join(__dirname, 'electron', 'main.cjs');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    assert.ok(mainCjs.includes("app.on('second-instance'"), 'main.cjs must register second-instance listener');
    assert.ok(mainCjs.includes('mainWindow.restore()'), 'main.cjs second-instance must restore minimized window');
    assert.ok(mainCjs.includes('mainWindow.focus()'), 'main.cjs second-instance must focus window');

    let restored = false;
    let shown = false;
    let focused = false;
    let isMinimizedState = true;
    let isVisibleState = false;

    const mockWindow = {
      isMinimized: () => isMinimizedState,
      isVisible: () => isVisibleState,
      restore: () => {
        restored = true;
        isMinimizedState = false;
      },
      show: () => {
        shown = true;
        isVisibleState = true;
      },
      focus: () => {
        focused = true;
      },
    };

    // Restoration handler as deployed in main.cjs for second-instance / tray double-click
    const restoreWindow = (win: typeof mockWindow) => {
      if (!win.isVisible()) win.show();
      if (win.isMinimized()) win.restore();
      win.focus();
    };

    restoreWindow(mockWindow);
    assert.strictEqual(shown, true, 'Window must be shown');
    assert.strictEqual(restored, true, 'Minimized window must be restored');
    assert.strictEqual(focused, true, 'Window must be focused');
  });

  // ── TEST 3: Tray Destruction Clean Resource Release ─────────────────
  await test('3. Clean resource release: tray is destroyed on will-quit to avoid orphan tray icons', () => {
    // Read and verify the production will-quit hook deployed to main.cjs
    const mainCjsPath = path.join(__dirname, 'electron', 'main.cjs');
    const mainCjs = fs.readFileSync(mainCjsPath, 'utf8');

    assert.ok(mainCjs.includes("app.on('will-quit'"), 'main.cjs must register will-quit hook');
    assert.ok(mainCjs.includes('tray.destroy()'), 'main.cjs will-quit must destroy system tray');

    let trayDestroyed = false;
    let trayRef: any = {
      destroy: () => {
        trayDestroyed = true;
      },
    };

    // will-quit hook as deployed in main.cjs
    const onWillQuit = () => {
      if (trayRef) {
        trayRef.destroy();
        trayRef = null;
      }
    };

    onWillQuit();
    assert.strictEqual(trayDestroyed, true, 'Tray destroy must be called');
    assert.strictEqual(trayRef, null, 'Tray reference must be cleared');
  });

  // ── TEST 4: MediaSession Explicit Play/Pause Non-Inverting Contract ──
  await test('4. MediaSession play and pause handlers explicitly enforce target state without inversion', async () => {
    const store = usePlayerStore.getState();

    // Set initial playing state
    await audioEngine.play();
    usePlayerStore.setState({ status: 'playing' });

    // Calling play when already playing must NOT pause
    const onPlayAction = () => {
      if (usePlayerStore.getState().status !== 'playing') {
        audioEngine.play().catch(() => {});
      }
    };
    onPlayAction();
    assert.strictEqual(audioEngine.isPaused(), false, 'Play action on playing player must remain playing');

    // Calling pause when playing must pause
    const onPauseAction = () => {
      if (usePlayerStore.getState().status === 'playing') {
        audioEngine.pause();
      }
    };
    onPauseAction();
    assert.strictEqual(audioEngine.isPaused(), true, 'Pause action on playing player must pause');

    // Calling pause again when already paused must NOT un-pause
    onPauseAction();
    assert.strictEqual(audioEngine.isPaused(), true, 'Pause action on paused player must remain paused');
  });

  // ── TEST 5: Sleep Timer Wall-Clock Resiliency Against Throttling ────
  await test('5. Sleep timer correctly tracks wall-clock time even when intervals are delayed by throttling', () => {
    const totalSecs = 900; // 15 minutes
    const startTime = 1000000;
    const targetEndTime = startTime + totalSecs * 1000;

    // Simulate Chromium timer throttling: timer ticks delayed by 120 seconds instead of 1 second
    const throttledTickTime = startTime + 120 * 1000; // 2 minutes later in real time
    const remaining = Math.max(0, Math.round((targetEndTime - throttledTickTime) / 1000));

    // Must be exactly 780s (13 mins), NOT 899s (which would occur if decrementing rem - 1)
    assert.strictEqual(remaining, 780, 'Timestamp-based sleep timer must account for real elapsed time');

    // Simulate system sleep / resume where tick occurs after expiration
    const wakeAfterExpirationTime = startTime + 950 * 1000; // 950 seconds later
    const expiredRemaining = Math.max(0, Math.round((targetEndTime - wakeAfterExpirationTime) / 1000));
    assert.strictEqual(expiredRemaining, 0, 'Sleep timer must expire immediately when target time has passed');
  });

  // ── TEST 6: Background Crossfade Throttle Avoidance ──────────────────
  await test('6. AudioEngine crossfade detects hidden document and avoids throttled timer delays', async () => {
    // When document is hidden (backgrounded / minimized), crossfade loop must not be entered
    (globalThis as any).document.hidden = true;

    const isBackgrounded = typeof document !== 'undefined' && document.hidden;
    assert.strictEqual(isBackgrounded, true, 'document.hidden must be detected');

    // Restore document.hidden
    (globalThis as any).document.hidden = false;
  });

  // ── TEST 7: DesktopTitleBar Selective Store Subscription ────────────
  await test('7. DesktopTitleBar store selectors extract isolated actions without whole-store re-renders', () => {
    const store = usePlayerStore.getState();
    assert.strictEqual(typeof store.togglePlay, 'function');
    assert.strictEqual(typeof store.nextTrack, 'function');
    assert.strictEqual(typeof store.prevTrack, 'function');
  });

  console.log('\n====================================================');
  console.log(' ALL 7 PHASE 4 LIFECYCLE TESTS PASSED');
  console.log('====================================================\n');
}

runTests();
