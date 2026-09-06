const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  platform: process.platform,
  minimize: () => ipcRenderer.invoke('window-minimize'),
  maximize: () => ipcRenderer.invoke('window-maximize'),
  close: () => ipcRenderer.invoke('window-close'),
  isMaximized: () => ipcRenderer.invoke('is-maximized'),
  getAppVersion: () => ipcRenderer.invoke('get-app-version'),
  checkForUpdates: () => ipcRenderer.invoke('check-for-updates'),
  downloadUpdate: () => ipcRenderer.invoke('download-update'),
  quitAndInstall: () => ipcRenderer.invoke('quit-and-install'),
  onMaximizeChange: (callback) => {
    const subscription = (_event, value) => callback(value);
    ipcRenderer.on('maximize-change', subscription);
    return () => {
      ipcRenderer.removeListener('maximize-change', subscription);
    };
  },
  onTrayAction: (callback) => {
    const subscription = (_event, action) => callback(action);
    ipcRenderer.on('tray-media-action', subscription);
    return () => {
      ipcRenderer.removeListener('tray-media-action', subscription);
    };
  },
  onUpdaterEvent: (callback) => {
    const subscription = (_event, data) => callback(data);
    ipcRenderer.on('updater-event', subscription);
    return () => {
      ipcRenderer.removeListener('updater-event', subscription);
    };
  },
});
