package io.scrollback.library;

import android.os.Build;
import android.webkit.WebView;

import org.json.JSONObject;

public class Bridge {
    WebView mWebView;
    MessageListener listener;

    Bridge(WebView w) {
        mWebView = w;
    }

    public void setOnMessageListener(MessageListener l) {
        listener = l;
    }

    public void evaluateJavascript(final String script) {
        mWebView.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    mWebView.evaluateJavascript(script, null);
                } else {
                    mWebView.loadUrl("javascript:" + script);
                }
            }
        });
    }

    public void onDocumentReady(final String script) {
        evaluateJavascript(
                        "(function() {" +
                        "   if (document.readyState === 'complete') {" +
                        "       " + script +
                        "   } else {" +
                        "       document.onreadystatechange = function() {" +
                        "           if (document.readyState === 'complete') {" +
                        "               " + script +
                        "           }" +
                        "       }" +
                        "   }" +
                        "}())"
        );
    }

    public void postMessage(final String message) {
        evaluateJavascript("window.postMessage(JSON.stringify(" + message + "), '*')");
    }

    public void postMessage(final JSONObject message) {
        if (message != null) {
            postMessage(message.toString());
        }
    }

    public void postMessage(final JSONMessage message) {
        if (message != null) {
            postMessage(message.toString());
        }
    }

    public void receiveMessage(final String message) {
        if (listener != null) {
            listener.onMessage(message);
        }
    }

    public void setStyleSheet(final String sheet) {
        onDocumentReady(
                "var style = document.getElementById('app-custom-style');" +
                "if (!style) {" +
                "   style = document.createElement('style');" +
                "   document.head.appendChild(style);" +
                "}" +
                "style.textContent = '" + sheet.replace("'", "\\'") + "';"
        );
    }
}
