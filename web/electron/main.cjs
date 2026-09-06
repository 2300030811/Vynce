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
    mainWindow?.webContents.send('updater-event', {
      status: 'error',
      error: err == null ? 'unknown' : (err.stack || err.message || err.toString()),
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
          mainWindow.show();
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

ipcMain.handle('is-maximized', () => {
  return mainWindow ? mainWindow.isMaximized() : false;
});

ipcMain.handle('get-platform', () => {
  return process.platform;
});

ipcMain.handle('get-app-version', () => {
  return app.getVersion();
});

// IPC Auto-Updater Controls
ipcMain.handle('check-for-updates', async () => {
  if (isDev) {
    return { status: 'dev-mode', message: 'Auto-updater is inactive in development mode.' };
  }
  try {
    const result = await autoUpdater.checkForUpdates();
    return { status: 'success', result };
  } catch (err) {
    return { status: 'error', error: err.message };
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
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
