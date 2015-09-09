package io.scrollback.library;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;


public abstract class ScrollbackIntentService extends IntentService {
    private static final String TAG = "GCM";

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
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                Log.e(TAG, "Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                Log.e(TAG, "Messages deleted on server: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                // If it's a regular GCM message, do some work

                Log.d(TAG, "payload: " + extras.toString());

                Log.d(TAG, "title: " + extras.getString("title"));
                Log.d(TAG, "subtitle: " + extras.getString("text"));
                Log.d(TAG, "path: " + extras.getString("path"));
                Log.d(TAG, "picture: " + extras.getString("picture"));

                Notification notif = new Notification();

                notif.setTitle(extras.getString("title"));
                notif.setText(extras.getString("text"));
                notif.setPath(extras.getString("path"));
                notif.setPicture(extras.getString("picture"));

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

        private String title;
        private String text;
        private String path;
        private String picture;

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

        public String getPicture() {
            return picture;
        }

        public void setPicture(String picture) {
            this.picture = picture;
        }

        public Bitmap getBitmap(String protocol, String host) {
            URL url = null;

            if (protocol == null) {
                protocol = Constants.PROTOCOL;
            }

            if (host == null) {
                host = Constants.HOST;
            }

            try {
                url = new URL(protocol + "//" + host + picture);
            } catch (MalformedURLException e) {
                Log.e(TAG, "Malformed URL " + picture, e);
            }

            if (url != null) {
                try {
                    Bitmap image = BitmapFactory.decodeStream(url.openConnection().getInputStream());

                    return image;
                } catch (IOException e) {
                    Log.e(TAG, "Couldn't fetch image from " + picture, e);
                }
            }

            return null;
        }
    }
}
