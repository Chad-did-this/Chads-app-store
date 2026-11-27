package com.chaddidthis.commandpro.hardware;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.*;
import android.hardware.*;
import android.hardware.camera2.*;
import android.hardware.usb.*;
import android.media.*;
import android.net.*;
import android.opengl.*;
import android.os.*;
import android.os.storage.*;
import android.util.DisplayMetrics;
import android.view.*;
import java.io.*;
import java.util.*;

final class DeepScan {
    private final Context ctx;
    private final ArrayList<String[]> output=new ArrayList<>();
    private interface Probe {String run() throws Exception;}
    DeepScan(Context context){ctx=context;}

    ArrayList<String[]> scan(boolean allApps){
        section("Firmware & security",this::firmware);
        section("Processor & memory",this::processor);
        section("Graphics / OpenGL",this::graphics);
        section("Battery & thermal",this::battery);
        section("Display & touch",this::display);
        section("Storage volumes",this::storage);
        section("Camera capabilities",this::camera);
        section("Sensor specifications",this::sensor);
        section("Audio hardware",this::audio);
        section("USB / external devices",this::usb);
        section("Network & radio capabilities",this::network);
        section("Android feature inventory",this::features);
        section("System package inventory",()->packages(true));
        if(allApps)section("Third-party package inventory",()->packages(false));
        section("Visible Linux hardware inventory",this::kernel);
        section("Input devices and touch controllers",this::inputs);
        section("Media codecs and hardware acceleration",this::codecs);
        section("Detailed camera metadata",this::cameraDetails);
        section("Audio routes and microphone inventory",this::audioDetails);
        section("Cellular, NFC and radio hardware",this::radios);
        section("Android system services",this::services);
        section("Firmware HAL declarations",this::hals);
        section("Kernel buses and driver inventory",this::buses);
        section("Power, charging and thermal interfaces",this::powerNodes);
        section("CPU, GPU and memory kernel telemetry",this::computeNodes);
        section("Block devices and filesystem capacity",this::blocks);
        section("Vendor firmware and driver files",this::firmwareFiles);
        section("Permission and scan coverage",this::coverage);
        section("Access boundaries",()->"No root or ADB. Android may hide vendor HALs, private app data, kernel devices, secure settings and some hardware telemetry. Restricted does not mean absent. No unique device identifiers, contacts, messages, account data or network identifiers are intentionally collected. Package inventories may reveal apps; review before sharing.");
        return output;
    }

    private void section(String name,Probe p){
        try{String s=p.run();output.add(new String[]{name,s==null||s.isEmpty()?"No data exposed":s});}
        catch(Exception|LinkageError e){output.add(new String[]{name,"Unavailable: "+e.getClass().getSimpleName()});}
    }

    private String firmware(){
        return "Manufacturer: "+Build.MANUFACTURER+"\nModel: "+Build.MODEL+"\nBoard: "+Build.BOARD+
        "\nHardware: "+Build.HARDWARE+"\nBootloader version: "+Build.BOOTLOADER+"\nBuild: "+Build.DISPLAY+
        "\nBuild type: "+Build.TYPE+"\nBuild tags: "+Build.TAGS+"\nAndroid: "+Build.VERSION.RELEASE+
        "\nSDK: "+Build.VERSION.SDK_INT+"\nSecurity patch: "+Build.VERSION.SECURITY_PATCH+
        "\nKernel: "+System.getProperty("os.version")+"\nABIs: "+Arrays.toString(Build.SUPPORTED_ABIS)+
        (Build.VERSION.SDK_INT>=31?"\nSoC: "+Build.SOC_MANUFACTURER+" "+Build.SOC_MODEL:"");
    }

    private String processor(){
        ActivityManager am=ctx.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();am.getMemoryInfo(info);
        StringBuilder b=new StringBuilder("Logical processors visible: "+Runtime.getRuntime().availableProcessors()+
          "\nRAM total: "+size(info.totalMem)+"\nRAM available: "+size(info.availMem)+"\nLow memory: "+info.lowMemory+
          "\nApp memory class: "+am.getMemoryClass()+" MiB\n");
        b.append("CPU online: ").append(read("/sys/devices/system/cpu/online",256)).append("\n");
        for(int i=0;i<Math.min(32,Runtime.getRuntime().availableProcessors());i++){
            String p="/sys/devices/system/cpu/cpu"+i+"/cpufreq/";
            b.append("CPU ").append(i).append(" current/max kHz: ").append(read(p+"scaling_cur_freq",64)).append(" / ").append(read(p+"cpuinfo_max_freq",64)).append("\n");
        }
        return b.toString();
    }

    private String graphics(){
        EGLDisplay d=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(d==EGL14.EGL_NO_DISPLAY)return "No EGL display";
        EGLContext context=EGL14.EGL_NO_CONTEXT;
        EGLSurface surface=EGL14.EGL_NO_SURFACE;
        try{
            int[] version=new int[2];
            if(!EGL14.eglInitialize(d,version,0,version,1))return "EGL initialization failed";
            int[] attrs={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            if(!EGL14.eglChooseConfig(d,attrs,0,configs,0,1,count,0)||count[0]<1)return "No suitable EGL configuration";
            context=EGL14.eglCreateContext(d,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(d,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(d,surface,surface,context))return "GPU context unavailable";
            int[] max=new int[1];GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,max,0);
            return "Vendor: "+GLES20.glGetString(GLES20.GL_VENDOR)+"\nRenderer: "+GLES20.glGetString(GLES20.GL_RENDERER)+
                "\nOpenGL ES: "+GLES20.glGetString(GLES20.GL_VERSION)+"\nGLSL: "+GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)+
                "\nMaximum texture dimension: "+max[0];
        }finally{
            EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(d,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(d,context);
            EGL14.eglTerminate(d);
        }
    }

    private String battery(){
        Intent i=ctx.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BatteryManager bm=ctx.getSystemService(BatteryManager.class);PowerManager pm=ctx.getSystemService(PowerManager.class);
        StringBuilder b=new StringBuilder();
        if(i!=null)b.append("Level: ").append(i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)).append(" / ").append(i.getIntExtra(BatteryManager.EXTRA_SCALE,-1))
            .append("\nTemperature (tenths °C): ").append(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1))
            .append("\nVoltage (mV): ").append(i.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1))
            .append("\nTechnology: ").append(i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY))
            .append("\nStatus code: ").append(i.getIntExtra(BatteryManager.EXTRA_STATUS,-1))
            .append("\nHealth code: ").append(i.getIntExtra(BatteryManager.EXTRA_HEALTH,-1))
            .append("\nPlugged code: ").append(i.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)).append("\n");
        b.append("Current now (µA): ").append(prop(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            .append("\nCurrent average (µA): ").append(prop(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE))
            .append("\nCharge counter (µAh): ").append(prop(bm,BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            .append("\nPower saver: ").append(pm.isPowerSaveMode());
        b.append("\nThermal status (Android scale 0–6): ").append(pm.getCurrentThermalStatus());
        b.append("\nCharge counter is remaining charge, not design capacity or battery health percentage.");
        return b.toString();
    }

    static String prop(BatteryManager bm,int id){
        if(bm==null)return "Unavailable";
        int x=bm.getIntProperty(id);return x==Integer.MIN_VALUE?"Unavailable":Integer.toString(x);
    }

    private String display(){
        WindowManager wm=ctx.getSystemService(WindowManager.class);Display d=wm.getDefaultDisplay();
        DisplayMetrics dm=new DisplayMetrics();d.getRealMetrics(dm);
        StringBuilder b=new StringBuilder("Display pixels: "+dm.widthPixels+" × "+dm.heightPixels+"\nDensity DPI: "+dm.densityDpi+
          "\nReported physical DPI X/Y: "+dm.xdpi+" / "+dm.ydpi+"\nCurrent refresh: "+d.getRefreshRate()+" Hz\nSupported modes:\n");
        for(Display.Mode mode:d.getSupportedModes())b.append(mode.getPhysicalWidth()).append(" × ").append(mode.getPhysicalHeight()).append(" @ ").append(mode.getRefreshRate()).append(" Hz\n");
        b.append("Touchscreen config: ").append(ctx.getResources().getConfiguration().touchscreen);
        return b.toString();
    }

    private String storage(){
        StringBuilder b=new StringBuilder();
        StatFs s=new StatFs(Environment.getDataDirectory().getPath());
        b.append("Internal data total: ").append(size(s.getTotalBytes())).append("\nInternal data free: ").append(size(s.getAvailableBytes())).append("\n");
        StorageManager sm=ctx.getSystemService(StorageManager.class);
        for(StorageVolume v:sm.getStorageVolumes())b.append("Volume: ").append(v.isPrimary()?"Primary":"Additional")
          .append(" | state: ").append(v.getState()).append(" | removable: ").append(v.isRemovable()).append(" | emulated: ").append(v.isEmulated()).append("\n");
        return b.toString();
    }

    private String camera() throws Exception{
        CameraManager cm=ctx.getSystemService(CameraManager.class);StringBuilder b=new StringBuilder();
        for(String id:cm.getCameraIdList()){
            CameraCharacteristics c=cm.getCameraCharacteristics(id);
            b.append("Camera ").append(id).append("\n Facing: ").append(c.get(CameraCharacteristics.LENS_FACING))
             .append("\n Pixel array: ").append(c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE))
             .append("\n Sensor physical size: ").append(c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE))
             .append("\n Focal lengths (mm): ").append(Arrays.toString(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)))
             .append("\n Apertures: ").append(Arrays.toString(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)))
             .append("\n Hardware level: ").append(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
             .append("\n Capabilities: ").append(Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))).append("\n");
            b.append(" Physical camera IDs: ").append(c.getPhysicalCameraIds()).append("\n");
        }return b.toString();
    }

    private String sensor(){
        SensorManager sm=ctx.getSystemService(SensorManager.class);StringBuilder b=new StringBuilder();
        for(Sensor s:sm.getSensorList(Sensor.TYPE_ALL))b.append(s.getName()).append(" | ").append(s.getStringType())
          .append("\n Vendor: ").append(s.getVendor()).append(" | version: ").append(s.getVersion())
          .append("\n Range: ").append(s.getMaximumRange()).append(" | resolution: ").append(s.getResolution())
          .append(" | power (mA): ").append(s.getPower()).append(" | min delay (µs): ").append(s.getMinDelay()).append("\n");
        return b.toString();
    }

    private String audio(){
        AudioManager am=ctx.getSystemService(AudioManager.class);StringBuilder b=new StringBuilder();
        b.append("Output sample rate: ").append(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE))
          .append("\nFrames per buffer: ").append(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER))
          .append("\nMusic volume: ").append(am.getStreamVolume(AudioManager.STREAM_MUSIC)).append(" / ").append(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).append("\n");
        for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_INPUTS | AudioManager.GET_DEVICES_OUTPUTS))b.append(d.isSource()?"Input":"Output")
          .append(" | Android type ").append(d.getType()).append(" | rates ").append(Arrays.toString(d.getSampleRates())).append("\n");
        return b.toString();
    }

    private String usb(){
        UsbManager um=ctx.getSystemService(UsbManager.class);StringBuilder b=new StringBuilder();
        for(UsbDevice d:um.getDeviceList().values())b.append("VID ").append(String.format(Locale.US,"%04x",d.getVendorId())).append(" PID ")
          .append(String.format(Locale.US,"%04x",d.getProductId())).append(" | class ").append(d.getDeviceClass()).append(" | interfaces ").append(d.getInterfaceCount()).append("\n");
        return b.length()==0?"No USB host devices exposed":b.toString();
    }

    private String network(){
        PackageManager pm=ctx.getPackageManager();ConnectivityManager cm=ctx.getSystemService(ConnectivityManager.class);
        StringBuilder b=new StringBuilder();
        for(String feature:new String[]{"android.hardware.wifi","android.hardware.wifi.direct","android.hardware.bluetooth","android.hardware.bluetooth_le","android.hardware.nfc","android.hardware.telephony","android.hardware.location.gps","android.hardware.consumerir","android.hardware.uwb"})
            b.append(feature).append(": ").append(pm.hasSystemFeature(feature)).append("\n");
        Network active=cm.getActiveNetwork();
        NetworkCapabilities nc=active==null?null:cm.getNetworkCapabilities(active);
        if(nc!=null)b.append("Active transport: Wi-Fi ").append(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).append(" | cellular ").append(nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).append(" | Ethernet ").append(nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)).append("\n");
        return b.toString();
    }

    private String features(){
        StringBuilder b=new StringBuilder();FeatureInfo[] ff=ctx.getPackageManager().getSystemAvailableFeatures();
        if(ff!=null)for(FeatureInfo f:ff)if(f.name!=null)b.append(f.name).append(" | version ").append(f.version).append("\n");
        return b.toString();
    }

    private String packages(boolean system){
        PackageManager pm=ctx.getPackageManager();StringBuilder b=new StringBuilder();
        List<PackageInfo> apps=pm.getInstalledPackages(0);apps.sort(Comparator.comparing(p->p.packageName));
        int count=0;
        for(PackageInfo p:apps){
            if(p.applicationInfo==null)continue;
            boolean sys=(p.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
            if(sys!=system)continue;count++;
            b.append(p.packageName).append(" | v").append(p.versionName).append(" | enabled ").append(p.applicationInfo.enabled).append("\n");
        }
        return "Count: "+count+"\n"+b;
    }

    private String kernel(){
        StringBuilder b=new StringBuilder();
        for(String path:new String[]{"/sys/class/thermal","/sys/class/power_supply","/sys/class/input","/sys/class/sensors","/sys/class/gpio","/sys/class/video4linux","/sys/class/drm","/sys/class/hwmon","/sys/class/leds","/sys/bus/i2c/devices","/sys/bus/spi/devices","/dev"}){
            b.append(path).append("\n");
            try{
                String[] names=new File(path).list();
                if(names==null)b.append("  Restricted or absent\n");
                else {Arrays.sort(names);for(int j=0;j<Math.min(names.length,120);j++)b.append("  ").append(names[j]).append("\n");if(names.length>120)b.append("  (truncated)\n");}
            }catch(Exception e){b.append("  Restricted\n");}
        }
        return b.toString();
    }


    private String inputs(){
        StringBuilder b=new StringBuilder();
        for(int id:InputDevice.getDeviceIds()){
            InputDevice d=InputDevice.getDevice(id);
            if(d==null)continue;
            b.append(d.getName()).append(" | vendor ").append(d.getVendorId()).append(" | product ").append(d.getProductId()).append(" | sources 0x").append(Integer.toHexString(d.getSources())).append(" | virtual ").append(d.isVirtual()).append("\n");
            for(InputDevice.MotionRange m:d.getMotionRanges())b.append("  ").append(MotionEvent.axisToString(m.getAxis())).append(": ").append(m.getMin()).append(" to ").append(m.getMax()).append(" | resolution ").append(m.getResolution()).append("\n");
        }
        return b.length()==0?"No input devices exposed":b.toString();
    }

    private String codecs(){
        StringBuilder b=new StringBuilder();
        for(MediaCodecInfo c:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()){
            b.append(c.getName()).append(c.isEncoder()?" | encoder":" | decoder");
            b.append(" | hardware: ").append(c.isHardwareAccelerated()).append(" | software: ").append(c.isSoftwareOnly()).append(" | vendor: ").append(c.isVendor());
            b.append("\n");
            for(String t:c.getSupportedTypes()){
                b.append("  ").append(t);
                try{
                    MediaCodecInfo.CodecCapabilities cap=c.getCapabilitiesForType(t);
                    if(t.startsWith("video/")){
                        MediaCodecInfo.VideoCapabilities v=cap.getVideoCapabilities();
                        if(v!=null)b.append(" | width ").append(v.getSupportedWidths()).append(" | height ").append(v.getSupportedHeights()).append(" | fps ").append(v.getSupportedFrameRates()).append(" (ranges not necessarily simultaneous)");
                    }else if(t.startsWith("audio/")){
                        MediaCodecInfo.AudioCapabilities a=cap.getAudioCapabilities();
                        if(a!=null)b.append(" | max channels ").append(a.getMaxInputChannelCount()).append(" | rates ").append(Arrays.toString(a.getSupportedSampleRateRanges()));
                    }
                    b.append(" | instances ").append(cap.getMaxSupportedInstances());
                }catch(Exception e){b.append(" | capability details unavailable");}
                b.append("\n");
            }
        }
        return b.toString();
    }

    private String cameraDetails() throws Exception{
        CameraManager cm=ctx.getSystemService(CameraManager.class);
        StringBuilder b=new StringBuilder();
        for(String id:cm.getCameraIdList()){
            CameraCharacteristics c=cm.getCameraCharacteristics(id);
            b.append("Camera ").append(id).append("\n");
            for(CameraCharacteristics.Key<?> k:c.getKeys()){
                String name=k.getName();
                if(!name.startsWith("android.")) {
                    b.append("  Vendor metadata key: ").append(name).append(" (value not collected)\n");
                    continue;
                }
                try{
                    Object value=c.get(k);
                    if(value==null)continue;
                    String v=valueString(value);
                    b.append("  ").append(name).append(": ").append(v.length()>500?v.substring(0,500)+" …":v).append("\n");
                }catch(Exception e){b.append("  ").append(name).append(": restricted\n");}
            }
            android.hardware.camera2.params.StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map!=null)for(int f:map.getOutputFormats()){
                b.append("  Output format ").append(f).append(": ");
                android.util.Size[] sizes=map.getOutputSizes(f);
                if(sizes!=null)b.append(Arrays.toString(sizes));
                b.append("\n");
            }
        }
        return b.toString();
    }

    private String valueString(Object v){
        if(!v.getClass().isArray())return String.valueOf(v);
        int n=java.lang.reflect.Array.getLength(v);
        StringBuilder b=new StringBuilder("[");
        for(int i=0;i<Math.min(40,n);i++){if(i>0)b.append(", ");b.append(java.lang.reflect.Array.get(v,i));}
        if(n>40)b.append(", … ").append(n).append(" total");
        return b.append("]").toString();
    }

    private String audioDetails() throws Exception{
        AudioManager am=ctx.getSystemService(AudioManager.class);
        StringBuilder b=new StringBuilder();
        for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_INPUTS | AudioManager.GET_DEVICES_OUTPUTS)){
            b.append(d.isSource()?"Input":"Output").append(" | type ").append(d.getType()).append(" | sample rates ").append(Arrays.toString(d.getSampleRates())).append(" | channel counts ").append(Arrays.toString(d.getChannelCounts())).append(" | encodings ").append(Arrays.toString(d.getEncodings())).append("\n");
        }
        try{
            for(MicrophoneInfo m:am.getMicrophones())b.append("Microphone: type ").append(m.getType()).append(" | location ").append(m.getLocation()).append(" | directionality ").append(m.getDirectionality()).append(" | sensitivity ").append(m.getSensitivity()).append("\n");
        }catch(Exception e){b.append("Microphone metadata restricted: ").append(e.getClass().getSimpleName()).append("\n");}
        return b.toString();
    }

    private String radios(){
        StringBuilder b=new StringBuilder();
        PackageManager pm=ctx.getPackageManager();
        for(FeatureInfo f:pm.getSystemAvailableFeatures())if(f.name!=null&&(f.name.contains("telephony")||f.name.contains("wifi")||f.name.contains("bluetooth")||f.name.contains("nfc")||f.name.contains("location")||f.name.contains("uwb")||f.name.contains("ir")||f.name.contains("ethernet")))b.append(f.name).append(" | version ").append(f.version).append("\n");
        try{
            android.telephony.TelephonyManager tm=ctx.getSystemService(android.telephony.TelephonyManager.class);
            if(tm!=null){
                b.append("Phone radio type: ").append(tm.getPhoneType()).append("\n");
                b.append("Active modem count: ").append(tm.getActiveModemCount()).append("\n");
            }
        }catch(Exception e){b.append("Modem details restricted\n");}
        b.append("Baseband build: ").append(Build.getRadioVersion()).append("\n");
        try{
            ConsumerIrManager ir=ctx.getSystemService(ConsumerIrManager.class);
            b.append("IR transmitter: ").append(ir!=null&&ir.hasIrEmitter()).append("\n");
            if(ir!=null&&ir.hasIrEmitter()){
                ConsumerIrManager.CarrierFrequencyRange[] ranges=ir.getCarrierFrequencies();
                if(ranges!=null)for(ConsumerIrManager.CarrierFrequencyRange range:ranges)b.append("  ").append(range.getMinFrequency()).append(" to ").append(range.getMaxFrequency()).append(" Hz\n");
            }
        }catch(Exception e){b.append("IR access restricted\n");}
        b.append("No SIM identifiers, nearby wireless devices, network addresses or location collected.");
        return b.toString();
    }

    private String services(){
        StringBuilder b=new StringBuilder();
        for(String name:new String[]{"accessibility","activity","alarm","audio","battery","batterymanager","bluetooth","camera","carrier_config","companiondevice","connectivity","consumer_ir","device_policy","display","download","ethernet","fingerprint","hardware_properties","input","input_method","ipsec","keyguard","lights","location","media_projection","media_router","media_session","midi","netstats","nfc","notification","power","print","radio","restrictions","role","sensor","sensor_privacy","serial","stats","storage","system_health","telecom","phone","thermalservice","uimode","usb","uwb","vibrator","vibrator_manager","vpn_management","wifi","wifip2p","wifiaware","wifirtt","window"}){
            try{
                Object service=ctx.getSystemService(name);
                b.append(name).append(": ").append(service==null?"not exposed / unknown":service.getClass().getName()).append("\n");
            }catch(Exception|LinkageError e){b.append(name).append(": restricted (").append(e.getClass().getSimpleName()).append(")\n");}
        }
        return b.toString();
    }

    private String hals(){
        StringBuilder b=new StringBuilder();
        for(String p:new String[]{"/vendor/etc/vintf/manifest.xml","/odm/etc/vintf/manifest.xml","/system/etc/vintf/manifest.xml","/system_ext/etc/vintf/manifest.xml","/product/etc/vintf/manifest.xml"})halFile(new File(p),b);
        for(String p:new String[]{"/vendor/etc/vintf/manifest","/odm/etc/vintf/manifest","/system/etc/vintf/manifest","/system_ext/etc/vintf/manifest","/product/etc/vintf/manifest"}){
            File[] files=children(p);
            if(files==null){b.append(p).append(": not visible\n");continue;}
            int n=0;
            for(File f:files)if(f.getName().endsWith(".xml")&&n++<100)halFile(f,b);
            if(n>100)b.append("Additional manifest files omitted\n");
        }
        b.append("HAL declarations describe firmware interfaces; they do not prove a physical component is installed or functional.");
        return b.toString();
    }

    private void halFile(File f,StringBuilder b){
        try(InputStream in=new FileInputStream(f)){
            if(f.length()>1048576){b.append(f.getPath()).append(": file too large\n");return;}
            org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
            parser.setInput(in,null);
            b.append(f.getPath()).append("\n");
            int event,halDepth=-1;
            while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                if(event==org.xmlpull.v1.XmlPullParser.START_TAG){
                    String tag=parser.getName();
                    if(tag.equals("hal")){halDepth=parser.getDepth();b.append("  HAL\n");}
                    else if(halDepth>=0&&(tag.equals("name")||tag.equals("version")||tag.equals("fqname")||tag.equals("instance"))){
                        String v=parser.nextText().trim();
                        b.append("    ").append(tag).append(": ").append(v.length()>160?v.substring(0,160):v).append("\n");
                    }
                }else if(event==org.xmlpull.v1.XmlPullParser.END_TAG&&parser.getDepth()==halDepth&&parser.getName().equals("hal"))halDepth=-1;
            }
        }catch(Exception e){b.append(f.getPath()).append(": absent, restricted or unreadable\n");}
    }

    private String buses(){
        StringBuilder b=new StringBuilder();
        File[] buses=children("/sys/bus");
        if(buses==null)b.append("/sys/bus restricted or absent\n");
        else for(int i=0;i<Math.min(50,buses.length);i++){
            File f=buses[i];
            inventory(b,new File(f,"devices").getPath(),80);
            inventory(b,new File(f,"drivers").getPath(),80);
        }
        inventory(b,"/sys/module",250);
        inventory(b,"/sys/class",150);
        inventory(b,"/sys/firmware",100);
        return b.toString();
    }

    private String powerNodes(){
        StringBuilder b=new StringBuilder();
        nodeGroup(b,"/sys/class/power_supply",new String[]{"type","status","health","present","online","capacity","technology","voltage_now","voltage_max","voltage_max_design","current_now","current_max","charge_now","charge_full","charge_full_design","energy_now","energy_full","cycle_count","temp","usb_type","scope"});
        nodeGroup(b,"/sys/class/thermal",new String[]{"type","temp","cur_state","max_state","trip_point_0_temp"});
        nodeGroup(b,"/sys/class/hwmon",new String[]{"name","temp1_input","temp2_input","in1_input","curr1_input","power1_input","fan1_input"});
        nodeGroup(b,"/sys/class/leds",new String[]{"brightness","max_brightness","trigger"});
        nodeGroup(b,"/sys/class/backlight",new String[]{"brightness","actual_brightness","max_brightness"});
        b.append("Raw kernel values retain driver-specific units; missing values are unknown.");
        return b.toString();
    }

    private String computeNodes(){
        StringBuilder b=new StringBuilder();
        for(String p:new String[]{"/proc/cpuinfo","/proc/meminfo","/proc/swaps","/proc/version","/proc/uptime"}){
            b.append(p).append("\n");
            String data=read(p,8192);
            if(p.equals("/proc/cpuinfo")){
                for(String line:data.split("\n"))if(!line.toLowerCase(Locale.ROOT).contains("serial"))b.append(line).append("\n");
            }else b.append(data).append("\n");
        }
        nodeGroup(b,"/sys/devices/system/cpu/cpufreq",new String[]{"affected_cpus","scaling_driver","scaling_governor","scaling_cur_freq","scaling_min_freq","scaling_max_freq","cpuinfo_min_freq","cpuinfo_max_freq","scaling_available_frequencies"});
        nodeGroup(b,"/sys/class/devfreq",new String[]{"name","governor","cur_freq","min_freq","max_freq","available_frequencies"});
        nodeGroup(b,"/sys/block/zram0",new String[]{"disksize","mm_stat"});
        for(String p:new String[]{"/sys/block/zram0/disksize","/sys/block/zram0/mm_stat","/sys/class/kgsl/kgsl-3d0/gpuclk","/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage","/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"})b.append(p).append(": ").append(read(p,1024)).append("\n");
        return b.toString();
    }

    private String blocks(){
        StringBuilder b=new StringBuilder();
        nodeGroup(b,"/sys/class/block",new String[]{"size","ro","removable","partition","queue/logical_block_size","queue/physical_block_size","queue/rotational","device/model","device/vendor"});
        inventory(b,"/dev/block/by-name",150);
        for(String p:new String[]{"/","/system","/vendor","/product","/odm","/system_ext","/data","/cache","/metadata","/storage/emulated/0"}){
            try{
                StatFs fs=new StatFs(p);
                b.append(p).append(": ").append(size(fs.getTotalBytes())).append(" total, ").append(size(fs.getAvailableBytes())).append(" available\n");
            }catch(Exception e){b.append(p).append(": unavailable\n");}
        }
        b.append("Mount paths can share a filesystem; capacities must not be added.");
        return b.toString();
    }

    private String firmwareFiles(){
        StringBuilder b=new StringBuilder();
        for(String p:new String[]{"/vendor/lib/hw","/vendor/lib64/hw","/odm/lib/hw","/odm/lib64/hw","/system/lib/hw","/system/lib64/hw","/vendor/bin/hw","/odm/bin/hw","/vendor/lib/modules","/odm/lib/modules","/vendor_dlkm/lib/modules","/system_dlkm/lib/modules","/vendor/firmware","/vendor/firmware_mnt","/odm/firmware","/vendor/etc/permissions","/odm/etc/permissions","/system/etc/permissions","/vendor/etc/init","/odm/etc/init"})inventory(b,p,150);
        b.append("File names only. No executable files, device nodes, firmware binaries or user files are opened.");
        return b.toString();
    }

    private String coverage(){
        StringBuilder b=new StringBuilder("Read-only inventory of public Android APIs and readable kernel/firmware metadata.\n");
        b.append("App package visibility: ").append(ctx.checkSelfPermission("android.permission.QUERY_ALL_PACKAGES")==PackageManager.PERMISSION_GRANTED?"granted":"not granted").append("\n");
        b.append("Root privileges: not requested or used\n");
        b.append("No access to private app data, messages, passwords, raw partitions, protected registers, or hardware schematics.\n");
        b.append("No active camera/microphone recording or sensor actuation. No internet transmission.\n");
        b.append("Unknown / restricted is not the same as missing hardware. Listings are bounded for performance; truncation is explicitly marked.");
        return b.toString();
    }

    private File[] children(String path){
        try{
            File[] list=new File(path).listFiles();
            if(list!=null)Arrays.sort(list,Comparator.comparing(File::getName));
            return list;
        }catch(Exception e){return null;}
    }

    private void inventory(StringBuilder b,String path,int limit){
        b.append(path).append("\n");
        File[] list=children(path);
        if(list==null){b.append("  Unknown: absent or restricted\n");return;}
        b.append("  ").append(list.length).append(" visible entries\n");
        for(int i=0;i<Math.min(limit,list.length);i++)b.append("  ").append(list[i].getName()).append("\n");
        if(list.length>limit)b.append("  Truncated: ").append(list.length-limit).append(" more\n");
    }

    private void nodeGroup(StringBuilder b,String path,String[] attrs){
        b.append(path).append("\n");
        File[] list=children(path);
        if(list==null){b.append("  Unknown: absent or restricted\n");return;}
        for(int i=0;i<Math.min(100,list.length);i++){
            File f=list[i];
            b.append("  ").append(f.getName()).append("\n");
            int visible=0;
            for(String key:attrs){
                String v=read(new File(f,key).getPath(),1024);
                if(!v.equals("Restricted or unavailable")&&!v.equals("Unavailable")){visible++;b.append("    ").append(key).append(": ").append(v).append("\n");}
            }
            if(visible==0)b.append("    No requested attributes readable\n");
        }
        if(list.length>100)b.append("  Truncated: ").append(list.length-100).append(" more\n");
    }

    private String read(String path,int cap){
        try(Reader r=new FileReader(path)){char[] b=new char[cap];int n=r.read(b);return n<=0?"Unavailable":new String(b,0,n).trim();}
        catch(Exception e){return "Restricted or unavailable";}
    }

    private String size(long bytes){return String.format(Locale.US,"%.2f GiB",bytes/1073741824.0);}
}
