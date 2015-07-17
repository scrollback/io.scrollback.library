package io.scrollback.library;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import com.google.android.gms.common.AccountPicker;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.webkit.WebSettings.LOAD_DEFAULT;

public abstract class ScrollbackFragment extends Fragment {
    private String accountName;

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private TextView mLoadError;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArr;

    private final int REQUEST_SELECT_FILE_LEGACY = 19264;
    private final int REQUEST_SELECT_FILE = 19275;

    private boolean debugMode = false;
    private String widgetName = "";

    private ScrollbackMessageHandler messagehandler;

    private CallbackManager callbackManager;

    private GoogleSetup googleSetup;
    private Bridge bridge;

    private Boolean isReady = false;
    private List<JSONMessage> pendingMessages = new ArrayList<>();

    public static String origin = Constants.HOST;
    public static String index = Constants.PROTOCOL + "//" + origin;
    public static String home = index + Constants.PATH;

    public void setLocation(String protocol, String host, String path) {
        origin = host;
        index = protocol + "//" + origin;
        home = index + path;
    }

    public void setEnableDebug(boolean debug) {
        debugMode = true;
    }

    public void setWidgetName(String name) {
        widgetName = name;
    }

    public void setMessageHandler(ScrollbackMessageHandler handler) {
        messagehandler = handler;
    }

    public void postMessage(JSONMessage message) {
        if (isReady) {
            if (bridge != null) {
                bridge.postMessage(message);
            }
        } else {
            pendingMessages.add(message);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_scrollback, container, false);

        mWebView = (WebView) v.findViewById(R.id.scrollback_webview);

        bridge = new Bridge(mWebView);

        bridge.setOnMessageListener(new ScrollbackMessageHandler() {
            @Override
            public void onNavMessage(NavMessage message) {
                if (messagehandler != null) {
                    messagehandler.onNavMessage(message);
                }
            }

            @Override
            public void onAuthMessage(AuthStatus message) {
                if (messagehandler != null) {
                    messagehandler.onAuthMessage(message);
                }
            }

            @Override
            public void onFollowMessage(FollowMessage message) {
                if (messagehandler != null) {
                    messagehandler.onFollowMessage(message);
                }
            }

            @Override
            public void onReadyMessage(ReadyMessage message) {
                isReady = true;

                if (messagehandler != null) {
                    messagehandler.onReadyMessage(message);
                }

                for (JSONMessage msg : pendingMessages) {
                    if (msg != null && bridge != null) {
                        bridge.postMessage(msg);
                    }
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideLoading();
                    }
                });
            }
        });

        googleSetup = new GoogleSetup(getActivity()) {
            @Override
            public void onGCMRegister(String regid, String uuid, String model) {
                emitGCMRegisterEvent(regid, uuid, model);
            }

            @Override
            public void onGCMUnRegister(String uuid) {
                emitGCMUnregisterEvent(uuid);
            }

            @Override
            public void onGoogleLogin(String token) {
                emitGoogleLoginEvent(token);
            }
        };

        mProgressBar = (ProgressBar) v.findViewById(R.id.scrollback_pgbar);
        mLoadError = (TextView) v.findViewById(R.id.scrollback_loaderror);

        // Enable debugging in webview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (debugMode) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
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
                googleSetup.registerBackground();
            }

            @SuppressWarnings("unused")
            @JavascriptInterface
            public void unregisterGCM() {
                googleSetup.unRegisterBackground();
            }
        }, "Android");

        mWebView.loadUrl(home);

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

    void emitGoogleLoginEvent(String token) {
        Log.d("emitGoogleLoginEvent", "token: " + token);

        bridge.postMessage(new AuthRequest("{ provider: 'google', token: '" + token + "' }"));
    }

    void emitFacebookLoginEvent(String token) {
        Log.d("emitFacebookLoginEvent", "token: " + token);

        bridge.postMessage(new AuthRequest("{ provider: 'facebook', token: '" + token + "' }"));
    }

    void emitGCMRegisterEvent(String regid, String uuid, String model) {
        Log.d("emitGCMRegisterEvent", "uuid: " + uuid + " regid: " + regid);

        bridge.evaluateJavascript("window.dispatchEvent(new CustomEvent('gcm_register', { detail :{'regId': '" + regid + "', 'uuid': '" + uuid + "', 'model': '" + model + "'} }))");
    }

    void emitGCMUnregisterEvent(String uuid) {
        bridge.evaluateJavascript("window.dispatchEvent(new CustomEvent('gcm_unregister', { detail :{'uuid': '" + uuid + "'} }))");
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Check if the key event was the Back button and if there's history
            if (mWebView.getUrl().equals(home) || !mWebView.canGoBack()) {
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

            if (uri.getAuthority().equals(origin)) {
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

            if (loginResult.getRecentlyGrantedPermissions().contains("email")) {
                GraphRequest.newMeRequest(
                        loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject me, GraphResponse response) {
                                if (response.getError() != null) {
                                    // handle error
                                } else {
                                    emitFacebookLoginEvent(loginResult.getAccessToken().getToken());
                                    // send email and id to your web server
                                }
                            }
                        }).executeAsync();
            }
            else {
                Toast.makeText(getActivity(), getString(R.string.signin_fail_error), Toast.LENGTH_SHORT).show();
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

            googleSetup.retriveGoogleToken(accountName);
        }

        else if (requestCode == Constants.REQ_SIGN_IN_REQUIRED && resultCode == Activity.RESULT_OK) {
            // We had to sign in - now we can finish off the token request.
            googleSetup.retriveGoogleToken(accountName);
        }

        else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }
}
