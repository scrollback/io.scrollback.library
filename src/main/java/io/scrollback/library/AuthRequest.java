package io.scrollback.library;

import org.json.JSONObject;

public class AuthRequest extends JSONMessage {
    public AuthRequest(String request) {
        super(request, "auth");
    }
}
