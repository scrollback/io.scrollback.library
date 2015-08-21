package io.scrollback.library;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class FollowMessage extends JSONMessage {
    public String room;
    public String role;

    @Override
    protected void init(JSONObject message) {
        super.init(message);

        try {
            room = json.getString("room");
            role = json.getString("role");
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error parsing follow message " + e);
        }
    }

    public FollowMessage(JSONObject message) {
        super(message);
    }

    public FollowMessage(String message) {
        super(message, "follow");
    }
}
