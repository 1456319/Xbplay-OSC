const { ipcRenderer, contextBridge } = require('electron')

// Expose safe API to the Main World
contextBridge.exposeInMainWorld('electronAPI', {
    send: (channel, data) => {
        let validChannels = [
            'login', 'xalTokenUpdateRequest', 'close_app', 'auto_login_toggle',
            'settings_items_updated', 'quitGame', 'startXHome', 'xCloudTitlePicker',
            'startGamepadOnly', 'startGamepadBuilder', 'reset', 'startPcPlay',
            'startXCloud', 'steamStartXCloud', 'pwa_prompt_for_shortcut_creation',
            'ui_language_update', 'downloadXCloudArtwork', 'show_gpu_settings',
            'saveXCloudImages', 'custom_log', 'close_pc_play'
        ];
        if (validChannels.includes(channel)) {
            ipcRenderer.send(channel, data);
        }
    },
    on: (channel, func) => {
        let validChannels = [
            'hide-unlock', 'set-version', 'set-steamcheck', 'set-ui-language',
            'set-language', 'set-consoles', 'set-regions', 'set-autologin-toggle'
        ];
        if (validChannels.includes(channel)) {
            ipcRenderer.on(channel, (event, ...args) => func(...args));
        }
    }
});

// Helper functions for preload-bound events
function quitGame(){ ipcRenderer.send('quitGame'); }
function startXHomeButtonAction(){
    const val = document.getElementById('xhome-console')?.value;
    ipcRenderer.send('startXHome', val);
}
function startXCloudButtonAction(){
    const region = document.getElementById('xcloud-region')?.value;
    const lang = document.getElementById('xcloud-language')?.value;
    ipcRenderer.send('xCloudTitlePicker', {region, language: lang});
}
function startGamepadOnlyButtonAction(){
    const val = document.getElementById('xhome-console')?.value;
    ipcRenderer.send('startGamepadOnly', val);
}
function startGamepadBuilderButtonAction(){ ipcRenderer.send('startGamepadBuilder'); }
function startLoginButtonAction(){
    let selectedOptionText = null;
    try {
        const el = document.getElementById('xcloud-region');
        selectedOptionText = el?.options[el.selectedIndex]?.textContent;
    } catch (err) {}
    ipcRenderer.send('login', {'xcloud-region-name': selectedOptionText});
}
function startResetButtonAction(){ ipcRenderer.send('reset'); }
function startPcPlayButtonAction(){ ipcRenderer.send('startPcPlay'); }

// Bind local events to DOM
window.addEventListener('DOMContentLoaded', () => {
    const bindings = {
        "xhome-tag": startXHomeButtonAction,
        "xCloudButton": startXCloudButtonAction,
        "gamepadOnlyButton": startGamepadOnlyButtonAction,
        "gamepadBuilder": startGamepadBuilderButtonAction,
        "login": startLoginButtonAction,
        "resetButton": startResetButtonAction,
        "pcPlay": startPcPlayButtonAction
    };
    for (const [id, action] of Object.entries(bindings)) {
        document.getElementById(id)?.addEventListener("click", action);
    }
    const toggleBtn = document.getElementById('toggleTypeButtonModal');
    if (toggleBtn) {
        toggleBtn.addEventListener("click", quitGame);
        toggleBtn.innerText = 'Exit';
    }
});

// Handle IPC messages (modify DOM directly in preload)
ipcRenderer.on('hide-unlock', () => {
    document.getElementById('purchase')?.remove();
    const login = document.getElementById('login');
    if (login) login.style.left = '32.5%';
});
ipcRenderer.on('set-version', (event, message) => {
    const el = document.getElementById('version-div');
    if (el) el.textContent = message;
});
ipcRenderer.on('set-steamcheck', (event, message) => {
    const el = document.getElementById('steamcheck-div');
    if (el) el.textContent = `Steam Running: ${message}`;
});
ipcRenderer.on('set-ui-language', (event, message) => {
    if (!message) return;
    if (localStorage.getItem('settings_ui_language') !== message) {
        localStorage.setItem('settings_ui_language', message);
        const el = document.getElementById('settings_ui_language');
        if (el) {
            el.value = message;
            el.dispatchEvent(new Event('change'));
        }
    }
});
ipcRenderer.on('set-language', (event, message) => {
    const dropdown = document.getElementById('xcloud-language');
    if (dropdown) {
        for (let i = 0; i < dropdown.length; i++) {
            if (message === dropdown[i].value) {
                dropdown[i].selected = true;
                break;
            }
        }
    }
});
ipcRenderer.on('set-consoles', (event, message) => {
    const dropdown = document.getElementById('xhome-console');
    if (!dropdown) return;
    dropdown.innerText = '';
    const consoleData = JSON.parse(message.consoles || '{}');
    const consoleKeys = Object.keys(consoleData);
    consoleKeys.forEach(name => {
        const opt = document.createElement("option");
        opt.value = consoleData[name];
        opt.text = name;
        if (message.lastUsed === opt.value) opt.selected = true;
        dropdown.appendChild(opt);
    });
    if (!consoleKeys.length) {
        const opt = document.createElement("option");
        opt.text = 'No Console - Login Required';
        opt.disabled = true; opt.selected = true;
        dropdown.appendChild(opt);
    }
});
ipcRenderer.on('set-regions', (event, message) => {
    const dropdown = document.getElementById('xcloud-region');
    if (!dropdown) return;
    dropdown.innerText = '';
    const regionData = JSON.parse(message.regions || '[]');
    regionData.forEach(item => {
        const opt = document.createElement("option");
        opt.value = item.baseUri;
        opt.text = item.name;
        if (message.defaultRegion === opt.value) {
            opt.text += ' (Default)';
            if (message.selectedRegion === message.defaultRegion || !message.selectedRegion) opt.selected = true;
        } else if (message.selectedRegion === opt.value) {
            opt.selected = true;
        }
        dropdown.appendChild(opt);
    });
    if (!regionData.length) {
        const opt = document.createElement("option");
        opt.text = 'Login Required';
        opt.disabled = true; opt.selected = true;
        dropdown.appendChild(opt);
    }
});
ipcRenderer.on('set-autologin-toggle', (event, message) => {
    const el = document.getElementById('auto_login');
    if (el) el.checked = message.is_set ? true : false;
});

// Backward compatibility: forward DOM events from Main World to IPC
const eventsToForward = [
    'login', 'xalTokenUpdateRequest', 'close_app', 'auto_login_enable',
    'auto_login_disable', 'settings_items_updated', 'quitGame',
    'startXCloud', 'steamStartXCloud', 'pwa_prompt_for_shortcut_creation',
    'ui_language_update', 'downloadXCloudArtwork', 'show_gpu_settings',
    'save_xcloud_images', 'custom_log'
];
eventsToForward.forEach(name => {
    window.addEventListener(name, (e) => {
        ipcRenderer.send(name, e.detail);
    });
});
