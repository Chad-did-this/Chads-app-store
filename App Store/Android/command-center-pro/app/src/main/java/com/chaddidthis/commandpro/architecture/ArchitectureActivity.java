package com.chaddidthis.commandpro.architecture;

import com.chaddidthis.commandpro.shared.adb.AdbConnectionDialog;
import com.chaddidthis.commandpro.shared.adb.LocalAdb;
import com.chaddidthis.commandpro.shared.ui.InsetsSupport;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import io.github.muntashirakon.adb.AdbStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArchitectureActivity extends Activity {
    private static final int BG=0xff101820, PANEL=0xff21313d, TEXT=0xffe4eef5, ACCENT=0xff70e1c4, MUTED=0xffa4b7c6;
    private final ExecutorService worker=LocalAdb.WORKER;
    private final ScheduledExecutorService timer=LocalAdb.TIMER;
    private final ExecutorService fileWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile LocalAdb adb;
    private volatile AdbStream active;
    private volatile boolean busy;
    private TextView status, summary;
    private LinearLayout cards;
    private ProgressBar progress;
    private String report="", exportSnapshot="";
    private final ArrayList<Finding> findings=new ArrayList<>();

    private static final class Section {
        final String title, purpose, cmd;
        Section(String t,String p,String c){title=t;purpose=p;cmd=c;}
    }

    private static final class Finding {
        final Section section;
        final String output;
        Finding(Section s,String o){section=s;output=o;}
    }

    private static final Section[] SECTIONS={
        new Section("Firmware and build","Android version, board, vendor, build configuration and verified boot. These are properties, not source code.",
            "for p in ro.product.manufacturer ro.product.model ro.product.device ro.product.board ro.board.platform ro.hardware ro.soc.manufacturer ro.soc.model ro.build.version.release ro.build.version.sdk ro.build.version.security_patch ro.build.type ro.build.tags ro.build.fingerprint ro.vendor.build.fingerprint ro.boot.verifiedbootstate ro.boot.flash.locked ro.boot.vbmeta.device_state; do printf '%s = ' \"$p\"; getprop \"$p\"; done"),
        new Section("CPU and kernel","Kernel release, processor topology, online cores and supported frequency policies.",
            "uname -srmo; cat /sys/devices/system/cpu/possible /sys/devices/system/cpu/online; ls -d /sys/devices/system/cpu/cpufreq/policy*; for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -d \"$p\" ] || continue; echo \"$p\"; cat \"$p/cpuinfo_min_freq\" \"$p/cpuinfo_max_freq\" \"$p/scaling_governor\"; done"),
        new Section("Android services","Binder service names and registered service types. Names may suggest what controls a component; they do not prove an accessible control API.",
            "service list; echo '--- Diagnostic services ---'; dumpsys -l"),
        new Section("Vendor software","Manufacturer executables, system packages and hardware abstraction library names.",
            "echo '--- Vendor binaries ---'; ls /vendor/bin /vendor/bin/hw /odm/bin /odm/bin/hw; echo '--- HAL libraries ---'; ls /vendor/lib64/hw /vendor/lib/hw /odm/lib64/hw; echo '--- System packages ---'; pm list packages -s -f"),
        new Section("Startup architecture","Visible init script and VINTF manifest filenames, plus the current SELinux mode.",
            "ls /vendor/etc/init /odm/etc/init /system/etc/init /vendor/etc/vintf /odm/etc/vintf; getenforce"),
        new Section("Hardware interfaces","Kernel interface classes, input devices, I2C, SPI and platform device names. Listing an interface does not establish write access.",
            "ls /sys/class; echo '--- Input ---'; ls /sys/class/input; echo '--- I2C ---'; ls /sys/bus/i2c/devices; echo '--- SPI ---'; ls /sys/bus/spi/devices; echo '--- Platform devices ---'; ls /sys/bus/platform/devices"),
        new Section("Lights and display","Possible backlight, LEDs, flashlight and vendor lighting interfaces. No writes are attempted.",
            "echo '--- LEDs ---'; ls /sys/class/leds /sys/class/backlight; echo '--- Display ---'; wm size; wm density; echo '--- Vendor names containing lighting/display terms ---'; ls /vendor/bin/hw /vendor/etc/init /odm/etc/init | grep -Ei 'led|light|flash|torch|display|backlight'"),
        new Section("Camera and night vision","Camera-related service and vendor names, and advertised camera features. Infrared capabilities require real device verification.",
            "pm list features | grep -Ei 'camera|infrared'; service list | grep -Ei 'camera|infrared|night'; ls /vendor/bin/hw /vendor/lib64/hw /vendor/etc/init /odm/etc/init | grep -Ei 'camera|infrared|night|ircam|sensor'"),
        new Section("USB and expansion","USB controller classes, Type-C roles, gadget functions, and advertised USB host/accessory features.",
            "pm list features | grep -Ei 'usb|nfc'; echo '--- USB classes ---'; ls /sys/class/udc /sys/class/typec /sys/class/usb_role /sys/bus/usb/devices; echo '--- USB properties ---'; getprop sys.usb.config; getprop sys.usb.state; getprop persist.sys.usb.config; echo '--- Type-C role ---'; for p in /sys/class/typec/port*/data_role /sys/class/typec/port*/power_role; do [ -f \"$p\" ] && { echo \"$p\"; cat \"$p\"; }; done"),
        new Section("Power and sensors","Power supply interfaces, thermal zones, available sensor names and battery status. Battery serial numbers are not requested.",
            "ls /sys/class/power_supply /sys/class/thermal /sys/class/hwmon /sys/bus/iio/devices; echo '--- Thermal zone types ---'; for p in /sys/class/thermal/thermal_zone*/type; do [ -r \"$p\" ] && { printf '%s ' \"$p\"; cat \"$p\"; }; done; echo '--- Battery ---'; dumpsys battery; echo '--- Sensor features ---'; pm list features | grep -Ei 'sensor'"),
        new Section("Storage and memory","Partition names, filesystem capacity, and memory totals without reading personal files.",
            "df -h /data /system /vendor /odm; ls /dev/block/by-name /dev/block/bootdevice/by-name; head -n 12 /proc/meminfo"),
        new Section("Security boundaries","Actual shell identity, SELinux mode, accessible kernel settings and permission limits. No security bypass or root is attempted.",
            "id; getenforce; echo '--- Readable kernel interfaces ---'; for p in /proc/modules /proc/config.gz /sys/kernel/security /vendor/etc/selinux /system/etc/selinux; do if [ -r \"$p\" ]; then echo \"Readable: $p\"; else echo \"Restricted or absent: $p\"; fi; done; echo '--- Kernel modules (names only) ---'; awk '{print $1}' /proc/modules")
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        adb=LocalAdb.peek();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(8));
        root.setBackgroundColor(BG);
        setContentView(root);
        InsetsSupport.apply(root);
        root.addView(label("PHONE ARCHITECTURE",23,ACCENT,true));
        root.addView(label("EXPLORER  //  READ-ONLY LOCAL ADB",12,MUTED,true));
        status=label(adb!=null&&adb.isConnected()?"Connected. Choose SCAN ALL.":"Not connected",14,TEXT,false);
        root.addView(status);
        LinearLayout row=new LinearLayout(this);
        root.addView(row);
        button(row,"CONNECT",v->connectionDialog());
        button(row,"SCAN ALL",v->scan(null));
        button(row,"STOP",v->stop());
        LinearLayout actions=new LinearLayout(this);
        root.addView(actions);
        button(actions,"SELECT SCAN",v->chooseSection());
        button(actions,"COPY REPORT",v->export(false));
        button(actions,"SAVE TXT",v->export(true));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(SECTIONS.length);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));
        ScrollView scroll=new ScrollView(this);
        cards=new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cards);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        summary=label("Explore firmware, vendor programs, hardware interfaces and Android services. Every scan uses fixed read-only commands. No files or settings are modified.",15,TEXT,false);
        cards.addView(summary);
        for(Section s:SECTIONS){
            TextView t=label(s.title+"\n"+s.purpose,14,MUTED,false);
            t.setPadding(dp(12),dp(10),dp(12),dp(10));
            cards.addView(t);
        }
    }

    private void connectionDialog(){
        if(busy)return;
        AdbConnectionDialog.show(this,this::operation);
    }

    private void operation(String name,AdbConnectionDialog.Operation op){
        if(busy)return;
        busy=true;status.setText(name+"…");
        worker.execute(()->{
            if(isDestroyed()){busy=false;return;}
            String result;
            try{adb=LocalAdb.get(this);result=op.run(adb);}
            catch(Exception e){result="Connection error: "+e;}
            final String message=result;
            runOnUiThread(()->{busy=false;if(!isDestroyed())status.setText(message);});
        });
    }

    private void chooseSection(){
        if(busy)return;
        String[] titles=new String[SECTIONS.length];
        for(int i=0;i<titles.length;i++)titles[i]=SECTIONS[i].title;
        new AlertDialog.Builder(this).setTitle("Scan a subsystem").setItems(titles,(d,i)->scan(SECTIONS[i])).show();
    }

    private void scan(Section only){
        if(busy)return;
        if(adb==null||!adb.isConnected()){toast("Pair and connect first");return;}
        new AlertDialog.Builder(this).setTitle("Scan this phone?")
            .setMessage("Reads system information through authorized local ADB. Some results may reveal device configuration. Data stays on your phone unless you export it.")
            .setNegativeButton("Cancel",null).setPositiveButton("Scan",(d,w)->startScan(only)).show();
    }

    private void startScan(Section only){
        busy=true;cancelled.set(false);findings.clear();cards.removeAllViews();
        summary=label("Scanning…",15,TEXT,false);cards.addView(summary);
        Section[] selected=only==null?SECTIONS:new Section[]{only};
        progress.setMax(selected.length);progress.setProgress(0);
        worker.execute(()->{
            if(isDestroyed()){busy=false;return;}
            for(int i=0;i<selected.length&&!cancelled.get();i++){
                Section s=selected[i];
                runOnUiThread(()->status.setText("Scanning "+s.title+"…"));
                String output;
                try{output=read(s.cmd);}
                catch(Exception e){output="[Unavailable: "+e+"]";}
                final Finding finding=new Finding(s,output);
                final int count=i+1;
                runOnUiThread(()->{
                    if(isDestroyed())return;
                    findings.add(finding);addCard(finding);progress.setProgress(count);updateReport();
                });
            }
            runOnUiThread(()->{
                busy=false;
                if(isDestroyed())return;
                status.setText(cancelled.get()?"Scan stopped":"Scan complete");
                updateReport();
            });
        });
    }

    private String read(String cmd)throws Exception{
        AtomicBoolean timeout=new AtomicBoolean();
        StringBuilder out=new StringBuilder();
        try(AdbStream stream=adb.openStream("shell:"+cmd+" 2>&1");
            InputStream input=stream.openInputStream()){
            active=stream;
            if(cancelled.get())stream.close();
            ScheduledFuture<?> deadline=timer.schedule(()->{timeout.set(true);try{stream.close();}catch(Exception ignored){}},20,TimeUnit.SECONDS);
            try{
                InputStreamReader reader=new InputStreamReader(input,StandardCharsets.UTF_8);
                char[] buf=new char[4096];int n;
                while(!cancelled.get()&&(n=reader.read(buf))!=-1){
                    int take=Math.min(n,60000-out.length());
                    if(take>0)out.append(buf,0,take);
                    if(out.length()>=60000){out.append("\n[Output limit reached]");break;}
                }
            }catch(IOException e){if(!cancelled.get()&&!timeout.get()&&!stream.isClosed())throw e;}
            finally{deadline.cancel(false);}
        }finally{active=null;}
        if(timeout.get())out.append("\n[Timed out; partial result]");
        if(cancelled.get())out.append("\n[Cancelled; partial result]");
        if(out.length()==0)out.append("[No output: interface may be absent, inaccessible, or unsupported]");
        return out.toString();
    }

    private void addCard(Finding f){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12),dp(10),dp(12),dp(10));panel.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,dp(4));cards.addView(panel,lp);
        panel.addView(label(f.section.title,17,ACCENT,true));
        panel.addView(label(f.section.purpose,13,MUTED,false));
        String lower=f.output.toLowerCase(Locale.US);
        boolean partial=lower.contains("permission denied")||lower.contains("no such file")||lower.contains("[unavailable")||lower.contains("[timed out")||lower.contains("[no output")||lower.contains("[cancelled");
        panel.addView(label(partial?"Includes unavailable/restricted results":"Output collected",12,partial?0xffffcb84:ACCENT,false));
        TextView body=label(f.output,12,TEXT,false);body.setTypeface(Typeface.MONOSPACE);body.setTextIsSelectable(true);body.setVisibility(View.GONE);
        button(panel,"VIEW RAW OUTPUT",v->body.setVisibility(body.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        panel.addView(body);
    }

    private void updateReport(){
        StringBuilder r=new StringBuilder("COMMAND CENTER PRO / ARCHITECTURE REPORT\nRead-only ADB inventory; unavailable paths may be absent or restricted.\n");
        r.append("Device information is not original source code. Names are clues, not evidence of an accessible control API.\n\n");
        for(Finding f:findings)r.append("=== ").append(f.section.title).append(" ===\n").append(f.section.purpose).append("\n").append(f.output).append("\n\n");
        report=r.toString();
        summary.setText(findings.size()+" subsystem results collected.\nTap VIEW RAW OUTPUT to inspect the actual evidence. Restricted or absent paths are reported rather than guessed.");
    }

    private void stop(){
        cancelled.set(true);
        AdbStream stream=active;
        if(stream!=null)new Thread(()->{try{stream.close();}catch(Exception ignored){}}).start();
    }

    private void export(boolean save){
        if(findings.isEmpty()){toast("Scan first");return;}
        final String snapshot=report;
        new AlertDialog.Builder(this).setTitle("Export device report?")
            .setMessage("Reports may contain firmware details, package names and device configuration. Only share with someone you trust.")
            .setNegativeButton("Cancel",null).setPositiveButton(save?"Save":"Copy",(d,w)->{
                if(save){
                    exportSnapshot=snapshot;
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.setType("text/plain");intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_TITLE,"command-center-pro-architecture.txt");
                    try{startActivityForResult(intent,44);}
                    catch(ActivityNotFoundException unavailable){toast("No document picker available");}
                }else{
                    ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Phone architecture",snapshot));toast("Copied");
                }
            }).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==44&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            android.net.Uri target=data.getData();
            String snapshot=exportSnapshot;
            fileWorker.execute(()->{
                String message;
                try(OutputStream out=getContentResolver().openOutputStream(target,"w")){
                    if(out==null)throw new IOException("Unable to open file");
                    out.write(snapshot.getBytes(StandardCharsets.UTF_8));
                    message="Report saved";
                }catch(Exception e){message="Report export failed: "+e.getClass().getSimpleName();}
                final String outcome=message;
                runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())toast(outcome);});
            });
        }
    }

    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,dp(5),0,dp(5));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }

    private void button(LinearLayout p,String s,View.OnClickListener action){
        Button b=new Button(this);b.setText(s);b.setTextSize(11);b.setTextColor(TEXT);b.setAllCaps(false);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));b.setOnClickListener(action);
        LinearLayout.LayoutParams lp=p.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,dp(45),1):new LinearLayout.LayoutParams(-1,dp(45));
        lp.setMargins(dp(2),dp(2),dp(2),dp(2));p.addView(b,lp);
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){
        fileWorker.shutdownNow();
        stop();
        super.onDestroy();
    }
}
