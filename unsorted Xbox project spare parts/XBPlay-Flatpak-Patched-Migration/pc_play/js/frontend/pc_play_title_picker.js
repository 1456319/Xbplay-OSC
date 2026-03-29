window.addEventListener('load_app_list', (event) => {
    const data = event.detail
    PCPlayTitlePicker.hostName = data['hostName']
    PCPlayTitlePicker.BeginTitleLoadProcess(data['appList'])
});

window.addEventListener('show_toast', (event) => {
    const data = event.detail
    const msg = data['message']
    const isSuccess = data['isSuccess'] ?? true

    if (msg){
        showToast(msg, isSuccess)
    }
});

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

const PCPlayTitlePicker = {
    titleData: null,
    recentTitleData: {},
    currentIndexActiveElement: -1,
    controlsAreEnabled: true,
    hostName: null,

    // name, image, storeDetails
    startStreamFromTitle: function(productId){
        const selectedTileData = this.titleData[productId]
        console.log('Card Clicked', productId, JSON.stringify(selectedTileData), selectedTileData['name'])

        this.addRecentTitle(productId)

        window.dispatchEvent(new CustomEvent("start_stream", {
            detail: {
                titleName: selectedTileData['name'],
                hostName: this.hostName,
                image: selectedTileData['image'],
            }
        }));
    },

    BeginTitleLoadProcess: async function (appList){
        // init ui elements
        this.setCardContainerHeight('cards-content-all')
        this.setCardContainerHeight('cards-content-recent')

        this.setupListeners()

        // loads this.titleData value and caches
        this.titleData = appList

        console.log('titles', this.titleData)
        if (!this.titleData ) {
            console.error('Failed to get title data, aborting')
            return
        }

        this.setupUI('cards-content-all', this.titleData, 'card_all_', false)
        this.hideLoadingSpinner('loading_all')

        // load recent title data tab (dont cache since it can change for every sesh)
        this.recentTitleData = this.loadRecentTitleData(appList)
        this.setupUI('cards-content-recent', this.recentTitleData, 'card_recent_', true)
        this.hideLoadingSpinner('loading_recent')
    },

    setupListeners: function() {
        document.getElementById('search_box').addEventListener('input', (event) => {
            const searchTitle = event.target.value
            this.filterCardsBySearch(searchTitle, this.titleData, 'card_all_')
            this.filterCardsBySearch(searchTitle, this.recentTitleData, 'card_recent_')
            this.currentIndexActiveElement = 3
        })

        window.addEventListener('resize', () => {
            this.setCardContainerHeight('cards-content-all')
            this.setCardContainerHeight('cards-content-recent')
        })

        // Capture the form submission event
        document.getElementById('search_form').addEventListener('submit', (event) => {
            event.preventDefault() // Prevent the default form submission behavior
            console.log('handle search')
            const searchTitle = document.getElementById('search_box').value
            this.filterCardsBySearch(searchTitle, this.titleData, 'card_all_')
            this.filterCardsBySearch(searchTitle, this.recentTitleData, 'card_recent_')
        })

        GamepadToGenericEvent.ListenForGamepadInputs('main')
        document.addEventListener('GamepadToGenericEvent', this.gamepadListener.bind(this))

        console.log('setting key listeners')
        window.addEventListener('keydown', this.interceptKeys.bind(this))
        window.addEventListener('keyup', this.disableKeyUp.bind(this))
    },

    interceptKeys: function(e){
        console.log('interceptKeys', e)

        // ignore movement on main screen when controls disabled (due to direct set from electron)
        if (!this.controlsAreEnabled){
            e.preventDefault()
            return
        }
        const selectableElementsHeader = this.FindChildrenInParent('#search_box, .btn-outline-primary, .nav-item', document.getElementById('navbar_id'))

        const parent = document.querySelectorAll('.tab-pane.active, .tab-pane.show, .tab-pane.active.show')[0]
        const selectableElementsMain = this.FindChildrenInParent('.card', parent)

        // console.log(this.currentIndexActiveElement, document.activeElement)

        const allSelectable = [...selectableElementsHeader, ...selectableElementsMain]
        if (e.key === 'ArrowRight' || e.key === 'ArrowLeft'){
            e.preventDefault()
            this.currentIndexActiveElement = this.MoveToNext(allSelectable, (e.key === 'ArrowRight'), this.currentIndexActiveElement)
        } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            e.preventDefault()
            let distance = 5
            if (window.matchMedia('(orientation: landscape)').matches) {
                // hack to account for one less item in the header
                if (this.currentIndexActiveElement <= 3 && e.key === 'ArrowDown'){
                    distance = 4
                } else if (this.currentIndexActiveElement >= 4 && this.currentIndexActiveElement <= 8 && e.key === 'ArrowUp'){
                    distance = 4
                } else {
                    distance = 5
                }
            } else {
                distance = 2
            }
            this.currentIndexActiveElement = this.MoveToNext(allSelectable, (e.key === 'ArrowDown'), this.currentIndexActiveElement, distance)
        } else if (e.key === 'Enter'){
            e.preventDefault()
            document.activeElement.click()
        } else if (e.key === 'Escape') {
            window.dispatchEvent(new Event("close_pc_play_title_picker"));
        }
    },

    disableKeyUp: function(e){
        console.log('keyup prevent')
        e.preventDefault()
    },

    gamepadListener: function(event){
        const index = event.detail.index
        const value = event.detail.value

        if(!value){
            return
        }

        let fakeEvent = {
            preventDefault: () => {console.log('ignore')},
            key: GamepadToGenericEvent.ButtonIndexToKeyValue(index),
            isGamepadInput: true
        }

        this.interceptKeys(fakeEvent)
    },

    hideLoadingSpinner: function(id){
        document.getElementById(id).style.visibility = 'hidden'
    },

    addRecentTitle: function(id){
        const recentTitlesRaw = localStorage.getItem('recent_titles') || '[]'
        const recentTitlesArray = JSON.parse(recentTitlesRaw)

        const index = recentTitlesArray.indexOf(id);
        if (index !== -1) {
            recentTitlesArray.splice(index, 1);
        }
        recentTitlesArray.unshift(id);
        recentTitlesArray.slice(0, 10);
        localStorage.setItem('recent_titles', JSON.stringify(recentTitlesArray))
    },

    loadRecentTitleData: function (appList) {
        const recentTitlesRaw = localStorage.getItem('recent_titles') || '[]'
        const recentTitlesArray = JSON.parse(recentTitlesRaw)

        let result = {}
        for (let i = 0; i < recentTitlesArray.length; i++){
            const key = recentTitlesArray[i]
            if (appList[key]){
                result[key] = appList[key]
                result[key]['order'] = i
            }
        }

        return result
    },

    filterCardsBySearch: (searchTitle, titleData, cardPrefix) => {
        console.log('Input changed:', searchTitle)
        if (titleData) {
            const productIds = Object.keys(titleData)

            // Loop through the card data and create cards dynamically
            for (let i = 0; i < productIds.length; i++) {
                const productId = productIds[i]
                const data = titleData[productId]
                const wrapper = document.getElementById(cardPrefix + productId)
                const card = document.getElementById(cardPrefix + productId + '_inner')

                if (!card || !wrapper){
                    console.log('cant find card', productId)
                    continue
                }

                if (searchTitle === '' || (data.name !== undefined && data.name.toUpperCase().includes(searchTitle.toUpperCase()))) {
                    card.style.display = ''
                    wrapper.style.display = ''
                } else {
                    card.style.display = 'none'
                    wrapper.style.display = 'none'
                }
            }
        }
    },

    setCardContainerHeight: (id) => {
        const navbarHeight = document.getElementById('navbar_id').offsetHeight
        const cardElement = document.getElementById(id)
        cardElement.style.height = (window.innerHeight - navbarHeight) + 'px'
    },

    setupUI: function (id, titleData, cardPrefix, orderResults = false) {
        console.log(id, titleData)
        // Get the card container
        const cardContainer = document.getElementById(id)
        const productIds = Object.keys(titleData)

        let orderedProductIds = []
        if (orderResults){
            let orderedTitles = {}
            for (let i = 0; i < productIds.length; i++) {
                const productId = productIds[i]
                const data = titleData[productId]
                const order = data['order']
                if (order !== undefined){
                    orderedTitles[order] = productId
                }
            }
            orderedProductIds = Object.values(orderedTitles)
            console.log('ordered titles', orderedProductIds)
        }

        const finalTitles = (orderResults) ? orderedProductIds : productIds

        // Loop through the card data and create cards dynamically
        for (let i = 0; i < finalTitles.length; i++) {
            const productId = finalTitles[i]
            const data = titleData[productId]

            const cardCol = document.createElement('div')
            cardCol.classList.add('col-lg-3', 'col-md-3', 'col-sm-3', 'mb-3', 'custom-col' )
            cardCol.id = cardPrefix + productId

            const cardDiv = document.createElement('div')
            cardDiv.classList.add('card')
            cardDiv.tabIndex = 0
            cardDiv.onclick = (e) => {
                this.setBackground(data['image'], 'bk_image')
                this.startStreamFromTitle(productId)
            }
            cardDiv.id = cardPrefix + productId + '_inner'

            const cardImage = document.createElement('img')
            //cardImage.src = data['image'] || '../images/error.png'
            // cardImage.loading='lazy'
            cardImage.classList.add('card-img-top', 'lozad')
            cardImage.alt = data.name
            cardImage.setAttribute('src', data['image'] || '../images/error.png')

            const cardBody = document.createElement('div')
            cardBody.classList.add('card-body')

            const cardTitle = document.createElement('p')
            cardTitle.classList.add('card-title')
            cardTitle.textContent = data.name || data.id

            // Assemble the card elements
            cardBody.appendChild(cardTitle)
            cardDiv.appendChild(cardImage)
            cardDiv.appendChild(cardBody)
            cardCol.appendChild(cardDiv)

            // Add the card column to the container
            cardContainer.appendChild(cardCol)

            this.setupScroll(cardTitle)
        }
    },

    setupScroll: function(contentDiv) {
        function shouldScroll() {
            return contentDiv.scrollWidth > contentDiv.clientWidth
        }

        let atEnd = false
        function scrollContent() {
            if (shouldScroll()) {
                if (contentDiv.scrollLeft + 1 >= (contentDiv.scrollWidth - contentDiv.clientWidth)) {
                    if (!atEnd){
                        setTimeout(() => {
                            contentDiv.scrollLeft = 0
                        }, 1000)
                        setTimeout(() => {
                            atEnd = false
                        }, 2000)
                        atEnd = true
                    }
                } else if (!atEnd) {
                    contentDiv.scrollLeft += 1 // Adjust the scroll speed
                }
            }
        }

        setInterval(scrollContent, 50) // Adjust the interval for scrolling

    },

    setBackground: async function(url, elementId){
        const element = $('#' + elementId)
        element.fadeOut('fast', () => {
            element.attr('src', url)
        })
        element.on('load', ()=> {
            element.fadeIn('fast')
        })
    },


    // -=-=-=-==-=-=--= DIRECT COPY PASTE OF XBOX-XCLOUD-PLAYER-FORK -=-=--=-=-==-=--==-=-//
    // -==-=--===--=-=-==-=-=-----------==---==--==-=-----------=--==--=-==-=-=-=-=-=-=-=-//
    FindChildrenInParent: function (childClassName, parentElement) {
        // Check if the tabPaneElement exists
        if (parentElement) {
            // Find all child elements with the class "settings_item"
            const settingsItemElements = parentElement.querySelectorAll(childClassName)
            return Array.from(settingsItemElements).filter(element => {

                // ignore element if hidden or parent is hidden, looking 4 levels deep
                return this.isElementShowingRecursive(element)
            })
        } else {
            // Handle the case where the tabPaneElement was not found
            console.error('Element with class not found')
            return []
        }
    },

    isElementShowingRecursive: function(element, currentDepth = 0, maxDepth = 4){
        if (currentDepth >= maxDepth){ // true if reached end
            return true
        } else if (!element){ // true if something wrong with element
            return true
        } else if (!element.parentElement){ // no parent, made it to end, consider not hidden
            return true
        }

        if (this.isElementVisible(element)){
            return this.isElementShowingRecursive(element.parentElement, currentDepth+1, maxDepth)
        } else {
            return false
        }
    },

    isElementVisible: function(element) {
        const isDisabled = element.disabled
        const isHidden = element.style.visible === 'hidden'
        const isDisplayed = element.style.display !== 'none'
        return !isDisabled && !isHidden && isDisplayed
    },

    MoveToNext: function (selectableElements, moveForward, currentIndex, moveCount = 1) {
        // console.log('currentIndex', currentIndex, selectableElements)

        if (currentIndex === -1){
            // console.log('Nothing selected, select first element')
            currentIndex = 0
            selectableElements[currentIndex].focus()
        } else {
            if (moveForward) {
                if (currentIndex >= selectableElements.length - moveCount) {
                    // console.log('At end')
                } else {
                    currentIndex += moveCount
                    selectableElements[currentIndex].focus()
                    // console.log('Focus next', selectableElements[currentIndex])
                }
            } else {
                if (currentIndex - moveCount < 0) {
                    // console.log('At beginning')
                } else {
                    currentIndex -= moveCount
                    selectableElements[currentIndex].focus()
                    // console.log('Focus prev', selectableElements[currentIndex])
                }
            }
        }
        return currentIndex
    },
}
