window.addEventListener('DOMContentLoaded', () => {
    console.log('loaded')
    document.getElementById('pcPlayAddHost').addEventListener("click", () => { toggleModal('add_host_modal')});
    document.getElementById('pcPlaySettings').addEventListener("click", () => { toggleModal('settings_modal')});
    document.getElementById('pcPlayHostSaveButton').addEventListener("click", () => { saveNewHost()});
    document.getElementById('pcPlayHostSearchButton').addEventListener("click", () => { searchHosts()});
    document.getElementById('pcPlayConfigureClient').addEventListener("click", () => { configureClient()});
    document.getElementById('pcPlayUninstallClient').addEventListener("click", () => { uninstallClient()});
    document.getElementById('pcPlayOpenClient').addEventListener("click", () => { pcPlayOpenClient()});
    document.getElementById('pcPlayAddHostCloseButton').addEventListener("click", () => { toggleModal('add_host_modal')});
    document.getElementById('pcPlaySettingsCloseButton').addEventListener("click", () => { toggleModal('settings_modal')});
    document.getElementById('close_btn').addEventListener("click", () => {
        window.dispatchEvent(new Event("close_pc_play"));
    });
    window.addEventListener("click", windowOnClick);

    SettingsClient.initSettingsWithSavedValues('pc_play')
    GamepadToGenericEvent.ListenForGamepadInputs('pc_play')
    //gamepadListener defined in sharedMenuNavFunctions
    document.addEventListener('GamepadToGenericEvent', gamepadListener)
});

// hit by renderer directly
function refocus(){
    focusFirstElement()
}

// using reveal instead
// function showLicenseAccept() {
//     alertify.defaults.glossary.title = ''
//     const pre = document.createElement('pre');
//     pre.style.maxHeight = "400px";
//     pre.style.margin = "0";
//     pre.style.padding = "24px";
//     pre.style.whiteSpace = "pre-wrap";
//     // pre.style.textAlign = "justify";
//     pre.style.overflowX = 'hidden'
//     pre.style.overflowY = 'auto'
//     pre.appendChild(document.createTextNode(LICENSE_TEXT));
//     alertify.confirm(pre, function(){
//         window.dispatchEvent(new CustomEvent("configure_client", {
//             detail: {}
//         }));
//     },function(){
//         alertify.error('Declined');
//     }).set({labels:{ok:'Agree and Install', cancel: 'Decline'}, padding: false});
// }

window.addEventListener('update_pc_play_installed_text', (event) => {
    console.log('update_pc_play_installed_text', event.detail)
    const data = event.detail
    const isInstalled = data['isInstalled'] ?? false
    updateClientInstalledStatus(isInstalled)
});

function updateClientInstalledStatus(isInstalled) {
    const installedDiv = document.getElementById('steamcheck-div')
    if (!installedDiv){
        console.error('cant find steamcheck-div')
        return
    }

    if (isInstalled){
        installedDiv.innerText = 'PC Play Installed: Yes'
    } else {
        installedDiv.innerText = 'PC Play Installed: No'
    }
}


window.addEventListener('load_saved_hosts', (event) => {
    console.log('load_saved_hosts', event.detail)

    const data = event.detail
    // const data =  { MSI: 'MSI 2', AlexMacBook: 'AlexMacBook' }
    const hostNameKeys = Object.keys(data)

    // delete any existing hosts so we can call this multiple times
    const scrollWrapper = document.querySelector('.scroll-wrapper');
    scrollWrapper.innerHTML = '';

    let firstButton = null
    for (let i = 0; i < hostNameKeys.length; i++){
        const hostName = hostNameKeys[i]
        const displayName = data[hostName]

        const pcPlayHostDiv = document.createElement('div');
        pcPlayHostDiv.classList.add('pcPlayHost');

        const title = document.createElement('div');
        title.innerText = displayName || hostName
        title.classList.add('pcPlayHostTitle', 'hostDivLayout', 'glowing-button10')

        const openClientButton = document.createElement('button');
        openClientButton.id = 'open_client_button_' + i
        openClientButton.classList.add('hostOpenClientButton', 'hostDivLayout', 'custom_focusable', 'glowing-button10', );
        openClientButton.textContent = `OPEN CLIENT`;
        openClientButton.onclick = () => {
            console.log('openClientButton', hostName)
            window.dispatchEvent(new CustomEvent('host_open_client', {
                detail: { hostName: hostName }
            }));
        }

        const listApps = document.createElement('button');
        listApps.id = 'list_apps_button_' + i
        listApps.classList.add('hostListAppsButton', 'hostDivLayout', 'custom_focusable', 'glowing-button10', );
        listApps.textContent = `SELECT GAME`;
        listApps.onclick = () => {
            console.log('listApps', hostName)
            window.dispatchEvent(new CustomEvent('host_list_apps', {
                detail: { hostName: hostName }
            }));
        }

        const connectButton = document.createElement('button');
        connectButton.id = 'connect_button_' + i
        connectButton.classList.add('hostConnectButton', 'hostDivLayout', 'custom_focusable', 'glowing-button10', );
        connectButton.textContent = `SYNC`;
        connectButton.onclick = () => {
            console.log('connect', hostName)
            window.dispatchEvent(new CustomEvent('host_connect', {
                detail: { hostName: hostName }
            }));
        }

        const deleteButton = document.createElement('button');
        deleteButton.classList.add('hostDeleteButton', 'hostDivLayout', 'custom_focusable', 'glowing-button10');
        deleteButton.textContent = 'DELETE';
        deleteButton.onclick = () => {
            console.log('delete', hostName)
            window.dispatchEvent(new CustomEvent('host_delete', {
                detail: { hostName: hostName }
            }));
        }

        // Append buttons to the pcPlayHostDiv
        pcPlayHostDiv.appendChild(title);
        pcPlayHostDiv.appendChild(listApps);
        pcPlayHostDiv.appendChild(openClientButton);

        pcPlayHostDiv.appendChild(connectButton);
        pcPlayHostDiv.appendChild(deleteButton);

        // Get the scroll-wrapper element and append the pcPlayHostDiv to it
        const scrollWrapper = document.querySelector('.scroll-wrapper');
        scrollWrapper.appendChild(pcPlayHostDiv);

        if(i === 0){
            firstButton = listApps
        }
    }

    if(firstButton){
        document.getElementById('close_btn').setAttribute('data-sn-down', '#' + firstButton.id);
    } else {
        document.getElementById('close_btn').removeAttribute('data-sn-down');
    }

    setupNav(firstButton)
})

window.addEventListener('show_toast', (event) => {
    const data = event.detail
    const msg = data['message']
    const isSuccess = data['isSuccess'] ?? true

    if (msg){
        showToast(msg, isSuccess)
    }
});

let downloadTimeout = null
window.addEventListener('download_percent', (event) => {
    const data = event.detail
    const msg = data['message']

    const downloadPercentElement = document.getElementById('download_percent')
    if (msg && downloadPercentElement){
        downloadPercentElement.style.display = 'block'
        downloadPercentElement.innerText = 'Download Progress: ' + msg

        clearTimeout(downloadTimeout)
        downloadTimeout = setTimeout(() => {
            downloadPercentElement.style.display = 'none'
        }, 2000)
    }
});

window.addEventListener('set_loading_visibility', (event) => {
    const data = event.detail
    const isLoading = data['isLoading'] ?? false
    setLoadingVisibility(isLoading)
});

function uninstallClient() {
    console.log('uninstallClient')
    window.dispatchEvent(new CustomEvent("uninstall_client", {
        detail: {}
    }));
    toggleModal('settings_modal')
    focusFirstElement()
}
function pcPlayOpenClient() {
    console.log('pcPlayOpenClient')
    window.dispatchEvent(new CustomEvent('host_open_client', {
        detail: { }
    }));
    toggleModal('settings_modal')
    focusFirstElement()
}
function configureClient() {
    console.log('configureClient')
    //showLicenseAccept()
    toggleModal('settings_modal')
    window.dispatchEvent(new CustomEvent("configure_client", {
        detail: {}
    }));
    focusFirstElement()
}

function searchHosts(){
    toggleModal('add_host_modal')

    window.dispatchEvent(new CustomEvent("host_search", {
        detail: { }
    }));
}

function saveNewHost(){
    const hostName = document.getElementById('pcPlayHostName').value
    const displayName = document.getElementById('pcPlayHostDisplayName').value

    console.log('hostName', hostName)
    console.log('displayName', displayName)

    if (!hostName){
        showToast('Host name required', false)
    } else {
        showToast('Adding host...')
        toggleModal('add_host_modal')

        window.dispatchEvent(new CustomEvent("host_added", {
            detail: { hostName: hostName, displayName: displayName }
        }));
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

function setLoadingVisibility(visible){
    if(visible){
        document.getElementById('loading_spinner').style.display = 'block'
    } else {
        document.getElementById('loading_spinner').style.display = 'none'
    }
}