const { BrowserWindow, session, screen, app, ipcMain, Menu, ipcRenderer} = require('electron')
const persistHelper = require('../js/backend/persist_client')
const RequestClient = require("../js/backend/requests");
const EventEmitter = require('events');
const PCPlayBinaryClient = require("./clients/pc_play_binary_client");
const SteamGridDBClient = require("../js/backend/steamgrid_client");

const path = require('path')
const globalConfig = require('../config.js');
const PopupClient = require('../js/popup/popup_client')
const PCPlayIntroWindow = require('./reveal/pc_play_intro_window')

const steamArtworkHelper = require("../js/backend/steam_artwork_helper");
const SteamCustomClient = require("../js/backend/steam_custom_client");
const {fileURLToPath} = require('url');

module.exports = class PCPlayClient extends EventEmitter {
    parentWindow
    persistClient
    binaryClient
    pCPlayIntroWindow
    popupClient
    steamArtworkClient
    steamGridClient
    alreadyShowedWarning
    customSteamClient

    constructor() {
        super();
        this.steamGridClient = new SteamGridDBClient()
        this.persistClient = new persistHelper()
        this.binaryClient = new PCPlayBinaryClient()
        this.pCPlayIntroWindow = new PCPlayIntroWindow()
        this.popupClient = new PopupClient(false, 'pc_play')
        this.customSteamClient = new SteamCustomClient()
        this.steamArtworkClient = new steamArtworkHelper(this.customSteamClient)
        this.alreadyShowedWarning = false

        app.whenReady().then(() => {
            ipcMain.on('host_added', (event, args) => { this.handleHostAdded(event, args)})
            ipcMain.on('host_search', (event, args) => { this.handleHostSearch(event, args)})
            ipcMain.on('host_connect', (event, args) => { this.handleHostConnect(event, args)})
            ipcMain.on('host_delete', (event, args) => { this.handleHostDelete(event, args)})
            ipcMain.on('host_list_apps', (event, args) => { this.handleHostListApps(event, args)})
            ipcMain.on('host_open_client', (event, args) => { this.handleHostOpenClient(event, args)})

            ipcMain.on('show_app_picker', (event) => { this.handleShowAppPicker(event)})
            ipcMain.on('start_stream', (event, args) => { this.handleStartStream(event, args)})
            ipcMain.on('close_pc_play_title_picker', (event, args) => { this.handleClosePCPlayTitlePicker(event, args)})
            ipcMain.on('configure_client', (event, args) => { this.handleConfigureClient(event, args)})
            ipcMain.on('uninstall_client', (event, args) => { this.handleUninstallClient(event, args)})
            ipcMain.on('settings_items_updated', (event, args) => { this.handleSettingsItemsUpdated(event, args)})
        });
    }

    async showTutorialIfRequired() {
        if (this.persistClient.get('showed_pc_play_tutorial_already')){
            return true
        } else {
            console.log('showing tut window')
            await this.pCPlayIntroWindow.showWindow()
            return new Promise((resolve) => {
                this.pCPlayIntroWindow.revealWindow.on('close', async (e) => {
                    console.log('tut window closed')
                    if (this.pCPlayIntroWindow.finished){
                        this.persistClient.save('showed_pc_play_tutorial_already', 1)
                    }
                    return resolve(this.pCPlayIntroWindow.finished)
                });
            });
        }
    }
    setParentWindow(parentWindow) {
        this.parentWindow = parentWindow
        this.binaryClient.setWindow(parentWindow)
        this.pCPlayIntroWindow.setParentWindow(parentWindow)
        this.popupClient.setParentWindow(parentWindow)
    }

    // -=-==--==-- IPC MESSAGE HANDLERS -=-=-=-=-=-==-=-
    async handleHostAdded(event, args){
        console.log('handleHostAdded', args)
        await this.showShortcutWarning()
        this.binaryClient.pairHost(args['hostName'], args['displayName'] ?? args['hostName'])
    }

    async handleHostSearch(event, args){
        console.log('handleHostSearch', args)
        await this.showShortcutWarning()
        this.binaryClient.pairHost('pc_play_auto_discover', null)
    }

    handleHostDelete(event, args){
        console.log('handleHostDelete', args)
        this.binaryClient.removeSavedHost(args['hostName'])
        this.binaryClient.showSavedHosts()
    }

    async handleHostConnect(event, args){
        console.log('handleHostConnect', args)
        await this.showShortcutWarning()
        this.binaryClient.pairHost(args['hostName'], args['displayName'] ?? args['hostName'])
    }

    async handleHostOpenClient(event, args){
        console.log('handleHostOpenClient', args)
        await this.showShortcutWarning()
        this.binaryClient.openHostClient(args['hostName'])
    }

    async handleHostListApps(event, args){
        console.log('handleHostListApps', args)
        await this.showShortcutWarning()
        this.binaryClient.listApps(args['hostName'])
    }
    async handleShowAppPicker(event){
        console.log('SHOW APP PICKER', event)
        if (this.parentWindow){
            await this.parentWindow.loadFile('./pc_play/html/pc_play_title_picker.html')
            this.sendFrontendMessage('load_app_list', event)
        } else {
            console.error('this.ParentWindow is null, dont show app picker...')
        }
    }

    async handleStartStream(event, args) {
        console.log('handleStartStream', args)

        const shouldStartStream = await this.promptPcPlayShortcut(args['hostName'], args['titleName'], args['image'])
        if (!shouldStartStream){
            console.log('not starting stream')
            return
        }

        this.binaryClient.startStream(args['hostName'], args['titleName'])
    }

    async showShortcutWarning(){
        console.log('showShortcutWarning', process.env['steamAppId'], globalConfig.isSteamVersion, this.alreadyShowedWarning, this.customSteamClient.getIsSteamDeck())
        if ((!process.env['SteamAppId'] || process.env['SteamAppId'] === '0') && globalConfig.isSteamVersion && !this.alreadyShowedWarning && this.customSteamClient.getIsSteamDeck()){
            const options = {
                type: 'warning',
                buttons: ['OK'],
                title: 'PCPlay May Not Launch',
                message: 'You started the XBPlay app from a <b>shortcut</b> rather than the actual steam app. This may cause the PCPlay app to open in a window behind the XBPlay app.',
                detail: 'To fix this, please launch from the actual XBPlay app. Alternatively, you can press the steam button and switch windows to see PCPlay.',
            };

            await this.showDialog(options)
            this.alreadyShowedWarning = true
        }
    }
    async promptPcPlayShortcut(hostName, titleName, image) {
        console.error('promptPcPlayShortcut', hostName, titleName, image)
        const filePath = fileURLToPath(image);
        console.log('File path:', filePath);

        const settingsConfig = JSON.parse(this.persistClient.get('pc_play_settings_items') ?? '{}');
        let optionsString = this.binaryClient.getSettingsString(settingsConfig).stringValue

        const args = {
            titleId: titleName,
            title: titleName,
            image: filePath,
            isPcPlay: true,
            pcPlayLaunchOptions: `stream ${optionsString} ${this.binaryClient.escapeCommand(hostName)} ${this.binaryClient.escapeCommand(titleName)}`,
            pcPlayInstallPath: `"${this.binaryClient.getBinaryPath()}"`
        }

        const options = {
            type: 'warning',
            buttons: ['Play', 'Add to Steam'],
            title: 'Play Title or Add to Steam',
            message: 'Would you like to play this game? Or add it to your steam library as its own shortcut?',
        };
        let response = {response: 0}

        // prompt user to play or add shortcut if settings allow
         if (this.persistClient.getJSONKey('pcPlayPromptForShortcutCreation', 'pc_play_settings_items') !== false) {
            response = await this.showDialog(options)
        }

        if (!response || response.canceled){ // clicked cancel
            console.log('ignoring')
            return false
        } else if (response.response === 0){ // clicked play xhome
            console.log('STARTING PC Play STREAM')
            return true
        } else if (response.response === 1){ // clicked add to steam
            console.log('add to steam clicked');

            if (this.persistClient.getJSONKey('pcPlayArtworkSource', 'pc_play_settings_items') !== 'pc_play_client'){
                const imageData = await this.steamGridClient.getArtworkFromGame(titleName)
                console.log('ImageData', imageData)
                if (imageData?.heroUrl){
                    args['steamGridHeroUrl'] = imageData.heroUrl
                } if (imageData?.capsuleUrl){
                    args['steamGridCapsuleUrl'] = imageData.capsuleUrl
                }
            }

            this.sendFrontendMessage('show_toast',{
                message: 'Adding shortcut. Please Wait...',
                isSuccess: true
            })
            const result = await this.steamArtworkClient.addSteamShortcut(args);
            if (!result['result'] && result['code'] === 1){ // if failed to add to steam
                // show dialog asking to force
                let message = 'Failed! Cannot add this game to your steam library. Most likely, it already exists.' +
                    " Would you like to add another possibly duplicate shortcut?";
                let detail = result['message'] || ''
                const options = {
                    type: 'warning',
                    buttons: ['Add Anyway', 'Cancel'],
                    title: 'Shortcut already found',
                    message: message,
                    detail: detail,
                };

                const failedResponse = await this.showDialog(options)
                if(!failedResponse){
                    console.log('cancel clicked do nothing');
                } else if(failedResponse.response === 0){ // force add shortcut
                    const result = await this.steamArtworkClient.addSteamShortcut(args, true);
                    if (result && result['result']){
                        let message = 'Game Added to Steam Library!'
                        let detail =
                            "1. <b>Launch Game</b>: Restart your device or the Steam app to see the new game. The game will appear under the non-steam games section in your library." +
                            "<br>2. <b>Artwork</b>: Artwork will automatically be added to your shortcut."
                        const options = {
                            type: 'warning',
                            buttons: ['Ok'],
                            title: 'Add Steam Shortcut',
                            message: message,
                            detail: detail,
                        };

                        await this.showDialog(options)
                    } else {
                        const options = {
                            type: 'warning',
                            buttons: ['Ok'],
                            title: 'Add Steam Shortcut Error',
                            message: 'An error occurred adding shortcut.',
                            detail: result['message'] || '',
                        };
                        await this.showDialog(options)
                    }
                } else if (failedResponse.response === 1){ // cancel
                    console.log('Cancel add shortcut')
                }
            } else if (!result['result'] && result['code'] === 2){
                const options = {
                    type: 'warning',
                    buttons: ['Ok'],
                    title: 'Add Steam Shortcut with Error',
                    message: 'An error occurred adding shortcut.',
                    detail: result['message'] || '',
                };

                await this.showDialog(options)
            } else if (!result['result'] && result['code'] === 0) {
                const options = {
                    type: 'warning',
                    buttons: ['Ok'],
                    title: 'Add Steam Shortcut Error',
                    message: 'An error occurred adding shortcut.',
                    detail: result['message'] || '',
                };

                await this.showDialog(options)
            } else { // if was able to add to steam
                let message = 'Game Added to Steam Library!'
                let detail =
                    "1. <b>Launch Game</b>: Restart your device or the Steam app to see the new game. The game will appear under the non-steam games section in your library." +
                    "<br>2. <b>Artwork</b>: Artwork will automatically be added to your shortcut."
                const options = {
                    type: 'warning',
                    buttons: ['Ok'],
                    title: 'Add Steam Shortcut',
                    message: message,
                    detail: detail,
                };

                await this.showDialog(options)
            }
        }
    }


    async handleClosePCPlayTitlePicker(){
        console.log('handleClosePCPlayTitlePicker')
        if (this.parentWindow){
            await this.parentWindow.loadFile('./pc_play/html/pc_play_host_picker.html')
            this.binaryClient.showSavedHosts()
        }
    }
    handleConfigureClient(){
        console.log('handleConfigureClient')
        this.binaryClient.setupPCBinaryFile()
    }

    handleUninstallClient(){
        console.log('handleUninstallClient')
        this.binaryClient.uninstallPCBinaryClient()
    }
    handleSettingsItemsUpdated(event, args){
        console.log('handleSettingsItemsUpdated pc_play', args)
        this.persistClient.save(args['settingsKeyName'], JSON.stringify(args['values']))
    }

    // -=-=-=--==-=--= HELPERS -=-=-=-==-=-
    sendFrontendMessage(title, data){
        if(this.parentWindow != null && !this.parentWindow.isDestroyed() && this.parentWindow.webContents != null) {
            this.parentWindow.webContents.send(title, data);
        } else {
            console.error('Unable to send frontend message', title, data)
        }
    }

    async showDialog(options, disableHomeScreenControls = true){
        if(!this.popupClient.canShowPopup()){
            console.log('not showing duplicate popup v2', options)
            return false
        } else if (!this.parentWindow){
            console.log('not showing popup due to missing parent window')
            return false
        }

        return new Promise((resolve) => {
            setTimeout(async () => {
                if(disableHomeScreenControls) {
                    try {
                        await this.parentWindow.webContents.executeJavaScript(`setControls(0)`);
                    } catch(err){}
                }

                let result = false
                try {
                    result = await this.popupClient.showPopup(options, this.parentWindow)
                } catch (err){}

                if(disableHomeScreenControls) {
                    try {
                        await this.parentWindow.webContents.executeJavaScript(`setControls(1)`);
                    } catch (err){}
                }
                setTimeout(() => {
                    return resolve(result);
                }, 500)
            }, 300)
        });

    }
}