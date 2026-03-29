// main vars
let setMainVisible = setTimeout(()=>{
    document.getElementById('main').style.visibility = 'visible'
}, 1000)

let setSplashHidden = setTimeout(()=>{
    console.log('Hiding splash')
    document.getElementById('splash').style.visibility = 'hidden'
    document.getElementById("xhome-tag").focus()
}, 3000)

// setup checkbox listener
setupCheckbox();
setupCloseButton()

GamepadToGenericEvent.ListenForGamepadInputs('main')
//gamepadListener defined in sharedMenuNavFunctions
document.addEventListener('GamepadToGenericEvent', gamepadListener)

async function handleLanguageChange(){
    console.log('handleLanguageChange')

    // we must manually handle saving language change values, as we dont want to save the language if not set by user manually so defaults work
    const langElement = document.getElementById('settings_ui_language')
    const value = langElement.value
    localStorage.setItem('settings_ui_language', value)

    console.log('handleLanguageChange', value)

    // await TranslateClient.LoadDynamicPhrases() // nothing using phrases in frontend, only happens for dialog popups in backend
    await TranslateClient.TranslatePageBruteForce('main_menu_steam.json')

    // send update to render process
    if (window.electronAPI) {
        window.electronAPI.send("ui_language_update", TranslateClient.desiredLanguage);
    }
}
window.addEventListener('load', async function() {
    console.log('page load')
    // await TranslateClient.LoadDynamicPhrases() // only happens in backend popups
    setupNav(document.getElementById('xhome-tag'))

    SettingsClient.initSettingsWithSavedValues('default')
    setupTooltips()
    setupSettingsChangeListeners()

    TranslateClient.TranslatePageBruteForce('main_menu_steam.json')
});

function setupSettingsChangeListeners(){
    const uiElement = document.getElementById('settings_ui_language');
    if (uiElement) { // Ensure the element exists
        // first set language dropdwon to correct value
        const savedLang = localStorage.getItem('settings_ui_language')
        if (savedLang){
            uiElement.value = savedLang
        }

        // then listen for changes
        uiElement.addEventListener('change', () => {
            handleLanguageChange();
        });
    } else {
        console.error('Element with id not found.');
    }
}

function setupTooltips(){
    var infoIcons = document.querySelectorAll('.info-icon');

    infoIcons.forEach(function (infoIcon) {
        var tooltipText = infoIcon.getAttribute('data-tooltip');
        var tooltip = document.createElement('div');
        tooltip.className = 'tooltip';
        tooltip.innerText = tooltipText;

        var parentContainer = infoIcon.closest('.settings_row');
        var nextSettingsRow = parentContainer.nextElementSibling;

        // Insert the tooltip after the next settings row
        nextSettingsRow.insertAdjacentElement('beforebegin', tooltip);

        // infoIcon.addEventListener('mouseenter', function () {
        //     tooltip.style.display = 'block';
        // });

        infoIcon.addEventListener('click', function () {
            if (tooltip.style.display === 'block'){
                tooltip.style.display = 'none';
            } else {
                tooltip.style.display = 'block';
            }

            setTimeout(function () {
                tooltip.style.display = 'none';
            }, 10000);
        });
    });

    // infoIcon.addEventListener('click', function () {
    //     infoIcon.classList.add('active');
    //     setTimeout(function () {
    //         infoIcon.classList.remove('active');
    //     }, 3000);
    // });

}

// setup modal listner
window.addEventListener('DOMContentLoaded', () => {
    const purchaseTrigger = document.getElementById("purchase");
    const purchaseCloseButton = document.getElementById("unlock_close_button");
    purchaseTrigger.addEventListener("click", () => { toggleModal('unlock_modal')});
    purchaseCloseButton.addEventListener("click", () => { toggleModal('unlock_modal')});

    const infoTrigger = document.getElementById("info");
    const infoCloseButton = document.getElementById("info_close_button");
    infoTrigger.addEventListener("click", () => { toggleModal('info_modal')});
    infoCloseButton.addEventListener("click", () => { toggleModal('info_modal')});

    const settingsTrigger = document.getElementById("settings_button");
    const settingsCloseButton = document.getElementById("settings_close_button");
    settingsTrigger.addEventListener("click", () => { toggleModal('settings_modal')});
    settingsCloseButton.addEventListener("click", () => { toggleModal('settings_modal')});

    window.addEventListener("click", windowOnClick);

    document.getElementById("redownload_xcloud_artwork_button").addEventListener("click", downloadXCloudArtwork);

    document.getElementById('gpu_status_button').addEventListener("click", showGPUSettings);
});

function setupCheckbox(){
    const checkbox = document.getElementById('auto_login')
    checkbox.addEventListener('change', function() {
        if (this.checked) {
            console.log("Checkbox is checked..");
            if (window.electronAPI) {
                window.electronAPI.send('auto_login_toggle', {is_set: 1})
            }
        } else {
            console.log("Checkbox is not checked..");
            if (window.electronAPI) {
                window.electronAPI.send('auto_login_toggle', {is_set: 0})
            }
        }
    });
}

// called by main process directly
function disableSplash(){
    console.log('Disable splash')
    clearTimeout(setMainVisible)
    clearTimeout(setSplashHidden)

    document.getElementById('main').style.visibility = 'visible'
    document.getElementById('splash').style.visibility = 'hidden'
    setTimeout(() => {
        try {
            document.getElementById("xhome-tag").focus()
        } catch (err){}
    }, 500)
}

function setupCloseButton(){
    document.getElementById('close_btn').addEventListener("click", () => {
        if (window.electronAPI) {
            window.electronAPI.send("close_app", {});
        }
    });
}

function downloadXCloudArtwork(){
    if (window.electronAPI) {
        window.electronAPI.send("downloadXCloudArtwork", {});
    }
}

function showGPUSettings(){
    if (window.electronAPI) {
        window.electronAPI.send("show_gpu_settings", {});
    }
}

function showToast(message, isSuccess = true){
    console.log('showToast', message, isSuccess)
    try{
        if (isSuccess){
            alertify.success(message);
        } else {
            alertify.warning(message);
        }
    } catch(err){
        console.log(err)
    }
}

function setLoginLoadingVisibility(visible){
    if(visible){
        document.getElementById('login_loading_spinner').style.display = 'block'
    } else {
        document.getElementById('login_loading_spinner').style.display = 'none'
    }
}