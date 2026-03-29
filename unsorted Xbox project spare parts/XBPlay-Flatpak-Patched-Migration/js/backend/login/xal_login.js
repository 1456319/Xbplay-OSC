const EventEmitter = require('events');
const loginHelper = require("./login_status_events");
const RequestClient = require("../requests");
const persistHelper = require("../persist_client");
const {screen, BrowserWindow, app, ipcMain, session, dialog} = require("electron");
const path = require("path");
const os = require('os');
const isMac = os.platform() === "darwin";
const isWindows = os.platform() === "win32";
const isLinux = os.platform() === "linux";
const config = require('../../../config.js');

module.exports = class XalLoginClient extends EventEmitter {
    persistClient
    requestClient
    loginWindow
    redirectTmpData
    redirectTmpUri
    headerTmpRedirectLocation
    isSteamDeck
    constructor(hostname, isSteamDeck) {
        super();
        this.persistClient = new persistHelper()
        this.requestClient = new RequestClient(hostname)
        this.isSteamDeck = isSteamDeck

        app.whenReady().then(() => {
            ipcMain.on('login', (event, args) => { this.loginButtonClicked(event, args, false)})
            ipcMain.on('manual_login_clicked', (event, args) => { this.loginButtonClicked(event, args, true)})
            ipcMain.on('close-login', (event, args) => { this.handleCloseLogin(event, args)})
            ipcMain.on('xalTokenUpdateRequest', (event, args) => { this.handleXalTokenUpdateRequest(event, args)})
            ipcMain.on('tokens_received', (event, args) => { this.handleTokensReceivedFromQR(event, args)})

        });
    }

    async handleCloseLogin(event, args){
        const useXalLogin = this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !this.persistClient.getJSONKey('settings_login_type', 'settings_items')
        if(!useXalLogin){
            console.log('XAL login process ignoring login close')
            return
        }

        if(this.loginWindow){
            this.loginWindow.close()
            this.loginWindow = null
        }

        // not doing this because its weird? Tokens should only expire if havent used in 20+ days or so. This shouldnt happen often
        // this.emit("handleCommandLineArgAutoStart");
    }

    async loginButtonClicked(event, args, forceManual = true){
        const useXalLogin = this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !this.persistClient.getJSONKey('settings_login_type', 'settings_items')
        if(!useXalLogin){
            console.log('XAL login process ignoring login event')
            return
        }

        // happens if need to login while in a remote play session
        if(args && args['requiresReload']){
            console.log('requireReload')
            this.emit("quitGame");
        }

        this.emit('loginPageStateChange', {isVisible: 1})

        const xalTokens = this.getSavedXalTokens()
        if (!xalTokens){ // first time lookup,
            if (forceManual){
                await this.manualLoginClicked()
            } else {
                let loginPageUrl = config.hostname + '/steam/login/login_page.html'
                const uiLanguage = this.persistClient.get('ui-language')
                if (uiLanguage){
                    loginPageUrl += `?language=${uiLanguage}`
                }
                await this.openLoginWindow(loginPageUrl)
            }
        } else {
            console.log('Found existing XAL tokens, trying to refresh', xalTokens)
            await this.updateExistingXalTokens(xalTokens)
        }

        this.emit('loginPageStateChange', {isVisible: 0})
    }

    async manualLoginClicked(){
        console.log('looking up redirect URI')
        const redirectUriData = await this.requestClient.getXalTokensRequest(null)
        await this.handleRedirectUriResponse(redirectUriData)
        this.emit('loginPageStateChange', {isVisible: 0})
    }

    handleTokensReceivedFromQR(event, args){
        console.log('caught handleTokensReceivedFromQR', args)

        if (args?.data?.xalData?.xalTokens){
            const formattedData = {
                type: 'tokens',
                data: args?.data?.xalData
            }
            this.handleXalTokenResponse(formattedData, true)
        } else {
            this.emit('showToast', {message: 'Error 3487: Failed Getting Companion App Data', isSuccess: false})
        }

        this.safeCloseLoginWindow()
    }

    // gets called when frontend silently refreshes tokens. Need to save on our client side
    handleXalTokenUpdateRequest(event, args) {
        console.log('caught handleXalTokenUpdateRequest', args)

        if (args?.data?.type){
            this.handleXalTokenResponse(args.data, false)
        }
    }

    // happens if login button clicked and already have logged in
    async updateExistingXalTokens(xalTokens){
        console.log('updateExistingXalTokens')

        // request data
        const requestData = {
            data: {
                xalTokens: xalTokens
            }
        }
        // add region data to request
        const loginRegionIp = this.persistClient.getJSONKey('settings_login_region', 'settings_items')
        if(loginRegionIp){
            requestData.data['loginRegionIp'] = loginRegionIp
        }

        // send request
        const response = await this.requestClient.getXalTokensRequest(requestData)
        this.handleXalTokenResponse(response)
    }

    // happens when login window closes after first time login
    async exchangeRedirectUriForTokens(redirectURI){
        console.log('exchangeRedirectUriForTokens', redirectURI)
        // request data
        const requestData = {
            redirectURI: redirectURI,
            data: this.redirectTmpData
        }
        // add region data to request
        const loginRegionIp = this.persistClient.getJSONKey('settings_login_region', 'settings_items')
        if(loginRegionIp){
            requestData.data['loginRegionIp'] = loginRegionIp
        }

        // send request
        const response = await this.requestClient.getXalTokensRequest(requestData)
        // close the login window after we get exchange tokens. If it fails, it will re-open
        this.safeCloseLoginWindow()
        this.handleXalTokenResponse(response)
    }

    // opens to login window when no xal tokens exist
    async handleRedirectUriResponse(response){
        const url = response?.redirectURI
        const redirectData = response?.data
        const headerLocation = response?.data?.headerRedirectUriLocation

        if (url && redirectData && headerLocation){
            console.log('Received valid redirectURI data', url, redirectData)
            this.redirectTmpData = redirectData
            this.redirectTmpUri = url
            this.headerTmpRedirectLocation = headerLocation
            await this.openLoginWindow(url)
        } else {
            console.error('Invalid login redirect uri response', response)
            this.emit('showToast', {message: 'Error Logging In. Try Clearing Cache. 3402', isSuccess: false})
        }
    }

    safeCloseLoginWindow() {
        try {
            console.log('closing login window');
            if (this.loginWindow && !this.loginWindow.isDestroyed()) {
                this.loginWindow.close();
                this.loginWindow = null;
            } else {
                console.log('cant close login window, it doesnt exist')
            }
        } catch (err) {
            console.error('Error closing login window:', err);
        }
    }

    // gets called every time we need to update tokens
    handleXalTokenResponse(response, frontend = true){
        console.log('handleXalTokenResponse', JSON.stringify(response))
        if (response?.type === 'tokens'){
            const xalTokens = response?.data?.xalTokens
            const streamingTokens = response?.data?.streamingTokens
            const consoles = response?.data?.consoles
            const webToken = response?.data?.webToken

            if(!xalTokens || !streamingTokens){
                console.error('Invalid token exchange response')
                if(frontend) this.emit('showToast', {message: 'Error Logging In. Try Clearing Cache. 1039', isSuccess: false})

            } else {
                this.persistClient.save('xalTokens', JSON.stringify(xalTokens))

                // save all required data from response
                this.saveXHomeData(streamingTokens?.xHomeToken?.data?.gsToken)
                this.saveAvailableConsoles(consoles)
                this.saveXCloudData(streamingTokens?.xCloudToken?.data)
                this.saveMsalData(xalTokens?.userToken?.data?.refresh_token)
                this.saveWebTokenData(webToken)

                if(frontend) {
                    this.emit("handleCommandLineArgAutoStart", true);
                    this.emit('showToast', {message: 'Login Success', isSuccess: true})
                }
            }
        } else if (response?.type === 'error') {
            if (frontend) this.emit('showToast', {message: 'Error 9302: ' + response?.error, isSuccess: false})
        } else if (response?.type === 'redirect') {
            if (frontend) this.emit('showToast', {message: 'Login Expired. Re-Login: ', isSuccess: false})
            this.persistClient.delete('xalTokens') // clear tokens so loginButtonClicked creates login popup
            this.loginButtonClicked(null, null, true)
        } else {
            if(frontend) this.emit('showToast', {message: 'Unknown Response Type: ' + response?.type + '. Ensure connected to internet.', isSuccess: false})
        }
    }

    saveXHomeData(token){
        this.persistClient.save('gsToken', token)
    }
    saveMsalData(token){
        this.persistClient.save('msal', token)
    }
    saveWebTokenData(token){
        this.persistClient.save('webToken', JSON.stringify(token))
    }
    saveXCloudData(xCloudTokenData){
        const token = xCloudTokenData?.gsToken
        const regions = xCloudTokenData?.offeringSettings?.regions

        if (!token){
            console.log('No xcloud token!')
            return
        }

        this.persistClient.save('xCloudToken', token)

        // save region data
        this.persistClient.save('regions', JSON.stringify(regions))
        for (let i = 0; i < regions.length; i++){
            const item = regions[i];
            if(item['isDefault']){
                this.persistClient.save('default-region', item['baseUri'])
                return
            }
        }
    }
    saveAvailableConsoles(consoleResponse){
        let consolesResults = consoleResponse['results'] || []
        if (!consolesResults.length){
            console.log('Console Response', consoleResponse)
            this.emit('showToast', {message: 'No Xbox Console Found.<br>Enable "Remote Features" on your Console.', isSuccess: false})
            return false
        }

        // only update list of available consoles
        const ids = {}
        for(let i = 0; i < consolesResults.length; i++){
            let console = consolesResults[i]
            ids[`${console['deviceName']} (${console['serverId']})`] = console['serverId']
        }
        this.persistClient.save('availableConsoles', JSON.stringify(ids))
    }

    getSavedXalTokens() {
        try {
            const xalTokensString = this.persistClient.get('xalTokens')
            if (!xalTokensString) {
                return false
            }
            return JSON.parse(xalTokensString)
        } catch (err){
            return false
        }
    }

    async safeLoadUrl(url){
        try {
            await this.loginWindow.loadURL(url, {userAgent: 'Mozilla/5.0 (Linux; Android 7.0; SM-G930V Build/NRD90M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.125 Mobile Safari/537.36'});
        } catch (err) {
            console.error('Failed to load login URL:', err);
            this.emit('showToast', {
                message: 'Failed to load. Ensure you are connected to the internet.',
                isSuccess: false
            });

            // Optional: close or hide the window
            if (this.loginWindow && !this.loginWindow.isDestroyed()) {
                this.loginWindow.close();
                this.loginWindow = null;
            }
        }
    }
    async openLoginWindow(url){
        if (this.loginWindow && !this.loginWindow.isDestroyed()) {
            await this.safeLoadUrl(url)
            return
        }

        const { width, height } = screen.getPrimaryDisplay().workAreaSize;
        const loginSession = session.fromPartition('persist:loginWindow');

        this.loginWindow = await new BrowserWindow({
            show: true,
            width: 1280,
            height: 800,
            y: 25,
            x: (width / 2) - 640,
            frame: true,
            fullscreen: false,
            title: 'Login',
            titleBarStyle: 'hidden',
            webPreferences: {
                session: loginSession, // Use the new session
                preload: path.join(__dirname, './xal_login_preload.js'),
                nodeIntegration: false,
                contextIsolation: true,
            }
        })

        // to track this is login window so we dont setControls()
        this.loginWindow.isLoginWindow = true;

        this.loginWindow.webContents.on('did-finish-load', () => {
            if (this.isSteamDeck){
                this.handleKeyboardBug()
            }
        });

        loginSession.webRequest.onHeadersReceived({
            urls: [
                '*://*/*',
            ],
        }, async (details, callback) => {
            let redirectCode
            try {
                if (details.responseHeaders.Location !== undefined && details.responseHeaders.Location[0].includes(this.headerTmpRedirectLocation)){
                    redirectCode = details.responseHeaders.Location[0]
                } else if (details.responseHeaders['x-bare-headers']) { // gross fallback for proxy login
                    const codeValue = JSON.parse(details.responseHeaders['x-bare-headers'][0] || '{}')
                    if (codeValue['Location'] !== undefined && codeValue['Location'].includes(this.headerTmpRedirectLocation)){
                        redirectCode = codeValue['Location']
                    }
                }
            } catch (err){
                console.log('error getting redirectCode', err)
            }

            if(redirectCode){
                console.log('Got redirect URI from OAUTH:', redirectCode)
                await callback({ cancel: true });  // Cancel only the redirect, not the request

                this.emit('loginPageStateChange', {isVisible: 1})
                await this.exchangeRedirectUriForTokens(redirectCode)
                this.emit('loginPageStateChange', {isVisible: 0})
            } else {
                callback(details)
            }
        })

        // warn user if using proxy login
        if (url && url.includes('xbgamestreamproxy')){
            const options = {
                type: 'warning',
                buttons: ['Email Verification', 'Close'],
                title: 'Login Blocked',
                message: 'There was an issue logging in.',
                detail: `Most likely, Microsoft sent you an email to approve this login attempt. Please choose one of the following options:
            
1. Open the email, approve the login request, and then try again.

2. Press email verification and login again. This time, you will be prompted for email verification.`,
            };

            const result = await dialog.showMessageBox(null, options)
            if (result.response === 0){
                await this.loginWindow.loadURL(url, {userAgent: 'Mozilla/5.0 (Linux; Android 7.0; SM-G930V Build/NRD90M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.125 Mobile Safari/537.36'});
            } else {
                this.safeCloseLoginWindow()
            }
        } else {
            await this.safeLoadUrl(url)
        }

        loginSession.webRequest.onBeforeRequest({ urls: ['*://*/*'] }, (details, callback) => {
            try {
                const urlObj = new URL(details.url);
                if (urlObj.pathname === '/consumers/fido/get') {
                    console.log('Detected attempt to load passkey login path:', details.url);
                    if (isLinux || isMac) {
                        dialog.showMessageBox(this.loginWindow, {
                            type: 'warning',
                            buttons: ['OK'],
                            title: 'Passkey Unsupported',
                            message: 'This login method may not work correctly.',
                            detail: `We detected an attempt to use Passkey authentication, which may not be supported in this environment. If login fails, go back and use a different login method. You should only have to login once.`
                        });
                    }
                }
            } catch (e) {
                console.error('Error parsing login request URL', e);
            }
            callback({ cancel: false }); // Always continue the request
        });
    }

    handleKeyboardBug() {
        try {
            console.log('handle handleKeyboardBug')
            this.loginWindow.webContents.executeJavaScript(`            
                 const SteamOSKeyboardFix = {
                    processedAInputField: false,
                    DisableInputFields: function (){
                        try {
                            const inputs = document.querySelectorAll('input[type="email"], input[type="tel"], input[type="password"], input[type="text"], textarea');
                            
                            // Iterate over the NodeList and set each to readonly
                            inputs.forEach(input => {                                
                                if (input.hasAttribute('processed')) {
                                    return;
                                } else {
                                    console.log('Processing', input);
                                }
                                
                                input.setAttribute('processed', true);
                                input.setAttribute('readonly', true);
                                if (input.placeholder) {
                                    input.placeholder += ' (SteamKey+X to type)';
                                } else {
                                    input.placeholder = 'SteamKey+X to type';
                                }
                                
                                input.addEventListener('keydown', function (event) {
                                    try {
                                        let currentValue = input.value
                                        if (event.key === 'Backspace') {
                                            event.preventDefault();
                                            if (currentValue.length > 0) {
                                                currentValue = currentValue.slice(0, -1)
                                            }
                                        } else if (event.key.length === 1) {
                                            event.preventDefault();
                                            currentValue += event.key
                                            console.log('|' + currentValue + '|');
                                        } else {
                                            console.log('ignoring event: ', event);
                                        }
                        
                                        SteamOSKeyboardFix.setReactInputValue(input, currentValue);
                                    } catch (err){
                                        console.error('Error catching keyboard input', err)
                                    }
                                });
                            });
                        } catch (err){
                            console.error('Error disabling inputs', err)
                        }
                    },
                    
                    setReactInputValue: function (input, value) {
                        const previousValue = input.value;
                        
                        // eslint-disable-next-line no-param-reassign
                        input.value = value;
                        
                        const tracker = input._valueTracker;
                        if (tracker) {
                            tracker.setValue(previousValue);
                        }
                        
                        // 'change' instead of 'input', see https://github.com/facebook/react/issues/11488#issuecomment-381590324
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                };
                console.error('Calling: DisableInputFields');
                setInterval(() => {
                    SteamOSKeyboardFix.DisableInputFields();
                }, 50);
        `, true).then((result) => {
                console.log('Done handleKeyboardBug logic', result)
            }).catch((err)=>{
                console.error('Error running js script on handleKeyboardBug:', err)
            }) // nbd this will fail sometimes
        } catch (err){
            console.log(err);
        }
    }

}