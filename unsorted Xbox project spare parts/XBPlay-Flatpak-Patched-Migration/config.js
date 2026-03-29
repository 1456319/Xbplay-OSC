config = {

    // active
    "hostname": "https://www.xbgamestream.com",
    "useDev": false,
    "isSteamVersion": false,
    "expectedPCPlayVersion": "1.0.1" // should NOT match pcplayversion in settings. Only used to display message if installed client is less than this

    // for dev testing
    // // "hostname": "http://localhost:3000", // for local server tests
    // "useDev": true,
    // "isSteamVersion": false,
    // "expectedPCPlayVersion": "5.0.1" // should match pcplayversion in settings. Only used to display message if installed client is less than this


    // for steam store build
    // "hostname": "https://www.xbgamestream.com",
    // "useDev": false,
    // "isSteamVersion": true,
    // "expectedPCPlayVersion": "5.0.1" // should match pcplayversion in settings. Only used to display message if installed client is less than this

    // // for direct raw distribution build
    // "hostname": "https://www.xbgamestream.com",
    // "useDev": false,
    // "isSteamVersion": false,
    // "expectedPCPlayVersion": "5.0.1" // should match pcplayversion in settings. Only used to display message if installed client is less than this
}
module.exports = config;
