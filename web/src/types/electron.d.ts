export interface UpdateEvent {
  status: 'checking' | 'available' | 'not-available' | 'downloading' | 'downloaded' | 'error' | 'dev-mode';
  version?: string;
  releaseDate?: string;
  releaseNotes?: string | any[];
  percent?: number;
  transferred?: number;
  total?: number;
  bytesPerSecond?: number;
  error?: string;
  message?: string;
}

export interface ElectronAPI {
  isElectron: boolean;
  platform: 'win32' | 'darwin' | 'linux';
  minimize: () => Promise<void>;
  maximize: () => Promise<void>;
  close: () => Promise<void>;
  isMaximized: () => Promise<boolean>;
  getAppVersion: () => Promise<string>;
  checkForUpdates: () => Promise<{ status: string; error?: string; message?: string }>;
  downloadUpdate: () => Promise<{ status: string; error?: string }>;
  quitAndInstall: () => Promise<void>;
  onMaximizeChange: (callback: (isMaximized: boolean) => void) => () => void;
  onTrayAction: (callback: (action: string) => void) => () => void;
  onUpdaterEvent: (callback: (data: UpdateEvent) => void) => () => void;
}

declare global {
  interface Window {
    electronAPI?: ElectronAPI;
  }
}
