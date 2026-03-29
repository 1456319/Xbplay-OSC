const { app, BrowserWindow, ipcMain, session, dialog, Menu, screen} = require('electron')
const path = require('path')

module.exports = class PopupClient {
	id
	useDev
	popupWindow
	response
	parentWindow

    constructor(dev, id) {
        this.useDev = dev
		this.id = id

       app.whenReady().then(() => {
		    ipcMain.on('alertify_confirm', (event, args) => { this.handleAlertResponse(event, args) })
	   });
    }

    canShowPopup(options){
    	if(this.popupWindow){ // if popup window already showing
    		return false
    	}
    	return true;
    }

	setParentWindow(window){
		this.parentWindow = window
	}

    async showPopup(options){
    	console.log('showing popup', options)

    	if(!this.canShowPopup(options)){
    		console.log('not showing duplicate popup')
    		return new Promise((resolve) => {
    			return resolve(false)
    		});
    	} else {
	    	this.response = null
			const windowOptions = {
				width: 800,
				height: 600,
				frame: false,
				transparent:true,
				resizable: false,
				alwaysOnTop: true,
				webPreferences: {
					// sandbox check
					preload: path.join(__dirname, 'popup_preload.js'),
					devTools: this.useDev,
					nodeIntegration: false,
					contextIsolation: true,
				},
				fullscreen: false,
				// titleBarStyle: 'hidden',
				title: "XBPlay",
				backgroundColor: '#00000000',
			}

			if (this.parentWindow && !this.parentWindow.isDestroyed()){
				windowOptions['parentWindow'] = this.parentWindow
				const size = this.parentWindow.getSize()
				const pos = this.parentWindow.getPosition()
				windowOptions['x'] = pos[0]
				windowOptions['y'] = pos[1]
				windowOptions['width'] = size[0]
				windowOptions['height'] = size[1]
			}

			this.popupWindow = new BrowserWindow(windowOptions)

		    this.popupWindow.webContents.on('did-finish-load', async () => {
		    	const stringData = encodeURIComponent(JSON.stringify(options))
		    	await this.popupWindow.webContents.executeJavaScript(`showAlert("${stringData}", "${this.id}")`)
				this.popupWindow.focus()
		    })

			this.popupWindow.on('close', async (e) => {
				if (!this.response) {
					e.preventDefault();
				}
			});

			await this.popupWindow.loadFile('./html/popup_window.html')

			return new Promise((resolve) => {
				const timer = setInterval(() => {
					//console.log('check', this.response)
					if(this.response){
						clearInterval(timer)
						return resolve(this.response)
					}
				}, 200);
			});
		}
    }

    async handleAlertResponse(event, args){
		if (args['id'] !== this.id){
			console.log('popup client ignoring id mismatch', this.id, args['id'])
			return
		}
    	console.log('response received', args)
    	this.response = args
		if(this.popupWindow) {
			console.log('destroying popup window')
			await this.popupWindow.close()
			await this.popupWindow.destroy()
			this.popupWindow = null
		}
    }
}

