// IMPORTANT:
// To use this you must first import the GamepadToGenericEvent script and the SettingsClient first
// You must also have all the required settings modals

function focusFirstElement() {
    const mainMenuStartElement = document.getElementById('xhome-tag')
    const pcPlayStartGameButton = document.getElementById('list_apps_button_0')
    const pcPlayBackButton = document.getElementById('close_btn')
    const loadingAll = document.getElementById('firstTab')

    if (mainMenuStartElement) {
        mainMenuStartElement.focus()
    } else if (pcPlayStartGameButton) {
        pcPlayStartGameButton.focus()
    } else if (pcPlayBackButton) {
        pcPlayBackButton.focus()
    } else if (loadingAll){
        loadingAll.focus()
    } else {
        console.error('no first element to focus!')
    }
}

function setupNav(focusElement = false){
    console.log('setupNav')

    //SpatialNavigation.uninit()

    // Initialize
    SpatialNavigation.init();


    // Define navigable elements (anchors and elements with "focusable" class).
    SpatialNavigation.add({
        selector: '[data-sn-left], [data-sn-right], [data-sn-up], [data-sn-down], .custom_focusable',
    });

    // Make the *currently existing* navigable elements focusable.
    SpatialNavigation.makeFocusable();

    if(focusElement){
        console.log('Focusing', focusElement)
        focusElement.focus()
    } else {
        focusFirstElement()
    }

    // Focus the first navigable element.
    //SpatialNavigation.focus();
}

//modal stuffs
function toggleModal(modalId) {
    const modal = document.getElementById(modalId);
    modal.classList.toggle("show-modal");

    // set the first settings item to focused if it exists
    if(modal.classList.contains('show-modal')){
        const elementsWithClass = modal.querySelectorAll('.settings_focusable');
        if (elementsWithClass[0]){
            elementsWithClass[0].focus()
        }
    } else { // save settings on close
        SettingsClient.saveCurrentSettingValues()
    }
    return modal.classList.contains('show-modal')
}

function windowOnClick(event) {
    console.log('Window Click Event')
    const elements = document.getElementsByClassName('show-modal')
    if(!elements[0]){
        if (document.activeElement.id == 'body'){
            focusFirstElement()
        }
        return;
    }
    const modalId  = elements[0].id;
    if (modalId && event.target === elements[0]) {
        toggleModal(modalId);
        focusFirstElement()
    }
}



let controlsAreEnabled = true
function setControls(enable){
    console.log('setControls', enable)
    controlsAreEnabled = enable
}

function gamepadListener(event) {
    const index = event.detail.index
    const value = event.detail.value

    if(!value || !controlsAreEnabled){
        return
    }

    const key = GamepadToGenericEvent.ButtonMappings[index]
    moveSafe(key)
}

function moveSafe(key){

    if (key === 'A'){
        simulateClick()
        return
    } else if (key === 'B'){
        simulateBack()
        return
    }

    const selectModal = document.getElementById('select_modal')
    const settingsModal = document.getElementById('settings_modal')
    const addHostModal = document.getElementById('add_host_modal')
    const infoModal = document.getElementById('info_modal')

    if(selectModal && selectModal.classList.contains('show-modal')){
        console.log('select dropdown nav')
        const index = document.activeElement.getAttribute('index');
        let nextElementName = 'fake';

        if(key === 'DPadDown' || key === 'DPadRight') {
            nextElementName = 'select_modal_button_' + (+index+1)
        } else {
            nextElementName = 'select_modal_button_' + (+index-1)
        }

        const nextFocus = document.getElementById(nextElementName)
        if (nextFocus){
            console.log('Focusing', nextFocus, nextElementName)
            nextFocus.focus()
        } else {
            console.log('Error nav popup select')
        }
    } else if(
        (settingsModal && settingsModal.classList.contains('show-modal')) ||
        (infoModal && infoModal.classList.contains('show-modal')) ||
        (addHostModal && addHostModal.classList.contains('show-modal'))
    ) {
        console.log('detected settings shown')
        let modalId = null
        if (settingsModal && settingsModal.classList.contains('show-modal')){
            modalId = settingsModal
        } else if (infoModal && infoModal.classList.contains('show-modal')){
            modalId = infoModal
        } else if (addHostModal && addHostModal.classList.contains('show-modal')){
            modalId = addHostModal
        } else {
            console.error('invalid settings move, no active settings modal')
            return
        }
        moveToNextSettingsModal(modalId, key)
    } else {
        switch (key){
            case 'DPadUp':
                SpatialNavigation.move('up')
                break;
            case 'DPadRight':
                SpatialNavigation.move('right')
                break
            case 'DPadLeft':
                SpatialNavigation.move('left')
                break
            case 'DPadDown':
                SpatialNavigation.move('down')
                break
            default:
                console.log('Unknown gamepad event', key)
        }
    }
}

function moveToNextSettingsModal(modal, key){
    // Get all buttons within the modal
    let activeItemIndex = -1
    let elements = []
    const elementsWithClass = modal.querySelectorAll('.settings_focusable');

    elementsWithClass.forEach(function(item, index) {
        if (document.activeElement.id === item.id){
            activeItemIndex = index
        }
        elements.push(item)
    });

    if(activeItemIndex === -1){
        activeItemIndex = 0
    } else if(key === 'DPadDown' || key === 'DPadRight') {
        activeItemIndex++
    } else {
        activeItemIndex--
    }
    activeItemIndex = Math.min(Math.max(activeItemIndex, 0), elements.length - 1);
    elements[activeItemIndex].focus()

    console.log('focusing', elements[activeItemIndex], activeItemIndex)
}

function simulateBack(){
    console.log('back button press')

    const elements = document.getElementsByClassName('show-modal')
    const maxIndex = (elements.length > 0) ? elements.length - 1 : 0
    if(!elements[maxIndex]){
        // emit back press for pc_play screen
        if (window.electronAPI) {
            window.electronAPI.send("close_pc_play");
        } else if (window.pcPlayAPI) {
            window.pcPlayAPI.send("close_pc_play");
        }
        return;
    }

    const modalId  = elements[maxIndex].id;
    console.log('closing modal due to back button press', modalId)
    toggleModal(modalId)

    // only focus home screen if multiple modals open
    if (maxIndex === 0) {
        setupNav()

    }
}

function simulateClick(){
    let activeElement = document.activeElement
    console.log(activeElement)
    if (activeElement){
        console.log('clicked')
        activeElement.click();

        if (activeElement.classList.contains('button-dropdown-menu') || activeElement.classList.contains('settings-dropdown-menu')){
            console.log('detected options dropdown select')
            const isShowing = toggleModal('select_modal');
            if(isShowing){
                fillSelectModal(activeElement)
            }
        }
    }
}

function fillSelectModal(activeElement){
    const modal = document.getElementById('select_modal_body');
    modal.innerText = ''
    let selectedItemIndex = 0

    const selectTitle = activeElement.getAttribute('selectTitle')
    if(selectTitle){
        document.getElementById('select_modal_header').innerText = selectTitle
    }

    for(let i = 0; i < activeElement.length; i++){
        console.log(activeElement[i])
        const item = activeElement[i]

        const button = document.createElement("button");
        button.id = 'select_modal_button_' + i
        button.setAttribute('index', i)
        button.classList.add("custom_focusable_popup");
        // button.style.cssText = 'background: transparent;width: 100%; border: none'
        button.style.width = '100%'
        button.style.background = 'transparent'
        button.style.border = 'none'

        button.innerText = item['text']
        button.onclick = function () {
            activeElement.value = item['value']
            toggleModal('select_modal')
            setupNav(activeElement)

            // call changed listener on element
            const changeEvent = new Event('change');
            activeElement.dispatchEvent(changeEvent);
        }
        if(item['value'] == activeElement.value){
            selectedItemIndex = i
        }

        button.addEventListener("focus", function () {
            console.log('Focusing button', item['text'])
            this.style.backgroundColor = "darkgreen";
        });

        button.addEventListener("blur", function() {
            console.log('blur button', item['text'])
            this.style.backgroundColor = "transparent";
        });
        modal.appendChild(document.createElement("hr"));
        modal.appendChild(button);
    }

    SpatialNavigation.uninit()


    console.log('Selecting item in popup: ', selectedItemIndex)
    const itemToSelect = document.getElementById('select_modal_button_' + selectedItemIndex);
    itemToSelect.focus()
    console.log(itemToSelect)
}