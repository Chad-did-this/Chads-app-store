package com.pianogame.app;

import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NativeMic {
    private static final int WINDOW=4096;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile String state="stopped";
    private volatile String latest="";
    private volatile Session active;
    private static final class Session {
        volatile boolean cancelled;
        volatile AudioRecord recorder;
    }
    String status(){return state;}
    String frame(){return latest;}
    void markStarting(){state="starting";latest="";}
    synchronized void start(){
        stop();
        Session s=new Session();
        active=s;
        state="starting";
        worker.execute(()->capture(s));
    }
    synchronized void fail(String message){stop();state=message;}
    synchronized void stop(){
        Session s=active;
        active=null;
        state="stopped";
        latest="";
        if(s!=null){
            s.cancelled=true;
            AudioRecord r=s.recorder;
            if(r!=null)try{r.stop();}catch(Exception ignored){}
        }
    }
    void destroy(){stop();worker.shutdownNow();}
    private synchronized void publish(Session s,String next,String packet){
        if(active!=s||s.cancelled)return;
        if(packet!=null)latest=packet;
        state=next;
    }
    private void capture(Session s){
        int[] sources={MediaRecorder.AudioSource.UNPROCESSED,MediaRecorder.AudioSource.MIC,MediaRecorder.AudioSource.DEFAULT,MediaRecorder.AudioSource.VOICE_RECOGNITION};
        int[] rates={48000,44100,32000,22050,16000};
        for(int source:sources){
            for(int rate:rates){
                if(s.cancelled)return;
                AudioRecord r=null;
                boolean produced=false;
                try{
                    int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                    if(min<=0)continue;
                    r=new AudioRecord(source,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,WINDOW*4));
                    if(r.getState()!=AudioRecord.STATE_INITIALIZED)continue;
                    s.recorder=r;
                    if(s.cancelled)return;
                    r.startRecording();
                    if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)continue;
                    short[] block=new short[Math.max(256,rate/50)];
                    short[] ring=new short[WINDOW];
                    byte[] bytes=new byte[WINDOW*2];
                    int pos=0,filled=0;
                    long seq=0;
                    while(!s.cancelled){
                        int n=r.read(block,0,block.length,AudioRecord.READ_BLOCKING);
                        if(n<=0)break;
                        for(int i=0;i<n;i++){ring[pos]=block[i];pos=(pos+1)%WINDOW;if(filled<WINDOW)filled++;}
                        if(filled<WINDOW)continue;
                        for(int i=0;i<WINDOW;i++){short v=ring[(pos+i)%WINDOW];bytes[2*i]=(byte)v;bytes[2*i+1]=(byte)(v>>8);}
                        produced=true;
                        publish(s,"ready",(++seq)+":"+rate+":"+Base64.encodeToString(bytes,Base64.NO_WRAP));
                    }
                }catch(SecurityException e){publish(s,"Permission denied or microphone disabled in Android settings.",null);return;}
                catch(Exception ignored){}
                finally{
                    s.recorder=null;
                    if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}
                }
                if(produced&&!s.cancelled){publish(s,"Recording interrupted. Close other microphone apps and tap Play again.",null);return;}
            }
        }
        publish(s,"Unable to start Android audio recording. Check microphone privacy and close other recording apps.",null);
    }
}
