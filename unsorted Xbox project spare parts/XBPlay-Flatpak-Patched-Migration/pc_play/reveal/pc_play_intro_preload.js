const { ipcRenderer, contextBridge } = require('electron');

contextBridge.exposeInMainWorld('introAPI', {
    send: (channel, data) => {
        let validChannels = ['close_tutorial'];
        if (validChannels.includes(channel)) {
            ipcRenderer.send(channel, data);
        }
    }
});

// This listener allows the preload to catch events from the Main World (web page)
// and relay them to the Main Process, even when contextIsolation is enabled.
window.addEventListener('close_tutorial', (event) => {
    console.log('close_tutorial', event.detail)
    ipcRenderer.send('close_tutorial', event.detail)
})
