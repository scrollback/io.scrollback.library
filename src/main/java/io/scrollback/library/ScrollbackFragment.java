package io.scrollback.library;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;

import static android.webkit.WebSettings.LOAD_DEFAULT;

public abstract class ScrollbackFragment extends Fragment {

    private String accountName;
    private String accessToken;

    GoogleCloudMessaging gcm;
    String regid;

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private TextView mLoadError;

    private boolean inProgress = false;

    ProgressDialog dialog;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArr;

    private final int REQUEST_SELECT_FILE_LEGACY = 19264;
    private final int REQUEST_SELECT_FILE = 19275;

    private boolean debugMode = false;
    private String widgetName = "";

    private ScrollbackMessageHandler messagehandler;

    private CallbackManager callbackManager;

    private Bridge bridge;

    public void setEnableDebug(boolean debug) {
        debugMode = true;
    }

    public void setWidgetName(String name) {
        widgetName = name;
    }

    public void setMessageHandler(ScrollbackMessageHandler handler) {
        messagehandler = handler;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_scrollback, container, false);

        mWebView = (WebView) v.findViewById(R.id.scrollback_webview);

        bridge = new Bridge(mWebView);

        bridge.setOnMessageListener(messagehandler);

        mProgressBar = (ProgressBar) v.findViewById(R.id.scrollback_pgbar);
        mLoadError = (TextView) v.findViewById(R.id.scrollback_loaderror);

        // Enable debugging in webview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (debugMode) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

        // Check device for Play Services APK. If check succeeds, proceed with
        // GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(getActivity());
            regid = getRegistrationId(getActivity());
        } else {
            Log.e(Constants.TAG, "No valid Google Play Services APK found.");
        }

        mWebView.setWebViewClient(mWebViewClient);

        mWebView.setWebChromeClient(new WebChromeClient() {
            // For Android < 3.0
            @SuppressWarnings("unused")
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                mUploadMessage = uploadMsg;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");

                startActivityForResult(Intent.createChooser(i, getString(R.string.select_file)), REQUEST_SELECT_FILE_LEGACY);

            }

            // For Android 3.0+
            @SuppressWarnings("unused")
            public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(acceptType);

                startActivityForResult(Intent.createChooser(i, getString(R.string.select_file)), REQUEST_SELECT_FILE_LEGACY);
            }

            // For Android 4.1+
            @SuppressWarnings("unused")
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                mUploadMessage = uploadMsg;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(acceptType);

                startActivityForResult(Intent.createChooser(i, getString(R.string.select_file)), REQUEST_SELECT_FILE_LEGACY);
            }

            // For Android 5.0+
            @SuppressLint("NewApi")
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessageArr != null) {
                    mUploadMessageArr.onReceiveValue(null);
                    mUploadMessageArr = null;
                }

                mUploadMessageArr = filePathCallback;

                Intent intent = fileChooserParams.createIntent();

                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    mUploadMessageArr = null;

                    Toast.makeText(getActivity(), getString(R.string.file_chooser_error), Toast.LENGTH_LONG).show();

                    return false;
                }

                return true;
            }
        });

        WebSettings mWebSettings = mWebView.getSettings();

        String appCachePath = getActivity().getCacheDir().getAbsolutePath();

        mWebSettings.setJavaScriptEnabled(true);
        mWebSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        mWebSettings.setSupportZoom(false);
        mWebSettings.setSaveFormData(true);
        mWebSettings.setDomStorageEnabled(true);
        mWebSettings.setAppCacheEnabled(true);
        mWebSettings.setAppCachePath(appCachePath);
        mWebSettings.setAllowFileAccess(true);
        mWebSettings.setCacheMode(LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            String databasePath = getActivity().getDir("databases", Context.MODE_PRIVATE).getPath();

            mWebSettings.setDatabaseEnabled(true);
            mWebSettings.setDatabasePath(databasePath);
        }

        mWebView.addJavascriptInterface(new ScrollbackInterface(getActivity()) {

            @SuppressWarnings("unused")
            @JavascriptInterface
            public String getWidgetName() {
                return widgetName;
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void postMessage(String json) {
                bridge.receiveMessage(json);
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void onFinishedLoading() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                    }
                });
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void googleLogin() {
                Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[]{"com.google"},
                        false, null, null, null, null);
                startActivityForResult(intent, Constants.SOME_REQUEST_CODE);
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void facebookLogin() {
                // Get a handler that can be used to post to the main thread
                Handler mainHandler = new Handler(getActivity().getMainLooper());

                Runnable myRunnable = new Runnable() {
                    @Override
                    public void run() {
                        doFacebookLogin();
                    }
                };
                mainHandler.post(myRunnable);
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void registerGCM() {
                registerBackground();
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void unregisterGCM() {
                unRegisterBackground();

            }
        }, "Android");

//        Intent intent = getIntent();
//        String action = intent.getAction();
//        Uri uri = intent.getData();
//
//        if (intent.hasExtra("scrollback_path")) {
//            //mWebView.loadUrl(Constants.INDEX + getIntent().getStringExtra("scrollback_path"));
//        } else if (Intent.ACTION_VIEW.equals(action) && uri != null) {
//            final String URL = uri.toString();
//
//            mWebView.loadUrl(URL);
//        } else {
//            mWebView.loadUrl(Constants.HOME);
//        }

        mWebView.loadUrl(Constants.HOME);

        mLoadError.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWebView.loadUrl(mWebView.getUrl());

                mLoadError.setVisibility(View.GONE);

                showLoading();
            }
        });

        showLoading();

        FacebookSdk.sdkInitialize(getActivity());
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, loginCallback);

        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWebView.saveState(outState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mWebView.destroy();
        super.onDestroyView();
    }

    void doFacebookLogin() {

        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("public_profile", "email"));

    }

    void hideLoading() {
        mProgressBar.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
    }

    void showLoading() {
        mProgressBar.setVisibility(View.VISIBLE);
        mWebView.setVisibility(View.GONE);
    }

    /*
        javascript:window.postMessage({ type:"signin", provider:"google", token":"asss"}, '*');

     */

    void emitGoogleLoginEvent(String token) {
        Log.d("emitGoogleLoginEvent", "token: " + token);

        bridge.postMessage(new AuthRequest("{ provider: 'google', token: '" + token + "' }"));
    }

    void emitFacebookLoginEvent(String email, String token) {
        Log.d("emitFacebookLoginEvent", "token: " + token);

        bridge.postMessage(new AuthRequest("{ provider: 'facebook', token: '" + token + "' }"));
    }

    void emitGCMRegisterEvent(String regid, String uuid, String model) {
        Log.d("emitGCMRegisterEvent", "uuid:"+uuid+" regid:"+regid);

        bridge.evaluateJavascript("window.dispatchEvent(new CustomEvent('gcm_register', { detail :{'regId': '" + regid + "', 'uuid': '" + uuid + "', 'model': '" + model + "'} }))");
    }

    void emitGCMUnregisterEvent(String uuid) {
        bridge.evaluateJavascript("window.dispatchEvent(new CustomEvent('gcm_unregister', { detail :{'uuid': '" + uuid + "'} }))");
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Check if the key event was the Back button and if there's history
            if (mWebView.getUrl().equals(Constants.HOME) || !mWebView.canGoBack()) {
                return getActivity().onKeyDown(keyCode, event);
            } else if (mWebView.canGoBack()) {
                mWebView.goBack();
            }

            return true;
        }
        return getActivity().onKeyDown(keyCode, event);
    }

    private WebViewClient mWebViewClient = new WebViewClient() {

        @SuppressWarnings("unused")
        public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d(getString(R.string.app_name), cm.message() + " -- From line "
                    + cm.lineNumber() + " of "
                    + cm.sourceId() );

            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);

            if (uri.getAuthority().equals(Constants.ORIGIN)) {
                // This is my web site, so do not override; let my WebView load the page
                return false;


            } else {
                // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);

                return true;
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            mLoadError.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
        }
    };

    FacebookCallback<LoginResult> loginCallback = new FacebookCallback<LoginResult>() {
        @Override
        public void onSuccess(final LoginResult loginResult) {

            if(loginResult.getRecentlyGrantedPermissions().contains("email")) {
                GraphRequest.newMeRequest(
                        loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject me, GraphResponse response) {
                                if (response.getError() != null) {
                                    // handle error
                                } else {
                                    String email = me.optString("email");
                                    emitFacebookLoginEvent(email, loginResult.getAccessToken().getToken());
                                    // send email and id to your web server
                                }
                            }
                        }).executeAsync();
            }
            else {
                Toast.makeText(getActivity(), "Something is wrong", Toast.LENGTH_SHORT).show();
            }



        }

        @Override
        public void onCancel() {

        }

        @Override
        public void onError(FacebookException e) {

        }
    };

    @SuppressLint("NewApi")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_FILE_LEGACY) {
            if (mUploadMessage == null) return;

            Uri result = data == null || resultCode != Activity.RESULT_OK ? null : data.getData();

            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }

        else if (requestCode == REQUEST_SELECT_FILE) {
            if (mUploadMessageArr == null) return;

            mUploadMessageArr.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            mUploadMessageArr = null;
        }

        else if (requestCode == Constants.SOME_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            new RetrieveGoogleTokenTask().execute(accountName);
        }

        else if (requestCode == Constants.REQ_SIGN_IN_REQUIRED && resultCode == Activity.RESULT_OK) {
            // We had to sign in - now we can finish off the token request.
            new RetrieveGoogleTokenTask().execute(accountName);
        }

        else {
            callbackManager.onActivityResult(requestCode, resultCode, data);

        }
    }

    private class RetrieveGoogleTokenTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(getActivity());
            dialog.setMessage(getString(R.string.google_signing_in));
            dialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            String accountName = params[0];
            String scopes = "oauth2:profile email";
            String token = null;

            try {
                token = GoogleAuthUtil.getToken(getActivity(), accountName, scopes);
            } catch (IOException e) {
                Log.e(Constants.TAG, e.getMessage());
            } catch (UserRecoverableAuthException e) {
                startActivityForResult(e.getIntent(), Constants.REQ_SIGN_IN_REQUIRED);
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
                Toast.makeText(getActivity(), getString(R.string.requesting_permission), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), getString(R.string.signed_in), Toast.LENGTH_SHORT).show();

                emitGoogleLoginEvent(s);

                accessToken = s;
            }
        }
    }

    private class DeleteTokenTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            inProgress = true;
        }

        @Override
        protected String doInBackground(String... params) {
            accessToken = params[0];

            String result = null;

            try {
                GoogleAuthUtil.clearToken(getActivity(), accessToken);

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

            inProgress = false;

        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p/>
     * Stores the registration id, app versionCode, and expiration time in the application's
     * shared preferences.
     */
    private void registerBackground() {
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
                        gcm = GoogleCloudMessaging.getInstance(getActivity());
                    }

                    regid = gcm.register(getString(R.string.gcm_sender_id));

                    msg = "Device registered, registration id=" + regid;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Save the regid - no need to register again.
                    setRegistrationId(getActivity(), regid);
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

                String uuid = Settings.Secure.getString(getActivity().getContentResolver(),
                        Settings.Secure.ANDROID_ID);
                emitGCMRegisterEvent(regid, uuid, Build.MODEL);
            }
        }.execute(null, null, null);
    }


    private void unRegisterBackground() {
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
                        gcm = GoogleCloudMessaging.getInstance(getActivity());
                    }

                    gcm.unregister();

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Save the regid - no need to register again.
                    setRegistrationId(getActivity(), regid);
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

                emitGCMUnregisterEvent(Build.MODEL);
            }
        }.execute(null, null, null);
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
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());

        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, getActivity(),
                        Constants.PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.d(Constants.TAG, "This device is not supported.");

            }

            return false;
        }

        return true;
    }
}
