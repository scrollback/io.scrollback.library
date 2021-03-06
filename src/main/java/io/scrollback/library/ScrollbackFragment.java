package io.scrollback.library;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
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
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.common.AccountPicker;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.webkit.WebSettings.LOAD_DEFAULT;

public abstract class ScrollbackFragment extends Fragment {
    private String HEYNBR = "heyneighbor.chat";

    private String accountName;

    private WebView mWebView;
    private LinearLayout mLoading;
    private TextView mLoadError;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArr;

    private final int REQUEST_SELECT_FILE_LEGACY = 19264;
    private final int REQUEST_SELECT_FILE = 19275;

    private final int LOADING_DELAY_LENGTH = 1000;

    private boolean debugMode = false;
    private boolean canChangeStatusBarColor = false;

    private String widgetName = "";

    private ScrollbackMessageHandler messagehandler;
    private ScrollbackInterface sbInterface;

    private CallbackManager callbackManager;

    private GoogleSetup googleSetup;
    private Bridge bridge;

    private String gcmSenderId;

    private Boolean isReady = false;
    private List<JSONMessage> pendingMessages = new ArrayList<>();

    private String initialUrl;

    private String primaryColor;
    private String primaryDarkColor;

    private final String primaryStyle = "" +
            ".button, [type=button], [type=submit], button { background-color: #3ca; }" +
            ".button:focus, [type=button]:focus, [type=submit]:focus, button:focus," +
            ".button:hover, [type=button]:hover, [type=submit]:hover, button:hover," +
            ".button:active, [type=button]:active, [type=submit]:active, button:active { background-color: #238b74; }" +
            ".appbar-primary { background-color: #3ca; }" +
            "::selection { background-color: #3ca; }" +
            "::-moz-selection { background-color: #3ca; }" +
            ".card-quick-reply.active .card-entry-reply { box-shadow: inset 0 0 0 1px #3ca; }" +
            ".chat-item-tag-action { border-right-color: #3ca; }" +
            ".multientry-segment { background-color: #3ca; }" +
            ".chat-area-input-entry:active, .chat-area-input-entry:focus, .chat-area-input-entry:hover," +
            ".multientry:active, .multientry:focus, .multientry:hover," +
            ".textarea-container:active, .textarea-container:focus, .textarea-container:hover," +
            ".entry:active, .entry:focus, .entry:hover," +
            "input:active, input:focus, input:hover, textarea:active, textarea:focus, textarea:hover { border-color: #3ca; }" +
            ".sidebar-right .searchbar { background-color: #3ca; }";

    private String customPrimaryStyle;

    public static String origin = Constants.HOST;
    public static String index = Constants.PROTOCOL + "//" + origin;
    public static String path = Constants.PATH;
    public static String home = index + path;

    public void setLocation(String protocol, String host, String path) {
        origin = host;
        index = protocol + "//" + origin;
        home = index + path;
    }

    private String replacePrimaryColors(String sheet) {
        return sheet
                .replace("#3ca", primaryColor)
                .replace("#238b74", primaryDarkColor);
    }

    public void setPrimaryColors(String primary, String primaryDark) {
        primaryColor = primary;
        primaryDarkColor = primaryDark;

        customPrimaryStyle = replacePrimaryColors(primaryStyle);

        if (bridge != null) {
            bridge.setStyleSheet(customPrimaryStyle);
        }
    }

    public void setEnableDebug(boolean debug) {
        debugMode = debug;
    }

    public void setCanChangeStatusBarColor(boolean status) {
        canChangeStatusBarColor = status;
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

    public void loadUrl(final String url) {
        if (mWebView != null) {
            mWebView.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl(url);
                }
            });
        } else {
            initialUrl = url;
        }
    }

    public void loadPath(String path) {
        loadUrl(index + path);
    }

    public void setGcmSenderId(String senderId) {
        if (googleSetup == null) {
            gcmSenderId = senderId;
        } else {
            googleSetup.setSenderId(senderId);
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

                pendingMessages = new ArrayList<>();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideLoading();
                            }
                        });
                    }
                }, LOADING_DELAY_LENGTH);
            }
        });

        googleSetup = new GoogleSetup(getActivity()) {
            @Override
            public void onGCMRegister(String regid, String uuid, String model) {
                Log.d("GCMRegister", "uuid: " + uuid + " regid: " + regid);

                bridge.evaluateJavascript("window.dispatchEvent(new CustomEvent('gcm_register', { detail :{'regId': '" + regid + "', 'uuid': '" + uuid + "', 'model': '" + model + "'} }))");
            }

            @Override
            public void onGCMUnRegister(String uuid) {
                Log.d("GCMUnRegister", "uuid: " + uuid);

                bridge.evaluateJavascript("window.dispatchEvent(new CustomEvent('gcm_unregister', { detail :{'uuid': '" + uuid + "'} }))");
            }

            @Override
            public void onGoogleLogin(String token) {
                Log.d("GoogleLogin", "token: " + token);

                bridge.postMessage(new AuthRequest("{ provider: 'google', token: '" + token + "' }"));
            }
        };

        if (gcmSenderId != null) {
            googleSetup.setSenderId(gcmSenderId);
        }

        mLoading = (LinearLayout) v.findViewById(R.id.loading_screen);
        mLoadError = (TextView) v.findViewById(R.id.scrollback_loaderror);

        // Enable debugging in webview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (debugMode) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }

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

        mWebSettings.setJavaScriptEnabled(true);
        mWebSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        mWebSettings.setSupportZoom(false);
        mWebSettings.setSaveFormData(true);
        mWebSettings.setDomStorageEnabled(true);
        mWebSettings.setAllowFileAccess(true);
        mWebSettings.setCacheMode(LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            String databasePath = getActivity().getDir("databases", Context.MODE_PRIVATE).getPath();

            mWebSettings.setDatabaseEnabled(true);
            mWebSettings.setDatabasePath(databasePath);
        }

        sbInterface = new ScrollbackInterface(getActivity()) {
            @Override
            public String preProcesorStatusBarColor(String color) {
                return replacePrimaryColors(color);
            }

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

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        doFacebookLogin();
                    }
                });
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
        };

        sbInterface.setCanChangeStatusBarColor(canChangeStatusBarColor);

        mWebView.addJavascriptInterface(sbInterface, "Android");

        final CacheManager cacheManager =
                new CacheManager.Builder()
                        .setIndex(index, path)
                        .setCachePath(getActivity().getCacheDir())
                        .setFallback(getActivity().getAssets(), "www")
                        .setUnsafeMode(debugMode)
                        .setCallback(new CacheManager.Callback() {
                            @Override
                            public void onCached() {
                                fireAppCacheEvent("cached");
                            }

                            @Override
                            public void onChecking() {
                                fireAppCacheEvent("checking");
                            }

                            @Override
                            public void onDownloading() {
                                fireAppCacheEvent("downloading");
                            }

                            @Override
                            public void onError() {
                                fireAppCacheEvent("error");
                            }

                            @Override
                            public void onNoUpdate() {
                                fireAppCacheEvent("noupdate");
                            }

                            @Override
                            public void onUpdateReady() {
                                fireAppCacheEvent("updateready");
                            }
                        })
                        .build();

        mWebView.setWebViewClient(new WebViewClient() {
            private String regex = "^/(?!socket)[a-z0-9-]+(/[a-z0-9-]+)?";

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view, String requestUrl) {
                if (requestUrl.startsWith(index) || Uri.parse(requestUrl).getHost().equals(HEYNBR)) {
                    String path = Uri.parse(requestUrl).getPath();

                    WebResourceResponse res = cacheManager.getCachedResponse(path.matches(regex) ? "/me" : path);

                    if (res != null) {
                        return res;
                    }
                }

                return super.shouldInterceptRequest(view, requestUrl);
            }

            @SuppressLint("NewApi")
            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();

                if (url.toString().startsWith(index) || url.getHost().equals(HEYNBR)) {
                    String path = url.getPath();

                    WebResourceResponse res = cacheManager.getCachedResponse(path.matches(regex) ? "/me" : path);

                    if (res != null) {
                        return res;
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }

            @SuppressWarnings("unused")
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d(getString(R.string.app_name), cm.message() + " -- From line "
                        + cm.lineNumber() + " of "
                        + cm.sourceId());

                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);

                if (uri.getHost().equals(origin) || uri.getHost().equals(HEYNBR)) {
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
                mLoading.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (debugMode) {
                    handler.proceed();
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                // Show the loading screen
                showLoading();

                // Refresh cache on reloads
                cacheManager.refreshCache();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (customPrimaryStyle != null) {
                    bridge.setStyleSheet(customPrimaryStyle);
                }
            }
        });

        if (initialUrl != null) {
            mWebView.loadUrl(initialUrl);
        } else {
            mWebView.loadUrl(home);
        }

        mLoadError.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWebView.loadUrl(mWebView.getUrl());

                mLoadError.setVisibility(View.GONE);

                showLoading();
            }
        });

        FacebookSdk.sdkInitialize(getActivity());
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, loginCallback);

        return v;
    }

    private void fireAppCacheEvent(String type) {
        bridge.evaluateJavascript("window.applicationCache.dispatchEvent(new Event('" + type + "'))");
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

        // Logs 'install' and 'app activate' App Events.
        AppEventsLogger.activateApp(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();

        // Logs 'app deactivate' App Event.
        AppEventsLogger.deactivateApp(getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (mWebView != null) {
            mWebView.loadUrl("about:blank");
            mWebView.destroy();
            mWebView = null;
        }

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    void doFacebookLogin() {
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("public_profile", "email"));

    }

    void hideLoading() {
        mLoading.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
    }

    void showLoading() {
        mLoading.setVisibility(View.VISIBLE);
        mWebView.setVisibility(View.GONE);
    }

    void onFacebookLogin(String token) {
        Log.d("FacebookLogin", "token: " + token);

        bridge.postMessage(new AuthRequest("{ provider: 'facebook', token: '" + token + "' }"));
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Check if the key event was the Back button and if there's history
            if (!mWebView.getUrl().equals(home) && mWebView.canGoBack()) {
                mWebView.goBack();

                return true;
            }

            return false;
        }

        return false;
    }

    FacebookCallback<LoginResult> loginCallback = new FacebookCallback<LoginResult>() {
        @Override
        public void onSuccess(final LoginResult loginResult) {

            if (loginResult.getRecentlyGrantedPermissions().contains("email")) {
                GraphRequest.newMeRequest(
                        loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                            @Override
                            public void onCompleted(JSONObject me, GraphResponse response) {
                                if (response.getError() != null) {
                                    Toast.makeText(getActivity(), getString(R.string.signin_fail_error), Toast.LENGTH_SHORT).show();
                                } else {
                                    onFacebookLogin(loginResult.getAccessToken().getToken());
                                }
                            }
                        }).executeAsync();
            } else {
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
        } else if (requestCode == REQUEST_SELECT_FILE) {
            if (mUploadMessageArr == null) return;

            mUploadMessageArr.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            mUploadMessageArr = null;
        } else if (requestCode == Constants.SOME_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

            googleSetup.retriveGoogleToken(accountName);
        } else if (requestCode == Constants.REQ_SIGN_IN_REQUIRED && resultCode == Activity.RESULT_OK) {
            // We had to sign in - now we can finish off the token request.
            googleSetup.retriveGoogleToken(accountName);
        } else {
            callbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }
}
