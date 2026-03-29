// This is required because the steam deck can crash if the keyboard automatically opens too quickly. This disables
// automatic keyboard opening completely by requiring that the user press Steam+x to open the keyboard
const SteamOSKeyboardFix = {

    getIsSteam: function() {
        return true
    },

    DisableInputFields: function (){
        console.log('Checking DisableInputFields')

        try {
            if (!this.getIsSteam()){
                console.log('Ignoring DisableInputFields due to not steam build')
                return
            }

            // Select all input elements
            const inputs = document.querySelectorAll('input[type="text"], input[type="search"]');

            // Iterate over the NodeList and set each to readonly
            inputs.forEach(input => {

                if (input.hasAttribute('readonly')) {
                    console.log('Dont set listener twice')
                    return
                }

                input.setAttribute('readonly', true);
                if (input.placeholder) {
                    input.placeholder += ' (SteamKey+X)'
                } else {
                    input.placeholder = 'Press SteamKey+X to type'
                }

                input.addEventListener('keydown', function (event) {
                    try {
                        event.preventDefault()

                        let currentValue = input.value

                        // Handle different keys
                        if (event.key === 'Backspace') {
                            if (currentValue.length > 0) {
                                currentValue = currentValue.slice(0, -1)
                            }
                        } else if (event.key.length === 1) {
                            currentValue += event.key
                        }
                        input.value = currentValue

                        if (input.onchange) {
                            input.onchange()
                        }

                        // Dispatch custom input event with updated value
                        const inputEvent = new CustomEvent('input', {
                            detail: {value: currentValue},
                            bubbles: true,
                        });
                        input.dispatchEvent(inputEvent);
                    } catch (err){
                        console.error('Error catching keyboard input')
                    }
                });
            });
        } catch (err){
            console.error('Error disabling inputs', err)
        }
    },
}