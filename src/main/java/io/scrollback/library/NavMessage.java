package io.scrollback.library;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class NavMessage extends JSONMessage {
    public String mode;
    public String view;
    public String room;
    public String thread;
    public String dialog;
    public long textTime;
    public long threadTime;
    public JSONObject dialogState;

    @Override
    protected void init(JSONObject message) {
        super.init(message);

        try {
            mode = json.getString("mode");
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Nav doesn't contain mode " + e);
        }

        try {
            view = json.getString("view");
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Nav doesn't contain view " + e);
        }

        try {
            room = json.getString("room");
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Nav doesn't contain room " + e);
        }

        try {
            thread = json.getString("thread");
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Nav doesn't contain thread " + e);
        }

        try {
            thread = json.getString("dialog");
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Nav doesn't contain dialog " + e);
        }

        try {
            JSONObject textRange = json.getJSONObject("textRange");

            if (textRange != null) {
                textTime = textRange.getLong("time");
            }
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Error parsing textRange " + e);
        }

        try {
            JSONObject threadRange = json.getJSONObject("threadRange");

            if (threadRange != null) {
                threadTime = threadRange.getLong("time");
            }
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Error parsing threadRange " + e);
        }

        try {
            dialogState = json.getJSONObject("dialogState");
        } catch (JSONException e) {
            Log.d(Constants.TAG, "Error parsing dialogState " + e);
        }
    }

    public NavMessage(JSONObject message) {
        super(message);
    }

    public NavMessage(String message) {
        super(message, "nav");
    }
}
