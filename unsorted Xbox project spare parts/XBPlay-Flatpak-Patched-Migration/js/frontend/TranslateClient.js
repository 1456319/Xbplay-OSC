const TranslateClient = {
    translations: null,
    translationPhrases: null,

    desiredLanguage: 'en',

    // used to determine if we should load new language files via fetch
    currentStaticLanguageLoaded: 'en',
    currentPhraseLanguageLoaded: 'en',

    phrasesFileName: 'phrases_steam.json',
    ignoredTranslationValues: [
        '4ms', '8ms', '16ms', '32ms', '64ms', // times
        '0', '60', // numbers
        'A', 'B', 'X', 'Y', 'LT', 'RT', 'LB', 'RB', // buttons
        'DEFAULT-CONTROLLER', 'DEFAULT-CONTROLLER-NO-GUIDE', 'OFFSET-STICKS', 'MINI (resize "touch control size on screen" to 50%)', 'RACING', 'RACING-TILT', "DEFAULT-REMOTE",
        'https://www.studio08.net/post/game-mods',
        'FPS:', 'LAG:', 'JTR:', 'PNG:',
        'WestUS (Default)' // remove default from my region
    ],
    // element IDS: this element and their CHILDREN (1 level deep) will be ignored
    ignoredTranslateElements: [
        'settings_pwa_ui_language',
        'settings_video_aspect_ratio',
        'tips_view_text', // tips are handled by phrases

        // electron specific values
        'settings_login_region',
        'settings_ui_language',
        'xcloud-language',
        'version-div',
        'xhome-console'
    ],
    translatableElementTypes: [
        'A', 'P', 'DIV', 'OPTION', 'H1', 'H2', 'H3', 'H4', 'H5', 'BUTTON',
        'LI', 'SELECT', 'INPUT', 'UL', 'OL', 'LABEL',
        'SPAN', 'STRONG',
    ],

    // translate values I know I need but arent on the main gamestream view (remote only view)
    customDynamicValues: {
        'WestUS': 'WestUS'
    },

    validLanguages: ['ar-SA', 'de-DE', 'el-GR', 'es-ES', 'fr-FR', 'hi-IN', 'id-ID', 'it-IT', 'ja-JP', 'ko-KR', 'nl-NL', 'pl-PL', 'pt-BR', 'ru-RU', 'tr-TR', 'uk-UA', 'vi-VN', 'zh-CN' ],


    logTranslations: false, // should be false in prod

    // translates elements that have 'data-translation-tag' tag, should not block
    TranslatePageWithTags: async function (translationsFile){
        console.log('TranslatePageWithTags called', this.desiredLanguage)

        const isNewTranslations = await this.loadStaticTranslations(translationsFile)
        await this.logAllElementValuesWithTag()

        if (isNewTranslations){
            this.applyTranslationsToSpecialTaggedElements()
            this.emitTranslationCompleteEvent()
        } else {
            console.log('TranslatePageWithTags skipping duplicate translation call')
        }
    },

    // looks up every element textContent and compares it to a list, should not block
    TranslatePageBruteForce: async function (translationsFile){
        console.log('TranslatePageBruteForce called', this.desiredLanguage)

        // must convert back to english, then translate to new lang
        let oldTranslations
        if (this.currentStaticLanguageLoaded !== 'en'){
            oldTranslations = this.translations
        }
        const isNewTranslations = await this.loadStaticTranslations(translationsFile)
        await this.logAllElementValues()

        if (isNewTranslations){
            this.applyTranslationsToAllElements(oldTranslations)
            this.emitTranslationCompleteEvent()
        } else {
            console.log('TranslatePageBruteForce skipping duplicate translation call')
        }
    },

    // dynamically translate an alert/toast/etc, returns original msg if no translation found
    TranslatePhrase: function(message){
        if (this.desiredLanguage === 'en'){
            return message
        } else if (!this.translationPhrases){
            console.error('TranslatePhrase called before translations loaded', message)
            return message
        }

        const value = this.translationPhrases[message]
        if (!value){
            console.error(`TranslateClient Phrase Missing: ${this.desiredLanguage}: ${message}`)
        }
        return value ?? message
    },

    // dynamic phrases need to block, called directly from client on load (blocking)
    LoadDynamicPhrases: async function (){
        this.desiredLanguage = this.getSavedLanguage()

        // load resource from file
        try {
            if (this.currentPhraseLanguageLoaded !== this.desiredLanguage){
                this.translationPhrases = await this.getTranslationsFromFile(this.buildFilePath(this.phrasesFileName))
                this.currentPhraseLanguageLoaded = this.desiredLanguage
                return true
            } else {
                console.log('LoadDynamicPhrases not reloading translations, already loaded', this.desiredLanguage)
            }
        } catch (err){
            console.error('Failed loading translations from LoadDynamicPhrases, continue...', this.desiredLanguage)
        }
        return false
    },

    loadStaticTranslations: async function (translationsFile){
        this.desiredLanguage = this.getSavedLanguage()

        // load resource from file
        try {
            if (this.currentStaticLanguageLoaded !== this.desiredLanguage) {
                this.translations = await this.getTranslationsFromFile(this.buildFilePath(translationsFile))
                this.currentStaticLanguageLoaded = this.desiredLanguage
                return true
            } else {
                console.log('loadStaticTranslations not reloading translations, already loaded', this.desiredLanguage)
            }
        } catch (err){
            console.error('Failed loading translations, continue...', this.desiredLanguage, translationsFile)
        }
        return false
    },

    buildFilePath(fileName){
        // Validate filename: Ensure it only contains alphanumeric characters, dashes, and underscores
        if (!/^[a-zA-Z0-9_-]+\.json$/.test(fileName)) {
            throw new Error('Invalid file name');
        }

        if (this.desiredLanguage === 'en'){ // for en look at the source files
            return '../assets/translations/src/' + fileName
        }
        return '../assets/translations/dist/' + this.desiredLanguage + '/' + fileName
    },

    getSavedLanguage: function () {
        let browserDefaultFull = navigator.language; // e.g., "en-US" or "es-MX"
        let browserDefaultPartial = null; // Will hold the fallback partial match

        // Step 1: Check for an exact match
        if (!this.validLanguages.includes(browserDefaultFull)) {
            browserDefaultFull = null;
        }

        // Step 2: Check for a partial match (first two characters of browser language)
        if (!browserDefaultFull) {
            const browserLanguagePrefix = navigator?.language?.split('-')?.[0]; // e.g., "en" from "en-US"
            browserDefaultPartial = this.validLanguages.find(lang => lang.startsWith(browserLanguagePrefix));
        }

        // Step 3: Return the saved language or fallbacks
        return (
            localStorage.getItem('settings_ui_language') ??
            browserDefaultFull ??
            browserDefaultPartial ??
            'en'
        );
    },

    // used for testing to get full list to translate, won't run unless logTranslations is enabled
    logAllElementValuesWithTag: async function (){
        if (!this.logTranslations){
            return
        }
        // Wait for 3 seconds before running
        await new Promise(resolve => setTimeout(resolve, 3000));

        const elementsWithTag = document.querySelectorAll('[data-translation-tag]');
        const allTexts = {}

        // Loop through the elements
        elementsWithTag.forEach(element => {
            const tagValue = element.getAttribute('data-translation-tag')
            allTexts[tagValue] = element.textContent?.trim()
        });

        console.log('Translations Source', Object.keys(allTexts)?.length, allTexts)

    },

    logAllElementValues: async function () {
        if (!this.logTranslations){
            return
        }
        // Wait for 3 seconds before running
        await new Promise(resolve => setTimeout(resolve, 3000));

        // Select all elements in the document
        const allElements = document.querySelectorAll('*');
        let allTexts = {}; // Object to store original textContent values
        const skippedTagTypes = {}

        // Loop through all elements
        allElements.forEach(element => {
            if (element.children?.length || !this.translatableElementTypes.includes(element.tagName)) {
                skippedTagTypes[element.tagName] = 1
                return
            }

            // Get the current textContent and trim whitespace
            const currentText = element.textContent?.trim()

            // Skip elements with no meaningful text
            if (this.shouldIgnoreElement(element)) {
                return
            }

            // Store the original text in the allTexts object
            allTexts[currentText] = currentText;
        });

        allTexts = { ...allTexts, ...this.customDynamicValues };
        // Print all textContent values
        console.log('Translations Source', Object.keys(allTexts)?.length, allTexts);
        console.log('Skipped Tag Types', skippedTagTypes)
    },

    shouldIgnoreElement: function(element) {
        const currentText = element?.textContent?.trim()

        if (!currentText
            || this.ignoredTranslationValues.includes(currentText)
            || this.ignoredTranslateElements.includes(element?.id)
            || this.ignoredTranslateElements.includes(element?.parentElement?.id)
            || !isNaN(Number(currentText))){
            return true
        }
        return false
    },

    getTranslationsFromFile: async function (filePath) {
        try {
            // Fetch the translations JSON file
            const response = await fetch(filePath)

            if (!response.ok) {
                console.error('Failed to load translation file', filePath)
                return
            }

            // Parse and return the JSON object
            return await response.json()
        } catch (error) {
            console.error('Error loading translations. File DNE', filePath, error)
        }
    },

    // oldTranslations is required if going from en -> sp -> german
    applyTranslationsToAllElements: function(oldTranslations) {
        // Select all elements on the page
        const allElements = document.querySelectorAll('*');

        allElements.forEach(element => {
            if (element.children?.length || !this.translatableElementTypes.includes(element.tagName)) {
                return
            }

            // Get the current text of the element
            let currentText = element.textContent?.trim()

            if (oldTranslations){
                currentText = this.convertOldTranslationToEnglish(oldTranslations, currentText)
            }

            // If there's a matching translation, update the text
            if (currentText && this.translations[currentText] && !this.shouldIgnoreElement(element)) {
                element.innerText = this.translations[currentText]
            } else if (currentText && !this.shouldIgnoreElement(element) && isNaN(Number(currentText))){
                console.warn('Missing translation for element: ', element.tagName, element.id, element.textContent, element.innerHTML)
            }
        });

        console.log('Translations applied.')
    },

    convertOldTranslationToEnglish: function(oldTranslations, currentText) {
        try {
            const keys = Object.keys(oldTranslations)
            for (let i = 0; i < keys.length; i++){
                const key = keys[i]
                const value = oldTranslations[key]

                if (value === currentText){
                    return key
                }
            }
        } catch (err){
            console.error('Error convertOldTranslationToEnglish', err)
        }
        return currentText
    },

    applyTranslationsToSpecialTaggedElements: function (){
        const elementsWithTag = document.querySelectorAll('[data-translation-tag]');

        // Loop through the elements
        elementsWithTag.forEach(element => {
            const tagValue = element.getAttribute('data-translation-tag')
            const transValue = this.exchangeTranslationTagForValue(tagValue)
            if (transValue) {
                element.innerText = transValue
            } else {
                console.warn('Missing translation for attribute: ', tagValue)
            }
        });
    },

    exchangeTranslationTagForValue(key){
        if (!this.translations){
            return null
        } if (this.ignoredTranslationValues.includes(key)){
            return null
        }
        const value = this.translations?.[key]
        return value ?? null
    },

    emitTranslationCompleteEvent: function () {
        const event = new CustomEvent('translationCompleteEvent', {
            detail: {
                language: this.desiredLanguage,
            },
        });
        window.dispatchEvent(event);
    },

}
