let port = browser.runtime.connectNative("browser");

function isValidData(data) {
    if (data === null) return true;
    const type = typeof data;
    if (type === 'string' || type === 'number' || type === 'boolean') return true;
    if (type === 'object') {
        if (Array.isArray(data)) {
            return data.every(isValidData);
        }
        // Ensure it's a plain object
        try {
            const proto = Object.getPrototypeOf(data);
            if (proto === Object.prototype || proto === null) {
                for (const key in data) {
                    if (Object.prototype.hasOwnProperty.call(data, key)) {
                        if (!isValidData(data[key])) return false;
                    }
                }
                return true;
            }
        } catch (e) {
            return false;
        }
    }
    return false;
}

// from app to webpage
port.onMessage.addListener(response => {
    try {
        // Let's just echo the message back
        console.log("Received message for firefox", response);

        if (!isValidData(response)) {
            console.error("Invalid data received from native port", response);
            return;
        }

        // we have to use this clone into function otherwise there are permission errors
        var clonedDetail = cloneInto(response, document.defaultView);

        // emit event
        var event = new CustomEvent('geckoview', { detail: clonedDetail });
        document.dispatchEvent(event);

        // log data
        console.log("Dispatched geckoview event!");
    } catch (err){
        console.error("Geckoview emit error", err)
    }
});

// from webpage to app
document.addEventListener('geckoview-event', (event) => {
    console.log("Caught geckoview event", event.detail)
    port.postMessage(JSON.stringify(event.detail));
})