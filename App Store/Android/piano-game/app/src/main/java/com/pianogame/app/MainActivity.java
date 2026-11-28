package com.pianogame.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final String ROOT = "https://appassets.androidplatform.net/game/";
    private static final String URL = ROOT + "index.html";
    private static final int AUDIO_PERMISSION = 42;

    private final AtomicInteger audioRequest = new AtomicInteger();
    private WebView web;
    private NativeMic mic;
    private boolean permissionPending;
    private boolean wantsMic;
    private int wantedRequest;
    private boolean foreground;
    private boolean destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mic = new NativeMic();
        web = new WebView(this);
        setContentView(web);

        web.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        web.requestApplyInsets();

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request
            ) {
                return localAsset(request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(
                WebView view, WebResourceRequest request
            ) {
                return !URL.equals(request.getUrl().toString());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }
        });
        web.addJavascriptInterface(new AudioBridge(), "AndroidMic");
        web.loadUrl(URL);
    }

    private WebResourceResponse localAsset(String url) {
        String file;
        String type;

        switch (url) {
            case URL:
                file = "index.html";
                type = "text/html";
                break;
            case ROOT + "css/game.css":
                file = "css/game.css";
                type = "text/css";
                break;
            case ROOT + "js/pitch.js":
                file = "js/pitch.js";
                type = "application/javascript";
                break;
            case ROOT + "js/native-microphone.js":
                file = "js/native-microphone.js";
                type = "application/javascript";
                break;
            case ROOT + "js/game.js":
                file = "js/game.js";
                type = "application/javascript";
                break;
            case ROOT + "js/note-study.js":
                file = "js/note-study.js";
                type = "application/javascript";
                break;
            default:
                return blocked();
        }

        try {
            return new WebResourceResponse(type, "UTF-8", getAssets().open(file));
        } catch (IOException error) {
            return blocked();
        }
    }

    private final class AudioBridge {
        @JavascriptInterface public void start() {
            int request = audioRequest.incrementAndGet();
            mic.markStarting();

            runOnUiThread(() -> {
                if (request != audioRequest.get() || destroyed) return;
                if (!foreground) {
                    mic.fail("Open Piano Game and tap Play to use the microphone.");
                    return;
                }

                wantsMic = true;
                wantedRequest = request;
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    mic.start();
                } else if (!permissionPending) {
                    permissionPending = true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
                }
            });
        }

        @JavascriptInterface public String status() {
            return mic.status();
        }

        @JavascriptInterface public String frame() {
            return mic.frame();
        }

        @JavascriptInterface public void stop() {
            int request = audioRequest.incrementAndGet();
            mic.stop();
            runOnUiThread(() -> {
                if (request == audioRequest.get()) wantsMic = false;
            });
        }
    }

    private WebResourceResponse blocked() {
        return new WebResourceResponse(
            "text/plain", "UTF-8", 403, "Forbidden",
            new HashMap<>(), new ByteArrayInputStream(new byte[0])
        );
    }

    @Override public void onRequestPermissionsResult(
        int code, String[] permissions, int[] results
    ) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != AUDIO_PERMISSION) return;
        permissionPending = false;
        if (!wantsMic || !foreground || destroyed || wantedRequest != audioRequest.get()) return;

        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            mic.start();
        } else {
            wantsMic = false;
            mic.fail("Permission denied. Allow microphone access in Android settings.");
        }
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
    }

    @Override protected void onStop() {
        foreground = false;
        wantsMic = false;
        audioRequest.incrementAndGet();
        mic.stop();
        if (web != null) {
            web.evaluateJavascript(
                "if (typeof starting !== 'undefined' && (starting || mic)) "
                    + "audioInterrupted('Recording stopped. Tap Play to restart.');",
                null
            );
        }
        super.onStop();
    }

    @Override protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        foreground = false;
        wantsMic = false;
        audioRequest.incrementAndGet();
        mic.destroy();
        if (web != null) {
            web.removeJavascriptInterface("AndroidMic");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

