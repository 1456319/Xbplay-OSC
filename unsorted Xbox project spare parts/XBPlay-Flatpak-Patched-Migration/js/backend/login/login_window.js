const { BrowserWindow, session, screen, app, ipcMain, Menu} = require('electron')
const loginHelper = require("./login_status_events");
const persistHelper = require('../persist_client.js')
const RequestClient = require("../requests");
const EventEmitter = require('events');
const path = require('path')
const config = require('../../../config.js');

module.exports = class LoginWindowClient extends EventEmitter {
    loginEvents
    loginWindow
    persistClient
    alreadyComplete
    lookupTimeout
    isSteamDeck

    constructor(hostname, isSteamDeck) {
        super();
        this.loginEvents = new loginHelper()
        this.persistClient = new persistHelper()
        this.requestClient = new RequestClient(hostname)
        this.alreadyComplete = false
        this.isSteamDeck = isSteamDeck

        app.whenReady().then(() => {
            ipcMain.on('login', (event, args) => { this.showLoginPage(event, args)})
            ipcMain.on('close-login', (event, args) => { this.handleCloseLogin(event, args)})
            ipcMain.on('hide-overlay-popup', (event, args) => {
                console.log('SHOW LOGIN PAGE')
                if (this.loginWindow){
                    this.loginWindow.show()
                }
            })
        });

        if(!config.useDev){
            Menu.setApplicationMenu(new Menu())
        }

    }

    // called via IPC Main
    async handleCloseLogin(event){
        if(this.loginWindow){
            this.loginWindow.close()
            this.loginWindow = null
        }
        this.emit("handleCommandLineArgAutoStart");

        //handleCommandLineArgAutoStart();
    }

    showLoginPage(event, args) {

        const useXalLogin = this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !this.persistClient.getJSONKey('settings_login_type', 'settings_items')
        if(useXalLogin){
            console.log('Legacy login process ignoring login event')
            return
        }

        if(args && args['requiresReload']){ // happens if need to login while in a remote play session
            console.log('requireReload')
            this.emit("quitGame");
            this.alreadyComplete = false
        }

        if (this.loginWindow){
            this.loginWindow.focus()
            this.handleLoginSelectButtonPress()
            console.log('Login window already created. Focus')
            return;
        }
        console.log('Started Login View', args)
        this.alreadyComplete = false

        // will only be set if login called from pressing the button, not from auto login
        // so we only update the xcloud-region-name when the user clicks the login button after changing a region
        if (args && args['xcloud-region-name']){
            this.persistClient.save('xcloud-region-name', args['xcloud-region-name'])
        }

        this.loginEvents = new loginHelper()

        const { width, height } = screen.getPrimaryDisplay().workAreaSize;
        const shouldShowWindow = this.persistClient.getJSONKey('settings_login_display', 'settings_items') !== 'background'

        this.loginWindow = new BrowserWindow({
            show: shouldShowWindow,
            width: 800,
            height: 600,
            y: 25,
            x: (width / 2) - 400,
            //alwaysOnTop: true,
            frame: true,
            fullscreen: false,
            title: 'Login',
            titleBarStyle: 'hidden',
            webPreferences: {
                // sandbox check
                preload: path.join(__dirname, './login_listeners.js'),
                nodeIntegration: false,
                contextIsolation: true,
                // devTools: true,
            }
        })

        // to track this is login window so we dont setControls()
        this.loginWindow.isLoginWindow = true;

        if (!shouldShowWindow){
            this.emit('showToast', {message: 'Logging In. Please Wait.', isSuccess: true})
            //this.loginWindow.hide() //this breaks things if i set this, so using 'show' in main options instead
        } else {
            this.loginWindow.show()
            this.loginWindow.setAlwaysOnTop(true)
        }
        this.loginWindow.webContents.on('did-finish-load', () => {
            console.log('Starting lookup timer');
            this.handlePrefillKey()
            this.lookupTimer()

            if (this.isSteamDeck){
                this.handleKeyboardBug()
            }
        });

        this.loginWindow.on('close', () => {
            console.log('Login window closed, aborting.')
            this.loginWindow = null
            clearInterval(this.lookupTimeout);
            this.emit('loginPageStateChange', {isVisible: 0})
        });

        this.loginWindow.loadURL('https://www.xbox.com/en-US/auth/msa?action=logIn&returnUrl=https%3A%2F%2Fwww.xbox.com%2Fen-US%2Fplay', {userAgent: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.6533.103 Mobile Safari/537.36'})
        this.emit('loginPageStateChange', {isVisible: 1})
    }

    lookupTimer() {
        clearInterval(this.lookupTimeout);
        this.lookupTimeout = setInterval(() => {
            try {
                if (!this.loginWindow){
                    clearInterval(this.lookupTimeout);
                    return
                }

                console.log('No valid GSToken yet, retry...');
                session.defaultSession.cookies.get({ url: 'https://www.xbox.com' })
                    .then((cookies) => {
                        this.handleStreamCookie(cookies)
                    }).catch((error) => {
                        console.log(error)
                    })
            } catch (err){
                console.log(err);
            }

        }, 3000); // this is basically a retry timeout now
    }

   handleLoginSelectButtonPress() {
        try {
            if (!this.loginWindow) {
                return;
            }
            const url = this.loginWindow.webContents.getURL()

            if (!url.includes('login.live.com/')){
                console.log('ignoring handleLoginSelectButtonPress')
                return;
            }

            console.log('handle handleLoginSelectButtonPress')
            this.loginWindow.webContents.executeJavaScript(`
            function handleLoginButtonPress() {
                try {
                    console.error('STARTING JS INJECTION: handleLoginButtonPress');
                    const submitButton = document.querySelector('input[type="submit"]');

                    if (!submitButton) {
                        console.warn('Could not find input fields');
                        return false;
                    }
                 
                    submitButton.focus();
                    submitButton.click();
                } catch (err) {
                    console.log(err);
                }
            }
            handleLoginButtonPress();

        `, true).then((result) => {
                console.log('Done helping with buttonpress logic', result)
            }).catch((err)=>{
                console.error('Error running js script on:', url, err)
            })
        } catch (err){
            console.log(err);
        }
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
    handlePrefillKey() {
        try {
            const url = this.loginWindow.webContents.getURL()

            if (!url.includes('login.live.com/')){
                console.log('ignoring prefilledKey')
                return;
            }

            console.log('handle prefilledKey')
            this.loginWindow.webContents.executeJavaScript(`
            let selectedAccount = false;            
            
            function bypassAccountSelector(){
                try {
                    const firstTableRow = document.querySelector('.pagination-view .table-row');
                    
                    if (firstTableRow) {
                        firstTableRow.click();
                        selectedAccount = true;
                    } else {
                        const listItems = document.querySelectorAll('[role="listitem"]');
                        for (let index = 0; index < listItems.length; index++){
                            const listItem = listItems[index];
                                                  
                            if (index === 0){
                                const title = listItem.childNodes[0].getAttribute('aria-describedby') || '';
                                if (!title || !title.toLowerCase().includes('pick')){
                                    continue;
                                }

                                const firstButtonInListItem = listItem.querySelector('button');
                                
                                if (firstButtonInListItem) {
                                    firstButtonInListItem.click();
                                    selectedAccount = true;
                                } else {
                                    console.log('no button in list view');
                                }
                            }
                        }
                    }
                } catch (err) {
                    console.log(err);
                }
            }
            
            function handlePrefillKey() {
                const prefilledKey = localStorage.getItem('prefilled_key');

                if(typeof window.loginAPI === 'undefined') {
                    window.loginAPI = {
                        send: (channel, data) => {
                            window.dispatchEvent(new CustomEvent(channel, { detail: data }));
                        }
                    };
                }

                try {
                    console.error('STARTING JS INJECTION: handlePrefillKey');
                    const prefillKeyField = document.querySelector('input[type="password"]');
                    const submitButton = document.querySelector('input[type="submit"]');

                    if (!prefillKeyField || !submitButton) {
                        //console.warn('Could not find input fields');
                        return false;
                    }

                    const originalMethod = submitButton.onclick;
                    submitButton.onclick = function (event) {
                        localStorage.setItem('prefilled_key', prefillKeyField.value);

                        if (originalMethod) {
                            originalMethod.call(this, event);
                        }
                    };

                    //window.dispatchEvent(new CustomEvent("showLoadingOverlay"));

                    if (prefilledKey) {
                        prefillKeyField.value = prefilledKey;
                        submitButton.focus();
                        //submitButton.click();
                    }

                    return true;
                } catch (err) {
                    console.log(err);
                    return false;
                }
            }

            let setValueOnceInterval = setInterval(() => {
                
                let worked = handlePrefillKey();
                if (worked) {
                    clearInterval(setValueOnceInterval);
                    console.log('worked, stop trying');
                }
                
                if (!selectedAccount) {
                    bypassAccountSelector();
                }
            }, 100);
        `, true).then((result) => {
                console.log('Done helping with prefill key logic', result)
            }).catch((err)=>{
                console.error('Error running js script on:', url, err)
            }) // nbd this will fail sometimes
        } catch (err){
            console.log(err);
        }
    }

    lookupMSALToken() {
        if (!this.loginWindow){
            return
        }

        const url = this.loginWindow.webContents.getURL()
        if (url.includes('login.live.com/')) { // ignore msal lookup on login screen
            return;
        }

        console.log('Looking for MSAL token at', url)
        this.loginWindow.webContents.executeJavaScript(`
            function getMsalDataInject() {
                let result = false;
                const items = localStorage;
                const keys = Object.keys(items);  
                
                 try {
                     console.error('STARTING JS INJECTION');
                     console.log(JSON.stringify(items));
                } catch (err) {
                    console.log(err);
                }
                      
                for(let i = 0; i < keys.length; i++){
                    try {
                        const key = keys[i];
                        const dataRaw = items[key];
                        let jsonData = JSON.parse(dataRaw);
                        console.log('LOCAL STORAGE ITEM', key, dataRaw);
                        if(jsonData &&
                                jsonData['credentialType'] &&
                                jsonData['credentialType'] === 'RefreshToken' &&
                                jsonData['environment'] == 'login.windows.net' &&
                                jsonData['secret']){
                                        result = jsonData['secret'];
                        }
                    } catch (err) {
                        console.log('Error', err);
                    }
                }

                if (result && window.loginAPI) {
                    window.loginAPI.send('msal', result);
                }
                return result;
            }
            getMsalDataInject();
        `, true).then((result) => {
            if (!result){
                console.log('======MSAL LOOKUP FAILED=======')
                console.warn('Could not get MSAL from ', url)
            } else {
                console.log('======MSAL LOOKUP COMPLETE=======', result)
                this.handleMsalFind(result)
            }
        }).catch((err)=>{
            console.error('Error running js script on:', url, err)
        }) // nbd this will fail sometimes
    }


    handleMsalFind(token) {
        console.log('handleMsalFind', token)
        this.persistClient.save('msal', token)

        this.loginEvents.msalStatus = this.loginEvents.status.complete
        this.checkIfComplete()
    }

    // handle API calls
    async handleStreamCookie(data){
        console.log('handleStreamCookie')
        let cookie = {};
        data.forEach(function(item) {
            const decodedName = decodeURIComponent(item.name)
            cookie[decodedName] = item.value;
        })
        if (!cookie['XBXXtkhttp://gssv.xboxlive.com/']){
            console.log("Could not find stream cookie yet...")
            return
        }
        console.log('Found stream cookie!')
        const streamCookieDecodedString = decodeURIComponent(cookie['XBXXtkhttp://gssv.xboxlive.com/']);
        try {
            const tokenJson = JSON.parse(streamCookieDecodedString)
            if (tokenJson){
                this.beginAPILookups(tokenJson['Token'] || tokenJson['tokenData']['token'])
            }
        } catch (err) {
            console.log(err)
        }
    }

    async safeHandleConsoleLookup(gsToken){
        // dont send duplicate requests, since we know gsToken is valid at this point, one failure will fail entire workflow
        if (this.loginEvents.consoleStatus !== this.loginEvents.status.waiting){ // only send request once
            console.log('safeHandleConsoleLookup', 'Ignoring duplicate API request')
            return false
        }
        this.loginEvents.consoleStatus = this.loginEvents.status.loading

        // get consoles
        const consoles = await this.requestClient.getConsoles(gsToken)
        if (!consoles){
            console.log('======CONSOLE LOOKUP FAILED=======')
            this.loginEvents.consoleStatus = this.loginEvents.status.failed
            this.emit('showToast', {message: 'Failed getting consoles. Enable "Remote Features" on your console.', isSuccess: false})
        } else {
            console.log('======CONSOLE LOOKUP COMPLETE=======')
            this.showConsoleSelect(consoles)
        }

        this.checkIfComplete()
    }

    async safeHandleGSLookup(token) {
        if (this.loginEvents.gsTokenStatus === this.loginEvents.status.loading){ // dont send duplicate requests
            console.log('safeHandleGSLookup', 'Ignoring duplicate API request')
            return false
        } else if (this.loginEvents.gsTokenStatus === this.loginEvents.status.complete) { // if already completed, send saved value
            return this.persistClient.get('gsToken')
        }

        this.loginEvents.gsTokenStatus = this.loginEvents.status.loading
        const gsToken = await this.requestClient.getGSToken(token)
        if (!gsToken){
            console.log('======GSTOKEN LOOKUP FAILED=======')
            this.loginEvents.gsTokenStatus = this.loginEvents.status.failed
            this.loginEvents.gsTokenFailureCount++ // only allow 3 failures before giving up
            this.checkIfComplete()
            return false
        } else {
            console.log('======GSTOKEN LOOKUP COMPLETE=======')
            this.persistClient.save('gsToken', gsToken)
            this.loginEvents.gsTokenStatus = this.loginEvents.status.complete
        }
        return gsToken
    }

    async safeHandleXCloudLookup(token) {
        // dont send duplicate requests, since we know gsToken is valid at this point, one failure will fail entire workflow
        if (this.loginEvents.xCloudTokenStatus !== this.loginEvents.status.waiting){
            console.log('safeHandleXCloudLookup', 'Ignoring duplicate API request')
            return false
        }
        this.loginEvents.xCloudTokenStatus = this.loginEvents.status.loading

        // getxCloudToken
        const defaultRegion = this.persistClient.get('xcloud-region-name');
        const forwardIp = this.getIpFromCustomRegion(defaultRegion)
        console.log('Using forwardIp', forwardIp)

        const xcloudToken = await this.requestClient.getXCloudToken(token, forwardIp, true)
        if (!xcloudToken || !xcloudToken['gsToken']){
            console.log('======XCLOUD LOOKUP FAILED=======')
            console.error('Failed to extract xcloudToken', xcloudToken)
            this.loginEvents.xCloudTokenStatus = this.loginEvents.status.failed
            // if we fail to get xCloudToken, no need to wait for msal token (as xcloud won't work anyway)
            if(this.loginEvents.msalStatus === this.loginEvents.status.waiting){
                this.loginEvents.msalStatus = this.loginEvents.status.failed
            }
            if (xcloudToken && xcloudToken['InvalidCountry']){
                this.emit('showToast', {message: 'InvalidCountry: Select a specific region to bypass.', isSuccess: false})
            }
        } else {
            console.log('======XCLOUD LOOKUP COMPLETE=======')
            this.persistClient.save('xCloudToken', xcloudToken['gsToken'])
            this.saveRegions(xcloudToken)

            this.loginEvents.xCloudTokenStatus = this.loginEvents.status.complete
        }

        this.checkIfComplete()
    }

    async beginAPILookups(token){
        if(this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xhome_only'){
            this.loginEvents.xCloudTokenStatus = this.loginEvents.status.complete
            this.loginEvents.msalStatus = this.loginEvents.status.complete
        } else if (this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xcloud_only'){
            this.loginEvents.consoleStatus = this.loginEvents.status.complete
        }

        const gsToken = await this.safeHandleGSLookup(token) //happens first and blocks
        if (gsToken) {
            // these two happen 3nd and dont block one another
            this.safeHandleConsoleLookup(gsToken)
            this.safeHandleXCloudLookup(token)
            this.lookupMSALToken();
        }
    }

    getIpFromCustomRegion(desiredCustomRegion) {
        console.log('getIpFromCustomRegion', desiredCustomRegion)
        try {
            if (!desiredCustomRegion || desiredCustomRegion === "default") {
                return "4.2.2.2";
            } else if (desiredCustomRegion.includes("Australia")) {
                return "203.41.44.20";
            } else if (desiredCustomRegion.includes("Brazil")) {
                return "200.221.11.101";
            } else if (desiredCustomRegion.includes("Europe") || desiredCustomRegion.includes("UK")){
                return "194.25.0.68";
            } else if (desiredCustomRegion.includes("Japan")) {
                return "122.1.0.154";
            } else if (desiredCustomRegion.includes("Korea")) {
                return "203.253.64.1";
            } else if (desiredCustomRegion.includes("US") || desiredCustomRegion.includes("Us")) {
                return "4.2.2.2";
            } else {
                return "4.2.2.2";
            }
        } catch (err){
            console.log(err);
        }
        return "4.2.2.2";
    }

    saveRegions(data){
        const regions = data['offeringSettings']['regions']
        if (!regions){
            console.error('Cant get regions, explode!')
            return;
        }
        console.log(JSON.stringify(regions))
        this.persistClient.save('regions', JSON.stringify(regions))

        for (let i = 0; i < regions.length; i++){
            const item = regions[i];
            if(item['isDefault']){
                this.persistClient.save('default-region', item['baseUri'])
                return;
            }
        }
    }

    async showConsoleSelect(consoleResponse){
        let consolesResults = consoleResponse['results'] || []
        if (!consolesResults.length){
            console.log('Console Response', consoleResponse)
            this.loginEvents.consoleStatus = this.loginEvents.status.failed
            this.checkIfComplete()
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

        // indicate complete
        this.loginEvents.consoleStatus = this.loginEvents.status.complete
        this.checkIfComplete()
    }

    checkIfComplete(){
        const result = this.loginEvents.checkIfComplete()
        if(this.alreadyComplete){
            console.log('Login process already completed. Ignoring duplicate message');
        } else if (result === this.loginEvents.status.complete){
            if(this.loginWindow){
                this.loginWindow.close()
                this.loginWindow = null
                clearInterval(this.lookupTimeout);
            }
            //this.emit("loadMainViewData");
            if(this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xhome_only'){
                if (this.loginEvents.consoleStatus === this.loginEvents.status.failed) {
                    this.emit('showToast', {message: `Login Status: Failed<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: ${this.loginEvents.consoleStatus}<br>XCloudToken: Skipped<br>MSAL: Skipped`, isSuccess: false})
                } else {
                    this.emit('showToast', {message: `Login Status: Success<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: ${this.loginEvents.consoleStatus}<br>XCloudToken: Skipped<br>MSAL: Skipped`, isSuccess: true})
                }
            } else if (this.persistClient.getJSONKey('settings_login_type', 'settings_items') === 'xcloud_only'){
                if (this.loginEvents.xCloudTokenStatus === this.loginEvents.status.failed){
                    this.emit('showToast', {message: `No Game Pass Ultimate Subscription. Cloud Gaming Unavailable.`, isSuccess: false})
                    this.emit('showToast', {message: `Login Status: Failed<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: Skipped<br>XCloudToken: ${this.loginEvents.xCloudTokenStatus}<br>MSAL: ${this.loginEvents.msalStatus}`, isSuccess: false})
                } else {
                    this.emit('showToast', {message: `Login Status: Success<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: Skipped<br>XCloudToken: ${this.loginEvents.xCloudTokenStatus}<br>MSAL: ${this.loginEvents.msalStatus}`, isSuccess: true})
                }
            } else {
                if (this.loginEvents.xCloudTokenStatus === this.loginEvents.status.failed){
                    this.emit('showToast', {message: `No Game Pass Ultimate Subscription. Cloud Gaming Unavailable.`, isSuccess: false})
                }

                if (this.loginEvents.xCloudTokenStatus === this.loginEvents.status.failed && this.loginEvents.consoleStatus === this.loginEvents.status.failed){
                    this.emit('showToast', {message: `Login Status: Failed<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: ${this.loginEvents.consoleStatus}<br>XCloudToken: ${this.loginEvents.xCloudTokenStatus}<br>MSAL: ${this.loginEvents.msalStatus}`, isSuccess: false})
                } else {
                    this.emit('showToast', {message: `Login Status: Success<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: ${this.loginEvents.consoleStatus}<br>XCloudToken: ${this.loginEvents.xCloudTokenStatus}<br>MSAL: ${this.loginEvents.msalStatus}`, isSuccess: true})
                }
            }

            this.emit("handleCommandLineArgAutoStart", true);
            this.alreadyComplete = true
        } else if (result === this.loginEvents.status.failed){
            if(this.loginWindow){
                this.loginWindow.close()
                this.loginWindow = null
                clearInterval(this.lookupTimeout);
            }
            this.emit('showToast', {message: `Login Status: Failed<br>GSToken: ${this.loginEvents.gsTokenStatus}<br>Consoles: ${this.loginEvents.consoleStatus}<br>XCloudToken: ${this.loginEvents.xCloudTokenStatus}<br>MSAL: ${this.loginEvents.msalStatus}`, isSuccess: false})
            this.alreadyComplete = true
        }
    }
}