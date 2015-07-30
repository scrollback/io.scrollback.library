package io.scrollback.library;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;


public abstract class GoogleSetup {
    private String regid;
    private Context mContext;
    private GoogleCloudMessaging gcm;
    private ProgressDialog dialog;

    private String senderId;
    private String accessToken;

    GoogleSetup(Context c) {
        mContext = c;

        // Check device for Play Services APK. If check succeeds, proceed with
        // GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(mContext);
            regid = getRegistrationId(mContext);
        } else {
            Log.e(Constants.TAG, "No valid Google Play Services APK found.");
        }
    }

    public abstract void onGCMRegister(String regid, String uuid, String model);

    public abstract void onGCMUnRegister(String regid);

    public abstract void onGoogleLogin(String token);

    public void setSenderId(String gcmSenderId) {
        senderId = gcmSenderId;
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, (Activity) mContext,
                        Constants.PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.d(Constants.TAG, "This device is not supported.");

            }

            return false;
        }

        return true;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);

            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // Should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Stores the registration id, app versionCode, and expiration time in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId   registration id
     */
    private void setRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences();
        int appVersion = getAppVersion(context);

        Log.v(Constants.TAG, "Saving regId on app version " + appVersion);

        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(Constants.PROPERTY_REG_ID, regId);
        editor.putInt(Constants.PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }


    /**
     * Gets the current registration id for application on GCM service.
     * <p/>
     * If result is empty, the registration has failed.
     *
     * @return registration id, or empty string if the registration is not
     * complete.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences();
        final String registrationId = prefs.getString(Constants.PROPERTY_REG_ID, "");

        if (registrationId.length() == 0) {
            Log.d(Constants.TAG, "Registration not found.");

            return "";
        }

        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = prefs.getInt(Constants.PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);

        if (registeredVersion != currentVersion) {
            Log.d(Constants.TAG, "App version changed or registration expired.");

            return "";
        }

        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration id, app versionCode, and expiration time in the application's
     * shared preferences.
     */
    protected void registerBackground() {
        if (senderId == null) {
            return;
        }

        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected String doInBackground(Void... params) {
                String msg;

                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(mContext);
                    }

                    regid = gcm.register(senderId);

                    msg = "Device registered, registration id=" + regid;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Save the regid - no need to register again.
                    setRegistrationId(mContext, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                }

                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                String uuid = Settings.Secure.getString(mContext.getContentResolver(),
                        Settings.Secure.ANDROID_ID);

                onGCMRegister(regid, uuid, Build.MODEL);
            }
        }.execute(null, null, null);
    }

    protected void unRegisterBackground() {
        new AsyncTask<Void, Void, String>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected String doInBackground(Void... params) {
                String msg = "";

                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(mContext);
                    }

                    gcm.unregister();

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Save the regid - no need to register again.
                    setRegistrationId(mContext, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                }

                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                onGCMUnRegister(regid);
            }
        }.execute(null, null, null);
    }

    protected void retriveGoogleToken(String accountName) {
        new AsyncTask<String, Void, String>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                dialog = new ProgressDialog(mContext);
                dialog.setMessage(mContext.getString(R.string.google_signing_in));
                dialog.show();
            }

            @Override
            protected String doInBackground(String... params) {
                String accountName = params[0];
                String scopes = "oauth2:profile email";
                String token = null;

                try {
                    token = GoogleAuthUtil.getToken(mContext, accountName, scopes);
                } catch (IOException e) {
                    Log.e(Constants.TAG, e.getMessage());
                } catch (UserRecoverableAuthException e) {
                    ((Activity) mContext).startActivityForResult(e.getIntent(), Constants.REQ_SIGN_IN_REQUIRED);
                } catch (GoogleAuthException e) {
                    Log.e(Constants.TAG, e.getMessage());
                }

                return token;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);

                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                if (s == null) {
                    Toast.makeText(mContext, mContext.getString(R.string.requesting_permission), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, mContext.getString(R.string.signed_in), Toast.LENGTH_SHORT).show();

                    accessToken = s;

                    onGoogleLogin(accessToken);
                }
            }
        }.execute(accountName);
    }

    protected void deleteGoogleToken(String accountName) {
        new AsyncTask<String, Void, String>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected String doInBackground(String... params) {
                accessToken = params[0];

                String result = null;

                try {
                    GoogleAuthUtil.clearToken(mContext, accessToken);

                    result = "true";
                } catch (GoogleAuthException e) {
                    Log.e(Constants.TAG, e.getMessage());
                } catch (IOException e) {
                    Log.e(Constants.TAG, e.getMessage());
                }

                return result;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
            }
        }.execute(accountName);
    }
}
