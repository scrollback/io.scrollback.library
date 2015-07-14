package io.scrollback.library;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class FollowMessage extends JSONMessage {
    public String room;
    public boolean value;

    @Override
    protected void init(JSONObject message) {
        super.init(message);

        try {
            room = json.getString("room");
            value = json.getBoolean("value");
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Error parsing follow message " + e);
        }
    }

    FollowMessage(JSONObject message) {
        super(message);
    }

    FollowMessage(String message) {
        super(message, "follow");
    }
}