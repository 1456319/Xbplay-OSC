const path = require('path');
const fs = require('fs');
const os = require('os');
const config = require('./../../config.js');
const { app } = require('electron')


// versions
const isMac = os.platform() === "darwin";
const isWindows = os.platform() === "win32";
const isLinux = os.platform() === "linux";

// only used if isSteamVersion = false
module.exports = class SteamFileClient {
    homeDir = os.homedir()
    userPath = this.getSteamUserFolderPath()

    constructor() {
    }

    getSteamUserFolderPath() {
        if(isWindows){
            const exePath = app.getPath('exe'); // Get the path of the Electron app
            const defaultSteamPath = path.join(path.parse(exePath).root, 'Program Files (x86)', 'Steam', 'userdata');

            // Check if the default Steam path exists
            if (fs.existsSync(defaultSteamPath)) {
                console.log('Using Default Steam Location', defaultSteamPath);
                return defaultSteamPath;
            }
            console.log('Not Using Default Steam Location')
            const drives = ['C:', 'D:', 'E:', 'F:', 'G:', 'H:', 'I:', 'J:'];

            // Loop through the drives and check the Steam path on each
            for (let drive of drives) {
                const potentialSteamPath = path.join(drive, 'Program Files (x86)', 'Steam', 'userdata');
                
                // Check if the Steam userdata folder exists directly on this drive
                if (fs.existsSync(potentialSteamPath)) {
                    console.log('Found Steam Location on Drive:', potentialSteamPath);
                    return potentialSteamPath;
                }
            }

            console.log('Steam path not found on any drive, returning default');
            return defaultSteamPath;
        } else if (isMac){
            return path.join(this.homeDir, 'Library', 'Application Support', 'Steam', 'userdata')
        } else if (isLinux){
            return path.join(this.homeDir, '.steam', 'steam', 'userdata')
        } else {
            console.log('Unsupported OS', os.platform)
        }
        return null
    }

    // assuming all linux installs are steam decks for now
    getIsSteamDeck() {
        if(isLinux){
            return true
        }

        return false
    }

    getSteamPlayerId() {
        if (!this.userPath){
            return null
        }

        const latestConfigDirectoryId = this.getLatestConfigDirectoryIds(this.userPath);
        console.log(latestConfigDirectoryId)

        if (latestConfigDirectoryId) {
            return {
                'accountId': latestConfigDirectoryId
            }
        }
    }

    getLatestConfigDirectoryIds(basePath) {
        try {
            // Read all folder names in the specified path
            const folderIds = fs.readdirSync(basePath, { withFileTypes: true })
                .filter(dirent => dirent.isDirectory() && !['0', '1'].includes(dirent.name))
                .filter(dirent => {
                  const configPath = path.join(basePath, dirent.name, 'config');
                  return fs.existsSync(configPath) && fs.statSync(configPath).isDirectory();
                })
                .map(dirent => dirent.name);

            console.log('Possible User Ids', folderIds, basePath)

            // Sort folderIds based on the modification time of the 'config' folder
            folderIds.sort((a, b) => {
                const configPathA = path.join(basePath, a, 'config');
                const configPathB = path.join(basePath, b, 'config');

                const mtimeA = this.getLatestConfigFileModTime(configPathA);
                const mtimeB = this.getLatestConfigFileModTime(configPathB);

                return mtimeB - mtimeA; // Sort in descending order based on modification time
            });

            return folderIds[0]; // Return the ID of the directory with the newest config file
        } catch (error) {
            console.error('Error:', error.message);
            return null;
        }
    }

    getLatestConfigFileModTime(configPath) {
        try {
            const configFiles = fs.readdirSync(configPath);
            const latestConfigFile = configFiles
                .filter(file => fs.statSync(path.join(configPath, file)).isFile())
                .reduce((latest, file) => {
                    const mtime = fs.statSync(path.join(configPath, file)).mtime;
                    return mtime > latest.mtime ? { file, mtime } : latest;
                }, { file: null, mtime: 0 });

            return latestConfigFile.mtime.getTime();
        } catch (error) {
            return 0;
        }
    }

}