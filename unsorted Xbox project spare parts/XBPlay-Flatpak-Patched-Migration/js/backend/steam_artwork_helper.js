const path = require('path');
const fs = require('fs');
const PersistClient = require('./persist_client.js')
const CustomSteamClient = require('./steam_custom_client.js')
const config = require('./../../config.js');
const os = require('os');
const isMac = os.platform() === "darwin";
const isWindows = os.platform() === "win32";
const isLinux = os.platform() === "linux";

const readVdf = require("steam-binary-vdf").readVdf
const writeVdf = require("steam-binary-vdf").writeVdf
const getShortcutHash = require("steam-binary-vdf").getShortcutHash
const getShortcutUrl = require("steam-binary-vdf").getShortcutUrl

const fsExtra = require("fs-extra");
const Axios = require('axios');

module.exports = class SteamArtworkHelper {
    localArtworkDir = '/assets/steamdeck_art'
    artworkDisableKey = 'disable_artwork_check'
    posterImagePersistKey = 'pending_poster_'
    heroImagePersistKey = 'pending_hero_'
    persistClient = new PersistClient()
    customSteamClient = null

    constructor(client) {
        this.customSteamClient = client
    }

    // checks if device is a steam device
    async isSteamDevice(){
        return this.customSteamClient.getDeviceHasSteamAppInstalled()
    }

    async shouldShowSteamInputWarning(){
        const check = this.persistClient.get('displayedSteamInputPrompt')
        if(check){
            return false;
        }
        this.persistClient.save('displayedSteamInputPrompt', true)

        const isSteam = await this.isSteamDevice()
        if(isSteam && isWindows){
            return true
        }

        return false;
    }

    // will only return true if is first time and no shortcut already exists for app
    async shouldShowGamepadModeStartupPrompt(){
        let result = false

        // dont prompt to add to gaming mode or show compat warning unless on steam deck
        if (!this.customSteamClient.getIsSteamDeck()) {
            return false;
        }

        const check = this.persistClient.get('displayedSteamStartupPrompt')
        if(check){
            console.log('Not showing init dialog due to displayedSteamStartupPrompt')
            return false;
        }

        const isSteam = await this.isSteamDevice()
        if(this.customSteamClient.getIsSteamDeck() && config.isSteamVersion){ // if steam deck and installed via steam, then show compat popup
            result = true;
        } else if(isSteam){
            const appIds = await this.getMainShortcutIds()
            console.log('Main app shortcut id is', appIds)

            if(!appIds.length){
                console.log('Unable to find ID for main app. Showing startup dialog')
                result = true;
            }
        } else {
            console.log('Not showing init dialog due to not steam device')
        }

        this.persistClient.save('displayedSteamStartupPrompt', true)
        return result;
    }
    async addMainAppShortcut(){
        const shortcutsPath =  this.customSteamClient.getShortcutFileLocation()
        if (!shortcutsPath){
            console.log('addMainAppShortcut failed due to no shortcut path')
            return
        }

        let shortcuts = {shortcuts: {}}
        const shortcutExists = await this.checkFileExists(shortcutsPath)
        if (shortcutExists) {
            const inBuffer = await fsExtra.readFile(shortcutsPath);
            shortcuts = readVdf(inBuffer);
        }

        if (shortcuts['shortcuts']) {
            const keys = Object.keys(shortcuts['shortcuts']);
            const shortcutId = keys.length.toString()
            const appId = Math.floor(Math.random() * (4294967295 - 3690428342) + 3690428342) //4251305412

            // add shortcut data
            let shortcutData = this.customSteamClient.getMainShortcutData(appId);
            if(!shortcutData){
                return {result: false, code: 0, message: `<br>Error Details: Cannot find shortcut file.`};
            }

            shortcuts.shortcuts[shortcutId] = shortcutData

            // write the shortcut
            const outBuffer = writeVdf(shortcuts);
            await fsExtra.writeFile(shortcutsPath, outBuffer);

            // save the default artwork images
            await this.saveDefaultArtworkFiles(appId, true);
            console.log('Finished adding steam shortcut')
        }

    }

    saveAllImages(data){
        if(!data || typeof data !== 'object'){
            console.log('error saving image urls. Not object', data)
            return;
        }
        const result = {};
        const keys = Object.keys(data);

        console.log('start saving images')
        for(let i = 0; i < keys.length; i++){
            try {
                const key = keys[i];
                const item = data[key];

                const titleId = item['titleId'];
                const image = item['image'];
                const details = item['storeDetails'];

                const posterImage = this.getBestImage(details, 'BrandedKeyArt', 'Poster', image);
                const heroImage = this.getBestImage(details, 'TitledHeroArt', 'SuperHeroArt', image);

                result[titleId] = {
                    posterImage: posterImage,
                    heroImage: heroImage,
                    title: item['name']
                }
            } catch (err){
                console.log(err)
            }
        }

        this.persistClient.save('all_images', JSON.stringify(result));
        console.log('done saving images')
    }

    // adds a steam shortcut, force adds a duplicate. 
    // on add saves the artwork URL by titleId for future lookup
    async addSteamShortcut(args, force = false){
        console.log('Attempting to add steam shortcut')
        let worked = false;
        try {
            const titleId = args['titleId'];
            const title = args['title'];
            const image = args['image'];
            const type = args['type'] ?? 'xcloud'

            const steamGridHero = args['steamGridHeroUrl'];
            const steamGridCapsule = args['steamGridCapsuleUrl'];

            // hero image logic
            let heroImage
            if (steamGridHero) {
                heroImage = steamGridHero
            } else {
                heroImage = this.getBestImage(args['details'], 'TitledHeroArt', 'SuperHeroArt', image);
            }

            // poster/capsule image logic
            let posterImage
            if (steamGridCapsule) {
                posterImage = steamGridCapsule
            } else {
                posterImage = this.getBestImage(args['details'], 'BrandedKeyArt', 'Poster', image);
            }

            // get the app id for this user by reading the shortcut data
            const user = this.customSteamClient.getSteamAccountId()
            const shortcutsPath = this.customSteamClient.getShortcutFileLocation()
            if (!shortcutsPath || !user) {
                console.log('addSteamShortcut failed due to no shortcut path')
                return {
                    result: false,
                    code: 3,
                    message: `<br>Error Details: <br>UserId: ${user}<br>Path: ${shortcutsPath}<br>Message: Steam app not installed or not running.`
                };
            }

            let shortcuts = {shortcuts: {}}
            const shortcutExists = await this.checkFileExists(shortcutsPath)
            if (shortcutExists) {
                const inBuffer = await fsExtra.readFile(shortcutsPath);
                shortcuts = readVdf(inBuffer);
                console.log(shortcuts)
            }

            // duplicate check (dont allow dup shortcuts if not in force mode)
            let shortcutId = 0
            if (shortcuts['shortcuts']) {
                const keys = Object.keys(shortcuts['shortcuts']);

                // dont allow the same shortcut to be added twice
                for (let k = 0; k < keys.length; k++) {
                    const key = keys[k];
                    const shortcut = shortcuts['shortcuts'][key];
                    if (!force
                        && shortcut['AppName'] && shortcut['AppName'].includes(title)
                        && shortcut['LaunchOptions'] && shortcut['LaunchOptions'].includes(titleId) && shortcut['LaunchOptions'].includes(type)) {
                        console.log("Shortcut already exists for this title. Dont allow a duplicate", JSON.stringify(shortcut), args);
                        return {
                            result: false,
                            code: 1,
                            message: `<br>Error Details: <br>UserId: ${user}<br>Existing Name: ${shortcut['AppName']}<br>Existing Options: ${shortcut['LaunchOptions']}. <br><br>New Title: ${title}<br>New Options: ${titleId}`
                        };
                    }
                }

                shortcutId = keys.length
            }

            // append the shortcut
            shortcutId = shortcutId.toString();
            const appId = Math.floor(Math.random() * (4294967295 - 3690428342) + 3690428342) //4251305412
            console.log('AppID should be', appId)

            // get shortcut data for OS and append to shortcuts object
            let shortcutData = this.customSteamClient.getXCloudShortcutData(title, titleId, appId, type)
            shortcutData = this.covertXCloudToPcPlayShortcut(shortcutData, args)

            console.log('shortcutData', shortcutData)
            if(!shortcutData){
                return {result: false, code: 0, message: `<br>Error Details: <br>UserId: ${user}<br>Name: ${titleId}<br>Error: Cannot detect operating system to add shortcut for: ${os.platform()}`};
            }

            shortcuts.shortcuts[shortcutId] = shortcutData
            // write the shortcut
            const outBuffer = writeVdf(shortcuts);
            await fsExtra.writeFile(shortcutsPath, outBuffer);

            // save that this poster is pending
            this.persistClient.save(this.posterImagePersistKey + titleId, posterImage);
            this.persistClient.save(this.heroImagePersistKey + titleId, heroImage);

            // save the artwork
            console.log('Saving artwork for', appId)
            try {
                if (args && args['isPcPlay']){
                    await this.savePcPlayArtworkFiles(appId)
                } else {
                    // first save the default artwork images, some will be overridden
                    await this.saveDefaultArtworkFiles(appId, false);
                }
                const steamImagePath = this.customSteamClient.getImageLocationDir()

                const posterPath = path.join(steamImagePath, appId + 'p.png');
                const logoPath = path.join(steamImagePath, appId + '.png');
                const heroPath = path.join(steamImagePath, appId + '_hero.png');

                await this.downloadOrCopyImageFile(posterImage, posterPath);
                await this.downloadOrCopyImageFile(heroImage, logoPath);
                await this.downloadOrCopyImageFile(heroImage, heroPath);

                console.log('Finished Downloading Artwork to', logoPath)

                worked = true;
            } catch (err){
                console.log('failed saving artwork')
                return {result: false, code: 2, message: `<br>Error Details: <br>UserId: ${user}<br>Name: ${titleId}<br>Error: ${err}<br>Message: Failed to save artwork. Shortcut was still created.`};
            }
        } catch (err){
            console.log('Error creating steam shortcut', err);
            return {result: false, code: 0, message: JSON.stringify(err)};
        }

        return {result: worked, code: 0, message: 'No Details'};
    }

    async downloadOrCopyImageFile(srcImage, dstPath){
        if (srcImage.startsWith('http')){
            console.log('downloading', srcImage)
            await this.downloadImage(srcImage, dstPath);
        } else {
            console.log('copying', srcImage)
            await this.copyFile(srcImage, dstPath);
        }

    }
    isEmptyObject(obj) {
        if (obj !== null && typeof obj === 'object') {
            return Object.entries(obj).length === 0;
        }
        return false;
    }

    covertXCloudToPcPlayShortcut(data, args){
        const isPcPlay = args['isPcPlay']
        if (!isPcPlay){
            return data
        }


        const result = this.customSteamClient.getPCPlayShortcutData(data['AppName'], data['appid'], args['pcPlayLaunchOptions'], args['pcPlayInstallPath'])
        console.log('CONVERTED PCPLAY DATA', result)
        return result
    }

    async setMainShortcutArtwork(force = false){
        console.log('Setting Artwork')
        if (!force && this.isArtworkCheckDisabled()){
            return;
        }

        const appIds = await this.getMainShortcutIds() // set artwork for main app
        console.log('Main app shortcut id is', appIds)

        const promises = []
        for (let k = 0; k < appIds.length; k++){
            promises.push(this.saveDefaultArtworkFiles(appIds[k]))
        }
        await Promise.all(promises)

        if(!appIds.length){
            console.log('Unable to find ID for main app')
        }

    }

    // initialted via the ui
    async updateAllXCloudArtwork(){
        console.log('updateAllXCloudArtwork')
        let result = ''
    
        const user = this.customSteamClient.getSteamAccountId()
        const shortcutsPath =  this.customSteamClient.getShortcutFileLocation()

        if (!user || !shortcutsPath){
            console.error('updateAllXCloudArtwork failed due to no user id')
            return 'Error: Steam app not installed or not running.'
        }

        let allImages = this.persistClient.get('all_images')
        if (allImages){
            allImages = JSON.parse(allImages)
        } else {
            return 'Error: You must open the xCloud title picker screen at least once to gather image artwork.'
        }

        try {
            let shortcuts = {shortcuts: {}}
            const shortcutExists = await this.checkFileExists(shortcutsPath)
            if (shortcutExists) {
                const inBuffer = await fsExtra.readFile(shortcutsPath);
                shortcuts = readVdf(inBuffer);
            }

            if (shortcuts['shortcuts']) {
                const keys = Object.keys(shortcuts['shortcuts']);
                for (let k = 0; k < keys.length; k++) {

                    try {
                        const key = keys[k];
                        const shortcut = shortcuts['shortcuts'][key];

                        const titleId = this.getTitleIdFromShortcut(shortcut['LaunchOptions']);
                        if(titleId){ // only care if its an xcloud xbplay shortcut
                            if (shortcut['appid']) {
                                // abort if already saved poster
                                const pendingPosterImageUrl = allImages[titleId]['posterImage'];
                                const pendingHeroImageUrl = allImages[titleId]['heroImage'];
                                if (!pendingPosterImageUrl || !pendingHeroImageUrl){
                                    console.log('Ignoring', shortcut['AppName'], titleId, 'because we dont have a image url saved')
                                    result += `<span>&#8226;</span> Failure: Ignoring Artwork For Game: ${shortcut['AppName']}. TitleId: ${titleId}. User: ${user}. Reason: Image URL not found.<br>`
                                    continue;
                                }

                                console.log('Saving images for xCloud artwork', shortcut['AppName'], titleId);

                                // first save the default artwork images
                                await this.saveDefaultArtworkFiles(shortcut['appid'], false);

                                // next save the custom xcloud poster
                                const steamImagePath = this.customSteamClient.getImageLocationDir()

                                const posterPath = path.join(steamImagePath, shortcut['appid'] + 'p.png');
                                await this.downloadImage(pendingPosterImageUrl, posterPath); // downloads to path

                                const logoPath = path.join(steamImagePath, shortcut['appid'] + '.png');
                                await this.downloadImage(pendingHeroImageUrl, logoPath); // downloads to path

                                const heroPath = path.join(steamImagePath, shortcut['appid'] + '_hero.png');
                                await this.downloadImage(pendingHeroImageUrl, heroPath); // downloads to path
                                result += `<span>&#8226;</span> Success: Updated Artwork For Game: ${titleId}. User: ${user}<br>`
                            } else {
                                result += `<span>&#8226;</span> Failure: Ignoring Artwork For Game: ${titleId}. User: ${user}. Reason: Shortcut not opened yet.<br>`
                            }
                        }
                     } catch (err){
                        console.log('<span>&#8226;</span> Error Saving Shortcut')
                        result += `Error Updating Shortcut: ${shortcuts['shortcuts'][keys[k]]['AppName']}. Reason: ${err}.<br>`
                    }
                }
            }
        } catch (err){
            console.log('Error Saving Shortcut', err)
        }
        console.log('Finished setting xcloud artwork', result)


        // force update main app artwork as well
        console.log('Setting main app artwork');
        await this.setMainShortcutArtwork(true);
        return result;
    }

    // lookup vdf file data
    async getMainShortcutIds(){
        const appIds = []
        try {
            const shortcutsPath =  this.customSteamClient.getShortcutFileLocation()
            if (!shortcutsPath){
                console.error('getMainShortcutIds no shortcut path')
                return []
            }

            // read the vdf
            let shortcuts = {shortcuts: {}}
            const shortcutExists = await this.checkFileExists(shortcutsPath)
            if (shortcutExists) {
                const inBuffer = await fsExtra.readFile(shortcutsPath);
                shortcuts = readVdf(inBuffer);
            }

            if (shortcuts['shortcuts']) {
                const keys = Object.keys(shortcuts['shortcuts']);
                for (let i = 0; i < keys.length; i++) {
                    const key = keys[i];
                    const shortcut = shortcuts['shortcuts'][key];
                    const launchOptions = shortcut['LaunchOptions'] || ''
                    const exe =  shortcut['Exe'] || ''

                    if (exe.includes("xbplay") || launchOptions.includes("xbplay")){ // xbplay shortcut
                        if (!launchOptions.includes("--xhome") && !launchOptions.includes("--xcloud")){ // ignore xcloud and xhome shortcuts
                            appIds.push(shortcut['appid']);
                        }
                    }
                }
            }
        } catch (err){
            console.log(err);
        }
        return appIds;
    }

    getTitleIdFromShortcut(launchOptions){
        let result = false;
        if(launchOptions) {
            const sections = (launchOptions || '').split(" ");
            for (let i = 0; i < sections.length; i++) {
                if (sections[i] && sections[i].includes('--xcloud=')) {
                    const paramsSplit = sections[i].split('=');
                    if (paramsSplit[1]) {
                        result = paramsSplit[1].replace('"', '')
                    }
                }
            }
        }

        console.log('TitleIDFromLaunchOptions', launchOptions, result, typeof result)
        return result;
    }

    disableArtworkCheck(){
        console.log('Successfully set steam artwork. Disabling it.');
        this.persistClient.save(this.artworkDisableKey, true);
    }

    isArtworkCheckDisabled(){
        const isDisabled = this.persistClient.get(this.artworkDisableKey);
        if(isDisabled){
            console.log('Artwork check disabled. Dont set artwork')
            return true;
        }
        console.log('Artwork check enabled. Attempt to set set artwork')
        return false;
    }

    async savePcPlayArtworkFiles(appId){
        const artworkPath = this.customSteamClient.getImageLocationDir()
        if (!artworkPath){
            console.log('saveDefaultArtworkFiles failed no image path')
            return
        }

        // only need these 3 images for pc play shortcuts, others will be overridden via steam grid db
        const logo = path.join(__dirname + '../../..', 'assets', 'pc_play', 'pc_play_logo_bordered.png'); // shown with hero art at corner
        const icon = path.join(__dirname + '../../..', 'assets', 'pc_play', 'pc_play_icon_512.png');
        const heroIcon = path.join(__dirname + '../../..', this.localArtworkDir, 'library_hero5.png');

        await this.copyFile(logo, path.join(artworkPath, appId + '_logo.png'));
        await this.copyFile(icon, path.join(artworkPath, appId + '_icon.png'));
        await this.copyFile(heroIcon, path.join(artworkPath, appId + '_hero.png'));
    }

    async saveDefaultArtworkFiles(appId, updateDisableCheck = true){
        const artworkPath = this.customSteamClient.getImageLocationDir()
        if (!artworkPath){
            console.log('saveDefaultArtworkFiles failed no image path')
            return
        }

        const heroIcon = path.join(__dirname + '../../..', this.localArtworkDir, 'library_hero5.png');
        const posterIcon = path.join(__dirname + '../../..', this.localArtworkDir, 'poster_4.png'); // vertical poster
        const logo = path.join(__dirname + '../../..', this.localArtworkDir, 'logo_bordered.png'); // shown with hero art at corner
        const main = path.join(__dirname + '../../..', this.localArtworkDir, 'logo_black_2.png'); // shown stand alone on home collection, horizontal
        const icon = path.join(__dirname + '../../..', 'assets', 'icon_512.png');

        // copy 4 icons files
        const result1 = await this.copyFile(heroIcon, path.join(artworkPath, appId + '_hero.png'));
        const result2 = await this.copyFile(posterIcon, path.join(artworkPath, appId + 'p.png'));
        const result3 = await this.copyFile(logo, path.join(artworkPath, appId + '_logo.png'));
        const result4 = await this.copyFile(main, path.join(artworkPath, appId + '.png'));
        const result5 = await this.copyFile(icon, path.join(artworkPath, appId + '_icon.png'));

        // once we copy the file, dont do it again
        if(updateDisableCheck && result1 && result2 && result3 && result4 && result5){
            this.disableArtworkCheck();
        }
    }

    getBestImage(data, primary, secondary, original){
        let result = original;

        if(data && data['LocalizedProperties'] && data['LocalizedProperties'][0] && data['LocalizedProperties'][0]['Images']){
            const images = data['LocalizedProperties'][0]['Images'];

            for(let i = 0; i < images.length; i++){
                const image = images[i];

                if(image['ImagePurpose']){
                    if (image['ImagePurpose'] === primary){
                        //console.log('Using primary', primary, image['Uri'])
                        return 'https:' + image['Uri'];
                    } else if (image['ImagePurpose'] === secondary){
                        //console.log('Found secondary', secondary, image['Uri'])
                        result = 'https:' + image['Uri'];
                    }
                }                
            }
        } else if(data && data['images']){ // handle game list shortcut
            const images = data['images'];
            for(let i = 0; i < images.length; i++){
                const image = images[i];

                if(image['type']){
                    if (image['type'] === primary){
                        return image['url'];
                    } else if (image['type'] === secondary){
                        result = image['url'];
                    }
                }
            }
        }
        return result;
    }

    async downloadImage(url, filepath) {
        const response = await Axios({
            url,
            method: 'GET',
            responseType: 'stream'
        });
        await this.ensureDirectoryExists(filepath); // Create directory recursively
        return new Promise((resolve, reject) => {
            response.data.pipe(fs.createWriteStream(filepath))
                .on('error', reject)
                .once('close', () => resolve(filepath)); 
        });
    }

    async copyFile(src, dst){
        let result = false
        try {
            await this.ensureDirectoryExists(dst); // Create directory recursively
            await fs.copyFileSync(src, dst);
            result = true
        } catch (err){
            console.log(err)
        }
        return result
    }

    async ensureDirectoryExists(filepath) {
        const directory = path.dirname(filepath);
        try {
            await fs.accessSync(directory); // Check if the directory exists
        } catch (err) {
            if (err.code === 'ENOENT') {
                try {
                // Directory doesn't exist, so create it
                console.log('creating steamdeck image directory for first time!')
                await fs.mkdirSync(directory, { recursive: true });
                } catch (err){
                    console.log(err)
                }
            }
        }
    }

    async checkFileExists(filepath) {
        try {
            await fs.accessSync(filepath); // Check if the directory exists
            return true
        } catch (err) {
            return false
        }
    }
}