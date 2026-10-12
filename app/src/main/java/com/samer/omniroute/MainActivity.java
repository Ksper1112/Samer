package com.samer.omniroute;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String PERM = "com.termux.permission.RUN_COMMAND";
    private static final String BASE = "http://127.0.0.1:20128";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        begin();
    }

    private void msg(String text) {
        ui.post(() -> web.loadDataWithBaseURL(null,
                "<html><body dir='rtl' style='font-family:sans-serif;background:#111;"
                        + "color:#eee;padding:24px;font-size:18px'>" + text + "</body></html>",
                "text/html", "UTF-8", null));
    }

    private void begin() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
        } catch (PackageManager.NameNotFoundException e) {
            msg("Termux غير منصّب.");
            return;
        }
        if (checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
            msg("وافق على صلاحية تشغيل أوامر Termux.");
            requestPermissions(new String[]{PERM}, 1);
            return;
        }
        launch();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            launch();
        } else {
            msg("الصلاحية مرفوضة. فعّلها من إعدادات التطبيق ثم أعد فتحه.");
        }
    }

    private void launch() {
        if (busy) return;
        busy = true;
        msg("جاري تشغيل الخادم...");
        new Thread(() -> {
            if (!up()) {
                ui.post(this::startTermux);
                for (int i = 0; i < 60 && !up(); i++) pause(1500);
            }
            if (up()) {
                ui.post(() -> web.loadUrl(BASE + "/dashboard"));
            } else {
                msg("تعذّر تشغيل الخادم. تأكد من وجود start-gateway.sh في Termux، "
                        + "ومن تفعيل allow-external-apps، ثم أعد فتح التطبيق.");
            }
            busy = false;
        }).start();
    }

    private void startTermux() {
        try {
            Intent i = new Intent();
            i.setClassName("com.termux", "com.termux.app.RunCommandService");
            i.setAction("com.termux.RUN_COMMAND");
            i.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                    new String[]{TERMUX_HOME + "/start-gateway.sh"});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(i);
        } catch (Exception e) {
            msg("فشل إرسال الأمر إلى Termux: " + e.getClass().getSimpleName());
        }
    }

    private boolean up() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/").openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(3000);
            c.setInstanceFollowRedirects(false);
            c.getResponseCode();
            c.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
