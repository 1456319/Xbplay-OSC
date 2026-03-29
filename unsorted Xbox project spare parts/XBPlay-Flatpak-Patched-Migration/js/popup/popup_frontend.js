window.addEventListener('DOMContentLoaded', () => {
	TranslateClient.LoadDynamicPhrases()
});


// buttons [ok, no]
// dispatches event with response being the id of the button clicked

let alertId = 'main'
async function showAlert(options, id){
		try {
			// wait incase its not loaded yet
			await TranslateClient.LoadDynamicPhrases()

			alertId = id
			options = JSON.parse(decodeURIComponent(options))
			console.log(options)

			const title = TranslateClient.TranslatePhrase(options['title'])
			const message = TranslateClient.TranslatePhrase(options['message'])
			const detail = TranslateClient.TranslatePhrase(options['detail'])

			if(options['buttons'] && options['buttons'].length > 1) {
				const button1 = TranslateClient.TranslatePhrase(options['buttons'][0])
				const button2 = TranslateClient.TranslatePhrase(options['buttons'][1])


				alertify.confirm(title, message + '<br>' + (detail || ''),
					function(){
					    // alertify.success(options['buttons'][0]);
						console.log('alertify', 'Button 0 pressed')

					    if (window.popupAPI) {
                            window.popupAPI.send("alertify_confirm", {
                                response: 0,
                                id: alertId
                            });
                        }
					 },
					function(){
					    // alertify.error(options['buttons'][1]);
						console.log('alertify', 'Button 1 pressed')

					    if (window.popupAPI) {
                            window.popupAPI.send("alertify_confirm", {
                                response: 1,
                                id: alertId
                            });
                        }
					}
				).setting({
					'labels': { ok: button1, cancel: button2 },
					'closable': false,
					'movable': false,
					// 'invokeOnCloseOff': true,
					// 'closableByDimmer': false,
					// 'onclose': function(event){
					// 	console.log('ALERT WAS closed', 'onClose', event)
					// },
					// 'oncancel': async function(closeEvent){
					// 	console.log('cancel', closeEvent)
					//
					// 	// this is gross. Wait enough time for the esc key listener to send the event first.
					// 	return setTimeout(() =>{
					// 		return true
					// 	}, 500)
					// }
				});
			} else {
				alertify.alert(title, message + '<br>' + (detail || ''),
					function(){
					    // alertify.success(okButton);

					    if (window.popupAPI) {
                            window.popupAPI.send("alertify_confirm", {
                                response: 0,
                                id: alertId
                            });
                        }
					 },
				).setting({
					'closable': false,
					'movable': false,
				});
			}

		} catch (err){
			console.error(err);
		}

	}

// wait 300ms before accepting gamepad input
setTimeout(async() => {
	listenForGamepadEvents()
}, 300)

window.addEventListener('keydown', function escKeyListener(event) {
	if (event.key === "Escape") {
		event.preventDefault();
		console.log('Escape key pressed!')

		// send the cancel event with canceled: true. This happens before oncancel above due to the timeout
		if (window.popupAPI) {
            window.popupAPI.send("alertify_confirm", {
                response: 1, canceled: true, id: alertId // CANCEL BUTTON IS ALWAYS THE 2ND BUTTON (1)
            });
        }
	}
});

function listenForGamepadEvents(){

	// setup gamepad listener on start
	GamepadToGenericEvent.ListenForGamepadInputs('main')
	document.addEventListener('GamepadToGenericEvent', gamepadListener)
}

function gamepadListener(event) {
	const index = event.detail.index
	const value = event.detail.value

	if(!value){
		return
	}

	const key = GamepadToGenericEvent.ButtonMappings[index]

	if (key && key.includes("DPad")){
		moveSafe(key)
	} else if (key === 'A'){
		simulateClick(true)
	} else if (key === 'B'){
		simulateClick(false)
	}
}
function moveSafe(){

	const okButton = document.getElementsByClassName('ajs-button ajs-ok')[0]
	const noButton = document.getElementsByClassName('ajs-button ajs-cancel')[0]
	console.log(okButton, noButton)
    if (document.activeElement.classList.contains('ajs-ok') && noButton) {
    	console.log('focus no button')
    	noButton.focus()
    } else if (document.activeElement.classList.contains('ajs-cancel') && okButton){
    	console.log('focus ok button')
    	okButton.focus()
    } else {
    	okButton.focus()
    	console.log('dont know what to do...')
    }
}

function simulateClick(isA){
	console.log('simulate click')
	if (isA){
		if (document.activeElement.classList.contains('ajs-ok')) {
			if (window.popupAPI) {
                window.popupAPI.send("alertify_confirm", {
                    response: 0, id: alertId // OK button is always first index
                });
            }
		} else {
			if (window.popupAPI) {
                window.popupAPI.send("alertify_confirm", {
                    response: 1, id: alertId
                });
            }
		}
	} else { // back button send no data
			console.log('sending cancel command')
			if (window.popupAPI) {
                window.popupAPI.send("alertify_confirm", {
                    response: 1, canceled: true, id: alertId // CANCEL BUTTON IS ALWAYS THE 2ND BUTTON (1)
                });
            }
	}
}

function setControls(value){
	console.log('ignore setControls call', value)
}
