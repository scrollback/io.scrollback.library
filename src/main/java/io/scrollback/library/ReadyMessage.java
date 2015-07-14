package io.scrollback.library;

import org.json.JSONObject;

public class ReadyMessage extends JSONMessage {
    ReadyMessage(JSONObject message) {
        super(message);
    }
}
