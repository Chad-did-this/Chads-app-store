package com.pianogame.app;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.util.HashMap;

public final class MainActivity extends Activity {
    private static final String HOST="appassets.androidplatform.net";
    private static final String URL="https://"+HOST+"/game/index.html";
    private WebView web;
    private NativeMic mic;
    private boolean permissionPending;
    private boolean wantsMic;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mic=new NativeMic();
        web=new WebView(this);
        setContentView(web);
        web.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.requestApplyInsets();
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
                if(URL.equals(req.getUrl().toString())){
                    try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}
                    catch(java.io.IOException e){return blocked();}
                }
                return blocked();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return !URL.equals(req.getUrl().toString());}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest req){req.deny();}
        });
        web.addJavascriptInterface(new AudioBridge(),"AndroidMic");
        web.loadUrl(URL);
    }
    private final class AudioBridge {
        @JavascriptInterface public void start(){
            mic.markStarting();
            runOnUiThread(()->{
                wantsMic=true;
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){mic.start();return;}
                if(!permissionPending){
                    permissionPending=true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42);
                }
            });
        }
        @JavascriptInterface public String status(){return mic.status();}
        @JavascriptInterface public String frame(){return mic.frame();}
        @JavascriptInterface public void stop(){runOnUiThread(()->{wantsMic=false;mic.stop();});}
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",new HashMap<>(),new ByteArrayInputStream(new byte[0]));}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code!=42)return;
        permissionPending=false;
        if(!wantsMic)return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)mic.start();
        else mic.fail("Permission denied. Allow microphone access in Android settings.");
    }
    @Override protected void onStop(){
        if(!permissionPending){wantsMic=false;mic.stop();if(web!=null)web.evaluateJavascript("if(typeof nativeAudio!=='undefined'&&nativeAudio)audioInterrupted('Recording stopped. Tap Play to restart.');",null);}
        super.onStop();
    }
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){wantsMic=false;mic.destroy();if(web!=null){web.removeJavascriptInterface("AndroidMic");web.destroy();web=null;}super.onDestroy();}
}
