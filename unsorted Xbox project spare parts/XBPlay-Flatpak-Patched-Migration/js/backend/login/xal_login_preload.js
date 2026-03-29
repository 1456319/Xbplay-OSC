const { ipcRenderer, contextBridge } = require('electron')

contextBridge.exposeInMainWorld('xalLoginAPI', {
    send: (channel, data) => {
        let validChannels = ['close-login', 'manual_login_clicked', 'tokens_received'];
        if (validChannels.includes(channel)) {
            ipcRenderer.send(channel, data);
        }
    }
});

window.addEventListener('DOMContentLoaded', () => {
   if (!window.location.href.includes('/steam/login/login_page.html')){
      showCloseButton()
   }
});

// These listeners allow the preload to catch events from the Main World (web page)
// and relay them to the Main Process, even when contextIsolation is enabled.
window.addEventListener('login_clicked', () => {
   ipcRenderer.send('manual_login_clicked')
});

window.addEventListener('close', () => {
   ipcRenderer.send('close-login')
});

window.addEventListener('tokens_received', (event) => {
   ipcRenderer.send('tokens_received', event.detail)
});

function showCloseButton() {
   try {
      const button = document.createElement("button");
      button.innerHTML = "ABORT";
      button.style.cssText = "border-radius: 5px; font-size: 18px; position:absolute; left:20%; top: 1%; width: 60%; height: 7%; z-index:10001; background-color: white; color: black; border: 1px solid grey;"

      // 2. Append somewhere
      document.body.appendChild(button);

      // 3. Add event handler
      button.addEventListener ("click", function() {
         ipcRenderer.send('close-login')
      });

   } catch (err){
      console.error(err)
   }
}
