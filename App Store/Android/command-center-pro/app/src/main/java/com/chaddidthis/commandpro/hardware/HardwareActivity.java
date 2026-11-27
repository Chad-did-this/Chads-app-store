package com.chaddidthis.commandpro.hardware;

import com.chaddidthis.commandpro.shared.ui.InsetsSupport;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import java.nio.charset.StandardCharsets;
import android.content.ComponentName;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.hardware.camera2.*;
import android.hardware.lights.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class HardwareActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout results;
    private TextView status;
    private Button scan,copy,export,save;
    private CheckBox includeApps;
    private LiveMonitor live;
    private static final int REPORT_REQUEST=43;
    private static final String CAMPING_PACKAGE="com.coosea.campingLight";
    private static final int EXPORT_REQUEST=42;
    private String report="";
    private final int BG=0xff0a1018, SURFACE=0xff182331, WHITE=0xffeef5ff, MUTED=0xffa8bbcf, ACCENT=0xffb9f978;
    private final String[] terms={"camp","flash","torch","light","lantern","halo","led","ulefone","rugged","toolbox","nightvision","infrared","emergency","siren"};
    private final ArrayList<String[]> sections=new ArrayList<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=column();
        root.setPadding(dp(18),dp(20),dp(18),dp(24));
        scroll.addView(root);
        root.addView(text("CHAD / STUDIO",12,ACCENT));
        TextView title=text("Hardware Explorer PRO",30,WHITE);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(title);
        root.addView(text("Device intelligence • Hardware, firmware, system components and live telemetry.",15,MUTED));
        LinearLayout controls=card();
        root.addView(controls);
        status=text("Ready to scan locally",14,ACCENT);
        controls.addView(status);
        scan=new Button(this);scan.setText("Scan hardware");scan.setAllCaps(false);
        controls.addView(scan);
        scan.setOnClickListener(v->runScan());
        copy=new Button(this);copy.setText("Copy diagnostic report");copy.setAllCaps(false);
        copy.setEnabled(false);controls.addView(copy);
        copy.setOnClickListener(v->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Command Center PRO hardware report",report));
            Toast.makeText(this,"Report copied. Review before sharing.",Toast.LENGTH_SHORT).show();
        });
        save=new Button(this);save.setText("Save full report (.txt)");save.setAllCaps(false);save.setEnabled(false);
        controls.addView(save);save.setOnClickListener(v->saveReport());
        includeApps=new CheckBox(this);includeApps.setText("Include third-party app names in report");includeApps.setTextColor(MUTED);
        controls.addView(includeApps);
        live=new LiveMonitor(this);
        root.addView(live);
        LinearLayout video=card();
        video.addView(text("HDMI / USB-C diagnostics",18,ACCENT));
        video.addView(text("Scan once without an adapter, then plug in a powered display adapter and scan again. No root or hardware writes.",14,MUTED));
        TextView videoResult=text("Ready to check video output hardware.",13,WHITE);
        videoResult.setTextIsSelectable(true);
        Button videoScan=new Button(this);videoScan.setText("Scan HDMI / USB-C");videoScan.setAllCaps(false);
        Button videoCopy=new Button(this);videoCopy.setText("Copy HDMI report");videoCopy.setAllCaps(false);videoCopy.setEnabled(false);
        video.addView(videoScan);video.addView(videoCopy);video.addView(videoResult);
        videoScan.setOnClickListener(v->{
            videoScan.setEnabled(false);videoCopy.setEnabled(false);
            videoResult.setText("Checking Android and readable kernel interfaces...");
            worker.execute(()->{
                String result=videoDiagnostics();
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    videoResult.setText(result);videoScan.setEnabled(true);videoCopy.setEnabled(true);
                });
            });
        });
        videoCopy.setOnClickListener(v->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("HDMI diagnostic report",videoResult.getText()));
            Toast.makeText(this,"HDMI report copied",Toast.LENGTH_SHORT).show();
        });
        root.addView(video);
        root.addView(text("Read-only scan. No internet, root, camera capture or hardware writes. App visibility is used to find matching system packages.",13,MUTED));
        LinearLayout camping=card();
        camping.addView(text("Camping light investigation",18,ACCENT));
        camping.addView(text("Open the official control screen or save a copy of the installed vendor APK for interface analysis. No app data is copied.",14,MUTED));
        Button open=new Button(this);open.setText("Open official Camping Light");open.setAllCaps(false);
        camping.addView(open);
        open.setOnClickListener(v->openCamping());
        export=new Button(this);export.setText("Export camping light APK");export.setAllCaps(false);
        camping.addView(export);
        export.setOnClickListener(v->chooseExport());
        root.addView(camping);
        results=column();root.addView(results);
        setContentView(scroll);
        InsetsSupport.apply(scroll);
    }

    private void runScan(){
        scan.setEnabled(false);copy.setEnabled(false);save.setEnabled(false);export.setEnabled(false);
        final boolean allApps=includeApps.isChecked();
        status.setText("Scanning device interfaces...");
        results.removeAllViews();
        worker.execute(()->{
            sections.clear();
            device();
            cameras();
            lights();
            packages();
            campingMetadata();
            paths();
            sensors();
            add("HDMI / USB-C diagnostics",videoDiagnostics());
            sections.addAll(new DeepScan(this).scan(allApps));
            StringBuilder out=new StringBuilder("COMMAND CENTER PRO / HARDWARE REPORT\n");
            for(String[] item:sections)out.append("\n=== ").append(item[0]).append(" ===\n").append(item[1]).append("\n");
            String finalReport=out.toString();
            ArrayList<String[]> snapshot=new ArrayList<>(sections);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                report=finalReport;
                for(String[] item:snapshot){
                    LinearLayout panel=card();
                    TextView heading=text(item[0],18,ACCENT);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    panel.addView(heading);
                    TextView body=text(item[1],13,WHITE);body.setTextIsSelectable(true);
                    body.setVisibility(View.GONE);
                    heading.setText(item[0]+"  ▸");
                    heading.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);heading.setText(item[0]+(open?"  ▾":"  ▸"));});
                    panel.addView(body);results.addView(panel);
                }
                status.setText("Scan complete • "+snapshot.size()+" sections");
                scan.setEnabled(true);copy.setEnabled(true);save.setEnabled(true);export.setEnabled(true);
            });
        });
    }


    private String videoDiagnostics(){
        StringBuilder b=new StringBuilder();
        boolean dpPort=false,dpActive=false,drmConnected=false,displayLink=false;
        int external=0;
        b.append("DEVICE: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        b.append("Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n");
        if(Build.VERSION.SDK_INT>=31)b.append("SoC: ").append(Build.SOC_MODEL).append("\n");

        b.append("\nUSB CAPABILITIES\n");
        try{
            PackageManager pm=getPackageManager();
            b.append("USB host / OTG: ").append(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)?"SUPPORTED (Android feature)":"NOT ADVERTISED").append("\n");
            b.append("USB accessory: ").append(pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)?"SUPPORTED (Android feature)":"NOT ADVERTISED").append("\n");
            b.append("HDMI CEC feature: ").append(pm.hasSystemFeature("android.hardware.hdmi.cec")?"Advertised":"Not advertised").append(" (CEC is not video output)\n");
            android.hardware.usb.UsbManager usb=getSystemService(android.hardware.usb.UsbManager.class);
            if(usb==null)b.append("USB device service unavailable\n");
            else {
                Collection<android.hardware.usb.UsbDevice> devices=usb.getDeviceList().values();
                b.append("Visible USB devices: ").append(devices.size()).append("\n");
                for(android.hardware.usb.UsbDevice d:devices){
                    b.append(String.format(Locale.ROOT,"VID %04x / PID %04x | class %d | interfaces %d\n",d.getVendorId(),d.getProductId(),d.getDeviceClass(),d.getInterfaceCount()));
                    if(d.getVendorId()==0x17e9){displayLink=true;b.append("  DisplayLink vendor ID detected (driver still required)\n");}
                    for(int i=0;i<d.getInterfaceCount();i++){
                        android.hardware.usb.UsbInterface it=d.getInterface(i);
                        b.append("  Interface ").append(i).append(": class ").append(it.getInterfaceClass()).append(", subclass ").append(it.getInterfaceSubclass()).append(", endpoints ").append(it.getEndpointCount()).append("\n");
                    }
                }
            }
        }catch(Exception e){b.append("USB inspection restricted: ").append(e.getClass().getSimpleName()).append("\n");}

        b.append("\nANDROID DISPLAYS\n");
        try{
            android.hardware.display.DisplayManager dm=getSystemService(android.hardware.display.DisplayManager.class);
            if(dm==null)b.append("Display service unavailable\n");
            else for(Display d:dm.getDisplays()){
                if(d.getDisplayId()!=Display.DEFAULT_DISPLAY)external++;
                Display.Mode mode=d.getMode();
                b.append(d.getDisplayId()==Display.DEFAULT_DISPLAY?"Primary":"Additional").append(" display #").append(d.getDisplayId());
                b.append(": ").append(mode.getPhysicalWidth()).append(" x ").append(mode.getPhysicalHeight()).append(" @ ").append(mode.getRefreshRate()).append(" Hz\n");
            }
            b.append("Additional displays may be wireless, virtual or wired; Android public APIs do not reliably distinguish them.\n");
        }catch(Exception e){b.append("Display inspection restricted: ").append(e.getClass().getSimpleName()).append("\n");}

        b.append("\nUSB-C / DISPLAYPORT ALTERNATE MODE\n");
        File[] ports=videoChildren(new File("/sys/class/typec"));
        if(ports==null||ports.length==0)b.append("UNKNOWN: Type-C kernel entries absent or inaccessible.\n");
        else {
            LinkedHashMap<String,File> modes=new LinkedHashMap<>();
            for(File p:ports){
                String n=p.getName();
                if(n.matches("port[0-9]+(?:-partner|-plug[0-9]+)?")){
                    b.append(n).append(": data role=").append(videoRead(new File(p,"data_role"))).append(", power role=").append(videoRead(new File(p,"power_role"))).append("\n");
                    File[] children=videoChildren(p);
                    if(children!=null)for(File c:children)if(c.getName().matches("port[0-9]+(?:-partner|-plug[0-9]+)?\\.[0-9]+"))modes.put(c.getName(),c);
                }
                if(n.matches("port[0-9]+(?:-partner|-plug[0-9]+)?\\.[0-9]+"))modes.put(n,p);
            }
            for(File m:modes.values()){
                String svid=videoRead(new File(m,"svid")),active=videoRead(new File(m,"active"));
                b.append(m.getName()).append(": SVID=").append(svid).append(", active=").append(active).append("\n");
                String id=svid.toLowerCase(Locale.ROOT);
                if(id.equals("0xff01")||id.equals("ff01")||id.equals("65281")){
                    if(m.getName().matches("port[0-9]+\\.[0-9]+"))dpPort=true;
                    if(active.equals("1")||active.equalsIgnoreCase("yes")||active.equalsIgnoreCase("true"))dpActive=true;
                }
            }
            if(modes.isEmpty())b.append("No readable alternate-mode entries. Capability remains UNKNOWN.\n");
        }
        b.append("Local port DisplayPort SVID: ").append(dpPort?"SUPPORTED MODE ADVERTISED":"UNKNOWN").append("\n");
        b.append("Active DisplayPort mode: ").append(dpActive?"DETECTED":"NOT CONFIRMED").append("\n");

        b.append("\nKERNEL VIDEO CONNECTORS\n");
        File[] connectors=videoChildren(new File("/sys/class/drm"));
        int count=0;
        if(connectors!=null)for(File c:connectors){
            String n=c.getName();
            if(!n.matches("card[0-9]+-(?:HDMI-[AB]|DP|DVI-[ADI]|VGA)-[0-9]+"))continue;
            count++;
            String status=videoRead(new File(c,"status"));
            if(status.equalsIgnoreCase("connected"))drmConnected=true;
            b.append(n).append(": ").append(status).append(" | enabled: ").append(videoRead(new File(c,"enabled"))).append("\n");
            String modes=videoRead(new File(c,"modes"));
            b.append("  Modes: ").append(modes.replace("\n",", ")).append("\n");
        }
        if(count==0)b.append("UNKNOWN: No external DRM connectors readable. Vendor drivers may use other interfaces.\n");
        b.append("\nDEVICE NODES / USB CONTROLLERS\n");
        for(String p:new String[]{"/dev/dri","/sys/class/udc","/sys/class/usb_role","/sys/bus/usb/devices","/sys/class/extcon","/sys/class/graphics"}){
            File[] children=videoChildren(new File(p));
            b.append(p).append(": ");
            if(children==null)b.append("not visible or restricted");
            else if(children.length==0)b.append("empty");
            else{
                for(int i=0;i<Math.min(20,children.length);i++){if(i>0)b.append(", ");b.append(children[i].getName());}
                if(children.length>20)b.append(" ...");
            }
            b.append("\n");
        }

        String summary;
        if(dpActive)summary="DisplayPort alternate mode ACTIVE (kernel evidence). Verify the actual picture on your monitor.";
        else if(drmConnected)summary="External kernel video connector reports CONNECTED. Verify the adapter type and actual monitor picture.";
        else if(dpPort)summary="DisplayPort mode is advertised by the local USB-C port. Working wired video is not yet confirmed.";
        else summary="Native USB-C video: UNKNOWN. No positive local DisplayPort or connected DRM evidence was readable. This is NOT proof of missing hardware.";
        return "HDMI / USB-C RESULT\n"+summary+"\nDisplayLink USB vendor: "+(displayLink?"DETECTED (driver needed)":"NOT DETECTED")+
            "\nAdditional Android displays: "+external+"\n\n"+b+
            "\nTEST: Connect an HDMI display using a compatible adapter, power the adapter if needed, and rescan. A visible picture confirms actual output. This app cannot enable hidden hardware, bypass Android permissions, or turn ordinary USB data into HDMI.";
    }

    private File[] videoChildren(File dir){
        try{
            File[] children=dir.listFiles();
            if(children!=null)Arrays.sort(children,Comparator.comparing(File::getName));
            return children;
        }catch(Exception e){return null;}
    }

    private String videoRead(File file){
        try(java.io.Reader in=new java.io.FileReader(file)){
            char[] data=new char[1024];
            int n=in.read(data);
            return n>0?new String(data,0,n).trim():"empty";
        }catch(Exception e){return "not exposed";}
    }

    private void saveReport(){
        if(report.isEmpty())return;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"command-center-pro-hardware.txt");
        try{startActivityForResult(i,REPORT_REQUEST);}catch(ActivityNotFoundException e){status.setText("No document picker available");}
    }

    @Override protected void onPause(){if(live!=null)live.stop();super.onPause();}

    private void openCamping(){
        try{
            Intent intent=new Intent();
            intent.setComponent(new ComponentName(CAMPING_PACKAGE,CAMPING_PACKAGE+".MainActivity"));
            startActivity(intent);
        }catch(Exception e){
            Toast.makeText(this,"Official control screen unavailable: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
        }
    }

    private void chooseExport(){
        if(!scan.isEnabled())return;
        try{
            getPackageManager().getApplicationInfo(CAMPING_PACKAGE,0);
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE,"CampingLight-Armor33.zip");
            startActivityForResult(intent,EXPORT_REQUEST);
        }catch(Exception e){
            status.setText("Cannot export camping light APK: "+e.getClass().getSimpleName());
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REPORT_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri target=data.getData();String snapshot=report;
            worker.execute(()->{
                String message;
                try(OutputStream out=getContentResolver().openOutputStream(target,"w")){
                    if(out==null)throw new IOException("No output stream");
                    out.write(snapshot.getBytes(StandardCharsets.UTF_8));message="Report saved";
                }catch(Exception e){message="Report export failed: "+e.getClass().getSimpleName();}
                final String outcome=message;runOnUiThread(()->{if(!isDestroyed())status.setText(outcome);});
            });return;
        }
        if(request!=EXPORT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri destination=data.getData();
        scan.setEnabled(false);export.setEnabled(false);
        status.setText("Exporting vendor APK files...");
        worker.execute(()->{
            String message;
            try{
                ApplicationInfo app=getPackageManager().getApplicationInfo(CAMPING_PACKAGE,0);
                OutputStream raw=getContentResolver().openOutputStream(destination,"w");
                if(raw==null)throw new IOException("Could not open destination");
                try(ZipOutputStream zip=new ZipOutputStream(raw)){
                    copyApk(zip,app.sourceDir,"base.apk");
                    if(app.splitSourceDirs!=null){
                        for(int i=0;i<app.splitSourceDirs.length;i++){
                            copyApk(zip,app.splitSourceDirs[i],"split-"+i+".apk");
                        }
                    }
                }
                message="Export saved. Attach the ZIP to the chat for analysis.";
            }catch(Exception e){
                message="Export failed: "+e.getClass().getSimpleName()+". Delete any incomplete ZIP.";
            }
            final String outcome=message;
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing())return;
                status.setText(outcome);scan.setEnabled(true);export.setEnabled(true);
            });
        });
    }

    private void copyApk(ZipOutputStream zip,String path,String name)throws IOException{
        try(InputStream in=new FileInputStream(path)){
            zip.putNextEntry(new ZipEntry(name));
            byte[] buffer=new byte[65536];int read;
            while((read=in.read(buffer))!=-1)zip.write(buffer,0,read);
            zip.closeEntry();
        }
    }

    private void campingMetadata(){
        StringBuilder b=new StringBuilder();
        try{
            PackageInfo pi=getPackageManager().getPackageInfo(CAMPING_PACKAGE,
                PackageManager.GET_SERVICES|PackageManager.GET_PROVIDERS|PackageManager.GET_ACTIVITIES|PackageManager.GET_RECEIVERS|PackageManager.GET_PERMISSIONS|PackageManager.GET_META_DATA);
            b.append("Version: ").append(pi.versionName).append("\n");
            b.append("Enabled: ").append(pi.applicationInfo.enabled).append("\n");
            b.append("Split APKs: ").append(pi.applicationInfo.splitSourceDirs==null?0:pi.applicationInfo.splitSourceDirs.length).append("\n");
            if(pi.providers!=null)for(ProviderInfo provider:pi.providers){
                b.append("Provider: ").append(provider.name).append("\n  authority: ").append(provider.authority);
                b.append("\n  exported: ").append(provider.exported).append(" | enabled: ").append(provider.enabled);
                b.append("\n  read permission: ").append(provider.readPermission).append(" | write permission: ").append(provider.writePermission).append("\n");
            }
            if(pi.services!=null)for(ServiceInfo service:pi.services){
                b.append("Service: ").append(service.name).append("\n  exported: ").append(service.exported);
                b.append(" | enabled: ").append(service.enabled).append(" | permission: ").append(service.permission).append("\n");
            }
            if(pi.requestedPermissions!=null)for(String permission:pi.requestedPermissions)b.append("App permission: ").append(permission).append("\n");
        }catch(Exception e){b.append("Metadata unavailable: ").append(e.getClass().getSimpleName());}
        add("Camping light targeted metadata",b.toString());
    }

    private void device(){
        add("Device",Build.MANUFACTURER+" "+Build.MODEL+"\nProduct: "+Build.PRODUCT+"\nDevice: "+Build.DEVICE+"\nAndroid "+Build.VERSION.RELEASE+" / SDK "+Build.VERSION.SDK_INT);
    }

    private void cameras(){
        StringBuilder b=new StringBuilder();
        try{
            CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE);
            for(String id:cm.getCameraIdList()){
                CameraCharacteristics c=cm.getCameraCharacteristics(id);
                b.append("Camera ").append(id).append(" | flash: ").append(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
                b.append(" | facing: ").append(c.get(CameraCharacteristics.LENS_FACING));
                if(Build.VERSION.SDK_INT>=33){
                    b.append(" | max torch level: ").append(c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL));
                }
                b.append("\n");
            }
        }catch(Exception e){b.append("Unavailable: ").append(e.getClass().getSimpleName());}
        add("Camera flash interfaces",b.length()==0?"No visible camera interfaces.":b.toString());
    }

    private void lights(){
        StringBuilder b=new StringBuilder();
        if(Build.VERSION.SDK_INT>=31){
            try{
                LightsManager lm=getSystemService(LightsManager.class);
                if(lm!=null)for(Light light:lm.getLights()){
                    b.append("Light ID ").append(light.getId()).append(" | type ").append(light.getType());
                    b.append(" | brightness control: ").append(light.hasBrightnessControl());
                    b.append(" | RGB control: ").append(light.hasRgbControl()).append("\n");
                }
            }catch(Exception e){b.append("Public lights API restricted: ").append(e.getClass().getSimpleName()).append("\n");}
        }
        if(b.length()==0)b.append("No public lights reported. This does not prove the camping light is absent.\n");
        try{
            ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
            b.append("IR transmitter: ").append(ir!=null&&ir.hasIrEmitter());
            if(ir!=null&&ir.hasIrEmitter()){
                ConsumerIrManager.CarrierFrequencyRange[] ranges=ir.getCarrierFrequencies();
                if(ranges==null||ranges.length==0)b.append("\nCarrier frequency ranges: unavailable");
                else for(ConsumerIrManager.CarrierFrequencyRange range:ranges)
                    b.append("\nCarrier range: ").append(range.getMinFrequency()).append("–").append(range.getMaxFrequency()).append(" Hz");
                b.append("\nStandard Android IR API supports transmission, not signal reception.");
            }
        }catch(Exception e){b.append("IR status unavailable");}
        add("Public light and IR APIs",b.toString());
    }

    private boolean match(String s){
        if(s==null)return false;
        String lower=s.toLowerCase(Locale.ROOT);
        for(String t:terms)if(lower.contains(t))return true;
        return false;
    }

    private void packages(){
        StringBuilder b=new StringBuilder();
        int count=0;
        PackageManager pm=getPackageManager();
        try{
            List<ApplicationInfo> apps=pm.getInstalledApplications(0);
            apps.sort(Comparator.comparing(a->a.packageName));
            for(ApplicationInfo app:apps){
                if((app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)continue;
                String name=String.valueOf(pm.getApplicationLabel(app));
                if(!match(name)&&!match(app.packageName))continue;
                count++;
                b.append(name).append("\n  ").append(app.packageName).append("\n");
                try{
                    PackageInfo pi=pm.getPackageInfo(app.packageName,PackageManager.GET_ACTIVITIES|PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS|PackageManager.GET_PROVIDERS|PackageManager.GET_PERMISSIONS);
                    if(pi.activities!=null)for(ActivityInfo a:pi.activities)if(a.exported) component(b,"activity",a.name,a.permission);
                    if(pi.services!=null)for(ServiceInfo a:pi.services)if(a.exported) component(b,"service",a.name,a.permission);
                    if(pi.receivers!=null)for(ActivityInfo a:pi.receivers)if(a.exported) component(b,"receiver",a.name,a.permission);
                    if(pi.providers!=null)for(ProviderInfo a:pi.providers)if(a.exported)component(b,"provider",a.name,a.readPermission);
                    if(pi.requestedPermissions!=null)for(String permission:pi.requestedPermissions)
                        if(match(permission)||!permission.startsWith("android.permission."))b.append("  requested permission: ").append(permission).append("\n");
                }catch(Exception e){b.append("  Component metadata unavailable\n");}
                b.append("\n");
            }
        }catch(Exception e){b.append("Package scan restricted: ").append(e.getClass().getSimpleName());}
        add("Lighting / vendor system apps ("+count+")",b.length()==0?"No matching visible system packages.":b.toString());
    }

    private void component(StringBuilder b,String type,String name,String permission){
        b.append("  exported ").append(type).append(": ").append(name);
        b.append(" | permission: ").append(permission==null?"none declared":permission).append("\n");
    }

    private void paths(){
        StringBuilder b=new StringBuilder();
        for(String path:new String[]{"/sys/class/leds","/sys/class/backlight","/sys/class/flashlight","/sys/class/torch","/sys/class/lights"}){
            b.append(path).append("\n");
            try{
                File[] children=new File(path).listFiles();
                if(children==null){b.append("  Not visible or not readable by ordinary apps\n");continue;}
                Arrays.sort(children,Comparator.comparing(File::getName));
                for(File child:children){
                    b.append("  ").append(child.getName()).append("\n");
                    for(String property:new String[]{"brightness","max_brightness","trigger"}){
                        File f=new File(child,property);
                        if(f.canRead()){
                            try(java.io.BufferedReader in=new java.io.BufferedReader(new java.io.FileReader(f))){
                                char[] buf=new char[256];int len=in.read(buf);
                                if(len>0)b.append("    ").append(property).append(": ").append(new String(buf,0,len).trim()).append("\n");
                            }catch(Exception ignored){}
                        }
                    }
                }
            }catch(Exception e){b.append("  Access restricted\n");}
        }
        add("Visible kernel light interfaces (read only)",b.toString());
    }

    private void sensors(){
        StringBuilder b=new StringBuilder();
        try{
            SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);
            for(Sensor s:sm.getSensorList(Sensor.TYPE_ALL))b.append(s.getName()).append(" | ").append(s.getStringType()).append("\n");
        }catch(Exception e){b.append("Unavailable");}
        add("Exposed sensors",b.toString());
    }

    private void add(String title,String value){sections.add(new String[]{title,value.trim()});}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){
        LinearLayout l=column();l.setPadding(dp(14),dp(14),dp(14),dp(14));
        GradientDrawable d=new GradientDrawable();d.setColor(SURFACE);d.setCornerRadius(dp(16));l.setBackground(d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);p.bottomMargin=dp(8);l.setLayoutParams(p);
        return l;
    }
    private TextView text(String s,int size,int color){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));return v;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onDestroy(){if(live!=null)live.stop();worker.shutdownNow();super.onDestroy();}
}
