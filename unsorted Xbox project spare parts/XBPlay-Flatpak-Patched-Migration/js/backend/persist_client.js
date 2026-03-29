const Store = require('electron-store');
const store = new Store();
const os = require('os');

module.exports = class PersistClient {
    returnXCloudConfig = false

    get(key) {
        let c = store.get(key)

        if (c) {
            return decodeURIComponent(c)
        }
        return null;
    }
    save(key, value){
        store.set(key, value);
    }
    delete(key){
        store.delete(key);
    }

    clear(){
        store.clear()
    }

    getJSONKey(jsonKey, settingsKey){
        const savedSettings = this.get(settingsKey) || '{}'
        const jsonSettings = JSON.parse(savedSettings)
        if (jsonSettings && jsonSettings[jsonKey] !== undefined){
            return jsonSettings[jsonKey]
        }
        return null
    }

    getConfigData(){
        let platform = 'electron:' + os.type()
        if (this.get('isSteamCheck') === 'true' || this.get('isSteamDeckV2') === 'true'){
            platform += `:SteamDeck`
        }
        if (!this.returnXCloudConfig) {
            const configData = {
                gsToken: this.get('gsToken'),
                serverId: this.get('serverId'),
                platform: platform,
                steam_game_list_shortcuts: true,
            }
            const useXalLogin = this.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !this.getJSONKey('settings_login_type', 'settings_items')
            if(useXalLogin && this.getSavedXalTokens()){
                configData['xal_tokens'] = this.getSavedXalTokens()
                configData['webToken'] = this.getSavedWebToken()
            }
            if (this.get('ui-language')){
                configData['ui_language'] = this.get('ui-language')
            }
            if (this.get('xhomeTitle')){
                configData['xhomeTitle'] = this.get('xhomeTitle')
                this.delete('xhomeTitle')
            }

            const loginRegionIp = this.getJSONKey('settings_login_region', 'settings_items')
            if(loginRegionIp){
                configData['login_region_ip'] = loginRegionIp
            }

            const isVulkan = this.getJSONKey('settings_render_pipeline', 'settings_items') === 'vulkan'
            if(isVulkan){
                configData['is_vulkan'] = true
            }
            return configData
        } else {
            const configData = {
                gsToken: this.get('xCloudToken'),
                serverId: this.get('serverId'),
                xcloudTitle: this.get('xCloudTitle'),
                msalToken: Buffer.from(this.get('msal') || '').toString('base64'),
                platform: platform,
               // gamepadRefreshRateMs: 16,
                xcloudRegion: this.get('selected-region') ? this.get('selected-region').replace('https://', '') : 'wus.core.gssv-play-prod.xboxlive.com',
                customLocal: this.get('selected-language') ? this.get('selected-language') : 'en-US',
                // pcheck: this.get('pcheck') // since cant pcheck from electron, dont pass value
                steam_game_list_shortcuts: true,
            }
            const useXalLogin = this.getJSONKey('settings_login_type', 'settings_items') === 'xal_token' || !this.getJSONKey('settings_login_type', 'settings_items')
            if(useXalLogin && this.getSavedXalTokens()){
                configData['xal_tokens'] = this.getSavedXalTokens()
                configData['webToken'] = this.getSavedWebToken()
            }
            if (this.get('ui-language')) {
                configData['ui_language'] = this.get('ui-language')
            }
            const loginRegionIp = this.getJSONKey('settings_login_region', 'settings_items')
            if(loginRegionIp){
                configData['login_region_ip'] = loginRegionIp
            }

            const isVulkan = this.getJSONKey('settings_render_pipeline', 'settings_items') === 'vulkan'
            if(isVulkan){
                configData['is_vulkan'] = true
            }
            return configData
        }
    }

    getSavedXalTokens() {
        try {
            const xalTokensString = this.get('xalTokens')
            if (!xalTokensString) {
                return false
            }
            return JSON.parse(xalTokensString)
        } catch (err){
            return false
        }
    }

    getSavedWebToken() {
        try {
            const token = this.get('webToken')
            if (!token) {
                return false
            }
            return JSON.parse(token)
        } catch (err){
            return false
        }
    }
}