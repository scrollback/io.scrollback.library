package io.scrollback.library;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONMessage {
    protected JSONObject json;

    protected void init(JSONObject message) {
        json = message;
    }

    JSONMessage(JSONObject message) {
        init(message);
    }

    JSONMessage(String message, String type) {
        try {
            JSONObject j = new JSONObject(message);

            if (j != null) {
                try {
                    json.put("type", type);
                } catch (JSONException e) {
                    Log.e(Constants.TAG, "Failed to set type for " + type + " " + e);
                }
            }

            init(j);
        } catch (JSONException e) {
            Log.e(Constants.TAG, "Failed to parse " + type + " message " + e);
        }
    }

    public JSONObject getJSONObject() {
        return json;
    }
}

