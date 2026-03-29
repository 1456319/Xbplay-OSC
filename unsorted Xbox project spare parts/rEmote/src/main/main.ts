/**
 * rEmote - Xbox Remote Play Client
 * Main Process Entry Point
 * 
 * This is a cleanroom implementation based on protocol specifications.
 * See docs/specs/ for complete protocol documentation.
 */

import { app, BrowserWindow } from 'electron';
import * as path from 'path';

// Handle creating/removing shortcuts on Windows when installing/uninstalling.
if (require('electron-squirrel-startup')) {
  app.quit();
}

let mainWindow: BrowserWindow | null = null;

const createWindow = (): void => {
  // Create the browser window.
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 720,
    webPreferences: {
      preload: path.join(__dirname, '../renderer/preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    title: 'rEmote - Xbox Remote Play',
    icon: path.join(__dirname, '../../assets/icons/icon.png'),
  });

  // Load the index.html of the app.
  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));

  // Open DevTools in development
  if (process.env.NODE_ENV === 'development') {
    mainWindow.webContents.openDevTools();
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
};

// This method will be called when Electron has finished initialization
app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    // On macOS, re-create window when dock icon is clicked
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

// Quit when all windows are closed, except on macOS
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

// TODO: Implement IPC handlers for:
// - Authentication (OAuth, XSTS token exchange)
// - Session management (create/close sessions)
// - WebRTC connection setup
// - DataChannel communication
// See docs/specs/ for implementation details
