package com.chaddidthis.commandpro.home;

import com.chaddidthis.commandpro.architecture.ArchitectureActivity;
import com.chaddidthis.commandpro.hardware.HardwareActivity;
import com.chaddidthis.commandpro.shared.adb.LocalAdb;
import com.chaddidthis.commandpro.shared.ui.InsetsSupport;
import com.chaddidthis.commandpro.terminal.TerminalActivity;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int BG=0xff0a1312, PANEL=0xff192923, TEXT=0xffe8f6ee, GREEN=0xff82efac, MUTED=0xff9eb7a9;
    private TextView status;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(24));root.setBackgroundColor(BG);scroll.addView(root);
        setContentView(scroll);
        InsetsSupport.apply(scroll);
        root.addView(label("COMMAND CENTER",29,GREEN,true));
        root.addView(label("PRO  //  DEVICE CONTROL & ARCHITECTURE",12,MUTED,true));
        status=label("",14,TEXT,false);root.addView(status);
        root.addView(label("STANDARD MODE",12,GREEN,true));
        card(root,"Hardware Explorer PRO","Hardware diagnostics, live sensors, camera, battery, USB, vendor lighting, reports and export. No ADB required.",HardwareActivity.class);
        root.addView(label("ADVANCED MODE",12,GREEN,true));
        card(root,"ADB Terminal & System Controls","Command console, packages, processes, settings, hardware, files, network, logs, saved scripts and Self Debugger quick scans. Pair with your own phone.",TerminalActivity.class);
        card(root,"Phone Architecture Explorer","12 automated read-only subsystem scans. Firmware, kernel, vendor software, service names, drivers, USB, lights and camera interfaces.",ArchitectureActivity.class);
        root.addView(label("One shared local ADB identity. Once paired and connected, both advanced tools can use the same connection. Disconnect inside either tool or disable Wireless debugging when finished.",13,MUTED,false));
        root.addView(label("No root or cloud service. Protected Android and vendor interfaces may remain unavailable. Reports stay local unless you export them.",13,MUTED,false));
    }
    @Override protected void onResume(){
        super.onResume();
        LocalAdb adb=LocalAdb.peek();
        if(status!=null)status.setText(adb!=null&&adb.isConnected()?"●  LOCAL ADB CONNECTED":"○  STANDARD MODE / ADB DISCONNECTED");
    }
    private void card(LinearLayout parent,String title,String description,Class<?> target){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),0xff345346);panel.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(7),0,dp(12));parent.addView(panel,params);
        panel.addView(label(title,20,TEXT,true));panel.addView(label(description,14,MUTED,false));
        TextView action=label("OPEN  →",14,GREEN,true);panel.addView(action);
        panel.setOnClickListener(v->startActivity(new Intent(this,target)));panel.setFocusable(true);
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
