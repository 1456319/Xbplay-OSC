const axios = require("axios");

module.exports = class RequestClient {
    hostname
    constructor(hostname) {
        this.hostname = hostname
    }
    async getGSToken(token){
        console.log('Getting gsToken')
        return await axios.post('https://xhome.gssv-play-prod.xboxlive.com/v2/login/user', {
            "token": token,
            "offeringId": "xhome"
        },{
            headers: {
                'x-gssv-client': 'XboxComBrowser'
            },
            timeout: 30000
        }).then(function (response) {
            return response.data['gsToken']
        }).catch(function (error) {
            console.log(error);
            return false;
        });
    }
    async getXCloudToken(token, invalidCountryCodeRetryIp = null, firstRequest = false){
        console.log('Getting getXCloudToken')
        const configData = {
            headers: {
                'x-gssv-client': 'XboxComBrowser'
            }
        }

        // first time ignore invalidCountryCodeRetryIp, then ues it on retries
        if (invalidCountryCodeRetryIp && !firstRequest){
            configData['headers']['X-Forwarded-For'] = invalidCountryCodeRetryIp
        }
        return await axios.post('https://xgpuweb.gssv-play-prod.xboxlive.com/v2/login/user', {
            "token": token,
            "offeringId": "xgpuweb"
        }, configData).then(function (response) {
            return response.data
        }).catch(async (error) => {
            if (!invalidCountryCodeRetryIp){
                console.log('invalidCountryCodeRetryIp set but still failed. Ignore failure')
            } else if(error && error['response'] && error['response']['data'] && error['response']['data']['code'] && error['response']['data']['code'] === 'InvalidCountry'){
                console.log('Failed due to InvalidCountry')
                if (invalidCountryCodeRetryIp && firstRequest){
                    return await this.getXCloudToken(token, invalidCountryCodeRetryIp, false)
                } else {
                    return {InvalidCountry: true}
                }
            }

            console.log('getXCloudToken Error', error);
            return false;
        });
    }

    async getUserData(token){
        console.log('Getting getUserData')
        return await axios.get(`${this.hostname}/api/get_user_data`, {
            headers: {
                'token': token
            }
        }).then(function (response) {
            //console.log(response.data)
            return response.data['purchased']
        }).catch(function (error) {
            //console.log(error);
            return false;
        });
    }

    async getSteamUserData(steamid){
        //console.log('Getting getSteamUserData')
        return await axios.get(`${this.hostname}/api/get_steam_app_data?steamid=${steamid}`, {
        }).then(function (response) {
            //console.log(response.data)
            if (response && response.status && response.status === 200 && response.data){
                if (response.data['appownership'] && response.data['appownership']['ownsapp'] && response.data['appownership']['ownsapp'] === true){
                    return true
                }
            }
            return false
        }).catch(function (error) {
            return false;
        });
    }

    async saveTokens(steamId, gsToken, platform){

        return await axios.post(`${this.hostname}/users/tokens/steam`, {
            "gsToken": gsToken,
            "purchaseToken": steamId,
            "orderId": platform,
        }, {}).then(function (response) {
            return response.data
        }).catch(async (error) => {
            console.log('saveTokens Error', error);
            return false;
        });
    }

    async getTokens(gsToken){
        return await axios.post(`${this.hostname}/users/tokens/get_active`, {
            "gsToken": gsToken
        }, {}).then(function (response) {
            return response.data
        }).catch(async (error) => {
            console.log('getTokens Error', error);
            return false;
        });
    }

    async getXalTokensRequest(data){
        return await axios.post(`${this.hostname}/xal/tokenData`,
            data,
            {}
        ).then(function (response) {
            return response.data
        }).catch(async (error) => {
            console.log('getXalTokens Error', error);
            return false;
        });
    }

    async getConsoles(token){
        console.log('Getting getConsoles')
        return await axios.get('https://uks.core.gssv-play-prodxhome.xboxlive.com/v6/servers/home', {
            headers: {
                'Content-Type': 'application/json; charset=utf-8',
                'Authorization': 'Bearer ' + token,
                'x-gssv-client': 'XboxComBrowser'
            }
        }).then(function (response) {
            return response.data;
        }).catch(function (error) {
            console.log(error);
            return false;
        });
    }
}

//
// module.exports = async function getGSToken(token){
//     console.log('Getting gsToken')
//     return await axios.post('https://xhome.gssv-play-prod.xboxlive.com/v2/login/user', {
//         "token": token,
//         "offeringId": "xgpuweb"
//     }).then(function (response) {
//         return response.data['gsToken']
//     }).catch(function (error) {
//         console.log(error);
//         return false;
//     });
// }

// module.exports = async function getXCloudToken(token){
//     console.log('Getting gsToken')
//     return await axios.post('https://xgpuweb.gssv-play-prod.xboxlive.com/v2/login/user', {
//         "token": token,
//         "offeringId": "xhome"
//     }).then(function (response) {
//         return response.data['gsToken']
//     }).catch(function (error) {
//         console.log(error);
//         return false;
//     });
// }