package io.scrollback.library;

import android.os.AsyncTask;
import android.util.Log;

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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okio.BufferedSink;
import okio.Okio;

public class CacheManager {
    private String hostUrl;
    private String manifestPath;
    private File cacheDir;
    private File fallbackDir;
    private File tempDir;

    private boolean isUnsafe;

    private String TAG = "CacheManager";

    public CacheManager load(String url, String path) {
        if (path == null) {
            manifestPath = "/manifest.appcache";
        } else {
            manifestPath = path;
        }

        Log.d(TAG, "Manifest file path set to " + manifestPath);

        hostUrl = url;

        return this;
    }

    public CacheManager directory(String path) {
        cacheDir = new File(path);

        Log.d(TAG, "Cache directory set to " + path);

        return this;
    }

    public CacheManager fallback(String path) throws FileNotFoundException {
        fallbackDir = new File(path);

        if (!fallbackDir.exists()) {
            throw new FileNotFoundException("The directory " + path + " doesn't exist.");
        }

        Log.d(TAG, "Fallback directory set to " + path);

        return this;
    }

    public CacheManager unsafe(Boolean value) {
        isUnsafe = value;

        if (isUnsafe == true) {
            Log.d(TAG, "Unsafe mode, SSL errors will be ignored.");
        }

        return this;
    }

    private String readFileAsText(File file) {
        Log.d(TAG, "Reading file as text " + file.getAbsolutePath());

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

    private void copyFiles(File source, File target) throws IOException {
        if (source.isDirectory()) {
            Log.d(TAG, "Copying files from " + source.getAbsolutePath() + " to " + target.getAbsolutePath());

            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Cannot create directory " + target.getAbsolutePath());
            }

            String[] children = source.list();

            for (int i = 0; i < children.length; i++) {
                copyFiles(new File(source, children[i]), new File(target, children[i]));
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

            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;

            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

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

        if (isUnsafe == true) {
            client = getUnsafeOkHttpClient();
        } else {
            client = new OkHttpClient();
        }

        Log.d(TAG, "Downloading file " + hostUrl + path);

        Request request = new Request.Builder()
                .url(hostUrl + path)
                .build();

        Response response = client.newCall(request).execute();

        File file = new File(dir, path);

        file.getParentFile().mkdirs();
        file.createNewFile();

        BufferedSink sink = Okio.buffer(Okio.sink(file));

        sink.writeAll(response.body().source());
        sink.close();
    }

    private void emptyDir(File dir) {
        Log.d(TAG, "Emptying directory " + dir.getAbsolutePath());

        if (dir.isDirectory()) {
            String[] children = dir.list();

            for (int i = 0; i < children.length; i++) {
                new File(dir, children[i]).delete();
            }
        }
    }

    private List<String> listFiles(File cacheManifest) {
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
                        if (isCacheSection == true) {
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
            Log.e(TAG, "Failed to read cache manifest " + manifestPath, e);

            return null;
        }

        return fileList;
    }

    private void refreshCache() {
        Log.d(TAG, "Refreshing cache");

        tempDir = new File(cacheDir.getParentFile(), "tmp");

        emptyDir(tempDir);

        try {
            downloadFile(manifestPath, tempDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to download cache manifest " + manifestPath, e);
            Log.e(TAG, "Aborting refresh.");

            return;
        }

        String manifestTemp = readFileAsText(new File(tempDir.getAbsolutePath(), manifestPath));
        String manifestCached = readFileAsText(new File(cacheDir.getAbsolutePath(), manifestPath));

        if (manifestTemp != null && manifestCached != null && manifestTemp.equals(manifestCached)) {
            Log.d(TAG, "Cache manifest has not changed.");

//            return;
        }

        File cacheManifest = new File(tempDir.getAbsolutePath(), manifestPath);

        List<String> fileList = listFiles(cacheManifest);

        for (String file : fileList) {
            try {
                downloadFile(file, tempDir);

                if (!new File(tempDir, file).exists()) {
                    throw new IOException("File doesn't exist");
                }
            } catch (IOException e) {
                // Cache download failed
                Log.e(TAG, "Failed to download file " + file, e);
                Log.e(TAG, "Aborting refresh.");

                return;
            }
        }

        emptyDir(cacheDir);

        try {
            copyFiles(tempDir, cacheDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy files from " + tempDir.getAbsolutePath() + " to " + cacheDir.getAbsolutePath(), e);
        }
    }

    private void executeSync() {
        if (fallbackDir != null && fallbackDir.exists()) {
            try {
                if (cacheDir.exists()) {
                    File[] contents = cacheDir.listFiles();

                    if (contents == null || contents.length == 0) {
                        Log.d(TAG, "Cache directory is empty");

                        copyFiles(fallbackDir, cacheDir);
                    }
                } else {
                    copyFiles(fallbackDir, cacheDir);
                }
            } catch (IOException e) {
                // Failed to read manifest file
                Log.e(TAG, "Failed to copy files from " + fallbackDir.getAbsolutePath() + " to " + cacheDir.getAbsolutePath(), e);
            }
        }

        // Refresh the cache
        refreshCache();
    }

    public void execute() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                executeSync();
            }
        });
    }
}
