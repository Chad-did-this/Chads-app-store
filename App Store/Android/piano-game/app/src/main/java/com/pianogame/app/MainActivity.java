package com.pianogame.app;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.util.HashMap;

public final class MainActivity extends Activity {
    private static final String HOST="appassets.androidplatform.net";
    private static final String URL="https://"+HOST+"/game/index.html";
    private WebView web;
    private PermissionRequest pending;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        s.setMediaPlaybackRequiresUserGesture(false);
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
            @Override public void onPermissionRequest(PermissionRequest req){
                runOnUiThread(()->{
                    Uri o=req.getOrigin();
                    String[] resources=req.getResources();
                    if(!"https".equals(o.getScheme())||!HOST.equals(o.getHost())||(o.getPort()!=-1&&o.getPort()!=443)||resources.length!=1||!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[0])){req.deny();return;}
                    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){req.grant(resources);return;}
                    if(pending!=null)pending.deny();
                    pending=req;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42);
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest req){runOnUiThread(()->{if(pending==req)pending=null;});}
        });
        web.loadUrl(URL);
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",new HashMap<>(),new ByteArrayInputStream(new byte[0]));}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==42&&pending!=null){PermissionRequest req=pending;pending=null;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else req.deny();}
    }
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("if(typeof closeMic==='function')closeMic();",null);web.onPause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(pending!=null){pending.deny();pending=null;}if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
