const { app, BrowserWindow, ipcMain, session, dialog, Menu, screen, globalShortcut} = require('electron')
const path = require('path')
const fs = require('fs')
const RequestClient = require('./js/backend/requests')
const persistHelper = require('./js/backend/persist_client.js')
const steamArtworkHelper = require('./js/backend/steam_artwork_helper')
const PopupClient = require('./js/popup/popup_client')
const LoginWindowClient = require("./js/backend/login/login_window");
const SteamCustomClient = require('./js/backend/steam_custom_client')
const PCPlayClient = require('./pc_play/pc_play_render')
const SteamGridClient = require('./js/backend/steamgrid_client')
const XalLoginClient = require("./js/backend/login/xal_login");

const config = require('./config.js');

let mainWindow // window
let showTrialWarning = false
let forceQuit = false
let forcePCheck = true // used to always perform pcheck on app open
let commandLineArgUsed = false
let commandLineArgLoginTries = 0;
let trailTimerInterval
let returnFromPcPlay = false
let alreadyAutoLoggedIn = false

// setup hostname
const useDev = config.useDev
const isSteamVersion = config.isSteamVersion
const trailTime = 1000 * 60 * 20
const steamTrialTime = 1000 * 60
const fetchTokenExTime = 24 * 60 * 60 * 1000 // 24 hours

let HOSTNAME = config.hostname
if(!useDev){
    Menu.setApplicationMenu(new Menu())
    if(!getCommandLineArg('verbose')){ // ignore logs in prod unless verbose flag set
        console.log = function(){}
    }
}

// classes
const steamCustomClient = new SteamCustomClient()
const persistClient = new persistHelper()
const steamArtworkClient = new steamArtworkHelper(steamCustomClient)
const popupClient = new PopupClient(useDev, 'main')
const requestClient = new RequestClient(HOSTNAME)
const loginWindowClient = new LoginWindowClient(HOSTNAME, !!(isSteamVersion && steamCustomClient.getIsSteamDeck()))
const xalLoginClient = new XalLoginClient(HOSTNAME, !!(isSteamVersion && steamCustomClient.getIsSteamDeck()))
const pcPlayClient = new PCPlayClient()
const steamGridClient = new SteamGridClient()

// handle GPU settings
if (persistClient.getJSONKey('settings_gpu_mode', 'settings_items') === 'dedicated') {
    app.commandLine.appendSwitch('force_high_performance_gpu', '1')
} else if (persistClient.getJSONKey('settings_gpu_mode', 'settings_items') === 'discrete') {
    app.commandLine.appendSwitch('force_high_performance_gpu', '0')
}

// command line flags for performance
app.commandLine.appendSwitch('enable-gpu-rasterization')
app.commandLine.appendSwitch('enable-oop-rasterization')
app.commandLine.appendSwitch('enable-zero-copy')
app.commandLine.appendSwitch('enable-accelerated-video-decode')


// settings enabled switches
if (persistClient.getJSONKey('settings_render_pipeline', 'settings_items') === 'vulkan') {
    app.commandLine.appendSwitch('use-vulkan')
    app.commandLine.appendSwitch('enable-features', 'Vulkan,VulkanFromANGLE,DefaultANGLEVulkan,VaapiIgnoreDriverChecks,VaapiVideoDecoder,PlatformHEVCDecoderSupport,CanvasOopRasterization')
}
// app.commandLine.appendSwitch('ozone-platform-hint', 'x11')

// app.disableHardwareAcceleration()

// listeners
loginWindowClient.on('handleCommandLineArgAutoStart', async (reloadMainView) => {
    console.log('handleCommandLineArgAutoStart', reloadMainView)
    if(reloadMainView){
        await loadMainViewData()
    }
    handleCommandLineArgAutoStart()
});
loginWindowClient.on('quitGame', () => {
    quitGame(null, true)
})
loginWindowClient.on('showToast', (data) => {
    console.log('showToast', data)
    try {
        if (mainWindow){
            mainWindow.webContents.executeJavaScript(`showToast('${data.message}', ${data.isSuccess});`)
        }
    } catch (err){
        console.log(err)
    }
})

loginWindowClient.on('loginPageStateChange', (data) => {
    console.log('loginPageStateChange', data)
    try {
        if (mainWindow){
            mainWindow.webContents.executeJavaScript(`setLoginLoadingVisibility(${data.isVisible});`)
        }
    } catch (err){
        console.log(err)
    }

    // if starting via xcloud shortcut start stream as soon as login is done
    if (data.isVisible === 0) {
        if (getCommandLineArg('xhome') || getCommandLineArg('xcloud')) {
            handleCommandLineArgAutoStart();
        }
    }
})

xalLoginClient.on('quitGame', () => {
    quitGame(null, true)
})
xalLoginClient.on('loginPageStateChange', (data) => {
    console.log('loginPageStateChange', data)
    try {
        if (mainWindow){
            mainWindow.webContents.executeJavaScript(`setLoginLoadingVisibility(${data.isVisible});`)
        }
    } catch (err){
        console.log(err)
    }

    // if starting via xcloud shortcut start stream as soon as login is done
    if (data.isVisible === 0) {
        if (getCommandLineArg('xhome') || getCommandLineArg('xcloud')) {
            handleCommandLineArgAutoStart();
        }
    }
})
xalLoginClient.on('showToast', (data) => {
    console.log('showToast', data)
    try {
        if (mainWindow){
            mainWindow.webContents.executeJavaScript(`showToast('${data.message}', ${data.isSuccess});`)
        }
    } catch (err){
        console.log(err)
    }
})
xalLoginClient.on('handleCommandLineArgAutoStart', async (reloadMainView) => {
    console.log('handleCommandLineArgAutoStart', reloadMainView)
    if(reloadMainView){
        await loadMainViewData()
    }
    handleCommandLineArgAutoStart()
});

async function showDialog(options, disableHomeScreenControls = true){
    if(!popupClient.canShowPopup()){
        console.log('not showing duplicate popup v2', options)
        return false;
    }

    return new Promise((resolve) => {
        setTimeout(async () => {
            if(disableHomeScreenControls) {
                try {
                    await mainWindow.webContents.executeJavaScript(`setControls(0)`);
                } catch(err){}
            }

            let result = false
            try {
                result = await popupClient.showPopup(options, this.parentWindow)
            } catch (err){}

            if(disableHomeScreenControls) {
                try {
                    await mainWindow.webContents.executeJavaScript(`setControls(1)`);
                } catch (err){}
            }
            setTimeout(() => {
                return resolve(result);
            }, 500)
        }, 300)
    });
}

const createMainWindow = async (isPcPlay = false) => {
    let preloadPath = 'js/backend/main_preload.js'
    let htmlPath = './html/index.html'
    let oldWindow = null
    if (isPcPlay) {
        preloadPath = 'pc_play/js/preload/pc_play_listeners.js'
        htmlPath = './pc_play/html/pc_play_host_picker.html'
    }

    let options = {
        backgroundColor: '#000000',
        width: 1280,
        height: 800,
        frame: false,
        webPreferences: {
            // sandbox check
            preload: path.join(__dirname, preloadPath),
            devTools: useDev,
            nodeIntegration: false,
            contextIsolation: true,
        },
        // fullscreen: fullscreenMode,
        // titleBarStyle: 'hidden',
        title: "XBPlay"
    }

    // partition settings
    if (getCommandLineArg('xcloud') && persistClient.getJSONKey('settings_partition_sessions', 'settings_items')){
        const xcloudTitle = getCommandLineArg('xcloud')
        options.webPreferences.partition = `persist:${xcloudTitle}`
    }

    // set fullscreen to true for steam big picture mode (otherwise, changing resolution doesnt do anyting while docked)
    // causes issues on mac if you start straight in fullscreen. Cant drag in fullscreen and if starts in false it never goes to fullscreen again
    if (steamCustomClient.getIsSteamDeck()){
        options['fullscreen'] = true
    }

    if (mainWindow) {
        oldWindow = mainWindow
        const pos = mainWindow.getPosition();
        const size = mainWindow.getSize()
        options.width = size[0]
        options.height = size[1]
        options.x = pos[0]
        options.y = pos[1]
    }
    mainWindow = new BrowserWindow(options)
    popupClient.setParentWindow(mainWindow)
    pcPlayClient.setParentWindow(mainWindow)

    mainWindow.webContents.on('did-fail-load', (_, errorCode, errorDescription) => {
        console.error('FAILED TO LOAD', errorCode, errorDescription)

        if (errorCode === -105 || errorCode === -102 || errorCode === -106) {
            mainWindow.loadFile('./html/index.html')
            if (mainWindow){
                mainWindow.webContents.executeJavaScript(`disableSplash()`)
                mainWindow.webContents.executeJavaScript(`showToast('No Internet: ${errorDescription}', false);`)
            }
            console.error('FAILED TO LOAD')
        }
    });

    // important to start in maximized view since we are not starting in fullscreen
    mainWindow.once('ready-to-show', () => {
        if (!isPcPlay && !returnFromPcPlay){
            mainWindow.maximize()
        }
    })

    mainWindow.webContents.on('dom-ready', () => {
        if(oldWindow){
            forceQuit = true
            oldWindow.close()
            oldWindow = null
        }

        // Bridge events from Main World to Main Process via exposed API (for remote pages)
        mainWindow.webContents.executeJavaScript(`
            (function() {
                const events = [
                    'login', 'xalTokenUpdateRequest', 'close_app', 'auto_login_enable',
                    'auto_login_disable', 'settings_items_updated', 'quitGame',
                    'startXCloud', 'steamStartXCloud', 'pwa_prompt_for_shortcut_creation',
                    'ui_language_update', 'downloadXCloudArtwork', 'show_gpu_settings',
                    'save_xcloud_images', 'custom_log'
                ];
                events.forEach(name => {
                    window.addEventListener(name, (e) => {
                        if (window.electronAPI) {
                            window.electronAPI.send(name, e.detail);
                        }
                    });
                });
            })();
        `);

        setZoomLevel()
        const currentURL = mainWindow.webContents.getURL() || ''
        if (currentURL.includes("index.html")) { // setup main screen
            loadMainViewData();
            setFullScreen(false);
        } else if (currentURL.includes("pc_play")){
            setFullScreen(false);

            if (isSteamVersion && steamCustomClient.getIsSteamDeck()){
                mainWindow.webContents.executeJavaScript(`
                    try {
                        SteamOSKeyboardFix.DisableInputFields();
                    } catch (err){}
                `)
            }
        } else { // setup streaming screens
            if (mainWindow.isMaximized()){
                setFullScreen(true);
            }
            if(getCommandLineArg('verbose')){
                mainWindow.webContents.executeJavaScript(`
                    console.log = function(...args){
                        window.dispatchEvent(new CustomEvent("custom_log", {
                            detail: args
                        }));
                    }`)
            }
            mainWindow.webContents.executeJavaScript(`
                try {
                    console.log('Setting Custom Electron Functions')
                    if(setConfigData) {
                        setConfigData('${JSON.stringify(persistClient.getConfigData())}');
                    }
        
                    window.linuxBus = {};
                    window.linuxBus['sendMessage'] = function(type, data){
                        console.log('sendMessage', data);
                        if (type === 'steamStartXCloud'){
                            // bubble message up to any listening scripts
                            window.dispatchEvent(new CustomEvent("steamStartXCloud", {
                              detail: {data: data},
                            }));
                        } else if (type === 'pwa_prompt_for_shortcut_creation'){
                            // bubble message up to any listening scripts
                            window.dispatchEvent(new CustomEvent("pwa_prompt_for_shortcut_creation", {
                              detail: {data: data},
                            }));
                        } else if(type === 'endXCloud' || type === 'quitGame') {
                            window.dispatchEvent(new Event("quitGame"));
                        } else if( type === 'save_xcloud_images'){
                            window.dispatchEvent(new CustomEvent("save_xcloud_images", {
                              detail: {titleData: data},
                            }));
                        } else if (type === 'start_xcloud_stream') {
                             window.dispatchEvent(new CustomEvent("startXCloud", {
                              detail: {title: data},
                            }));
                        } else if (type === 'reLoginRequest') {
                             window.dispatchEvent(new Event("login"));
                        } else if (type === 'xalTokenUpdateRequest') {
                              window.dispatchEvent(new CustomEvent("xalTokenUpdateRequest", {
                              detail: {data: data},
                            }));
                        } else if (type === 'ui_language_update') {
                              window.dispatchEvent(new CustomEvent("ui_language_update", {
                              detail: {data: data},
                            }));
                        } else if (type === 'gamepad') {
                            window.dispatchEvent(new CustomEvent("gamepad", { detail: data }));
                        } else if (type === 'gamepad_rumble') {
                            window.dispatchEvent(new CustomEvent("gamepad_rumble", { detail: data }));
                        } else {
                            console.log('Unknown linux message sent', type, data);
                        }
                    }
                    
                    // dont prompt steam user because they are easily annoyed
                    if(confirm){
                        confirm = function(msg){
                            return true;
                        }
                    }
                    
                    // always start the stream right away
                    connectButtonAction();
                } catch (err){}
            `)
        }

        if(showTrialWarning){
            showTrialWarning = false

            if(isSteamVersion){
                showSteamValidationWarning()
            } else {
                const options = {
                    type: 'warning',
                    buttons: ['OK'],
                    title: 'Free Trail Ended',
                    message: 'Thanks for trying out the app!',
                    detail: "If you liked it, click the 'Unlock' button to see how to get the full version."
                };

                showDialog(options) // dont really need to await it?
            }
        }
    })

    mainWindow.on('close', async function(e) {
        console.log('onClose', forceQuit)
        if(!forceQuit && !steamCustomClient.getIsSteamDeck()) {
            const options = {
                type: 'warning',
                buttons: ['Exit App', 'Cancel'],
                title: 'Exit App',
                message: 'Would you like to exit the application?',
            };

            const response = await showDialog(options)
            if (response && response.response === 1) {
                e.preventDefault();
            } else {
                app.quit()
            }
        }
    });

    await mainWindow.loadFile(htmlPath)
    if (!isPcPlay && !returnFromPcPlay){
        setupAutoLoginAndShortcutStart()
    }
}

function setupAutoLoginAndShortcutStart(){
    const useXalLogin = persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !persistClient.getJSONKey('settings_login_type', 'settings_items')

    // if auto login, login on start (wait 3 seconds if in foreground mode)
    if (persistClient.get('auto_login') && !alreadyAutoLoggedIn && !useXalLogin){
        alreadyAutoLoggedIn = true
        if (persistClient.getJSONKey('settings_login_display', 'settings_items') === 'background'){
            loginWindowClient.showLoginPage()
        } else {
            setTimeout(()=>{
                loginWindowClient.showLoginPage()
            }, 3000)
        }
    } else if (getCommandLineArg('xhome') || getCommandLineArg('xcloud')){
        // if login will happen automatically, don't start shortcut sesh (it will happen after login)
        setTimeout(()=>{
            handleCommandLineArgAutoStart();
        }, 3000)

    }
}

function setZoomLevel(){
    if(!mainWindow){
        return
    }
    const zoomFactor = persistClient.getJSONKey('settings_zoom', 'settings_items') ?? '100'
    const zoomFactorFloat = parseFloat((zoomFactor / 100).toFixed(2));
    console.log('Setting Zoom Level', zoomFactorFloat)
    mainWindow.webContents.setZoomFactor(zoomFactorFloat);
}
function setFullScreen(enabled){
    if (mainWindow) {
        mainWindow.setFullScreen(enabled);

        try {
            // only show fullscreen popup 10 times
            const showedShortcut = +persistClient.get('fullscreenPopupCounter') || 0
            if (showedShortcut <= 5) {
                persistClient.save('fullscreenPopupCounter', +showedShortcut + 1)
                mainWindow.webContents.executeJavaScript(`showToast('Use F10 or F11 to toggle fullscreen', false);`)
            }
        } catch (err){}
    }
}

async function loadMainViewData(){
    mainWindow.webContents.send('set-autologin-toggle', {is_set: persistClient.get('auto_login')});

    // console c
    const consoleData = persistClient.get('availableConsoles') || '{}'
    const lastUsedConsole = persistClient.get('serverId');
    mainWindow.webContents.send('set-consoles', {consoles: consoleData, lastUsed: lastUsedConsole});

    // region data
    const regionData = persistClient.get('regions') || '{}'
    const defaultRegion = persistClient.get('default-region');
    const lastSelectedRegion = persistClient.get('selected-region');
    mainWindow.webContents.send('set-regions', {regions: regionData, defaultRegion: defaultRegion, selectedRegion: lastSelectedRegion});

    // language data
    const selectedLanguage = persistClient.get('selected-language')
    mainWindow.webContents.send('set-language', selectedLanguage);

    // show app version
    mainWindow.webContents.send('set-version', 'Version: ' + app.getVersion());

    mainWindow.webContents.send('set-steamcheck',  (steamCustomClient.isSteamDevice) ? 'Yes' : 'No');

    // ui language data
    const uiLanguage = persistClient.get('ui-language')
    if (uiLanguage){
        mainWindow.webContents.send('set-ui-language', uiLanguage);
    }

    await handleLicensing()
    forcePCheck = false
}

function getCommandLineArg(key){
    console.log(process.argv)
    if(process.argv && process.argv.length){
        for(let i = 0; i < process.argv.length; i++){
            const arg = (process.argv[i] || '').split("=");
            if(arg[0].includes(key)){
                if(arg[1]){
                    return arg[1];
                } else {
                    return true
                }
            }
        }
    }
    return false;
}
async function setupArtwork(){
    await steamArtworkClient.setMainShortcutArtwork(false)
}

async function showStartupDialog(){
    setTimeout(async ()=> { // wait for splash screen
        const check = await steamArtworkClient.shouldShowGamepadModeStartupPrompt()
        const badProtonDetected = steamCustomClient.getBadProtonModeDetected();
        const showSteamInputWarning = await steamArtworkClient.shouldShowSteamInputWarning();

        if (badProtonDetected) {
            const options = {
                type: 'warning',
                buttons: ['Exit', 'Continue'],
                title: 'Error: Invalid Proton Compatibility Mode',
                message: 'Steam launched this app with <b>Proton Compatibility</b> mode enabled, a configuration that may introduce potential instability to the app.<br>',
                detail: "To fix this, on Steam go to <b>Properties > Compatibility</b> and select <b>Linux Runtime</b>. If Linux Runtime is already selected, toggle it off and then back on again. Its possible that a restart will re-enable proton compatibility mode, if this happens, you may need to click 'Delete Proton Files' as well."
            };
            const response = await showDialog(options)
            if(response && response.response == 0){
                forceQuit = true
                app.quit()
            }
        } else if(check){
            if (steamCustomClient.getIsSteamDeck() && isSteamVersion){ // show compatibility mode warning if on steam deck and running steam version

                const options = {
                    type: 'warning',
                    buttons: ['OK'],
                    title: 'Compatibility Mode Warning on Steam Deck',
                    message: "<b>Do not</b> ever enable 'Proton Compatibility' mode on the Steam Deck for this app. It will cause the app to not launch.<br>",
                    detail: "If this app ever fails to launch, its because Proton has been enabled. To fix it, on Steam go to 'Properties > Compatibility' and select 'Linux Runtime' or uncheck the compatibility checkbox altogether. Once enabled, its possible that a restart will re-enable proton compatibility mode, if this happens, you may need to click 'Delete Proton Files' as well."
                };
                await showDialog(options)
            } else { // show add to steam
                const options = {
                    type: 'warning',
                    buttons: ['Add Now', 'Cancel'],
                    title: 'Steam Setup',
                    message: 'This app works best in Gaming mode.',
                    detail: 'Would you like to add this app as a game in your steam library now?'
                };
                const response = await showDialog(options)
                if(response && response.response == 0){
                    console.log('Add main shortcut')
                    const result = await steamArtworkClient.addMainAppShortcut()
                    const options = {
                        type: 'warning',
                        buttons: ['OK'],
                        title: 'XBPlay Added to Gaming Mode',
                        message: 'You can switch back to Gaming Mode now and use the XBPlay app. The app will be saved in your Steam library under <b>Non-Steam Games</b>'
                    };

                    await showDialog(options)
               } 
            }
        } else if (showSteamInputWarning){
            const options = {
                type: 'warning',
                buttons: ['OK'],
                title: 'Steam Input Warning',
                message: "Some devices may require Steam Input to be disabled for controllers to work properly. If input isn’t working, try disabling Steam Input for the XBPlay app in Steam settings.",
            };
            await showDialog(options)
        }
    }, 3000);
}

app.whenReady().then(() => {
    ipcMain.on('quitGame', quitGame)
    ipcMain.on('xCloudTitlePicker', xCloudTitlePicker)

    ipcMain.on('startXCloud', startXCloud)
    ipcMain.on('startXHome', startXHome)
    ipcMain.on('startGamepadOnly', startGamepadOnly)
    ipcMain.on('startGamepadBuilder', startGamepadBuilder)
    ipcMain.on('reset', reset)

    ipcMain.on('auto_login_toggle', autoLoginToggle)
    ipcMain.on('close_app', closeApp)
    ipcMain.on('steamStartXCloud', steamStartXCloud)
    ipcMain.on('pwa_prompt_for_shortcut_creation', pwa_prompt_for_shortcut_creation)
    ipcMain.on('downloadXCloudArtwork', downloadXCloudArtwork)
    ipcMain.on('show_gpu_settings', show_gpu_settings)
    ipcMain.on('saveXCloudImages', saveXCloudImages)
    ipcMain.on('custom_log', customLog)
    ipcMain.on('settings_items_updated', settingsItemsUpdated)
    ipcMain.on('startPcPlay', startPcPlay)
    ipcMain.on('close_pc_play', (event, args) => {handleClosePcPlay(event, args)})
    ipcMain.on('ui_language_update', uiLanguageUpdate)


    app.on('browser-window-focus', async (event, win) => {
        console.log('browser-window-focus', win.webContents.id)
        try {
            if (win && win.isLoginWindow) {
                console.log('Window title is Login, skipping execution');
                return;
            }
        } catch (err){
            console.error('failed to get window title')
        }

        setTimeout(async() => {
            try {
                await win.webContents.executeJavaScript(`setControls(1)`);
            } catch (err){
                console.log('Error setControls(1)')
            } 
        }, 300);
       
        setTimeout(async() => {
            try {
                await win.webContents.executeJavaScript(`refocus()`);
            } catch (err){
                console.log('Error refocus(1)')
            } 
        }, 300);
       
    })
    app.on('browser-window-blur', async (event, win) => {
        if (win.webContents.isDevToolsFocused()) {
        } else {
            console.log('browser-window-blur', win.webContents.id)
            try {
                if (win && win.isLoginWindow) {
                    console.log('Window title is Login, skipping execution');
                    return;
                }
            } catch (err){
                console.error('failed to get window title')
            }

            try {
                await win.webContents.executeJavaScript(`setControls(0)`);
            } catch (err){
                console.log('Error setControls(0) blur')
            }
        }
    })

    createMainWindow()

    app.on('activate', () => {
        if (BrowserWindow.getAllWindows().length === 0){
            createMainWindow()
        }
    })

    setupArtwork()


    showStartupDialog() // this needs to be called before maintainSteamShortcut otherwise 2 will be added on start
    maintainSteamShortcut() // this needs to be after showStartupDialog otherwise 2 will be added on start

    // Register a global shortcut
    globalShortcut.register('F11', () => {
        toggleFullscreen()
    })

    globalShortcut.register('F10', () => {
      toggleFullscreen()
    });

})

async function maintainSteamShortcut(){
    try {
        if (steamCustomClient.isSteamDevice && persistClient.getJSONKey('settings_keep_shortcut', 'settings_items') === true) {
            const appIds = await steamArtworkClient.getMainShortcutIds()
            console.log('Main app shortcut id is', appIds)

            if(!appIds.length) {
                console.log('Unable to find ID for main app. Creating startup dialog')
                await steamArtworkClient.addMainAppShortcut()
            } else {
                console.log('Main shortcut already exists. Dont re-create', appIds)
            }
        }
    } catch (err){
        console.error(err)
    }
}

function toggleFullscreen(){
    if (mainWindow){
        // unmax window. Otherwise, switching between maximized and fullscreen shows no real difference to the user and it looks like its not doing anything
        if (mainWindow.isMaximized()){
            mainWindow.unmaximize();
        }

        setFullScreen(!mainWindow.isFullScreen());
    }
}
async function handleLicensing() {
    if (isSteamVersion === true){
        await steamAppCheck()
    } else {
        await pcheck(forcePCheck)
    }
}

async function showSteamValidationWarning(){
    const options = {
        type: 'warning',
        buttons: ['OK'],
        title: 'Steam License Not Found',
        message: "You're using a version of the app originally designed for Steam. However, we're unable to confirm your ownership of this app. Please make sure you are:",
        detail: "<br>1. Connected to the internet.<br>2. Have the Steam client app <b>installed and open</b>. <br>3. Have successfully purchased this app on Steam.<br>Afterward, reopen this app.<br>"
    };

    const isDeviceOnline = await isConnectedToInternet()

    if (!steamCustomClient.getDeviceHasSteamAppInstalled()){
        options.title = 'License Not Found - Steam App Not Running'
        options.detail += '<br>Details: <b>Unable to communicate with the Steam app</b>.<br>'
        options.detail += '- Ensure proton compatibility mode is not enabled.<br>- Open the Steam App.<br>- Restart your device.<br>'
    } else if (!isDeviceOnline){
        options.title = 'No Internet'
        options.detail += '<br>Details: <b>Unable to Connect to the Internet</b>.<br>'
        options.detail += '- Ensure you are connected to the internet then restart the app'
    } else {
        options.buttons = ['OK', 'Check Again']
        options.detail += '<br>Troubleshooting:<br>- Clear the cache and login again.<br>- Ensure proton compatibility mode is not enabled.<br>- Restart your device.'
    }

    const response = await showDialog(options)
    if(response && response.response === 1){
        const gsToken = persistClient.get('gsToken')
        if (!gsToken){
            await showToast('Must Login First', false)
            return
        }

        // allow user to restore without clearing cache
        persistClient.save('last_token', null)
        persistClient.save('last_valid_token_timestamp', null)

        await handleTokenUpdate()
        const result = await handleTokenLookup()
        if (result){
            clearInterval(trailTimerInterval)
           await showToast('License Valid. Restored', true)
        } else {
            await showToast('License Invalid.', false)
        }
    }
}

async function showToast(message, isSuccess = true){
    try {
        if (mainWindow && !mainWindow.isDestroyed()) {
            await mainWindow.webContents.executeJavaScript(`showToast('${message}', ${isSuccess});`)
        }
    } catch (err){
        console.log('Error Showing Toast')
    }
}

async function steamAppCheck() {
    mainWindow.webContents.send('hide-unlock', {});

    try {
        const isSubscribedApp = steamCustomClient.isSubscribedApp()
        console.log('Steam isSubscribedApp: ', isSubscribedApp)

        if (!isSubscribedApp) {
            handlePCheckFailSteamVersion()
            return
        }

        // add a new token if we need to
        await handleTokenUpdate()

        // lookup token status
        await handleTokenLookup()
    } catch (err){
        handlePCheckFailSteamVersion()
    }
}

function isTokenResponseExpired() {
    return false; // is still valid
}

async function handleTokenLookup(){
    persistClient.save('pcheck', 1)
    return true
}

// updates the token value, can call this frequently because it caches
async function handleTokenUpdate(){
    try {
        const gsToken = persistClient.get('gsToken')
        const steamId64 = steamCustomClient.getSteamUserId64()
        const previousToken = persistClient.get('last_token')

        // dont update if not on steam version, not logged in, or already updated
        if (!config.isSteamVersion || !gsToken || !steamId64 || previousToken === steamId64) {
            console.log('Ignore token update', previousToken, steamId64)
            return
        }

        const updateTokens = await requestClient.saveTokens(steamCustomClient.getSteamUserId64(), gsToken, persistClient.getConfigData().platform)
        if (updateTokens.purchaseToken) {
            persistClient.save('last_token', updateTokens.purchaseToken)
        }
    } catch (err){
        console.log(err)
    }

}

function handlePCheckFailSteamVersion() {
    persistClient.save('pcheck', 1)
    persistClient.save('last_valid_token_timestamp', null); // cache lookup time for next request
    // runGameTimer(steamTrialTime)
    showSteamValidationWarning()
}

async function pcheck(force = false){
    persistClient.save('pcheck', 1)
    mainWindow.webContents.send('hide-unlock', {});
}

function runGameTimer(delay){
    clearInterval(trailTimerInterval)
    // trailTimerInterval = setInterval(async ()=>{
    //     console.log('Running game timer')
    //     if (!persistClient.get('pcheck')){
    //         showTrialWarning = false
    //         await createMainWindow(false)
    //     }
    // }, delay)
}

function show_gpu_settings(){
    const gpuWin = new BrowserWindow({
        width: 1000,
        height: 800,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
        }
    });

    gpuWin.loadURL('chrome://gpu');
}

async function downloadXCloudArtwork(){
    const options = {
            type: 'warning',
            buttons: ['Continue', 'Cancel'],
            title: 'Redownload xCloud Artwork?',
            message: 'This will download the artwork for all xCloud shortcuts. If you saved custom artwork for any xCloud shortcuts, it will be overridden. Continue?<br>',
            detail: 'This process can take a few minutes. A popup will be displayed on completion'
        };

    const response = await showDialog(options)
    if(response && response.response == 0){
        console.log('redownload')
        const result = await steamArtworkClient.updateAllXCloudArtwork()
        const options = {
            type: 'warning',
            buttons: ['OK'],
            title: 'Artwork Result',
            message: 'You must restart your Steam Deck or the Steam app to see new artwork.<br>',
            detail: result
        };

        await showDialog(options)
   }
}

function settingsItemsUpdated(event, data) {
    console.log(data)
    persistClient.save(data['settingsKeyName'], JSON.stringify(data['values']))

    // do any realtime settings updates
    setZoomLevel()
}

// receive lang update events from website, save to render
function uiLanguageUpdate(event, data){
    console.log('uiLanguageUpdate', data)
    persistClient.save('ui-language', data)
}

async function startPcPlay(event, data) {
    console.log('startPcPlay')

    const tutSuccess = await pcPlayClient.showTutorialIfRequired()

    if (tutSuccess) {
        await createMainWindow(true);
        pcPlayClient.binaryClient.showSavedHosts()

        if(!(await pcPlayClient.binaryClient.getIsClientInstalled())){
            const options = {
                type: 'warning',
                buttons: ['Yes', 'Cancel'],
                title: 'PC Play Client Missing',
                message: 'You must install the PC Play client to use this feature. Would you like to install the PC Play client now?',
            };

            const response = await showDialog(options)
            if (response && response.response === 1) {
               console.log('nope')
            } else {
                pcPlayClient.handleConfigureClient()
            }
        } else {
            pcPlayClient.binaryClient.checkVersionForUpdates()
        }

    }
}

async function handleClosePcPlay(event, data) {
    console.log('handleClosePcPlay')
    returnFromPcPlay = true
    await createMainWindow(false);
    returnFromPcPlay = false
    await mainWindow.webContents.executeJavaScript(`disableSplash()`)
}

function customLog(event, args){
    console.log('LOG:',args)
}
async function saveXCloudImages(event, args) {
    console.log('saveXCloudImages')
    if(args && args['titleData']){
        try {
            steamArtworkClient.saveAllImages(args['titleData'])
        } catch (err){
            console.log(err)
        }
    }
}

// quitGame
async function quitGame(event = null, fromReloginEvent = false) {
    console.log('caught quitGame')
    if (!fromReloginEvent && (getCommandLineArg('xcloud') || getCommandLineArg('xhome'))) { // dont quit if returning to main menu from login
        closeApp()
    } else {
        // reset command line usage flag so we return back to the game on completion
        commandLineArgUsed = false
        setFullScreen(false);

        await mainWindow.loadFile('./html/index.html')
        // if we need to send msg back to the renderer
        await mainWindow.webContents.executeJavaScript(`disableSplash()`)
    }
}

function checkInFocus() {
    if (mainWindow && mainWindow.isFocused()){
        return true
    } else {
        console.log('Not in Focus! Ignoring call!')
        try {
            loginWindowClient.handleLoginSelectButtonPress()
        } catch (err){
            console.log(err)
        }
        return false
    }
}

async function xCloudTitlePicker(event, args) {
    console.log('xCloudTitlePicker', `'${JSON.stringify(args)}'`, typeof args, args == true)
    if (!checkInFocus()){
        return;
    }

    const hasXCloudToken = persistClient.get('xCloudToken', args)
    const msalToken = persistClient.get('msal')
    if(hasXCloudToken && msalToken) {
        persistClient.save('selected-region', args['region'])
        persistClient.save('selected-language', args['language'])
        persistClient.returnXCloudConfig = true
        let url = null

        const useXalLogin = persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !persistClient.getJSONKey('settings_login_type', 'settings_items')
        if(!useXalLogin){
            url = `${HOSTNAME}/title_picker_v3.html?xcloudToken=${persistClient.get('xCloudToken')}`
            if (await steamArtworkClient.isSteamDevice()){
                url += `&isSteam=1`
            }

            // legacy set lang
            const uiLanguage = persistClient.get('ui-language')
            if (uiLanguage){
                url += `&ui_language=${uiLanguage}`
            }
        } else {
            url = `${HOSTNAME}/title_picker_v3.html`
            if (await steamArtworkClient.isSteamDevice()){
                url += `?isSteam=1`
            }
        }

        mainWindow.loadURL(url)
    } else {
        const options = {
            type: 'warning',
            buttons: ['Ok'],
            title: 'Login Required',
            message: 'You must login to use remote play.',
            detail: 'Please login to the Microsoft account associated with your Xbox Live profile by pressing the Login button below. Note, you must have a GamePass Ultimate subscription to use Cloud Play.',
        };
        await showDialog(options)
    }
}

function startXCloud(event, args) {
    persistClient.save('xCloudTitle', args)
    persistClient.returnXCloudConfig = true

    // lookup title from title id
    let titleToDisplay = args
    try {
        const titlesRaw = persistClient.get('all_images');
        const titles = JSON.parse(titlesRaw)

        if (titles[args]['title']) {
            titleToDisplay = titles[args]['title']
        }
    } catch (err){
        console.log('Could not find title name for id', args, err)
    }

    steamCustomClient.setRichPresenceXCloudGame(titleToDisplay)

    if (persistClient.getJSONKey('settings_use_official_web', 'settings_items')) {
        mainWindow.loadURL('https://www.xbox.com/play');
    } else if (isSteamVersion && steamCustomClient.getIsSteamDeck()){
        mainWindow.loadFile('assets/android_stream.html', { query: { isSteam: '1' } })
    } else {
        mainWindow.loadFile('assets/android_stream.html')
    }
}

async function pwa_prompt_for_shortcut_creation(event, args){
    console.error('pwa_prompt_for_shortcut_creation', args)
    await createShortcuts(JSON.parse(args))
}

async function createShortcuts(args){
    if (persistClient.getJSONKey('setting_artwork_source', 'settings_items') === 'steamgrid'){
        const imageData = await steamGridClient.getArtworkFromGame(args['title'])
        console.log('ImageData', imageData)
        if (imageData?.heroUrl){
            args['steamGridHeroUrl'] = imageData.heroUrl
        } if (imageData?.capsuleUrl){
            args['steamGridCapsuleUrl'] = imageData.capsuleUrl
        }
    }

    const result = await steamArtworkClient.addSteamShortcut(args);
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

        const failedResponse = await showDialog(options)
        if(!failedResponse){
            console.log('cancel clicked do nothing');
        } else if(failedResponse.response === 0){ // force add shortcut
            const result = await steamArtworkClient.addSteamShortcut(args, true);
            if (result && result['result']){
                let message = 'Launch Game: Restart your device or the Steam app to see the new game. The game will appear under the non-steam games section in your library.'
                let detail = "Artwork: Artwork will automatically be added to your shortcut. For xCloud shortcuts, if the artwork gets out of sync, you can redownload it via 'Settings->Resync XCloud Artwork'"
                const options = {
                    type: 'warning',
                    buttons: ['Ok'],
                    title: 'Game Added to Steam Library!',
                    message: message,
                    detail: detail,
                };

                await showDialog(options)
            } else {
                const options = {
                    type: 'warning',
                    buttons: ['Ok'],
                    title: 'Add Steam Shortcut Error',
                    message: 'An error occurred adding shortcut.',
                    detail: result['message'] || '',
                };
                await showDialog(options)
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

        await showDialog(options)
    } else if (!result['result'] && result['code'] === 0) {
        const options = {
            type: 'warning',
            buttons: ['Ok'],
            title: 'Add Steam Shortcut Error',
            message: 'An error occurred adding shortcut.',
            detail: result['message'] || '',
        };

        await showDialog(options)
    } else { // if was able to add to steam
        let message = 'Launch Game: Restart your device or the Steam app to see the new game. The game will appear under the non-steam games section in your library.'
        let detail = "Artwork: Artwork will automatically be added to your shortcut. For xCloud shortcuts, if the artwork gets out of sync, you can redownload it via 'Settings->Resync XCloud Artwork'"
        const options = {
            type: 'warning',
            buttons: ['Ok'],
            title: 'Game Added to Steam Library!',
            message: message,
            detail: detail,
        };

        await showDialog(options)
    }
}

async function steamStartXCloud(event, args) {
    console.error('steamStartXCloud')

    persistClient.save('xCloudTitle', args['titleId'])
    persistClient.returnXCloudConfig = true

    const options = {
        type: 'warning',
        buttons: ['Play', 'Add to Steam'],
        title: 'Play Title or Add to Steam',
        message: 'Would you like to play this game? Or add it to your steam library as its own shortcut?',
    };

    let response = {response: 0}

    // prompt user to play or add shortcut if settings allow
    if (persistClient.getJSONKey('settings_allow_xcloud_shortcut', 'settings_items') !== false) {
        response = await showDialog(options)
    }

    if (!response || response.canceled){ // clicked cancel
        console.log('ignoring duplicate message')
    } else if (response.response === 0){ // clicked play xhome
        console.log('STARTING XCLOUD STREAM')
        steamCustomClient.setRichPresenceXCloudGame(args['title'])

        if (persistClient.getJSONKey('settings_use_official_web', 'settings_items')) {
            mainWindow.loadURL('https://www.xbox.com/play');
        } else if (isSteamVersion && steamCustomClient.getIsSteamDeck()){
            mainWindow.loadFile('assets/android_stream.html', { query: { isSteam: '1' } })
        } else {
            mainWindow.loadFile('assets/android_stream.html')
        }

    } else if (response.response === 1){ // clicked add to steam
        console.log('add to steam clicked');

        await createShortcuts(args)
    }
}

function startXHome(event, args){
    if (!checkInFocus()){
        return;
    }
    const hasXHomeTokens = persistClient.get('serverId')
    console.log('startXHome', hasXHomeTokens, `'${args}'`, typeof args, args == true)
    if(args || hasXHomeTokens) {
        if(args){
            persistClient.save('serverId', args) // using dropdown on homescreen now
        }
        persistClient.returnXCloudConfig = false
        steamCustomClient.setRichPresenceXHomeGame()

        if (persistClient.getJSONKey('settings_use_official_web', 'settings_items')) {
            mainWindow.loadURL('https://www.xbox.com/play');
        } else if (isSteamVersion && steamCustomClient.getIsSteamDeck()){
            mainWindow.loadFile('assets/android_stream.html', { query: { isSteam: '1' } })
        } else {
            mainWindow.loadFile('assets/android_stream.html')
        }
    } else {
        const options = {
            type: 'warning',
            buttons: ['Ok'],
            title: 'Login Required',
            message: 'You must login to use remote play.',
            detail: 'Please login to the Microsoft account associated with your Xbox Live profile by pressing the Login button below.',
        };
        showDialog(options)
    }
}
function startGamepadOnly(event, args){
    if (!checkInFocus()){
        return;
    }

    const hasXHomeTokens = persistClient.get('serverId')
    if(args || hasXHomeTokens) {
        if(args){
            persistClient.save('serverId', args)
        }
        persistClient.returnXCloudConfig = false

        if (persistClient.getJSONKey('settings_use_official_web', 'settings_items')) {
            mainWindow.loadURL('https://www.xbox.com/play');
        } else if (isSteamVersion && steamCustomClient.getIsSteamDeck()){
            mainWindow.loadFile('assets/android_stream.html', { query: { controllerOnly: '1', isSteam: '1' } })
        } else {
            mainWindow.loadFile('assets/android_stream.html', { query: { controllerOnly: '1' } })
        }
    } else {
        const options = {
            type: 'warning',
            buttons: ['Ok'],
            title: 'Login Required',
            message: 'You must login to use remote play.',
            detail: 'Please login to the Microsoft account associated with your Xbox Live profile by pressing the Login button below.',
        };
        showDialog(options)
    }
}

async function closeApp(event = false, args = false){
    console.log('closeApp', args)
    if (steamCustomClient.getIsSteamDeck() || (args && args['forceQuit'])){
        forceQuit = true
        app.quit()
        return
    }

    const options = {
        type: 'warning',
        buttons: ['Exit App', 'Cancel'],
        title: 'Exit App',
        message: 'Would you like to exit the application?',
    };

    const response = await showDialog(options)

    if (response && response.response === 0){
        forceQuit = true
        app.quit()
    }
}
function autoLoginToggle(event, args){
    persistClient.save('auto_login', args['is_set'] ? 1 : 0)
}
function startGamepadBuilder(event){
    if (!checkInFocus()){
        return;
    }

    mainWindow.loadURL(`${HOSTNAME}/builder/controller_builder.html?customTypeList=standaloneControllerOption,gamestreamControllerOption`)
}

async function reset(event){
    const options = {
        type: 'warning',
        buttons: ['Continue', 'Cancel'],
        title: 'Clear Cache',
        message: 'This will clear all saved data and restart the app. Continue?',
    };

    const response = await showDialog(options)
    if (!response || response.response === 1){
        console.log('Cancel')
        return
    }

    persistClient.delete('gsToken')
    persistClient.delete('serverId')
    persistClient.delete('xCloudToken')
    persistClient.delete('msal')
    persistClient.delete('xCloudTitle')
    persistClient.delete('consoleSaved')
    persistClient.delete('pcheck')
    persistClient.delete('auto_login')
    persistClient.delete('regions')
    persistClient.delete('default-region')
    persistClient.delete('availableConsoles')
    persistClient.delete('selected-language')
    persistClient.delete('displayedSteamStartupPrompt')
    persistClient.delete('all_images')
    persistClient.delete('xcloud-region-name')
    persistClient.delete('xcloud-region')
    persistClient.delete('steamAccountIdV2')
    persistClient.delete('steamUserId64V2')
    persistClient.delete('fullscreenPopupCounter')
    persistClient.delete('last_token')
    persistClient.clear()

    session.defaultSession.clearStorageData([], function (data) {
        console.log(data);
    })

    await mainWindow.webContents.session.clearStorageData()

    const appDataPath = path.join(app.getPath('appData'), app.getName())
    if(!appDataPath){
        return
    }

    console.log('Clearing', appDataPath)
    deleteFolderRecursive(appDataPath)

    function deleteFolderRecursive (thePath) {
        let files = [];
        if( fs.existsSync(thePath) ) {
            files = fs.readdirSync(thePath);
            files.forEach(function(file,index){
                try {
                    let curPath = path.join(thePath, file);
                    if(fs.lstatSync(curPath).isDirectory()) { // recurse
                        deleteFolderRecursive(curPath);
                    } else { // delete file
                        fs.unlinkSync(curPath);
                    }
                } catch (err) {
                    console.log('Unable to remove', thePath, err)
                }
            });
            try {
                fs.rmdirSync(thePath);
            } catch (err){
                console.log('Unable to remove', thePath, err)
            }
        }
    };

    app.relaunch()
    app.exit()

}

async function handleCommandLineArgAutoStart(){
    if(commandLineArgUsed || commandLineArgLoginTries >= 3){
        return;
    }

    commandLineArgUsed = true // only auto start first time
    commandLineArgLoginTries++
    const startXHomeCheck = getCommandLineArg('xhome');
    const startXCloudCheck = getCommandLineArg('xcloud');

    // always delete xhome title after using it (deleted in persist client too).
    persistClient.delete('xhomeTitle')

    if (startXHomeCheck){
        console.log('Starting xHome', startXHomeCheck);
        if (startXHomeCheck && startXHomeCheck !== true){
            persistClient.save('xhomeTitle', startXHomeCheck)
        }
        startXHome()
    } else if (startXCloudCheck){
        console.log('Starting xCloud');
        startXCloud(null, startXCloudCheck)
    } else {
        console.log('No command line args set. Dont auto connect');
    }
}

app.on('window-all-closed', () => {
    // if (process.platform !== 'darwin')
    app.quit()
})

async function isConnectedToInternet(){
    try {
        const isOnline = (await import('is-online')).default;
        const online = await isOnline({timeout: 2000});
        console.log(online ? 'You are online' : 'You are offline');
        return online;
    } catch (err){
        return true
    }
}