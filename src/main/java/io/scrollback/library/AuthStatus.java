package io.scrollback.library;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class AuthStatus extends JSONMessage {
    public String status;

    @Override
    protected void init(JSONObject message) {
        super.init(message);

        try {
            status = message.getString("status");
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error parsing auth status " + e);
        }
    }

    AuthStatus(JSONObject message) {
        super(message);
    }
}
