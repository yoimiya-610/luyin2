package cn.luyin.player;

import android.content.Context;
import android.os.*;
import java.io.IOException;

/** Foreground-only, memory-only trial. Independent of CaptureJournal, MediaStore and file naming. */
public final class TrialMonitor {
    interface Engine {void start();void stop()throws IOException;void release();int amplitude();long duration();}
    interface Factory {Engine create(LiveAnalysis analysis,Runnable error,Runnable silenced)throws IOException;}
    private final Factory factory;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LiveAnalysis analysis=new LiveAnalysis();
    private final WaveformBuffer waveform=new WaveformBuffer(120);
    private Engine engine;
    private long generation,duration;
    private boolean hasSession;
    private String message="";
    public TrialMonitor(Context context){
        Context app=context.getApplicationContext();
        factory=(a,error,silenced)->{PcmRecorder pcm=PcmRecorder.monitor(app,a,error,silenced);return new Engine(){public void start(){pcm.start();}public void stop()throws IOException{pcm.stop();}public void release(){pcm.release();}public int amplitude(){return pcm.getMaxAmplitude();}public long duration(){return pcm.durationMs();}};};
    }
    TrialMonitor(Factory factory){this.factory=factory;}
    private final Runnable sample=new Runnable(){public void run(){if(engine!=null){waveform.add(engine.amplitude());handler.postDelayed(this,100);}}};
    public boolean running(){return engine!=null;}
    public boolean hasSession(){return hasSession;}
    public String message(){return message;}
    public long duration(){return engine==null?duration:engine.duration();}
    public LiveAnalysis.Snapshot snapshot(){return analysis.snapshot();}
    public float[] waveform(){return waveform.snapshot();}
    public void configure(boolean enabled,boolean pitch){analysis.configure(enabled,pitch);}
    public void start()throws IOException{
        if(engine!=null)return;
        long token=++generation;Engine candidate=null;
        try{
            candidate=factory.create(analysis,()->interrupted(token,"麦克风读取失败，试音已停止"),()->interrupted(token,"麦克风被占用，试音已停止"));
            analysis.newSession();waveform.clear();duration=0;message="";candidate.start();engine=candidate;hasSession=true;handler.post(sample);
        }catch(IOException|RuntimeException e){generation++;if(candidate!=null)candidate.release();throw e;}
    }
    private void interrupted(long token,String text){if(token==generation&&engine!=null){stop();message=text;}}
    public void stop(){
        Engine old=engine;if(old==null)return;engine=null;generation++;handler.removeCallbacks(sample);
        try{old.stop();}catch(IOException|RuntimeException e){message="试音已停止";}finally{duration=old.duration();old.release();analysis.boundary();}
    }
    public void clear(){stop();hasSession=false;message="";duration=0;waveform.clear();analysis.newSession();}
    public void close(){stop();analysis.close();}
}
