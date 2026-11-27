package com.chaddidthis.universalstore;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BACKGROUND=0xff10141b, PANEL=0xff1a202a, MUTED=0xffa5afbe;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<ReleaseCatalog.App> apps=new ArrayList<>();
    private LinearLayout root, list;
    private TextView status, androidTab, iosTab;
    private EditText search;
    private String platform="android";
    private boolean loading=false, installing=false;
    private long lastSuccess=0;
    private final android.os.Handler refreshHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable periodicRefresh=new Runnable(){ public void run(){ if(!isFinishing()&&!isDestroyed()){if(!installing)refresh();refreshHandler.postDelayed(this,120000);} } };
    private File pending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(24));
        if(android.os.Build.VERSION.SDK_INT>=35) {
            root.setOnApplyWindowInsetsListener((v,i)->{
                android.graphics.Insets bars=i.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(dp(20),dp(16)+bars.top,dp(20),dp(20)+bars.bottom);
                return i;
            });
        }
        scroll.addView(root);
        setContentView(scroll);
        root.addView(label("CHAD'S APP STORE",14,MUTED,true));
        root.addView(label("Your apps. Your platform.",31,Color.WHITE,true));
        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        androidTab=button("Android",0xff65e4a1,()->choose("android"));
        iosTab=button("Apple",0xff9fc4ff,()->choose("ios"));
        tabs.addView(androidTab,new LinearLayout.LayoutParams(0,dp(66),1));
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(66),1);
        right.leftMargin=dp(8);
        tabs.addView(iosTab,right);
        root.addView(tabs);
        search=new EditText(this);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(MUTED);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("Search applications");
        search.setPadding(dp(12),dp(10),dp(12),dp(10));
        search.setBackground(shape(PANEL));
        add(root,search,12);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int a,int b,int c){render();}
            public void afterTextChanged(android.text.Editable e){}
        });
        add(root,button("Refresh / Check for updates",0xffe7edf7,this::refresh),10);
        status=label("",13,MUTED,false);
        add(root,status,10);
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        choose("android");
    }

    @Override protected void onResume() {
        super.onResume();
        if(pending!=null && getPackageManager().canRequestPackageInstalls()) {
            File apk=pending; pending=null; launchInstaller(apk);
        }
        if(list!=null) { render(); if(!installing)refresh(); }
        refreshHandler.removeCallbacks(periodicRefresh);
        refreshHandler.postDelayed(periodicRefresh,120000);
    }

    @Override protected void onPause() {
        refreshHandler.removeCallbacks(periodicRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        refreshHandler.removeCallbacks(periodicRefresh);
        worker.shutdownNow();
        super.onDestroy();
    }

    private void choose(String value) {
        platform=value;
        androidTab.setBackground(shape(value.equals("android")?0xff20583c:0xff142c23));
        iosTab.setBackground(shape(value.equals("ios")?0xff2c4b79:0xff192a45));
        render();
    }

    private void refresh() {
        if(loading || isDestroyed()) return;
        loading=true;
        status.setText("Checking GitHub releases…");
        worker.execute(()->{
            try {
                List<ReleaseCatalog.App> data=ReleaseCatalog.load();
                runOnUiThread(()->{if(isDestroyed())return;loading=false;apps=data;lastSuccess=System.currentTimeMillis();status.setText("Catalog updated · "+java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(lastSuccess)));render();});
            } catch(Exception e) {
                runOnUiThread(()->{if(isDestroyed())return;loading=false;status.setText((lastSuccess==0?"Catalog unavailable: ":"Refresh failed; showing last successful list: ")+e.getMessage());});
            }
        });
    }

    private void render() {
        if(list==null || search==null) return;
        list.removeAllViews();
        int accent=platform.equals("android")?0xff65e4a1:0xff9fc4ff;
        list.addView(label(platform.equals("android")?"ANDROID / APK":"APPLE / IPA",13,accent,true));
        String query=search.getText().toString().trim().toLowerCase(Locale.US);
        String previous="";
        List<ReleaseCatalog.App> sorted=new ArrayList<>(apps);
        sorted.sort(Comparator.comparing(a->a.category+" "+a.name));
        for(ReleaseCatalog.App app:sorted) {
            if(!app.platform.equals(platform) || !(app.name+" "+app.category+" "+app.description).toLowerCase(Locale.US).contains(query)) continue;
            if(!previous.equals(app.category)) {
                list.addView(label(app.category,16,MUTED,true));
                previous=app.category;
            }
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(15),dp(12),dp(15),dp(12));
            card.setBackground(shape(PANEL));
            card.addView(label(app.name,20,Color.WHITE,true));
            card.addView(label(app.description,14,MUTED,false));
            card.addView(label(app.url==null?"Not released":"Available: "+app.version,13,accent,false));
            if(app.url!=null) {
                if(platform.equals("ios")) {
                    card.addView(button("Download IPA",accent,()->browse(app.url)));
                } else {
                    PackageInfo installed=installed(app.packageId);
                    long current=installed==null?0:version(installed);
                    if(current>=app.versionCode) {
                        card.addView(button("Open / Installed",accent,()->openApp(app.packageId)));
                    } else {
                        card.addView(button(current>0?"Update":"Install",accent,()->install(app)));
                    }
                }
            }
            add(list,card,8);
        }
        if(platform.equals("ios")) {
            add(list,label("iPhone apps require signing and a compatible installation method. Downloading an IPA alone does not install it.",14,MUTED,false),16);
            add(list,button("SideStore setup",accent,()->browse("https://sidestore.io/")),8);
            add(list,button("AltStore Classic setup",accent,()->browse("https://altstore.io/")),8);
            add(list,button("Copy iPhone source URL",accent,()->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("App source",ReleaseCatalog.SOURCE));
                Toast.makeText(this,"Source URL copied",Toast.LENGTH_SHORT).show();
            }),8);
        }
        add(list,button("GitHub release history",MUTED,()->browse(ReleaseCatalog.WEB+"/releases")),14);
    }

    private PackageInfo installed(String pkg) {
        if(pkg==null || pkg.isEmpty()) return null;
        try { return getPackageManager().getPackageInfo(pkg,signingFlag()); }
        catch(PackageManager.NameNotFoundException e){return null;}
    }

    private int signingFlag() {
        return android.os.Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
    }

    private long version(PackageInfo p) {
        return android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;
    }

    private Signature[] signatures(PackageInfo p) {
        if(android.os.Build.VERSION.SDK_INT>=28 && p.signingInfo!=null) return p.signingInfo.getApkContentsSigners();
        return p.signatures;
    }

    private void install(ReleaseCatalog.App app) {
        if(installing) return;
        installing=true;
        status.setText("Downloading and verifying "+app.name+"…");
        worker.execute(()->{
            File apk=null;
            try {
                apk=ReleaseCatalog.download(app,new File(getCacheDir(),"downloads"));
                PackageInfo archive=getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),signingFlag());
                if(archive==null || !archive.packageName.equals(app.packageId) || version(archive)!=app.versionCode) throw new Exception("APK package or version mismatch");
                PackageInfo existing=installed(app.packageId);
                if(existing!=null) {
                    Signature[] before=signatures(existing), after=signatures(archive);
                    if(before==null || after==null || !new HashSet<>(Arrays.asList(before)).equals(new HashSet<>(Arrays.asList(after)))) throw new Exception("Signing key changed. A matching signed update is required.");
                }
                final File ready=apk;
                runOnUiThread(()->{
                    installing=false;
                    status.setText("APK verified. Confirm installation in Android.");
                    if(!getPackageManager().canRequestPackageInstalls()) {
                        pending=ready;
                        try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))); }
                        catch(Exception e){status.setText("Enable Install unknown apps for this store in Android settings.");}
                    } else launchInstaller(ready);
                });
            } catch(Exception e) {
                if(apk!=null) apk.delete();
                runOnUiThread(()->{installing=false;status.setText("Install unavailable: "+e.getMessage());});
            }
        });
    }

    private void launchInstaller(File apk) {
        try {
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",apk);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch(Exception e){status.setText("Could not open Android installer: "+e.getMessage());}
    }

    private void openApp(String pkg) {
        Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent!=null) startActivity(intent);
        else Toast.makeText(this,"Already installed",Toast.LENGTH_SHORT).show();
    }

    private void browse(String address) {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address))); }
        catch(Exception e){Toast.makeText(this,"No browser available",Toast.LENGTH_SHORT).show();}
    }

    private TextView label(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(5),0,dp(5));
        return t;
    }

    private TextView button(String value,int color,Runnable action) {
        TextView t=label(value,15,color,true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(12),dp(13),dp(12),dp(13));
        t.setBackground(shape(0xff283342));
        t.setFocusable(true);
        t.setOnClickListener(v->action.run());
        return t;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(14));return d;
    }

    private void add(LinearLayout parent,View v,int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(margin);parent.addView(v,p);
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
