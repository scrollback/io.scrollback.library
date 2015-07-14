package io.scrollback.library;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class ScrollbackMessageHandler implements MessageListener {
    public abstract void onNavMessage(NavMessage message);

    public abstract void onAuthMessage(AuthStatus message);

    public abstract void onFollowMessage(FollowMessage message);

    public abstract void onReadyMessage(ReadyMessage message);

    @Override
    public void onMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            String type = json.getString("type");

            if (type != null) {
                switch (type) {
                    case "nav":
                        onNavMessage(new NavMessage(json));
                        break;
                    case "auth":
                        onAuthMessage(new AuthStatus(json));
                        break;
                    case "follow":
                        onFollowMessage(new FollowMessage(json));
                        break;
                    case "ready":
                        onReadyMessage(new ReadyMessage(json));
                        break;
                }
            }
        } catch(JSONException e) {
            Log.e(Constants.TAG, "Error parsing JSON " + e.getMessage());
        }
    }
}
