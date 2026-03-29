// convention: call "initSettingsWithSavedValues('some_new_setting_page') // will save it in frontend to some_new_setting_page_settings_values (u dont care)
// access in backend via persistClient.getJSONKey('some_single_setting_key', 'some_new_setting_page_settings_items')

const SettingsClient = {
    id: null,
    legacyExcludedSettings: ['auto_login', 'xcloud-language', 'xcloud-region', 'xhome-console', 'settings_ui_language'], // these are handled directly
    settingsValues: {},

    initSettingsWithSavedValues(settingsIdKey){
        this.id = settingsIdKey
        const currentSettings = this.getCurrentSettingValues()
        let savedValues = this.getSavedSettingsValues()

        // save default settings on first start
        if (Object.keys(savedValues).length === 0){
            this.saveCurrentSettingValues()
            savedValues = this.getSavedSettingsValues()
        }

        console.log('init saved settings', savedValues)
        const settingIds = Object.keys(currentSettings)
        for(let i = 0; i < settingIds.length; i++){
            const id = settingIds[i]
            const value = currentSettings[id]
            const settingsElement = document.getElementById(id)

            if (!settingsElement){
                console.log('ignoring settings element', settingIds)
                continue
            }

            if (savedValues[id] === undefined){
                console.warn('Found a setting element that doesnt have a saved value', id, value, savedValues[id])
            } else if (value !== savedValues[id]){
                if (settingsElement.type === 'checkbox'){
                    settingsElement.checked = savedValues[id]
                } else {
                    settingsElement.value = savedValues[id]
                }
            }
        }
    },

    // id is required to support side menu closing after modal opening
    getCurrentSettingValues: function () {
        const settingsValues = {}
        const settingsElements = document.body.querySelectorAll('.settings_focusable');

        settingsElements.forEach((item, index) => {
            if (!this.legacyExcludedSettings.includes(item.id)){
                if (item.type === 'checkbox'){
                    settingsValues[item.id] = item.checked
                } else if (item.type === 'submit') {
                    console.log('ignore button', item)
                } else {
                    settingsValues[item.id] = item.value
                }
            }
        });

        this.settingsValues = settingsValues
        return settingsValues
    },

    saveCurrentSettingValues: function (){
        const values = this.getCurrentSettingValues()
        let settingsKeyName = this.getSettingsSaveKey()

        localStorage.setItem(settingsKeyName, JSON.stringify(values))
        console.log('saved settings', this.id, this.getSavedSettingsValues())

        // update main render with new settings data
        if (window.electronAPI) {
            window.electronAPI.send("settings_items_updated", {
                settingsKeyName: this.settingsValueToSettingsKey(settingsKeyName),
                values: values
            });
        }
    },

    getSavedSettingsValues: function() {
        let settingsKeyName = this.getSettingsSaveKey()
        return JSON.parse(localStorage.getItem(settingsKeyName) || '{}')
    },

    // I am stupid and choose the naming convention 'settings_values' for frontend and 'settings_items' for backend persist client
    // the message we emit here should have the key of the persist client settings key that should be updated.
    settingsValueToSettingsKey: function(settingsValue) {
        return settingsValue.replace('settings_values', 'settings_items')
    },

    getSettingsSaveKey: function() {
        let settingsKeyName = 'settings_values'

        // if we call with id of default dont change lookup name otherwise will wipe exising users settings
        if (this.id && this.id !== 'default'){
            settingsKeyName =  this.id + '_settings_values'
        }
        return settingsKeyName
    }
}