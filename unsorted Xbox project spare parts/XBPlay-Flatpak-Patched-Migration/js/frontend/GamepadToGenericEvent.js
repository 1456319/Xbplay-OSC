const GamepadToGenericEvent = {
    ButtonMappings: {
        0: 'A',
        1: 'B',
        15: 'DPadRight',
        14: 'DPadLeft',
        12: 'DPadUp',
        13: 'DPadDown',
        4: 'LeftShoulder',
        5: 'RightShoulder',
    },
    gamepadConnectionListener: null,
    gamepadDisconnectionListener: null,
    gamepadStates: {},
    gamepadSummedState: {},
    listenersById: {},

    // id is required to support side menu closing after modal opening
    ListenForGamepadInputs: function (id){
        console.log('ListenForGamepadInputs')

        // remove any duplicate listeners
        if (this.listenersById[id]){
            clearInterval(this.listenersById[id])
        } if (this.gamepadConnectionListener){
            window.removeEventListener('gamepadconnected', this.gamepadConnectionListener, false)
            window.removeEventListener('gamepaddisconnected', this.gamepadDisconnectionListener, false)
        }

        this.gamepadConnectionListener = this.listenForGamepadConnected.bind(this)
        this.gamepadDisconnectionListener = this.listenForGamepadDisconnected.bind(this)

        window.addEventListener('gamepadconnected', this.gamepadConnectionListener)
        window.addEventListener('gamepaddisconnected', this.gamepadDisconnectionListener)

        // only listen after 300ms
        setTimeout(() => {
            this.pollGamepadState(id)
        }, 300)
    },

    StopListeningForGamepadInputs: function (id) {
        console.log('StopListeningForGamepadInputs', id)

        window.removeEventListener('gamepadconnected', this.gamepadConnectionListener, false)
        window.removeEventListener('gamepaddisconnected', this.gamepadDisconnectionListener, false)
        clearInterval(this.listenersById[id])
    },

    // helper for consumers
    ButtonIndexToKeyValue: function(index) {
        switch (GamepadToGenericEvent.ButtonMappings[index]){
            case 'A':
                return 'Enter'
            case 'B':
                return 'Escape'
            case 'DPadRight':
                return 'ArrowRight'
            case 'DPadLeft':
                return 'ArrowLeft'
            case 'DPadUp':
                return 'ArrowUp'
            case 'DPadDown':
                return 'ArrowDown'
            case 'LeftShoulder':
                return '['
            case 'RightShoulder':
                return ']'
            default:
                console.log('No mappings exist for button', index)
                return false
        }
    },

    listenForGamepadConnected: function (event) {
        console.log('CONNECTED listenForGamepadConnected', event.gamepad)
        const id = event.gamepad.index
        this.gamepadStates[id] = {}
    },

    listenForGamepadDisconnected: function(event){
        console.log('DISCONNECTED listenForGamepadDisconnected', event.gamepad)
        const id = event.gamepad.index
        if (this.gamepadStates[id]){
            delete this.gamepadStates[id]
        }
    },

    pollGamepadState: function(id) {
        this.listenersById[id] = setInterval(() => {
            try {
                const gamepads = navigator.getGamepads();

                gamepads.forEach((myGamepad, gamepadIndex) => {
                    if (!myGamepad) {
                        return;
                    }

                    // set the state for each button
                    myGamepad.buttons.map(e => e.pressed).forEach((isPressed, buttonIndex) => {
                        const axesPress = this.checkIfButtonShouldBePressedFromAxes(myGamepad, buttonIndex);
                        this.setGamepadState(isPressed || axesPress, buttonIndex, gamepadIndex);
                    });
                });

            } catch (err){
            }
        }, 50)
    },

    checkIfButtonShouldBePressedFromAxes: function(myGamepad, buttonIndex){
        let isPressed = false

        try {
            const LeftThumbXAxis = myGamepad.axes[0]
            const LeftThumbYAxis = myGamepad.axes[1]

            if (buttonIndex === 12 && LeftThumbYAxis < -.5) {
                isPressed = true
            } else if (buttonIndex === 13 && LeftThumbYAxis > .5) {
                isPressed = true
            } else if (buttonIndex === 14 && LeftThumbXAxis < -.5) { // left
                isPressed = true
            } else if (buttonIndex === 15 && LeftThumbXAxis > .5) { // right
                isPressed = true
            }
            return isPressed
        } catch (err){
            return isPressed
        }
    },
    setGamepadState: function(isPressed, buttonIndex, gamepadIndex){
        // init gamepad state
        if (!this.gamepadStates[gamepadIndex]){
            this.gamepadStates[gamepadIndex] = {}
        }

        if (this.gamepadStates[gamepadIndex][buttonIndex] === undefined && !isPressed){ // init button state
            this.gamepadStates[gamepadIndex][buttonIndex] = isPressed
        } else if (this.gamepadStates[gamepadIndex][buttonIndex] !== isPressed) {
            this.gamepadStates[gamepadIndex][buttonIndex] = isPressed
        }

        // get the summed state value by looping over the value for all gamepads
        let newSummedState = false
        const gamepadStateKeys = Object.keys(this.gamepadStates)
        for(let i = 0; i < gamepadStateKeys.length; i++){
            const key = gamepadStateKeys[i]
            const state = this.gamepadStates[key]
            newSummedState = newSummedState || state[buttonIndex]
        }

        // emit event if summed gamepad value changed
        if (newSummedState !== this.gamepadSummedState[buttonIndex]){
            this.gamepadSummedState[buttonIndex] = newSummedState

            // emit event
            const customEvent = new CustomEvent('GamepadToGenericEvent', {
                detail: { index: buttonIndex, value: this.gamepadSummedState[buttonIndex], state: this.gamepadSummedState },
            })
            document.dispatchEvent(customEvent)
        }
    },
}