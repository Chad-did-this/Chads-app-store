package com.chaddidthis.commandpro.hardware;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.os.*;
import android.widget.*;
import java.util.*;

final class LiveMonitor extends LinearLayout implements SensorEventListener {
    private final SensorManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<Integer,String> readings=new LinkedHashMap<>();
    private final TextView values;
    private final Button toggle;
    private boolean running;
    private final int[] types={Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_LIGHT,Sensor.TYPE_PRESSURE,Sensor.TYPE_PROXIMITY,Sensor.TYPE_GRAVITY,Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_ROTATION_VECTOR};
    private final Runnable tick=new Runnable(){public void run(){if(!running)return;refresh();handler.postDelayed(this,500);}};

    LiveMonitor(Context context){
        super(context);setOrientation(VERTICAL);int pad=dp(14);setPadding(pad,pad,pad,pad);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff182331);bg.setCornerRadius(dp(16));setBackground(bg);
        LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(12);setLayoutParams(p);
        manager=context.getSystemService(SensorManager.class);
        TextView title=new TextView(context);title.setText("LIVE TELEMETRY");title.setTextSize(18);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(0xffb9f978);addView(title);
        values=new TextView(context);values.setTextColor(0xffeef5ff);values.setTextSize(14);values.setPadding(0,pad,0,pad);values.setText("Start monitoring to see live readings.");addView(values);
        toggle=new Button(context);toggle.setText("Start live monitoring");toggle.setAllCaps(false);addView(toggle);
        toggle.setOnClickListener(v->{if(running)stop();else start();});
    }

    private void start(){
        if(running)return;running=true;readings.clear();
        for(int type:types){
            Sensor s=manager.getDefaultSensor(type);
            if(s==null){readings.put(type,"Not exposed");continue;}
            try{boolean ok=manager.registerListener(this,s,SensorManager.SENSOR_DELAY_NORMAL);readings.put(type,ok?"Awaiting readings":"Not available");}
            catch(Exception e){readings.put(type,"Restricted");}
        }
        toggle.setText("Stop monitoring");handler.post(tick);
    }

    void stop(){
        if(!running)return;running=false;manager.unregisterListener(this);handler.removeCallbacks(tick);toggle.setText("Start live monitoring");
        values.setText(values.getText()+"\nMonitoring stopped.");
    }

    @Override public void onSensorChanged(SensorEvent e){
        if(!running)return;
        StringBuilder b=new StringBuilder();
        for(int i=0;i<Math.min(e.values.length,4);i++){if(i>0)b.append(" / ");b.append(String.format(Locale.US,"%.2f",e.values[i]));}
        readings.put(e.sensor.getType(),b.toString());
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}
    private void refresh(){
        StringBuilder b=new StringBuilder();
        for(int type:types)b.append(label(type)).append(": ").append(readings.get(type)).append("\n");
        try{
            BatteryManager bm=getContext().getSystemService(BatteryManager.class);
            Intent i=getContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(i!=null)b.append("Battery temperature: ").append(i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10.0).append(" °C\n");
            b.append("Battery current (µA): ").append(DeepScan.prop(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)).append("\n");
            b.append("Thermal status: ").append(getContext().getSystemService(PowerManager.class).getCurrentThermalStatus());
        }catch(Exception e){b.append("Battery telemetry unavailable");}
        values.setText(b.toString());
    }
    private String label(int t){
        switch(t){
            case Sensor.TYPE_ACCELEROMETER:return "Acceleration (m/s²)";
            case Sensor.TYPE_GYROSCOPE:return "Gyroscope (rad/s)";
            case Sensor.TYPE_MAGNETIC_FIELD:return "Magnetic field (µT)";
            case Sensor.TYPE_LIGHT:return "Ambient light (lux)";
            case Sensor.TYPE_PRESSURE:return "Pressure (hPa)";
            case Sensor.TYPE_PROXIMITY:return "Proximity (cm)";
            case Sensor.TYPE_GRAVITY:return "Gravity (m/s²)";
            case Sensor.TYPE_LINEAR_ACCELERATION:return "Linear acceleration (m/s²)";
            default:return "Rotation vector";
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
