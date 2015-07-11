package io.scrollback.library;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;


public abstract class ScrollbackInterface {

    Context mContext;

    /**
     * Instantiate the interface and set the context
     */
    ScrollbackInterface(Context c) {
        mContext = c;
    }

    /**
     * Show a toast from the web page
     */
    @JavascriptInterface
    public void showToast(String toast/*, Function f*/) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();

    }

    public abstract void googleLogin();

    public abstract void facebookLogin();

    public abstract void registerGCM();

    public abstract void unregisterGCM();

    public abstract void onFinishedLoading();
}