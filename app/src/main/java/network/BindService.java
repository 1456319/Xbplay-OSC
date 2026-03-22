package network;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import Interfaces.SmartglassEvents;

public class BindService extends Service {
    public boolean ready = false;
    private final IBinder binder = new MyBinder();
    private SmartglassEvents listener;

    public class MyBinder extends Binder {
        public BindService getService() {
            return BindService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setListener(SmartglassEvents listener) {
        this.listener = listener;
    }

    public void discover() {}
    public void connect() {}
    public void openChannels() {}
    public String getLiveId() { return ""; }
    public void powerOn(String liveId) {}
    public void powerOff() {}
    public void sendSystemInputCommand(byte[] command) {}
    public void sendSystemInputSequence(byte[][] sequence, int speed) {}
}
