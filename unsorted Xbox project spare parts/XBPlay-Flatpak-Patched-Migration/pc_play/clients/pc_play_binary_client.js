const path = require('path');
const fsExtra = require("fs-extra");
const fs = require('fs');
const os = require('os');
const globalConfigs = require('../../config.js');
const { app, clipboard, dialog } = require('electron')
const child_process = require('child_process');
const axios = require('axios').default
const decompress = require("decompress");
const persistHelper = require('../../js/backend/persist_client')
const { ipcMain } = require('electron');
const tar = require('tar');
const fsPromise = require('fs').promises;
const semver = require('semver');
const steamArtworkHelper = require('../../js/backend/steam_artwork_helper')

let isActivelyDownloading = false

// versions
const isMac = os.platform() === "darwin";
const isWindows = os.platform() === "win32";
const isLinux = os.platform() === "linux";
const isArchLinux = os.platform() === 'linux' && os.release().toLowerCase().includes('arch');

// only used if isSteamVersion = false
module.exports = class PCPlayBinaryClient {
    binaryFolder
    binaryZip
    binaryExtractedFolder
    parentWindow
    settingsItemsWithKeys = ['fps', 'bitrate', 'display-mode', 'audio-config', 'capture-system-keys', 'video-codec', 'video-decoder' ]
    isCommandLineArgsStart = false
    showedVersionError = false

    constructor() {
        this.binaryFolder = path.join(app.getPath('userData'), 'binary');
        this.binaryZip = path.join(this.binaryFolder, 'raw.tar.gz');
        this.binaryExtractedFolder = path.join(this.binaryFolder, 'extracted');
        this.persistClient = new persistHelper()

        if (isArchLinux){
            this.binaryFolder = path.join(app.getPath('userData'), '..', 'PCPlayBin');
            this.binaryExtractedFolder = path.join(this.binaryFolder, 'bin');
        }
        console.log('PCPlayClient', this.getBinaryPath())
    }


    /////////// PUBLIC ////////////
    ////////////////////////////////
    setWindow(window){
        this.parentWindow = window
    }

    showSavedHosts() {
        console.log('Show Saved ML Hosts', this.getSavedHosts())
        this.sendFrontendMessage('load_saved_hosts',  this.getSavedHosts())
        this.updateClientInstalledText()
    }

    async uninstallPCBinaryClient() {
        try {
            if (fs.existsSync(this.binaryZip)) {
                console.log('Deleting...', this.binaryZip);
                fs.unlinkSync(this.binaryZip);
            }
            if (fs.existsSync(this.binaryFolder)) {
                console.log('Deleting...', this.binaryFolder);
                fsExtra.removeSync(this.binaryFolder) // need to use this to delete a folder
            }

            this.sendFrontendMessage('show_toast',{
                message: 'Deleted Client',
                isSuccess: false
            })
        } catch (err){
            console.error('Error uninstalling bin file', err)
        }

        await this.updateClientInstalledText()
    }
    async setupPCBinaryFile(){
        try {
            if(isActivelyDownloading){
                console.log('Dont download twice')
                return
            }
            if (isArchLinux){
                await this.setupArchLinuxPkg()
            } else {
                const outputZip = await this.downloadBinary()
                await this.extractTarGz(outputZip, this.binaryExtractedFolder);
                await this.updateClientInstalledText()
                this.sendFrontendMessage('show_toast',{
                    message: 'Install Complete. Add a Host',
                    isSuccess: true
                })
            }
        } catch (err){
            this.sendFrontendMessage('show_toast',{
                message: 'Install Failed',
                isSuccess: false
            })
            console.error('Error downloading bin file', err)
        }
    }

    async setupArchLinuxPkg(){
        if (fs.existsSync(this.binaryExtractedFolder)) {
            console.log('Deleting...', this.binaryExtractedFolder);
            fsExtra.emptyDirSync(this.binaryExtractedFolder)
            fsExtra.removeSync(this.binaryExtractedFolder)
        }
        const steamArtworkClient = new steamArtworkHelper(null)
        const buildFile = path.join(__dirname + '../../..', 'assets', 'pc_play', 'PKGBUILD');
        await steamArtworkClient.copyFile(buildFile, path.join(this.binaryExtractedFolder, 'PKGBUILD'));
        await this.replaceStringInFile(path.join(this.binaryExtractedFolder, 'PKGBUILD'), '<REPLACE_URL>', this.getBinaryUrl())

        const text = 'cd "' + this.binaryExtractedFolder + '" && makepkg -s --noconfirm'
        dialog.showMessageBox({
            title: 'Hi Arch Linux User',
            message: `The PCPlay feature is not fully functional on Arch Linux yet. However, you can try to install it.\n\nTo install the PCPlay client on Arch Linux you must run the following command in the Konsole: \n\n${text}`,
            buttons: ['Copy Command to Clipboard']
        }).then((result) => {
            console.log('Dialog closed with button index:', result.response);
            clipboard.writeText(text);
        }).catch((error) => {
            console.error('Error displaying dialog:', error);
        });
    }

    async replaceStringInFile(filePath, searchString, replacement) {
        return new Promise((resolve, reject) => {
            fs.readFile(filePath, 'utf8', (err, data) => {
                if (err) {
                    reject(err);
                    return;
                }

                // Replace the first occurrence of the search string
                const updatedContent = data.replace(searchString, replacement);

                // Write the updated content back to the file
                fs.writeFile(filePath, updatedContent, 'utf8', (err) => {
                    if (err) {
                        reject(err);
                        return;
                    }
                    resolve();
                });
            });
        });
    }

    removeSavedHost(hostName) {
        const existingHostData = this.getSavedHosts()

        if (existingHostData[hostName] !== undefined) {
            delete existingHostData[hostName]
            this.persistClient.save('hostToDisplayMap', JSON.stringify(existingHostData))
            console.log('Removed Host', hostName, existingHostData)
        } else {
            console.log('Cant remove non existent host', hostName, existingHostData)
        }

        this.persistClient.delete('pc_play_app_list_' + hostName);
    }

    getSettingsString(settingsConfig) {
        let optionsString = ''
        let optionsArray = []

        const keys = Object.keys(settingsConfig)
        for (let i = 0; i < keys.length; i++){
            const key = keys[i]
            const value = settingsConfig[key]

            // ignore any default values
            if (value === 'default' || key.startsWith('pcPlay')){ // pcPlay keys are for my settings.
                console.log('ignore default setting', key)
                continue
            }

            // if value is a 'no-' value (will never have subsequent key or in the list of params that dont have a key
            if (this.settingsItemsWithKeys.includes(key)){
                optionsString += ` --${key} ${value}`
                optionsArray.push(`--${key}`)
                optionsArray.push(value)
            } else {
                optionsString += ` --${value}`
                optionsArray.push(`--${value}`)
            }
        }
        return {
            stringValue: optionsString,
            arrayValue: optionsArray
        }
    }

    checkVersionForUpdates() {
        this.runScript(this.getBinaryPath(), ['-pc_play_version'], this.scriptResultHandler, {
            id: 'version'
        })
    }

    startStream(host, app) {
        this.sendFrontendMessage('set_loading_visibility', {
            isLoading: true
        });

        const options = JSON.parse(this.persistClient.get('pc_play_settings_items') ?? '{}');

        let optionsArray = this.getSettingsString(options).arrayValue
        console.log('optionsString', optionsArray)
        this.runScript(this.getBinaryPath(), [...['stream', host, app], ...optionsArray], this.scriptResultHandler, {
            id: 'stream'
        })
    }
    listApps(hostName){

        const appListData = this.getSavedAppList(hostName)

        if (!appListData || !Object.keys(appListData).length){
            this.sendFrontendMessage('show_toast', {
                message: 'No games found. Press "Sync" to update.',
                isSuccess: false
            })
        } else {
            this.showAppPickerScreen(appListData, hostName)
        }
    }

    pairHost(hostName, displayName) {
        this.sendFrontendMessage('set_loading_visibility', {
            isLoading: true
        });

        this.runScript(this.getBinaryPath(), ['pair', hostName], this.scriptResultHandler, {
            id: 'pair',
            hostName: hostName,
            displayName: displayName
        })
    }

    openHostClient(hostName) {
        this.sendFrontendMessage('set_loading_visibility', {
            isLoading: true
        });

        this.runScript(this.getBinaryPath(), null, this.scriptResultHandler, {
            id: 'open',
            hostName: hostName,
        })
    }

    async updateClientInstalledText() {
        let result = await this.getIsClientInstalled()
        this.sendFrontendMessage('update_pc_play_installed_text', {isInstalled: result})
    }

    async getIsClientInstalled(){
        let result
        try {
            const fileExists = async path => !!(await fs.promises.stat(path).catch(e => false));
            result = await fileExists(this.getBinaryPath());
        } catch (err) {
            console.error('File does not exist', err);
            result = false
        }
        return result
    }
    /////////// PRIVATE ////////////
    ////////////////////////////////

    escapeCommand(input) {
        return JSON.stringify(input)
    }

    getBinaryUrl() {
        if (isMac){
            return 'https://www.dropbox.com/scl/fi/nssloexlt3xig3na4g4t1/mac_bin.tar.gz?rlkey=49jh5owvxb4ddhwt8y4w75atc&st=8llyv2x5&dl=1'
        } else if (isArchLinux){ // order is important, must be after linux
            return 'https://www.dropbox.com/scl/fi/1wg805tvb1bjpv95rd2jg/PCPlaySrc-r33-x86_64.tar.gz?rlkey=z8vs7sdmeyri4ql1l6pdhbyhc&st=wnco62qg&dl=1'
        } else if (isLinux){ // order is important, must be before archlinux
            return 'https://www.dropbox.com/scl/fi/khl8caxjl4zx7mvwnjumj/linux_bin.tar.gz?rlkey=vzxb0haauqqbpkc1wcjsnqzdx&st=fldmvs2a&dl=1'
        } else if (isWindows){
            //arm64, x64, ia32(intel 32 bit), arm (arm 32 bit)
            if(os.arch() === 'arm64'){ // 64 bit arm
                return 'https://www.dropbox.com/scl/fi/aeiztld8x3nxvz988w96h/windows-arm64_bin.tar.gz?rlkey=uqf6aq2b9901tgns0e5e3totx&st=99ycbk1a&dl=1'
            } else if (os.arch() === 'ia32') { // 32 bit intel
                return 'https://www.dropbox.com/scl/fi/sf6nofn3i6e45i74obfhb/windows-x86_bin.tar.gz?rlkey=n36cwixgtz0wndnphv94qscy3&st=yh8tw7g5&dl=0'
            } else { // 64 bit intel
                return 'https://www.dropbox.com/scl/fi/jrz3ao058itqpuh6dgcbc/windows-x64_bin.tar.gz?rlkey=v2czfrq2n2j4xf88bj5fhqlac&st=pbmynhvj&dl=1'
            }
        } else {
            console.error('Unsupported OS')
            this.sendFrontendMessage('show_toast', {
                message: 'Failed to get client. Unsupported OS: ' + os.platform(),
                isSuccess: false
            })
        }
    }
    getBinaryPath(){
        if (isMac){
            return path.join(this.binaryExtractedFolder, 'PCPlay.app/Contents/MacOS/Moonlight')
        } else if (isArchLinux){ // order is important, before linux
            return path.join(this.binaryExtractedFolder, 'pkg', 'pcplay', 'usr', 'bin', 'moonlight')
        } else if (isLinux){ // order is important, after archlinux
            return path.join(this.binaryExtractedFolder, 'squashfs-root/AppRun')
        } else if (isWindows){
            return path.join(this.binaryExtractedFolder, 'PCPlay/Moonlight.exe')
        } else {
            console.error('Unsupported OS')
        }
    }

    sendFrontendMessage(title, data){
        if(this.parentWindow != null && !this.parentWindow.isDestroyed() && this.parentWindow.webContents != null) {
            console.log('sendFrontendMessage', title, data)
            this.parentWindow.webContents.send(title, data);
        } else {
            console.error('Unable to send frontend message', title, data)
        }
    }

    // get saved data commands
    getSavedHosts() {
        const hostData = this.persistClient.get('hostToDisplayMap') || '{}'
        return JSON.parse(hostData)
    }

    addSavedHost(newHost, displayName) {
        const existingHostData = this.getSavedHosts()

        const existingHosts = Object.keys(existingHostData)
        if (!existingHosts.includes(newHost)) {
            existingHostData[newHost] = (displayName && displayName !== '' ) ? displayName : newHost
            this.persistClient.save('hostToDisplayMap', JSON.stringify(existingHostData))
            console.log('Added Host', existingHostData)
            this.showSavedHosts()
        }
    }

    showAppPickerScreen(appList, hostname){
        ipcMain.emit('show_app_picker', {
            appList: appList,
            hostName: hostname
        });
    }

    showVersionError(actual, expected){
        if (this.showedVersionError){
            return
        }
        this.showedVersionError = true
        this.sendFrontendMessage('show_toast', {
            message: `PCPlay Client Outdated. Re-Install Client in Settings<br>${actual ?? 'NA'} < ${expected}`,
            isSuccess: false
        })
    }

    scriptResultHandler = (title, msg, config) => {
        if (this.isIgnorableMessage(title, msg)){
            console.log('IGNORE:', title, msg, config)
            return;
        }
        console.log('PROCESS', title, msg)

        if (title === 'close'){
            this.sendFrontendMessage('set_loading_visibility', {
                isLoading: false
            });
            if (msg == 127 || msg == -2){
                this.sendFrontendMessage('show_toast',{
                    message: 'Missing PC Play Client. Download it via Settings->Re-Install Client first.',
                    isSuccess: false
                })
            }
            return
        }

        // {type=listApp, message=Failed, data=name,id,pic} /
        const messageData = this.extractPcPlayMessage(msg)

        const id = config['id']
        switch(id){
            case 'version':
                try {
                    const actualVersion = messageData['message']
                    console.log('actualVersion', actualVersion)

                    if (!actualVersion || semver.valid(actualVersion)) {
                        console.log('Actual Version Valid', actualVersion)
                        if (semver.lt(actualVersion, globalConfigs.expectedPCPlayVersion)) {
                            this.showVersionError(actualVersion, globalConfigs.expectedPCPlayVersion)
                        }
                    } else {
                        console.log('Invalid PCPlay Version', actualVersion)
                        // this.showVersionError('N/A', globalConfigs.expectedPCPlayVersion)
                    }
                } catch (err){
                    console.log('Error reading PCPlay Version')
                    // this.showVersionError('NA', globalConfigs.expectedPCPlayVersion)
                }
                break;
            case 'pair':
                console.log('Pair Script Response:', title, msg)
                if (messageData['message'] === 'START') {
                    this.sendFrontendMessage('set_loading_visibility', {
                        isLoading: true
                    });
                } else if (messageData['message'] === 'HOSTS_DISCOVERED') {
                    const hosts = messageData['data']
                    for (let i = 0; i < hosts.length; i++){
                        const host = hosts[i]
                        this.addSavedHost(host['Name'], config['Name'])
                    }

                } else if (messageData['message'] === 'APPLIST') {
                    this.addSavedHost(config['hostName'], config['displayName'])

                    const appList = this.extractListAppData(messageData['data'])
                    console.log('App List', appList)

                    if(Object.keys(appList).length){
                        this.saveAppList(config['hostName'], appList)
                        this.sendFrontendMessage('show_toast',{
                            message: 'Synced ' + Object.keys(appList).length + ' games from: ' + config['hostName'],
                            isSuccess: true
                        })
                    } else {
                        this.sendFrontendMessage('show_toast',{
                            message: 'Press "Sync" update games list.',
                            isSuccess: false
                        })
                    }
                } else if (messageData['message'] === 'FAILED') {
                    this.sendFrontendMessage('show_toast',{
                        message: 'Pair Failed',
                        isSuccess: false
                    })
                    this.sendFrontendMessage('set_loading_visibility', {
                        isLoading: false
                    });
                } else if (messageData['message'] === 'TIMEOUT') {
                    this.sendFrontendMessage('show_toast',{
                        message: 'Pair Timeout. Failed to connect.',
                        isSuccess: false
                    })
                    this.sendFrontendMessage('set_loading_visibility', {
                        isLoading: false
                    });
                } else if (messageData['message'] === 'COMPLETED') {
                    // add saved host
                    this.addSavedHost(config['hostName'], config['displayName'])

                    this.sendFrontendMessage('show_toast',{
                        message: 'Sync Complete.',
                        isSuccess: true
                    })
                    this.sendFrontendMessage('set_loading_visibility', {
                        isLoading: false
                    });
                } else {
                    console.error('Unsupported pair response', messageData)
                }
                break;
            case 'stream':
                if (messageData['message'] === 'START'){
                    this.sendFrontendMessage('set_loading_visibility', { isLoading: true });

                    if (this.isCommandLineArgsStart){
                        ipcMain.emit('close_app', null, {forceQuit: true})
                    }
                } else if (messageData['message'] === 'COMPLETED'){
                    this.sendFrontendMessage('set_loading_visibility', { isLoading: false });

                    // close app
                    const quitOnStartStream = this.persistClient.getJSONKey('pcPlayCloseOnStart', 'pc_play_settings_items') === true
                    if (quitOnStartStream){
                       this.sendFrontendMessage('close_app', {forceQuit: true})
                    }
                } else if (messageData['message'] === 'NOT_PAIRED'){
                    this.sendFrontendMessage('set_loading_visibility', { isLoading: false });
                    this.sendFrontendMessage('show_toast',{
                        message: 'Not Paired. Press "Sync" and re-pair.',
                        isSuccess: false
                    })
                } else if (messageData['message'] === 'QUIT_FAILED'){
                    this.sendFrontendMessage('set_loading_visibility', { isLoading: false });

                    this.sendFrontendMessage('show_toast',{
                        message: 'Failed to quit. Restart Sunshine on PC.',
                        isSuccess: false
                    })
                } else if (messageData['message'] === 'FAILED'){
                    this.sendFrontendMessage('set_loading_visibility', { isLoading: false });
                    this.sendFrontendMessage('show_toast',{
                        message: 'Failed to connect to PC.',
                        isSuccess: false
                    })
                } else if (messageData['message'] === 'INVALID_APP'){
                    this.sendFrontendMessage('set_loading_visibility', { isLoading: false });
                    this.sendFrontendMessage('show_toast',{
                        message: 'Invalid Game Selected.',
                        isSuccess: false
                    })
                } else {
                    console.error('Unsupported stream response', messageData)
                }
                break;
            default:
                console.error('Unsupported script result id', id)
        }
    }

    extractPcPlayMessage(msg){
        const response = {
            'type': null,
            'message': null,
            'data': null
        }
        try {
            let data = JSON.parse(msg)
            response ['type'] = data['type']; // list app
            response['message'] = data['message']; // result/failed/not_paired
            response['data'] = (data['data']) ? JSON.parse(data['data']) : null // 123, 123, 123, 213
        } catch (err){
            console.log('JSON Error', `'${msg}'`)
        }
        return response
    }
    isIgnorableMessage(title, msg, config) {
        try {
            if (msg && title !== 'close' && typeof msg === "string") { // process all version messages
                if (msg.includes("Redirecting log output")){ // always ignore this, need to for version to work
                    return true
                } else if (config['id'] === 'version'){ // always process version messages
                    return false
                } else if (!msg.includes('"id":"PCPLAY"')) {
                    return true
                }
            } else {
                return false
            }
        } catch (err){
            return false
        }
        return false
    }

    saveAppList(hostname, appList){
        console.log('updated app list')
        this.persistClient.save('pc_play_app_list_' + hostname, JSON.stringify(appList))
    }

    getSavedAppList(hostname){
        const data = this.persistClient.get('pc_play_app_list_' + hostname)
        if (data){
            return JSON.parse(data)
        }
        return {}
    }

    extractListAppData(data) {
        console.log('extractListAppData', data)
        const output = {};
        let emptyImageCount = 0;
        for (let i = 0; i < data.length; i++) {
            const appData = data[i];
            const name = appData['Name'];
            const id = appData['ID'];
            let image = appData['Boxart URL'];

            if (image === 'qrc:/res/no_app_image.png'){
                emptyImageCount++
                image = '../../assets/pc_play/no_image_found_image.png'
            }

            if(name && id && name !== 'Name'){
                output[id] = {
                    id: id,
                    name: name,
                    image:image
                }
            }
        }
        if(data.length > 0 && emptyImageCount === data.length){
            setTimeout(() => {
                this.sendFrontendMessage('show_toast',{
                    message: 'Failed to load some images. You may need to press \'Sync\' or restart Sunshine',
                    isSuccess: true
                })
            }, 3000)
        }
        return output
    }

    runScript(command, args, callback, config) {
        console.log("command", command, args)

        // need to remove LD_LIBRARY_PATH for pcplay to work on oled deck
        const env = process.env
        delete env['LD_LIBRARY_PATH']

        const child = child_process.spawn(command, args, {
            encoding: 'utf8',
            shell: false,
            detached: true,
            env: env
        });

        // You can also use a variable to save the output for when the script closes later
        child.on('error', async (error) => {
            console.log('Script Error', error)
            callback('error', error, config)
        });

        child.stdout.setEncoding('utf8');
        child.stdout.on('data', (data) => {
            //Here is the output
            data=data.toString();
            // console.log('stdout', data);
            callback('stdout', data, config)
        });

        child.stderr.setEncoding('utf8');
        child.stderr.on('data', (data) => {
            //Here is the output from the command
            data=data.toString();
            // console.log('stderr', data);
            callback('stderr', data, config)
        });

        child.on('close', async (code) => {
            code = code ?? '-1'
            callback('close', code.toString(), config)
        });
    }

    // download and startup bin here //////
    async downloadBinary() {
        this.sendFrontendMessage('show_toast',{
            message: 'Downloading Client. Please Wait.',
            isSuccess: true
        })
        return new Promise(async (resolve, reject) => {
            let binaryUrl = this.getBinaryUrl()

            isActivelyDownloading = true
            if (!fs.existsSync(this.binaryFolder)) {
                console.log('Binary folder DNE, creating');
                fs.mkdirSync(this.binaryFolder);
            }
            const { data, headers } = await axios({
                url: binaryUrl,
                method: "GET",
                responseType: "stream",
            });

            const contentLength = headers['content-length'];
            let downloadedBytes = 0;
            let lastDownloadPercent = 0

            data.on('data', (chunk) => {
                downloadedBytes += chunk.length;
                const percentDownloaded = Math.round((downloadedBytes / contentLength) * 100);
                if (percentDownloaded - lastDownloadPercent < 1){
                    return
                }
                // update stats
                lastDownloadPercent = percentDownloaded
                const msg = `${Math.round(downloadedBytes / 1000000)}MB/${Math.round(contentLength / 1000000)}MB ${percentDownloaded}%`
                this.sendFrontendMessage('download_percent',{
                    message: msg
                })
            });


            // Check if the file already exists
            if (fs.existsSync(this.binaryZip)) {
                console.log('File already exists. Deleting...');
                fs.unlinkSync(this.binaryZip); // Delete the file if it already exists
            }

            // Pipe the data stream to a file
            const writeStream = fs.createWriteStream(this.binaryZip);
            data.pipe(writeStream);

            writeStream.on('finish', async () => {
                console.log('Done Saving Bin To', this.binaryZip);
                this.sendFrontendMessage('download_percent',{
                    message: 'Download Complete. Extracting'
                })
                isActivelyDownloading = false
                resolve(this.binaryZip); // Resolve the promise with the path to the downloaded file
            });

            writeStream.on('error', (error) => {
                console.error('Error downloading file:', error);
                this.sendFrontendMessage('show_toast',{
                    message: 'Install Error: ' + error.message,
                    isSuccess: false
                })
                isActivelyDownloading = false
                reject(error); // Reject the promise with the error if download fails
            });
        });
    }

    async extractTarGz(tarFilePath, extractFolder) {
        try {
            if (fs.existsSync(extractFolder)) {
                console.log('Deleting...', extractFolder);
                fsExtra.emptyDirSync(extractFolder)
                fsExtra.removeSync(extractFolder)
            }

            // Create the extraction folder if it doesn't exist
            await fsPromise.mkdir(extractFolder, { recursive: true });

            // Extract the .tar.gz file
            await tar.x({
                file: tarFilePath,
                cwd: extractFolder,
                // strip: 1 // Strip the first path component (usually the folder name inside the archive)
            });

            // Check if the file already exists
            if (fs.existsSync(this.binaryZip)) {
                console.log('Done extracting. Delete source file...');
                fs.unlinkSync(this.binaryZip); // Delete the file if it already exists
            }

            console.log('Extraction complete');
        } catch (error) {
            console.error('Extraction failed:', error);
            throw error; // Propagate the error to the caller
        }
    }

    async extractZip(zipFilePath) {
        try {
            if (!fs.existsSync(this.binaryExtractedFolder)) {
                console.log('Binary folder DNE, creating');
                fs.mkdirSync(this.binaryExtractedFolder);
            }

            // Check if the file already exists
            try {
                fs.rmSync(this.binaryExtractedFolder, { recursive: true });
                console.log('Folder deleted successfully');
            } catch (error) {
                console.error('Error deleting folder:', error);
            }

            return new Promise(async (resolve, reject) => {
                decompress(zipFilePath, this.binaryExtractedFolder)
                    .then((files) => {
                        console.log('Done Extracting Files');
                        resolve(true)
                    })
                    .catch((error) => {
                        console.log(error);
                        this.sendFrontendMessage('download_percent',{
                            message: 'Failed Extracting'
                        })
                        reject(false)
                    });
            });
        } catch (error) {
            console.error('Error extracting zip file:', error);
            this.sendFrontendMessage('download_percent',{
                message: 'Failed Extracting 2123.'
            })
            throw error;
        }
    }
}