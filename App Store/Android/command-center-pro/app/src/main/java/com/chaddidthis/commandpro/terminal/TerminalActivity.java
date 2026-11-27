package com.chaddidthis.commandpro.terminal;

import com.chaddidthis.commandpro.shared.adb.AdbConnectionDialog;
import com.chaddidthis.commandpro.shared.adb.LocalAdb;
import com.chaddidthis.commandpro.shared.ui.InsetsSupport;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import io.github.muntashirakon.adb.AdbStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public class TerminalActivity extends Activity {
    private static final int BG = Color.rgb(8,15,13);
    private static final int PANEL = Color.rgb(19,34,30);
    private static final int GREEN = Color.rgb(123,246,166);
    private static final int MUTED = Color.rgb(166,192,181);
    private static final int WHITE = Color.rgb(231,243,235);
    private static final int LIMIT = 200000;
    private final ExecutorService worker = LocalAdb.WORKER;
    private final ScheduledExecutorService timer = LocalAdb.TIMER;
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private final ArrayList<Session> sessions = new ArrayList<>();
    private final ArrayList<String> history = new ArrayList<>();
    private volatile AdbStream activeStream;
    private volatile LocalAdb adb;
    private volatile boolean busy;
    private volatile boolean disconnecting;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private int selected, historyIndex;
    private LinearLayout tabs, tools;
    private TextView status, terminal, sessionLabel;
    private ScrollView outputScroll;
    private AutoCompleteTextView command;
    private final String[] suggestions = {"id","whoami","getprop","getprop ro.product.model","ls -la","pwd","df -h","ps -A","top -b -n 1","pm list packages","dumpsys battery","dumpsys usb","dumpsys display","dumpsys sensorservice","settings list system","settings list secure","settings list global","ip addr","ip route","logcat -d -t 200","service list","cat /proc/cpuinfo","cat /proc/meminfo","am start -a android.settings.SETTINGS","input keyevent KEYCODE_HOME","wm size","wm density"};

    private static final class Session {
        final String name;
        final StringBuilder output = new StringBuilder();
        Session(String name) { this.name = name; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adb=LocalAdb.peek();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10),dp(10),dp(10),dp(8));
        setContentView(root);
        InsetsSupport.apply(root);
        TextView title = label("ADB TERMINAL",20,GREEN);
        title.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        root.addView(title);
        status = label(adb!=null&&adb.isConnected()?"LOCAL ADB // CONNECTED":"LOCAL ADB // DISCONNECTED",12,MUTED);
        root.addView(status);
        LinearLayout connection = horizontal(root);
        button(connection,"CONNECT",v -> connectionDialog());
        button(connection,"DISCONNECT",v -> disconnect());
        button(connection,"STOP",v -> stop());
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabs = new LinearLayout(this);
        tabScroll.addView(tabs);
        root.addView(tabScroll);
        for(String name:new String[]{"Shell","Processes","Packages","Settings","Hardware","Network","Files","Logs","Quick scan","Scripts"}) {
            Session s = new Session(name);
            s.output.append("[").append(name).append(" console]\n$ Connect to your own phone to begin.\n");
            sessions.add(s);
        }
        sessionLabel = label("",12,GREEN);
        root.addView(sessionLabel);
        HorizontalScrollView toolScroll = new HorizontalScrollView(this);
        tools = new LinearLayout(this);
        toolScroll.addView(tools);
        root.addView(toolScroll);
        outputScroll = new ScrollView(this);
        outputScroll.setFillViewport(true);
        terminal = label("",13,GREEN);
        terminal.setTypeface(Typeface.MONOSPACE);
        terminal.setTextIsSelectable(true);
        terminal.setPadding(dp(8),dp(10),dp(8),dp(10));
        terminal.setBackgroundColor(Color.rgb(3,9,6));
        outputScroll.addView(terminal,new ScrollView.LayoutParams(-1,-2));
        root.addView(outputScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout entry = horizontal(root);
        TextView prompt = label("$ ",19,GREEN);
        entry.addView(prompt);
        command = new AutoCompleteTextView(this);
        command.setTextColor(WHITE);
        command.setHintTextColor(MUTED);
        command.setHint("Enter shell command");
        command.setTypeface(Typeface.MONOSPACE);
        command.setSingleLine(true);
        command.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        command.setImeOptions(EditorInfo.IME_ACTION_GO);
        command.setThreshold(1);
        command.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line,suggestions));
        entry.addView(command,new LinearLayout.LayoutParams(0,-2,1));
        command.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_GO){ submit(); return true; }
            return false;
        });
        button(entry,"RUN",v -> submit());
        LinearLayout actions = horizontal(root);
        button(actions,"HIST ↑",v -> recall(-1));
        button(actions,"↓",v -> recall(1));
        button(actions,"SAVE",v -> saveScript());
        button(actions,"COPY",v -> copy());
        button(actions,"CLEAR",v -> { current().output.setLength(0); render(); });
        select(0);
    }

    private Session current(){ return sessions.get(selected); }

    private void select(int index){
        selected=index;
        tabs.removeAllViews();
        for(int i=0;i<sessions.size();i++){
            final int n=i;
            Button b=button(tabs,sessions.get(i).name,v->select(n));
            b.setTextColor(i==selected?GREEN:WHITE);
        }
        sessionLabel.setText(current().name.toUpperCase(Locale.US)+" // separate output tab");
        tools.removeAllViews();
        switch(current().name){
            case "Shell":
                preset("Identity","id"); preset("Model","getprop ro.product.model"); preset("Home","input keyevent KEYCODE_HOME"); break;
            case "Processes":
                preset("Processes","ps -A"); preset("CPU","top -b -n 1"); preset("Memory","dumpsys meminfo"); break;
            case "Packages":
                preset("All apps","pm list packages -f"); preset("User apps","pm list packages -3"); preset("Permissions","pm list permissions -g"); break;
            case "Settings":
                preset("System","settings list system"); preset("Secure","settings list secure"); preset("Global","settings list global"); preset("Open settings","am start -a android.settings.SETTINGS"); break;
            case "Hardware":
                preset("CPU","cat /proc/cpuinfo"); preset("Battery","dumpsys battery"); preset("USB","dumpsys usb"); preset("Sensors","dumpsys sensorservice"); preset("Display","dumpsys display"); break;
            case "Network":
                preset("Interfaces","ip addr"); preset("Routes","ip route"); preset("Connectivity","dumpsys connectivity"); break;
            case "Files":
                preset("Root","ls -la /"); preset("Shared storage","ls -la /sdcard"); preset("Storage","df -h"); break;
            case "Logs":
                preset("Recent","logcat -d -t 200"); preset("Live (45s)","logcat -v brief"); preset("Services","service list"); break;
            case "Quick scan":
                preset("Full diagnostic scan", "echo ===SYSTEM===; getprop; echo ===CPU===; cat /proc/cpuinfo; echo ===MEMORY===; cat /proc/meminfo; echo ===USB===; dumpsys usb; echo ===BATTERY===; dumpsys battery; echo ===SENSORS===; dumpsys sensorservice; echo ===DISPLAY===; dumpsys display; echo ===STORAGE===; df -h; echo ===SERVICES===; service list");
                preset("Hardware and system properties","getprop; cat /proc/cpuinfo; uname -a");
                preset("USB ports and controller","dumpsys usb; ls -l /sys/class/typec /sys/class/udc /sys/bus/usb/devices");
                preset("Battery and power","dumpsys battery; dumpsys power");
                preset("Sensors and display","dumpsys sensorservice; dumpsys display");
                preset("Memory and storage","cat /proc/meminfo; df -h; dumpsys meminfo");
                preset("Running processes","ps -A");
                preset("Installed apps and APK paths","pm list packages -f");
                preset("Permissions and package info","dumpsys package permissions; pm list permissions -g");
                preset("System services","service list; dumpsys -l");
                preset("Accessible system directories","ls -la / /system /vendor /sdcard /sys/class");
                preset("Recent system log (500 lines)","logcat -d -t 500");
                break;
            case "Scripts":
                button(tools,"SAVED SCRIPTS",v->scriptsDialog()); button(tools,"NEW SCRIPT",v->saveScript()); break;
        }
        button(tools,"SAVE TXT",v->saveReport());
        render();
    }

    private void preset(String name,String value){
        button(tools,name,v->{command.setText(value);command.setSelection(command.length());});
    }

    private void submit(){
        String value=command.getText().toString().trim();
        if(value.isEmpty())return;
        if(value.length()>8000){ status.setText("Command too long (8,000 character limit)");return; }
        if(busy){ status.setText("A command or connection operation is already running");return; }
        if(adb==null||!adb.isConnected()){ status.setText("Connect to local ADB first");return; }
        new AlertDialog.Builder(this).setTitle("Execute on this phone?")
            .setMessage(value+"\n\nRuns with Android shell privileges. Commands can modify settings or files.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Run",(d,w)->{
                if(history.isEmpty()||!history.get(history.size()-1).equals(value)){
                    history.add(value);
                    if(history.size()>100)history.remove(0);
                }
                historyIndex=history.size();
                command.setText("");
                runCommand(current(),value);
            }).show();
    }

    private void recall(int step){
        if(history.isEmpty())return;
        historyIndex=Math.max(0,Math.min(history.size(),historyIndex+step));
        command.setText(historyIndex==history.size()?"":history.get(historyIndex));
        command.setSelection(command.length());
    }

    private void connectionDialog(){
        if(busy)return;
        AdbConnectionDialog.show(this,this::operation);
    }

    private void operation(String name,AdbConnectionDialog.Operation op){
        if(busy)return;
        busy=true;
        status.setText(name+"…");
        Session session=current();
        worker.execute(()->{
            if(isDestroyed()){finishOperation();return;}
            try{
                adb=LocalAdb.get(this);
                append(session,"\n["+op.run(adb)+"]\n");
            }catch(Exception e){append(session,"\n[Connection error: "+e+"]\n");}
            finally{ finishOperation(); }
        });
    }

    private void disconnect(){
        if(disconnecting)return;
        disconnecting=true;
        stop();
        busy=true;
        status.setText("Disconnecting…");
        Session session=current();
        worker.execute(()->{
            try{
                LocalAdb connection=LocalAdb.peek();
                if(connection!=null)connection.disconnect();
                append(session,"\n[Disconnected. Disable Wireless debugging when finished.]\n");
            }catch(Exception e){append(session,"\n[Disconnect error: "+e+"]\n");}
            finally{
                runOnUiThread(()->{
                    disconnecting=false;
                    busy=false;
                    if(!isDestroyed())status.setText(adb!=null&&adb.isConnected()?"LOCAL ADB // CONNECTED":"LOCAL ADB // DISCONNECTED");
                });
            }
        });
    }

    private void runCommand(Session s,String value){
        busy=true;
        stopped.set(false);
        status.setText("RUNNING // "+s.name);
        append(s,"\n$ "+value+"\n");
        worker.execute(()->{
            if(isDestroyed()){finishOperation();return;}
            AtomicBoolean timeout=new AtomicBoolean();
            try(AdbStream stream=adb.openStream("shell:"+value);
                InputStream input=stream.openInputStream()){
                activeStream=stream;
                if(stopped.get())stream.close();
                ScheduledFuture<?> deadline=timer.schedule(()->{
                    timeout.set(true);
                    try{stream.close();}catch(Exception ignored){}
                },45,TimeUnit.SECONDS);
                try{
                    InputStreamReader reader=new InputStreamReader(input,StandardCharsets.UTF_8);
                    char[] buffer=new char[4096];
                    int n,total=0;
                    while(!stopped.get()&&(n=reader.read(buffer))!=-1){
                        if(n==0)continue;
                        int count=Math.min(n,2000000-total);
                        if(count>0)append(s,new String(buffer,0,count));
                        total+=count;
                        if(total>=2000000){append(s,"\n[Output limit reached]\n");break;}
                    }
                }catch(IOException e){
                    if(!stopped.get()&&!timeout.get()&&!stream.isClosed())throw e;
                }finally{deadline.cancel(false);}
                if(timeout.get())append(s,"\n[45-second time limit reached]\n");
                if(stopped.get())append(s,"\n[Stop requested; remote process termination is not guaranteed]\n");
            }catch(Exception e){append(s,"\n[Command error: "+e+"]\n");}
            finally{
                activeStream=null;
                append(s,"\n$ ");
                finishOperation();
            }
        });
    }

    private void stop(){
        stopped.set(true);
        AdbStream stream=activeStream;
        if(stream!=null){
            status.setText("Stopping output…");
            new Thread(()->{try{stream.close();}catch(Exception ignored){}}).start();
        }else if(busy)status.setText("Connection operation in progress; wait for timeout");
    }

    private void finishOperation(){
        runOnUiThread(()->{
            if(disconnecting)return;
            busy=false;
            if(!isDestroyed())status.setText(adb!=null&&adb.isConnected()?"LOCAL ADB // CONNECTED":"LOCAL ADB // DISCONNECTED");
        });
    }

    private void append(Session s,String text){
        runOnUiThread(()->{
            if(isDestroyed())return;
            s.output.append(text);
            if(s.output.length()>LIMIT)s.output.delete(0,s.output.length()-LIMIT);
            if(s==current())render();
        });
    }

    private void render(){
        terminal.setText(current().output.toString());
        outputScroll.post(()->outputScroll.fullScroll(View.FOCUS_DOWN));
    }

    private String exportSnapshot="";
    private void saveReport(){
        exportSnapshot=current().output.toString();
        new AlertDialog.Builder(this).setTitle("Export console output?")
            .setMessage("This report may contain device configuration or private information. Share only with trusted recipients.")
            .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("text/plain");intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE,"command-center-report.txt");
                try{startActivityForResult(intent,55);}
                catch(ActivityNotFoundException unavailable){Toast.makeText(this,"No document picker available",Toast.LENGTH_LONG).show();}
            }).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==55&&result==RESULT_OK&&data!=null&&data.getData()!=null){
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
                runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())Toast.makeText(this,outcome,Toast.LENGTH_LONG).show();});
            });
        }
    }

    private void copy(){
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Command Center output",current().output.toString()));
        Toast.makeText(this,"Copied local console output",Toast.LENGTH_SHORT).show();
    }

    private JSONObject scripts(){
        try{return new JSONObject(getSharedPreferences("local_scripts",MODE_PRIVATE).getString("items","{}"));}
        catch(Exception e){return new JSONObject();}
    }

    private void saveScript(){
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18),dp(10),dp(18),dp(10));
        EditText name=new EditText(this); name.setHint("Script name"); form.addView(name);
        EditText body=new EditText(this);body.setHint("Shell commands, one per line");body.setMinLines(4);
        body.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setText(command.getText());form.addView(body);
        form.addView(label("Stored locally. Do not save passwords or tokens in scripts.",12,MUTED));
        new AlertDialog.Builder(this).setTitle("Save local script").setView(form).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->{
                String key=name.getText().toString().trim(),value=body.getText().toString().trim();
                if(key.isEmpty()||key.length()>80||value.isEmpty()||value.length()>8000){Toast.makeText(this,"Invalid script name or size",Toast.LENGTH_LONG).show();return;}
                try{
                    JSONObject items=scripts();
                    if(!items.has(key)&&items.length()>=50)throw new IOException("50 script limit");
                    items.put(key,value);
                    getSharedPreferences("local_scripts",MODE_PRIVATE).edit().putString("items",items.toString()).apply();
                    Toast.makeText(this,"Saved locally",Toast.LENGTH_SHORT).show();
                }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
            }).show();
    }

    private void scriptsDialog(){
        JSONObject items=scripts();
        ArrayList<String> names=new ArrayList<>();
        Iterator<String> iterator=items.keys();
        while(iterator.hasNext())names.add(iterator.next());
        Collections.sort(names);
        if(names.isEmpty()){saveScript();return;}
        new AlertDialog.Builder(this).setTitle("Local scripts").setItems(names.toArray(new String[0]),(d,i)->{
            String name=names.get(i),value=items.optString(name);
            new AlertDialog.Builder(this).setTitle(name).setMessage(value)
                .setNegativeButton("Close",null).setNeutralButton("Delete",(a,b)->{
                    items.remove(name);
                    getSharedPreferences("local_scripts",MODE_PRIVATE).edit().putString("items",items.toString()).apply();
                }).setPositiveButton("Load",(a,b)->{
                    command.setText(value);command.setSelection(command.length());
                }).show();
        }).show();
    }

    private LinearLayout horizontal(LinearLayout parent){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
    }

    private Button button(LinearLayout parent,String text,View.OnClickListener action){
        Button b=new Button(this);
        b.setText(text);b.setTextSize(11);b.setAllCaps(false);b.setTextColor(WHITE);
        b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(8),0,dp(8),0);
        b.setBackgroundTintList(ColorStateList.valueOf(PANEL));
        b.setOnClickListener(action);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(43));
        lp.setMargins(dp(2),dp(2),dp(2),dp(2));
        parent.addView(b,lp);return b;
    }

    private TextView label(String value,int size,int color){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    @Override protected void onDestroy(){
        fileWorker.shutdownNow();
        stopped.set(true);
        AdbStream stream=activeStream;
        if(stream!=null)new Thread(()->{try{stream.close();}catch(Exception ignored){}}).start();

        super.onDestroy();
    }
}
