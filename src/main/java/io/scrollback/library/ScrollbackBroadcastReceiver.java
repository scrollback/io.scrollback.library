package io.scrollback.library;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;


public class ScrollbackBroadcastReceiver extends WakefulBroadcastReceiver {

    String intentServiceName = null;

    public ScrollbackBroadcastReceiver(String serviceName) {
        intentServiceName = serviceName;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intentServiceName == null) {
            Log.e(Constants.TAG, "ScrollbackIntentService not implemented or IntentService name not set");

            return;
        }

        // Explicitly specify that GcmIntentService will handle the intent.
        ComponentName comp = new ComponentName(context.getPackageName(), intentServiceName);

        // Start the service, keeping the device awake while it is launching.
        startWakefulService(context, (intent.setComponent(comp)));
        setResultCode(Activity.RESULT_OK);
    }
}