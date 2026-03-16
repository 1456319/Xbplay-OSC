package com.studio08.xbgamestream.Web;

public interface StreamWebviewListener {
    void onReLoginRequest();
    void closeScreen();
    void pressButtonWifiRemote(String type);
    void setOrientationValue(String value);
    void vibrate();
    void genericMessage(String type, String msg);
}