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
    private String hostUrl;
    private String indexPath;
    private File fallbackDir;
    private File cacheDir;
    private File wwwDir;
    private File tmpDir;

    private boolean isUnsafe;

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
        cacheDir = new File(path, "CacheManager");

        cacheDir.mkdirs();

        wwwDir = new File(cacheDir, "www");
        tmpDir = new File(cacheDir, "tmp");

        Log.d(TAG, "Cache directory set to " + path);

        return this;
    }

    public CacheManager fallback(File path) throws FileNotFoundException {
        fallbackDir = path;

        if (!fallbackDir.exists()) {
            throw new FileNotFoundException("The directory " + path + " doesn't exist.");
        }

        Log.d(TAG, "Fallback directory set to " + path);

        return this;
    }

    public CacheManager unsafe(Boolean value) {
        isUnsafe = value;

        if (isUnsafe == true) {
            Log.d(TAG, "Unsafe mode, SSL errors will be ignored");
        }

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

        try {
            Response response = client.newCall(request).execute();

            File file = new File(dir, path);

            file.getParentFile().mkdirs();
            file.createNewFile();

            BufferedSink sink = Okio.buffer(Okio.sink(file));

            sink.writeAll(response.body().source());
            sink.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to download file " + hostUrl + path);

            throw e;
        }
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
            Log.e(TAG, "Failed to read cache manifest " + cacheManifest.getAbsolutePath());

            throw e;
        }

        return fileList;
    }

    private void cleanUp() {
        Log.d(TAG, "Cleaning up temporary files");

        tmpDir.delete();
    }

    private void refreshCache() throws IOException {
        Log.d(TAG, "Refreshing cache");

        emptyDir(tmpDir);

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

        String manifesttmp = readFileAsText(new File(tmpDir.getAbsolutePath(), manifestPath));
        String manifestCached = readFileAsText(new File(wwwDir.getAbsolutePath(), manifestPath));

        if (manifesttmp != null && manifestCached != null && manifesttmp.equals(manifestCached)) {
            Log.d(TAG, "Cache manifest has not changed");

            return;
        }

        File cacheManifest = new File(tmpDir.getAbsolutePath(), manifestPath);

        List<String> fileList = listFiles(cacheManifest);

        for (String file : fileList) {
            downloadFile(file, tmpDir);

            if (!new File(tmpDir, file).exists()) {
                throw new IOException();
            }
        }

        emptyDir(wwwDir);

        try {
            copyFiles(tmpDir, wwwDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy files from " + tmpDir.getAbsolutePath() + " to " + wwwDir.getAbsolutePath());

            emptyDir(wwwDir);

            throw e;
        }

        cleanUp();
    }

    private void executeSync() {
        if (fallbackDir != null && fallbackDir.exists()) {
            try {
                if (wwwDir.exists()) {
                    File[] contents = wwwDir.listFiles();

                    if (contents == null || contents.length == 0) {
                        Log.d(TAG, "Cache directory is empty");

                        copyFiles(fallbackDir, wwwDir);
                    }
                } else {
                    copyFiles(fallbackDir, wwwDir);
                }
            } catch (IOException e) {
                // Failed to read manifest file
                Log.e(TAG, "Failed to copy files from " + fallbackDir.getAbsolutePath() + " to " + wwwDir.getAbsolutePath(), e);
            }
        }

        // Refresh the cache
        try {
            refreshCache();
        } catch (IOException e) {
            Log.e(TAG, "Aborting refresh", e);

            cleanUp();
        }
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
