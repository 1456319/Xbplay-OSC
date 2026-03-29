/**
 * rEmote - Renderer Process Preload Script
 * 
 * Exposes safe APIs from main process to renderer.
 * Maintains contextIsolation for security.
 */

import { contextBridge, ipcRenderer } from 'electron';

// Expose protected APIs to renderer
contextBridge.exposeInMainWorld('remote', {
  // Authentication APIs
  auth: {
    login: () => ipcRenderer.invoke('auth:login'),
    logout: () => ipcRenderer.invoke('auth:logout'),
    getStatus: () => ipcRenderer.invoke('auth:status'),
  },

  // Session management
  session: {
    create: (consoleId: string) => ipcRenderer.invoke('session:create', consoleId),
    close: () => ipcRenderer.invoke('session:close'),
    getStatus: () => ipcRenderer.invoke('session:status'),
  },

  // Input handling
  input: {
    sendGamepad: (state: any) => ipcRenderer.send('input:gamepad', state),
    sendKeyboard: (event: any) => ipcRenderer.send('input:keyboard', event),
  },

  // Event listeners
  on: (channel: string, callback: Function) => {
    const validChannels = ['session:status', 'webrtc:status', 'error'];
    if (validChannels.includes(channel)) {
      ipcRenderer.on(channel, (event, ...args) => callback(...args));
    }
  },

  off: (channel: string, callback: Function) => {
    ipcRenderer.removeListener(channel, callback);
  },
});

// Declare global types for TypeScript
declare global {
  interface Window {
    remote: {
      auth: {
        login: () => Promise<void>;
        logout: () => Promise<void>;
        getStatus: () => Promise<any>;
      };
      session: {
        create: (consoleId: string) => Promise<void>;
        close: () => Promise<void>;
        getStatus: () => Promise<any>;
      };
      input: {
        sendGamepad: (state: any) => void;
        sendKeyboard: (event: any) => void;
      };
      on: (channel: string, callback: Function) => void;
      off: (channel: string, callback: Function) => void;
    };
  }
}
