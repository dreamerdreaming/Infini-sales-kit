package com.infini.saleskit;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int PICK = 7;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0E1220"));
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportMultipleWindows(false);
        s.setTextZoom(100);
        web.setBackgroundColor(Color.parseColor("#0E1220"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                if (openWhatsApp(u)) return true;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                catch (Exception e) { toast("No app to open this link"); }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                String type = "*/*";
                String[] acc = p.getAcceptTypes();
                if (acc != null && acc.length > 0 && acc[0] != null && acc[0].startsWith("image")) type = "image/*";
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(type);
                try { startActivityForResult(Intent.createChooser(i, "Choose file"), PICK); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });

        if (b != null) web.restoreState(b);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK && fileCb != null) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null && data.getData() != null) out = new Uri[]{data.getData()};
            fileCb.onReceiveValue(out);
            fileCb = null;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    private String waPkg = "com.whatsapp.w4b";

    /** Sends wa.me / api.whatsapp.com links to the chosen WhatsApp app, falling back to the other one. */
    private boolean openWhatsApp(Uri u) {
        String h = u.getHost();
        if (h == null || !(h.equals("wa.me") || h.endsWith("whatsapp.com"))) return false;
        String phone = "";
        if (h.equals("wa.me")) { String p = u.getPath(); if (p != null) phone = p.replace("/", ""); }
        else { String p = u.getQueryParameter("phone"); if (p != null) phone = p; }
        String text = u.getQueryParameter("text");
        Uri api = Uri.parse("https://api.whatsapp.com/send?phone=" + phone + (text != null ? "&text=" + Uri.encode(text) : ""));
        Uri scheme = Uri.parse("whatsapp://send?phone=" + phone + (text != null ? "&text=" + Uri.encode(text) : ""));
        String other = waPkg.equals("com.whatsapp") ? "com.whatsapp.w4b" : "com.whatsapp";
        for (String pkg : new String[]{waPkg, other}) {
            for (Uri target : new Uri[]{scheme, api}) {
                try { Intent i = new Intent(Intent.ACTION_VIEW, target); i.setPackage(pkg); startActivity(i); return true; }
                catch (Exception ignored) { }
            }
        }
        return false;
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
    }

    /** Writes a PNG into Pictures/Infini and returns its uri (null on old Android). */
    private Uri writeImage(String dataUrl, String name) throws Exception {
        byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
        String fn = System.currentTimeMillis() + "-" + name;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, fn);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Infini");
            Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(bytes); }
            return u;
        }
        File f = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fn);
        try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
        return null;
    }

    class Bridge {
        @JavascriptInterface
        public void setWaApp(String pkg) {
            if ("com.whatsapp".equals(pkg) || "com.whatsapp.w4b".equals(pkg)) waPkg = pkg;
        }

        @JavascriptInterface
        public void tap() {
            runOnUiThread(() -> web.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP));
        }

        @JavascriptInterface
        public void setBars(String hex, boolean lightBg) {
            runOnUiThread(() -> {
                try {
                    int c = Color.parseColor(hex);
                    getWindow().setStatusBarColor(c);
                    getWindow().setNavigationBarColor(c);
                    web.setBackgroundColor(c);
                    int f = 0;
                    if (lightBg) {
                        f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                        if (Build.VERSION.SDK_INT >= 26) f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                    getWindow().getDecorView().setSystemUiVisibility(f);
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void saveImage(String dataUrl, String name) {
            try {
                Uri u = writeImage(dataUrl, name);
                toast(u != null ? "Saved to Gallery › Pictures/Infini" : "Saved in app pictures folder");
            } catch (Exception e) { toast("Could not save image"); }
        }

        @JavascriptInterface
        public void shareImage(String dataUrl, String name) {
            try {
                Uri u = writeImage(dataUrl, name);
                if (u == null) { toast("Saved. Share it from your Gallery."); return; }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/png");
                send.putExtra(Intent.EXTRA_STREAM, u);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent wb = new Intent(send);
                wb.setPackage(waPkg);
                Intent wa = new Intent(send);
                wa.setPackage(waPkg.equals("com.whatsapp") ? "com.whatsapp.w4b" : "com.whatsapp");
                runOnUiThread(() -> {
                    try { startActivity(wb); }
                    catch (Exception e1) {
                        try { startActivity(wa); }
                        catch (Exception e2) { startActivity(Intent.createChooser(send, "Share image")); }
                    }
                });
            } catch (Exception e) { toast("Could not share image"); }
        }

        @JavascriptInterface
        public void saveFile(String text, String name) {
            try {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Infini");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(bytes); }
                    toast("Backup saved to Downloads › Infini");
                } else {
                    File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                    toast("Backup saved: " + f.getAbsolutePath());
                }
            } catch (Exception e) { toast("Could not save backup"); }
        }
    }
}
