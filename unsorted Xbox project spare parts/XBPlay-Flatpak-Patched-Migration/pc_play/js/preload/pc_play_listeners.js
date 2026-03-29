const { ipcRenderer, contextBridge } = require('electron');

contextBridge.exposeInMainWorld('pcPlayAPI', {
    send: (channel, data) => {
        let validChannels = [
            'close_app', 'host_list_apps', 'host_open_client', 'host_added',
            'host_search', 'host_delete', 'host_connect', 'start_stream',
            'close_pc_play', 'close_pc_play_title_picker', 'configure_client',
            'uninstall_client', 'settings_items_updated'
        ];
        if (validChannels.includes(channel)) {
            ipcRenderer.send(channel, data);
        }
    },
    on: (channel, func) => {
        let validChannels = [
            'load_saved_hosts', 'update_pc_play_installed_text', 'show_toast',
            'set_loading_visibility', 'download_percent', 'load_app_list', 'close_app'
        ];
        if (validChannels.includes(channel)) {
            ipcRenderer.on(channel, (event, ...args) => func(...args));
        }
    }
});

// These listeners allow the preload to catch events from the Main World (web page)
// and relay them to the Main Process, even when contextIsolation is enabled.
window.addEventListener('host_list_apps', (e) => ipcRenderer.send('host_list_apps', e.detail));
window.addEventListener('host_open_client', (e) => ipcRenderer.send('host_open_client', e.detail));
window.addEventListener('host_added', (e) => ipcRenderer.send('host_added', e.detail));
window.addEventListener('host_search', (e) => ipcRenderer.send('host_search', e.detail));
window.addEventListener('host_delete', (e) => ipcRenderer.send('host_delete', e.detail));
window.addEventListener('host_connect', (e) => ipcRenderer.send('host_connect', e.detail));
window.addEventListener('start_stream', (e) => ipcRenderer.send('start_stream', e.detail));
window.addEventListener('close_pc_play', (e) => ipcRenderer.send('close_pc_play', e.detail));
window.addEventListener('close_pc_play_title_picker', (e) => ipcRenderer.send('close_pc_play_title_picker', e.detail));
window.addEventListener('configure_client', (e) => ipcRenderer.send('configure_client', e.detail));
window.addEventListener('uninstall_client', (e) => ipcRenderer.send('uninstall_client', e.detail));
window.addEventListener('settings_items_updated', (e) => ipcRenderer.send('settings_items_updated', e.detail));

// Re-dispatch IPC events as DOM events for Main World compatibility
const forwardToMainWorld = (channel) => {
    ipcRenderer.on(channel, (event, data) => {
        window.dispatchEvent(new CustomEvent(channel, { detail: data }));
    });
};

['load_saved_hosts', 'update_pc_play_installed_text', 'show_toast',
 'set_loading_visibility', 'download_percent', 'load_app_list', 'close_app'].forEach(forwardToMainWorld);
