package io.scrollback.library;

/**
 * Created by karthikbalakrishnan on 20/07/15.
 */
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;


public class ScrollbackBroadcastReceiver extends WakefulBroadcastReceiver {

    String intentServiceName = null;
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intentServiceName==null) {
            Log.e("scrollbacklib", "ScrollbackIntentService not implemented or IntentService name not set");
            // Explicitly specify that GcmIntentService will handle the intent.
            ComponentName comp = new ComponentName(context.getPackageName(),
                    intentServiceName);
            // Start the service, keeping the device awake while it is launching.
            startWakefulService(context, (intent.setComponent(comp)));
            setResultCode(Activity.RESULT_OK);
        }
    }

    public void setIntentServiceName(String intentServiceName) {
        this.intentServiceName=intentServiceName;
    }


}