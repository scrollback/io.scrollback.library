package io.scrollback.library;

import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.WebResourceResponse;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okio.BufferedSink;
import okio.Okio;

public class CacheManager {
    private MimeTypes mimeTypes = new MimeTypes();

    private String hostUrl;
    private String indexPath;

    private AssetManager assetManager;
    private String assetsPath;

    private File wwwDir;
    private File tmpDir;

    private Callback cacheCallback;

    private boolean isUnsafe;

    private boolean isRefreshing = false;

    private String TAG = "CacheManager";

    public CacheManager load(String url, String path) {
        if (path == null) {
            indexPath = "/index.html";
        } else {
            indexPath = path;

            Log.d(TAG, "Index file path set to " + indexPath);
        }

        hostUrl = url;

        return this;
    }

    public CacheManager load(String url) {
        return load(url, null);
    }

    public CacheManager cache(File path) {
        File cacheDir = new File(path, "CacheManager");

        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.e(TAG, "Failed to create cache directory " + cacheDir.getAbsolutePath());
        }

        wwwDir = new File(cacheDir, "www");
        tmpDir = new File(cacheDir, "tmp");

        Log.d(TAG, "Cache directory set to " + path);

        return this;
    }

    public CacheManager fallback(AssetManager assets, String path) {
        assetManager = assets;
        assetsPath = path;

        Log.d(TAG, "Fallback directory set to assets directory " + path);

        return this;
    }

    public CacheManager unsafe(Boolean value) {
        isUnsafe = value;

        if (isUnsafe) {
            Log.d(TAG, "Unsafe mode, SSL errors will be ignored");
        }

        return this;
    }

    public CacheManager callback(Callback cb) {
        cacheCallback = cb;

        return this;
    }

    private String readFileAsText(File file) {
        Log.d(TAG, "Reading file " + file.getAbsolutePath());

        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }

            br.close();

            return text.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file " + file.getAbsolutePath(), e);

            return null;
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;

        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    private void copyFiles(File source, File target) throws IOException {
        if (source.isDirectory()) {
            Log.d(TAG, "Copying files from " + source.getAbsolutePath() + " to " + target.getAbsolutePath());

            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Cannot create directory " + target.getAbsolutePath());
            }

            String[] children = source.list();

            for (String child : children) {
                copyFiles(new File(source, child), new File(target, child));
            }
        } else {
            Log.d(TAG, "Copying file " + source.getName() + " to " + target.getParent());

            // Make sure the directory we plan to store the recording in exists
            File directory = target.getParentFile();

            if (directory != null && !directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create dir " + directory.getAbsolutePath());
            }

            InputStream in = new FileInputStream(source);
            OutputStream out = new FileOutputStream(target);

            copyFile(in, out);

            in.close();
            out.close();
        }
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient okHttpClient = new OkHttpClient();
            okHttpClient.setSslSocketFactory(sslSocketFactory);
            okHttpClient.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void downloadFile(String path, File dir) throws IOException {
        OkHttpClient client;

        if (isUnsafe) {
            client = getUnsafeOkHttpClient();
        } else {
            client = new OkHttpClient();
        }

        String downloadUrl = hostUrl + (path.startsWith("/") ? path : "/" + path);

        Log.d(TAG, "Downloading file " + downloadUrl + " to " + dir.getAbsolutePath());

        Request request = new Request.Builder()
                .url(downloadUrl)
                .build();

        try {
            Response response = client.newCall(request).execute();

            File file = new File(dir, path);

            if (!file.exists() && !file.getParentFile().mkdirs() && !file.createNewFile()) {
                Log.e(TAG, "Failed to create file " + file.getAbsolutePath());

                throw new IOException();
            }

            BufferedSink sink = Okio.buffer(Okio.sink(file));

            sink.writeAll(response.body().source());
            sink.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to download file " + downloadUrl);

            throw e;
        }
    }

    private List<String> listFiles(File cacheManifest) throws IOException {
        List<String> fileList = new ArrayList<>();

        Boolean isCacheSection = false;

        try {
            Log.d(TAG, "Reading cache manifest " + cacheManifest.getAbsolutePath());

            BufferedReader br = new BufferedReader(new FileReader(cacheManifest));

            String line;

            while ((line = br.readLine()) != null) {
                // Print comments
                if (line.trim().matches("^#.+$")) {
                    Log.d(TAG, line);

                    continue;
                }

                // Check if line is a header
                if (line.trim().matches("^[A-Z]+:$")) {
                    if (line.trim().equals("CACHE:")) {
                        isCacheSection = true;
                    } else {
                        // Cache section ended
                        if (isCacheSection) {
                            break;
                        }
                    }

                    continue;
                }

                if (isCacheSection && !line.trim().equals("")) {
                    fileList.add(line.trim());
                }
            }

            br.close();
        } catch (IOException e) {
            // Failed to read manifest file
            Log.e(TAG, "Failed to read cache manifest " + cacheManifest.getAbsolutePath());

            throw e;
        }

        return fileList;
    }

    private void updateCache() throws IOException {
        boolean isFirst = !wwwDir.exists();

        if (tmpDir.delete()) {
            Log.d(TAG, "Deleted temporary directory " + tmpDir.getAbsolutePath());
        }

        Log.d(TAG, "Checking update");

        if (cacheCallback != null) {
            cacheCallback.onChecking();
        }

        downloadFile(indexPath, tmpDir);

        File indexFile = new File(tmpDir, indexPath);

        String manifestPath = null;

        try {
            Log.d(TAG, "Reading index file " + indexFile.getAbsolutePath());

            BufferedReader br = new BufferedReader(new FileReader(indexFile));

            String line;

            while ((line = br.readLine()) != null) {
                Pattern pattern = Pattern.compile("(manifest=['\"])([^'^\"]+)(['\"])");

                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    manifestPath = matcher.group(2);

                    if (!manifestPath.matches("^/.+$")) {
                        manifestPath = "/" + manifestPath;
                    }

                    Log.d(TAG, "Found manifest file path " + manifestPath);

                    break;
                }
            }

            br.close();
        } catch (IOException e) {
            // Failed to read manifest file
            Log.e(TAG, "Failed to read index file " + indexFile.getAbsolutePath());

            throw e;
        }

        if (manifestPath == null) {
            Log.e(TAG, "Couldn't find manifest file path from index");

            throw new IOException();
        }

        downloadFile(manifestPath, tmpDir);

        File tmpManifest = new File(tmpDir, manifestPath);
        File wwwManifest = new File(wwwDir, manifestPath);

        if (tmpManifest.exists() && wwwManifest.exists()) {
            String wwwText = readFileAsText(wwwManifest);
            String tmpText = readFileAsText(tmpManifest);

            if (tmpText != null && wwwText != null && tmpText.equals(wwwText)) {
                Log.d(TAG, "Cache manifest has not changed");

                if (cacheCallback != null) {
                    cacheCallback.onNoUpdate();
                }

                return;
            }
        }

        Log.d(TAG, "Refreshing cache");

        if (cacheCallback != null) {
            cacheCallback.onDownloading();
        }

        List<String> fileList = listFiles(tmpManifest);

        for (String file : fileList) {
            downloadFile(file, tmpDir);

            if (!new File(tmpDir, file).exists()) {
                throw new IOException();
            }
        }

        if (wwwDir.delete()) {
            Log.d(TAG, "Deleted directory " + wwwDir.getAbsolutePath());
        }

        try {
            copyFiles(tmpDir, wwwDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy files from " + tmpDir.getAbsolutePath() + " to " + wwwDir.getAbsolutePath());

            if (wwwDir.delete()) {
                Log.d(TAG, "Deleted directory " + wwwDir.getAbsolutePath());
            }

            throw e;
        }

        if (cacheCallback != null) {
            if (isFirst) {
                cacheCallback.onCached();
            }

            cacheCallback.onUpdateReady();
        }
    }

    public void refreshCache() {
        if (isRefreshing) {
            Log.d(TAG, "Cache refresh already in progress.");

            return;
        }

        // Refresh the cache
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                isRefreshing = true;

                try {
                    updateCache();
                } catch (IOException e) {
                    tmpDir.delete();

                    if (cacheCallback != null) {
                        cacheCallback.onError();
                    }

                    Log.e(TAG, "Aborting cache refresh", e);
                }

                isRefreshing = false;
            }
        });
    }

    public void execute() {
        if (wwwDir.exists()) {
            File[] contents = wwwDir.listFiles();

            if (contents == null || contents.length == 0) {
                Log.d(TAG, "Cache directory is empty");

                wwwDir.delete();
            }
        }

        // Refresh the cache
        refreshCache();
    }

    public WebResourceResponse getCachedResponse(String url) {
        String path = url.replaceFirst("^" + hostUrl, "");
        InputStream stream = null;

        File file = new File(wwwDir, path);

        if (file.exists()) {
            Log.d(TAG, "File found in cache for " + url);

            try {
                stream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to initialize stream from " + file.getAbsolutePath(), e);
            }
        } else {
            if (assetManager != null && assetsPath != null) {
                try {
                    stream = assetManager.open(assetsPath + path);
                } catch (IOException e) {
                    // Do nothing
                }

                if (stream != null) {
                    Log.d(TAG, "File found in assets directory for " + url);
                }
            }
        }

        if (stream != null) {
            String mime = mimeTypes.get(path.equals(indexPath) ? "html" : path.substring(path.lastIndexOf(".") + 1));

            if (mime != null) {
                Log.d(TAG, "Setting mime type " + mime + " for " + path);

                return new WebResourceResponse(mime, "UTF-8", stream);
            } else {
                Log.e(TAG, "Couldn't determine mime type for " + path);
            }
        }

        return null;
    }

    public interface Callback {
        void onCached();

        void onChecking();

        void onDownloading();

        void onError();

        void onNoUpdate();

        void onUpdateReady();
    }
}
