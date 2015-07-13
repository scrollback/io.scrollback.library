package io.scrollback.library;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;


public abstract class ScrollbackInterface {

    Context mContext;

    ScrollbackInterface(Context c) {
        mContext = c;
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getPackageName() {
        return mContext.getPackageName();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();

    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(mContext.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);

        clipboard.setPrimaryClip(clip);

        Toast toast = Toast.makeText(mContext, mContext.getString(R.string.clipboard_success), Toast.LENGTH_SHORT);
        toast.show();
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public boolean isFileUploadAvailable(final boolean needsCorrectMimeType) {
        if (Build.VERSION.SDK_INT == 19) {
            final String platformVersion = (Build.VERSION.RELEASE == null) ? "" : Build.VERSION.RELEASE;

            return !needsCorrectMimeType && (platformVersion.startsWith("4.4.3") || platformVersion.startsWith("4.4.4"));
        } else {
            return true;
        }
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public boolean isFileUploadAvailable() {
        return isFileUploadAvailable(false);
    }

    @SuppressWarnings("unused")
    @JavascriptInterface
    public void shareItem(String title, String content) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);

        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, content);

        mContext.startActivity(Intent.createChooser(sharingIntent, title));
    }


    public abstract void googleLogin();

    public abstract void facebookLogin();

    public abstract void registerGCM();

    public abstract void unregisterGCM();

    public abstract void onFinishedLoading();
}