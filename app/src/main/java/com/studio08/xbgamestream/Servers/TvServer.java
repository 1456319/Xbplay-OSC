package com.studio08.xbgamestream.Servers;

import android.text.TextUtils;

import java.io.IOException;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class TvServer extends NanoHTTPD {
    public static final int PORT = 9087;
    private ConnectionListener listener;
    public interface ConnectionListener {
        void onValidConnection(String gsToken, String serverId, String xcloudToken, String msalToken);
    }

    public TvServer(ConnectionListener listener) throws IOException {
        super(PORT);
        this.listener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        if (uri.equals("/startSession")) {
            Map<String, String> params = session.getParms();
            String gsToken = params.get("gsToken");
            String serverId = params.get("serverId");
            String xcloudToken = params.get("xcloudToken");
            String msalToken = params.get("msalToken");

            if(TextUtils.isEmpty(gsToken) || TextUtils.isEmpty(serverId)) {
                return newFixedLengthResponse("Error 1038931");
            }
            listener.onValidConnection(gsToken, serverId, xcloudToken, msalToken);
            return newFixedLengthResponse("ok");
        }
        return null;
    }
}