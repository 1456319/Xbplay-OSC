const { ipcRenderer, contextBridge } = require('electron')

contextBridge.exposeInMainWorld('popupAPI', {
    send: (channel, data) => {
        let validChannels = ['alertify_confirm'];
        if (validChannels.includes(channel)) {
            ipcRenderer.send(channel, data);
        }
    }
});

window.addEventListener('DOMContentLoaded', () => {
	console.log('loaded')
});

// This listener allows the preload to catch events from the Main World (web page)
// and relay them to the Main Process, even when contextIsolation is enabled.
window.addEventListener('alertify', (event) => {
    console.warn('alertify', event.detail)
    ipcRenderer.send('alertify_confirm', {
        response: event.detail.response,
        canceled: event.detail.canceled ?? false,
        id: event.detail.id
    })
})
