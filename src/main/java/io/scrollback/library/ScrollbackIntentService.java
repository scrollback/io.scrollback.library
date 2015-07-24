package io.scrollback.library;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;


public abstract class ScrollbackIntentService extends IntentService {

    public ScrollbackIntentService() {
        super("ScrollbackIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);

        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM
             * will be extended in the future with new message types, just ignore
             * any message types you're not interested in, or that you don't
             * recognize.
             */
            if (GoogleCloudMessaging.
                    MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                Log.e(Constants.TAG, "GCM send error: " + extras.toString());
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_DELETED.equals(messageType)) {
                Log.e(Constants.TAG, "GCM messages deleted on server: " +
                        extras.toString());
                // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)) {

                Log.d(Constants.TAG, "gcm_payload: " + extras.toString());

                Log.d(Constants.TAG, "gcm_title: " + extras.getString("title"));
                Log.d(Constants.TAG, "gcm_subtitle: " + extras.getString("text"));
                Log.d(Constants.TAG, "gcm_path: " + extras.getString("path"));

                Notification notif = new Notification();

                notif.setTitle(extras.getString("title", "Scrollback"));
                notif.setText(extras.getString("text", "There is new activity"));
                notif.setPath(extras.getString("path", "/me"));

                sendNotification(notif);
            }
        }

        // Release the wake lock provided by the WakefulBroadcastReceiver.
        ScrollbackBroadcastReceiver.completeWakefulIntent(intent);
    }

    // Put the message into a notification and post it.
    // This is just one simple example of what you might choose to do with
    // a GCM message.
    public abstract void sendNotification(Notification n);

    public class Notification {

        private String title = "";
        private String text = "";
        private String path = "";

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
