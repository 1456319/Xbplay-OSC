const {dialog} = require("electron");
const PersistClient = require('../persist_client')

module.exports = class LoginStatusEvents {
    gsTokenFailureCount = 0
    status = {
        waiting: "Waiting",
        failed: "Failed",
        complete: "Complete",
        loading: "Loading" // an api request is currently being made (awaiting response)
    }
    gsTokenStatus = this.status.waiting
    xCloudTokenStatus = this.status.waiting
    consoleStatus = this.status.waiting
    msalStatus = this.status.waiting

    checkIfComplete() {
        if (
            this.gsTokenFailureCount < 3 && (
            this.gsTokenStatus === this.status.waiting || this.gsTokenStatus === this.status.loading ||
            this.xCloudTokenStatus === this.status.waiting || this.xCloudTokenStatus === this.status.loading ||
            this.consoleStatus === this.status.waiting || this.consoleStatus === this.status.loading ||
            this.msalStatus === this.status.waiting || this.msalStatus === this.status.loading )) { //1. If anything is still waiting, consider not complete
            console.log('Still waiting/loading',
                'this.gsTokenFailureCount', this.gsTokenFailureCount,
                'this.gsTokenStatus', this.gsTokenStatus,
                'this.xCloudTokenStatus', this.xCloudTokenStatus,
                'this.consoleStatus', this.consoleStatus,
                'this.msalStatus', this.msalStatus
                )
            return this.status.waiting
        } else if ( // once gsTokenFailureCount > 3 then this.gsTokenStatus will always be false. So we will always get in here if failure is due to gsTokenFailureCount
            this.gsTokenStatus === this.status.failed) { // since consoleStatus is set called after gsToken is validated, we shouldnt have false positives from polling an old gsToken too soon
            console.log('xHome Failed')
            const options = {
                type: 'warning',
                buttons: ['Ok'],
                title: 'Login Failed',
                message: 'Failed to Login! ',
                detail: 'Possible Solutions: (1) Check if there is an Xbox Live outage (2) Open the settings and clear the cache (3) Temporarily connect to a different WiFi network, such as a mobile hotspot.',
            };
            dialog.showMessageBox(null, options)
            return this.status.failed
            // return this.status.waiting
        } else if (this.consoleStatus === this.status.failed) {
            console.log('xHome Failed')
            return this.status.complete
        } else if (
            this.xCloudTokenStatus === this.status.failed ||
            this.msalStatus === this.status.failed) { // 3. if we cant get xCloud token or msal token, (but nothing else failed or waiting) consider complete (without xCloud)
            console.log('xCloud Failed')
            return this.status.complete
        } else { // everything worked
            console.log('Login Worked',
                'this.gsTokenFailureCount', this.gsTokenFailureCount,
                'this.gsTokenStatus', this.gsTokenStatus,
                'this.xCloudTokenStatus', this.xCloudTokenStatus,
                'this.consoleStatus', this.consoleStatus,
                'this.msalStatus', this.msalStatus
            )
            return this.status.complete
        }
    }
}