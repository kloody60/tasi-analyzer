package com.tasi.analyzer;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView wv;
    private final ExecutorService ex = Executors.newFixedThreadPool(2);
    private int nid = 1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("sig", "تنبيهات الإشارة", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true; // لا نسمح بالتنقل خارج الصفحة المحلية
            }
        });
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wv != null) wv.onResume();
    }

    @Override
    protected void onDestroy() {
        ex.shutdownNow();
        if (wv != null) wv.destroy();
        super.onDestroy();
    }

    private class Bridge {
        @JavascriptInterface
        public void get(final int id, final String url) {
            ex.execute(() -> {
                String out;
                boolean ok = false;
                try {
                    URL u = new URL(url);
                    String h = u.getHost();
                    if (!"https".equals(u.getProtocol())
                            || !(h.equals("query1.finance.yahoo.com") || h.equals("query2.finance.yahoo.com"))) {
                        throw new IOException("blocked");
                    }
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(12000);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    if (in != null) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                        in.close();
                    }
                    ok = code < 400;
                    out = ok ? bo.toString("UTF-8") : String.valueOf(code);
                } catch (Exception e) {
                    out = String.valueOf(e.getMessage());
                }
                final String o = out;
                final boolean k = ok;
                runOnUiThread(() -> {
                    if (wv != null) wv.evaluateJavascript("window.__cb(" + id + "," + k + "," + JSONObject.quote(o) + ")", null);
                });
            });
        }

        @JavascriptInterface
        public void notify(final String title, final String text) {
            runOnUiThread(() -> {
                try {
                    Notification.Builder nb = Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(MainActivity.this, "sig")
                            : new Notification.Builder(MainActivity.this);
                    nb.setContentTitle(title).setContentText(text)
                            .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);
                    getSystemService(NotificationManager.class).notify(nid++, nb.build());
                    Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (vb != null) vb.vibrate(300);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
