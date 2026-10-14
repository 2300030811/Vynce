const { app, BrowserWindow, ipcMain, shell, Tray, Menu, nativeImage } = require('electron');
const path = require('path');
const { autoUpdater } = require('electron-updater');

let mainWindow = null;
let tray = null;
const isDev = !app.isPackaged || process.env.NODE_ENV === 'development';

// Configure autoUpdater
autoUpdater.autoDownload = false;
autoUpdater.autoInstallOnAppQuit = true;

function setupAutoUpdater() {
  autoUpdater.on('checking-for-update', () => {
    mainWindow?.webContents.send('updater-event', { status: 'checking' });
  });

  autoUpdater.on('update-available', (info) => {
    mainWindow?.webContents.send('updater-event', {
      status: 'available',
      version: info.version,
      releaseDate: info.releaseDate,
      releaseNotes: info.releaseNotes,
    });
  });

  autoUpdater.on('update-not-available', (info) => {
    mainWindow?.webContents.send('updater-event', {
      status: 'not-available',
      version: info?.version,
    });
  });

  autoUpdater.on('error', (err) => {
    console.warn('[AutoUpdater] Check error:', err?.message || err);
    mainWindow?.webContents.send('updater-event', {
      status: 'error',
      error: err?.message || 'Update check failed.',
    });
  });

  autoUpdater.on('download-progress', (progressObj) => {
    mainWindow?.webContents.send('updater-event', {
      status: 'downloading',
      percent: progressObj.percent,
      transferred: progressObj.transferred,
      total: progressObj.total,
      bytesPerSecond: progressObj.bytesPerSecond,
    });
  });

  autoUpdater.on('update-downloaded', (info) => {
    mainWindow?.webContents.send('updater-event', {
      status: 'downloaded',
      version: info.version,
    });
  });
}

// Enforce single instance lock
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });
}

function getIconPath() {
  if (isDev) {
    return path.join(__dirname, '../public/icon.ico');
  }
  return path.join(__dirname, '../dist/icon.ico');
}

function createWindow() {
  const iconPath = getIconPath();

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 840,
    minWidth: 940,
    minHeight: 620,
    backgroundColor: '#08090d',
    frame: false, // Frameless for modern custom titlebar
    titleBarStyle: 'hidden',
    icon: iconPath,
    show: false, // Show once ready to avoid white flash
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: false,
      webSecurity: true,
    },
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  // Open external links in user's default browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http://') || url.startsWith('https://')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  // Maximize state changes notify renderer
  mainWindow.on('maximize', () => {
    mainWindow.webContents.send('maximize-change', true);
  });
  mainWindow.on('unmaximize', () => {
    mainWindow.webContents.send('maximize-change', false);
  });

  // Handle window close: hide to tray unless explicitly quitting
  mainWindow.on('close', (event) => {
    if (!app.isQuitting) {
      event.preventDefault();
      mainWindow.hide();
      return false;
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Load URL
  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
  } else if (isDev && !process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL('http://localhost:5173').catch(() => {
      // Fallback to dist if dev server not running
      mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
    });
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  createTray();
}

function createTray() {
  if (tray) return;

  try {
    const iconPath = getIconPath();
    const icon = nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 });
    tray = new Tray(icon);

    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'Show Vynce',
        click: () => {
          if (mainWindow) {
            if (mainWindow.isMinimized()) mainWindow.restore();
            mainWindow.show();
            mainWindow.focus();
          }
        },
      },
      { type: 'separator' },
      {
        label: 'Play / Pause',
        click: () => {
          if (mainWindow) {
            mainWindow.webContents.send('tray-media-action', 'play-pause');
          }
        },
      },
      {
        label: 'Next Track',
        click: () => {
          if (mainWindow) {
            mainWindow.webContents.send('tray-media-action', 'next');
          }
        },
      },
      {
        label: 'Previous Track',
        click: () => {
          if (mainWindow) {
            mainWindow.webContents.send('tray-media-action', 'prev');
          }
        },
      },
      { type: 'separator' },
      {
        label: 'Quit Vynce',
        click: () => {
          app.isQuitting = true;
          app.quit();
        },
      },
    ]);

    tray.setToolTip('Vynce Music');
    tray.setContextMenu(contextMenu);

    tray.on('double-click', () => {
      if (mainWindow) {
        if (mainWindow.isVisible()) {
          mainWindow.focus();
        } else {
          if (mainWindow.isMinimized()) mainWindow.restore();
          mainWindow.show();
          mainWindow.focus();
        }
      }
    });
  } catch (err) {
    console.warn('Tray creation failed:', err);
  }
}

// IPC Window Controls
ipcMain.handle('window-minimize', () => {
  if (mainWindow) mainWindow.minimize();
});

ipcMain.handle('window-maximize', () => {
  if (mainWindow) {
    if (mainWindow.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow.maximize();
    }
  }
});

ipcMain.handle('window-close', () => {
  if (mainWindow) mainWindow.close();
});

ipcMain.handle('app-quit', () => {
  app.isQuitting = true;
  app.quit();
});

ipcMain.handle('is-maximized', () => {
  return mainWindow ? mainWindow.isMaximized() : false;
});

ipcMain.handle('get-platform', () => {
  return process.platform;
});

ipcMain.handle('get-app-version', () => {
  return app.getVersion();
});

// IPC Spotify Embed Proxy for Desktop (Bypasses browser CORS completely with strict input validation)
ipcMain.handle('fetch-spotify-embed', async (_event, resourcePath) => {
  if (typeof resourcePath !== 'string') return null;
  const clean = resourcePath.trim();
  // Strictly validate resource type and Spotify alphanumeric ID: e.g. "playlist/37i9dQZF1DXcBWIGoYBM5M" or "track/4cOdK2wGLETKBW3PvgPWqT"
  const match = clean.match(/^(?:(playlist|track|album|artist)\/)?([a-zA-Z0-9]+)$/);
  if (!match) {
    console.warn('[Spotify] Blocked invalid embed resource path:', clean);
    return null;
  }
  const type = match[1] || 'playlist';
  const id = match[2];
  try {
    const res = await fetch(`https://open.spotify.com/embed/${type}/${id}`, {
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
      },
      signal: typeof AbortSignal?.timeout === 'function' ? AbortSignal.timeout(10000) : undefined,
    });
    if (!res.ok) return null;
    return await res.text();
  } catch (err) {
    console.warn('[Spotify] Embed fetch error in main:', err?.message || err);
    return null;
  }
});

// IPC JioSaavn Direct API for Desktop (Bypasses browser CORS and supplies required headers)
ipcMain.handle('fetch-jiosaavn-direct', async (_event, queryOrPath) => {
  if (typeof queryOrPath !== 'string') return null;
  // Clean query: strip leading "/api.php?" or "api.php?" or "?"
  const cleaned = queryOrPath.replace(/^\/?(?:api\.php)?\??/, '').trim();
  // Strictly validate query string: allow safe query characters only, max 2048 chars
  if (!cleaned || cleaned.length > 2048 || !/^[a-zA-Z0-9_.\-=%&,]+$/.test(cleaned)) {
    console.warn('[JioSaavn] Blocked invalid query string in main IPC:', queryOrPath);
    return null;
  }

  const targetUrl = `https://www.jiosaavn.com/api.php?${cleaned}`;
  try {
    const res = await fetch(targetUrl, {
      headers: {
        'Referer': 'https://www.jiosaavn.com/',
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
      },
      signal: typeof AbortSignal?.timeout === 'function' ? AbortSignal.timeout(10000) : undefined,
    });
    if (!res.ok) return null;
    return await res.json();
  } catch (err) {
    console.warn('[JioSaavn] Direct fetch error in main:', err?.message || err);
    return null;
  }
});


// IPC Auto-Updater Controls
ipcMain.handle('check-for-updates', async () => {
  if (isDev) {
    return { status: 'dev-mode', message: 'Auto-updater is inactive in development mode.' };
  }
  try {
    const result = await autoUpdater.checkForUpdates();
    if (!result || !result.updateInfo) {
      return { status: 'not-available', message: `Vynce v${app.getVersion()} is up to date.` };
    }

    const latestVersion = result.updateInfo.version;
    const currentVersion = app.getVersion();
    if (latestVersion && latestVersion !== currentVersion) {
      return { status: 'available', updateInfo: result.updateInfo, version: latestVersion };
    }

    return { status: 'not-available', message: `Vynce v${currentVersion} is up to date.` };
  } catch (err) {
    console.warn('[AutoUpdater] IPC check error:', err?.message || err);
    const msg = String(err?.message || '');

    if (msg.includes('404') || msg.includes('latest.yml') || msg.includes('Cannot find')) {
      return {
        status: 'error',
        error: 'Update manifest (latest.yml) not found on GitHub Releases (HTTP 404).',
      };
    }

    if (
      msg.includes('ENOTFOUND') ||
      msg.includes('ETIMEDOUT') ||
      msg.includes('net::ERR_INTERNET_DISCONNECTED') ||
      msg.includes('ERR_NAME_NOT_RESOLVED')
    ) {
      return {
        status: 'error',
        error: 'Unable to reach update server. Check your internet connection.',
      };
    }

    return { status: 'error', error: msg || 'Could not reach update server.' };
  }
});

ipcMain.handle('download-update', async () => {
  if (isDev) return { status: 'dev-mode' };
  try {
    await autoUpdater.downloadUpdate();
    return { status: 'success' };
  } catch (err) {
    return { status: 'error', error: err.message };
  }
});

ipcMain.handle('quit-and-install', () => {
  autoUpdater.quitAndInstall(false, true);
});

app.whenReady().then(() => {
  createWindow();
  setupAutoUpdater();

  // Automatically check for updates 4 seconds after launch in production
  if (!isDev) {
    setTimeout(() => {
      autoUpdater.checkForUpdates().catch((err) => {
        console.log('Background update check:', err?.message || err);
      });
    }, 4000);
  }

  app.on('activate', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    } else if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('before-quit', () => {
  app.isQuitting = true;
});

app.on('will-quit', () => {
  if (tray) {
    tray.destroy();
    tray = null;
  }
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
