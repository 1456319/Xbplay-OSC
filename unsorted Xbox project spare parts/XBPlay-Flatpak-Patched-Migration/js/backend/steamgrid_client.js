const path = require('path');
const fs = require('fs');
const os = require('os');
const config = require('./../../config.js');
const { app } = require('electron')
const axios = require("axios");


// only used if isSteamVersion = false
module.exports = class SteamGridClient {
    apiKey = '088da3e49f7462415f03d2960e8900d1'
    hostname = 'https://www.steamgriddb.com/api/v2/'

    constructor() {
    }

    async getArtworkFromGame(gameName) {
        try {
            const gameId = await this.searchGamesApi(gameName)

            if (gameId){
                const heroUrl = await this.getHeroApi(gameId)
                const capsuleUrl = await this.getCapsuleApi(gameId)
                return {
                    heroUrl: heroUrl,
                    capsuleUrl: capsuleUrl
                }
            }
        } catch (err){
            console.error(err)
        }
        return null
    }

    async searchGamesApi(searchString){
        console.log('Getting games')
        return await axios.get(this.hostname + `search/autocomplete/${encodeURIComponent(searchString)}`,{
            headers: {
                'Authorization': 'Bearer ' +  this.apiKey
            },
            timeout: 15000
        }).then(function (response) {
            console.log(response.data)
            return response?.data?.data[0]?.id ?? false;
        }).catch(function (error) {
            console.log(error);
            return false;
        });
    }

    async getHeroApi(gameId){
        console.log('Getting hero')
        return await axios.get(this.hostname + `heroes/game/${gameId}?dimensions=1920x620`,{
            headers: {
                'Authorization': 'Bearer ' +  this.apiKey
            },
            timeout: 15000
        }).then(function (response) {
            console.log(response.data)
            return response?.data?.data[0]?.url ?? false;
        }).catch(function (error) {
            console.log(error);
            return false;
        });
    }

    async getCapsuleApi(gameId){
        console.log('Getting capsule')
        return await axios.get(this.hostname + `grids/game/${gameId}?dimensions=600x900`,{
            headers: {
                'Authorization': 'Bearer ' +  this.apiKey
            },
            timeout: 15000
        }).then(function (response) {
            console.log(response.data)
            return response?.data?.data[0]?.url ?? false;
        }).catch(function (error) {
            console.log(error);
            return false;
        });
    }
}