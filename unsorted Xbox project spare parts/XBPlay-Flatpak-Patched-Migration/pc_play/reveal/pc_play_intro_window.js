const { BrowserWindow, session, screen, app, ipcMain, Menu, ipcRenderer} = require('electron')
const EventEmitter = require('events');
const path = require('path')

module.exports = class PCPlayIntroWindow extends EventEmitter {
    revealWindow
    finished

    constructor() {
        super();
        this.finished = false
        app.whenReady().then(() => {
            ipcMain.on('close_tutorial', (event, args) => { this.handleTutorialClosed(event, args)})
        });
    }

    async showWindow(){
        this.finished = false
        let options = {
            backgroundColor: '#000000',
            x: this.parentWindow.getPosition()[0],
            y: this.parentWindow.getPosition()[1],
            width: this.parentWindow.getSize()[0] || 1280,
            height: this.parentWindow.getSize()[1] || 800,
            frame: true,
            webPreferences: {
                // sandbox check
                preload: path.join(__dirname, './pc_play_intro_preload.js'),
                devTools: false,
                nodeIntegration: false,
                contextIsolation: true,
            },
            // fullscreen: fullscreenMode,
            // titleBarStyle: 'hidden',
            title: "PC Play",
            // parentWindow: this.parentWindow
        }
        if (this.revealWindow && !this.revealWindow.isDestroyed() && this.revealWindow.webContents != null){
            this.revealWindow.focus()
            return
        }
        this.revealWindow = new BrowserWindow(options)
        await this.revealWindow.loadFile('./pc_play/reveal/pc_play_intro.html')
    }

    setParentWindow(parentWindow) {
        this.parentWindow = parentWindow
    }

    handleTutorialClosed(event, args) {
        console.log('handleTutorialClosed', args)

        if (args['finished']) {
            this.finished = true
        }

        this.revealWindow.close()
    }

}