const axios = require("axios");
const path = require('path');
const fs = require('fs');
const os = require('os');
const PersistClient = require('./persist_client.js')
const config = require('./../../config.js');
const { app } = require('electron')
const SteamFileClient = require('./steam_file_client.js')


// versions
const isMac = os.platform() === "darwin";
const isWindows = os.platform() === "win32";
const isLinux = os.platform() === "linux";

module.exports = class SteamCustomClient {
    appId = 2693120
    steamworks = null
    client = null
    persistClient = new PersistClient()
    steamFileClient = new SteamFileClient()

    // these get set on init
    isSteamDevice = false
    steamPlayerId = null
    isSteamDeck = false
    installDir = null

    constructor() {
        try {
            if (config.isSteamVersion){ // this throws on failure
                this.steamworks = require("opensuse-compat-steamworks.js");
                const client = this.steamworks.init(this.appId)
                this.steamPlayerId = client.localplayer.getSteamId()
                this.isSteamDeck = client.utils.isSteamRunningOnSteamDeck()
                this.installDir = client.apps.appInstallDir(this.appId)
                this.isSteamDevice = true
                this.client = client
                client.localplayer.setRichPresence('GAME', null)
                this.client.localplayer.setRichPresence('steam_display', null)
            } else {
                this.getDataForNonSteamInstallSource()
            }

            console.log('Steam Details', this.steamPlayerId, this.isSteamDeck, this.getImageLocationDir(), this.installDir)
        } catch (err){
            this.isSteamDevice = false
            console.error('Error initializing steam client', err)
        }
    }

    setRichPresenceXCloudGame(gameTitle){
        try {
            console.log('setRichPresenceXCloudGame', gameTitle)
            this.client.localplayer.setRichPresence('GAME', gameTitle)
            this.client.localplayer.setRichPresence('steam_display', '#StatusCloudGame')
        } catch (err){
            console.error('Cant set rich presence', err)
        }
    }

    setRichPresenceXHomeGame(){
        try {
            this.client.localplayer.setRichPresence('GAME', null)
            this.client.localplayer.setRichPresence('steam_display', '#StatusRemotePlayGame')
        }  catch (err){
            console.error('Cant set rich presence', err)
        }
    }

    getDataForNonSteamInstallSource() {
        this.steamPlayerId = this.steamFileClient.getSteamPlayerId()
        if (this.steamPlayerId){
            this.isSteamDevice = true
            this.isSteamDeck = this.steamFileClient.getIsSteamDeck()
        }
        
    }

    getSteamUserFolderPath() {
        if (!this.getDeviceHasSteamAppInstalled()){
            return false
        } else {
            return this.steamFileClient.getSteamUserFolderPath()
        } 
    }

    getIsSteamDeck() {
        const result = this.persistClient.get('isSteamDeckV2', 'false')
        if (result === 'true') {
            return true
        } else if (this.isSteamDeck){
            this.persistClient.save('isSteamDeckV2', 'true')
        }

        return this.isSteamDeck
    }

    getBadProtonModeDetected() {
        if(config.isSteamVersion && this.installDir && app.getPath('exe')){
            console.log('getBadProtonModeDetected', this.installDir.includes('Program Files'), app.getPath('exe').endsWith('.exe'))

            // full file path isn't windows, but app is an exe
            if (this.installDir.includes('home/deck') && app.getPath('exe').endsWith('.exe')){
                return true
            }
        } else {
            console.log('getBadProtonModeDetected: invalid path cant check', this.installDir, app.getPath('exe'))
        }
        return false
    }

    isSubscribedApp() {
        if (this.getDeviceHasSteamAppInstalled() && this.getSteamUserId64()){
            return this.client.apps.isSubscribedApp(this.appId)
        } else {
            return false
        }
    }

    getDeviceHasSteamAppInstalled() {
        return this.isSteamDevice
    }

    getImageLocationDir() {
        if (!this.getDeviceHasSteamAppInstalled()){
            return false
        }
        return path.join(this.getSteamUserFolderPath(), this.getSteamAccountId(), 'config', 'grid')
    }

    getShortcutFileLocation() {
        if (!this.getDeviceHasSteamAppInstalled()){
            return false
        }

        return path.join(this.getSteamUserFolderPath(), this.getSteamAccountId(), 'config', 'shortcuts.vdf')
    }

    getAppInstallDir(){
        if (!this.getDeviceHasSteamAppInstalled()){
            return false
        }
        return this.installDir
    }

    getSteamAccountId(){
        if (!this.getDeviceHasSteamAppInstalled() || !this.steamPlayerId) {
            return false
        }

        const result = this.persistClient.get('steamAccountIdV2', null)
        if (result){
            console.log('got cached steam account id', result)
            return result
        }

        this.persistClient.save('steamAccountIdV2', this.steamPlayerId['accountId'].toString())
        return this.steamPlayerId['accountId'].toString() || null
    }

    getSteamUserId64(){
        if (!this.getDeviceHasSteamAppInstalled() || !this.steamPlayerId) {
            return false
        }

        const result = this.persistClient.get('steamUserId64V2', null)
        if (result){
            console.log('got cached steam user64 id', result)
            return result
        }

        this.persistClient.save('steamUserId64V2', this.steamPlayerId['steamId64'].toString())
        return this.steamPlayerId['steamId64'].toString() || null
    }

    getXCloudShortcutData(title, titleId, appId, type) {
        if (!this.getDeviceHasSteamAppInstalled()) {
            return false;
        }

        if (isLinux && !config.isSteamVersion){ // for linux flatpak (not installed via steam)
            return {
                appid: appId,
                AppName: title,
                Exe: '/usr/bin/flatpak',
                StartDir: '/usr/bin/',
                ShortcutPath: path.join(os.homedir(), '.local/share/flatpak/exports/share/applications/net.studio08.xbplay.desktop'),
                LaunchOptions: `"run" "--branch=stable" "--arch=x86_64" "--command=run.sh" "net.studio08.xbplay" "--${type}=${titleId}"`,
                Icon: path.join(os.homedir(), '.local/share/flatpak/exports/share/icons/hicolor/512x512/apps/net.studio08.xbplay.png')
            }
        } else if (isMac) {
            // app.getPath('exe') return bundle.app/Contents/MacOS/theExe but we want the app bundle. So go out 2 dirs
            const exePath = config.isSteamVersion ? `"${path.join(this.getAppInstallDir(), 'mac', 'xbplay', 'XBPlay Remote Play for Xbox.app')}"`: `"${path.join(app.getPath('exe'), '..', '..', '..')}"`

            return {
                appid: appId,
                AppName: title,
                Exe: exePath,
                LaunchOptions: `"--${type}=${titleId}"`,
                Icon: path.join(this.getImageLocationDir(), appId + '_icon.png') // this is saved from the /assets/icon_512 file when an xcloud shortcut is added
            };
        } else if (isWindows){
            // probably can use app.getPath('exe') for all?
            const exePath = config.isSteamVersion ? `"${path.join(this.getAppInstallDir(), 'windows', 'xbplay', 'XBPlay Remote Play for Xbox.exe')}"`: `"${app.getPath('exe')}"`

            return {
                appid: appId,
                AppName: title,
                Exe: exePath,
                LaunchOptions: `"--${type}=${titleId}"`,
                Icon: path.join(this.getImageLocationDir(), appId + '_icon.png')
            };
        } else if (isLinux){ // for linux installed via steam (steam deck + linux)
            return {
                appid: appId,
                AppName: title,
                Exe: `"${path.join(this.getAppInstallDir(), 'linux', 'xbplay', 'net.studio08.xbplay')}"`,
                LaunchOptions: `"--${type}=${titleId}" "--no-sandbox"`,
                Icon: path.join(this.getImageLocationDir(), appId + '_icon.png')
            };
        } else {
            console.error('Cannot detect OS to add shortcut', os.platform())
        }

        return false

    }

    getPCPlayShortcutData(title, appId, pcPlayLaunchOptions, pcPlayInstallPath) {
        if (!this.getDeviceHasSteamAppInstalled()) {
            return false;
        }
        return {
            appid: appId,
            AppName: title,
            Exe: pcPlayInstallPath,
            LaunchOptions: pcPlayLaunchOptions,
            Icon: path.join(this.getImageLocationDir(), appId + '_icon.png')
        };
    }

    getMainShortcutData(appId) {
        if (!this.getDeviceHasSteamAppInstalled()) {
            return false;
        }

        if (isLinux && !config.isSteamVersion) { // for linux flatpak (not installed via steam)
            return {
                appid: appId,
                AppName: 'XBPlay: Remote Play for Xbox',
                Exe: '/usr/bin/flatpak',
                StartDir: '/usr/bin/',
                ShortcutPath: path.join(os.homedir(), '.local/share/flatpak/exports/share/applications/net.studio08.xbplay.desktop'),
                LaunchOptions: `"run" "--branch=stable" "--arch=x86_64" "--command=run.sh" "net.studio08.xbplay"`,
                Icon: path.join(os.homedir(), '.local/share/flatpak/exports/share/icons/hicolor/512x512/apps/net.studio08.xbplay.png')
            }
        } else if (isMac){
            const exePath = config.isSteamVersion ? `"${path.join(this.getAppInstallDir(), 'mac', 'xbplay', 'XBPlay Remote Play for Xbox.app')}"` : `"${path.join(app.getPath('exe'), '..', '..', '..')}"`

            return {
                appid: appId,
                AppName: 'XBPlay Remote Play for Xbox',
                Exe: exePath,
                Icon: path.join(this.getImageLocationDir(), appId + '_icon.png')
            };
        } else if (isWindows){
            const exePath = config.isSteamVersion ? `"${path.join(this.getAppInstallDir(), 'windows', 'xbplay', 'XBPlay Remote Play for Xbox.exe')}"` : `"${app.getPath('exe')}"`

            return {
                appid: appId,
                AppName: 'XBPlay Remote Play for Xbox',
                Exe: exePath,
                Icon: path.join(this.getImageLocationDir(), appId + '_icon.png')
            };
        } else if (isLinux){ // linux installed by steam (steam deck + linux)
            return {
                appid: appId,
                AppName: 'XBPlay Remote Play for Xbox',
                Exe: `"${path.join(this.getAppInstallDir(), 'linux', 'xbplay', 'net.studio08.xbplay')}"`,
                Icon: path.join(this.getImageLocationDir(), appId + '_icon.png'),
                LaunchOptions: `"--no-sandbox"`,
            };
        } else {
            console.error('Cannot detect OS to add main shortcut', os.platform())
        }

        return false
    }
}