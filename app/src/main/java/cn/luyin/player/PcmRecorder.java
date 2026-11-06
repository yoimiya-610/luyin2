package cn.luyin.player;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/** The only microphone client. The same samples feed encoding and bounded asynchronous analysis. */
public final class PcmRecorder {
    private static PcmRecorder owner;
    private boolean released;
    private final AudioRecord input;
    private final PcmAudioFile file;
    private final CaptureFormat format;
    private final LiveAnalysis analysis;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable error,limit,silenced;
    private final AtomicInteger peak=new AtomicInteger();
    private volatile boolean running,closed,finalized;
    private volatile IOException failure;
    private volatile long durationMs;
    private Thread writer;
    public PcmRecorder(Context context,ParcelFileDescriptor descriptor,CaptureFormat format,LiveAnalysis analysis,Runnable error,Runnable limit,Runnable silenced)throws IOException{
        this(context,java.util.Objects.requireNonNull(descriptor),format,analysis,error,limit,silenced,false);
    }
    /** Memory-only microphone input: never creates a file or initializes an encoder. */
    public static PcmRecorder monitor(Context context,LiveAnalysis analysis,Runnable error,Runnable silenced)throws IOException{
        return new PcmRecorder(context,null,CaptureFormat.WAV,analysis,error,()->{},silenced,true);
    }
    private PcmRecorder(Context context,ParcelFileDescriptor descriptor,CaptureFormat format,LiveAnalysis analysis,Runnable error,Runnable limit,Runnable silenced,boolean monitor)throws IOException{
        synchronized(PcmRecorder.class){
        if(owner!=null)throw new IOException("麦克风已被录音或试音占用，请先停止当前任务");
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("未获得麦克风权限");
        this.format=format;this.analysis=analysis;this.error=error;this.limit=limit;this.silenced=silenced;
        int min=AudioRecord.getMinBufferSize(format.sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw new IOException("设备不支持录音采样参数");
        input=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(new AudioFormat.Builder().setSampleRate(format.sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(Math.max(min*4,format.sampleRate*2/2)).build();
        if(input.getState()!=AudioRecord.STATE_INITIALIZED){input.release();throw new IOException("无法初始化音频采样");}
        try{file=monitor?null:new PcmAudioFile(descriptor,format);}catch(IOException e){input.release();throw e;}
        try{input.registerAudioRecordingCallback(context.getMainExecutor(),new AudioManager.AudioRecordingCallback(){@Override public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> configs){if(running)for(AudioRecordingConfiguration c:configs)if(c.getClientAudioSessionId()==input.getAudioSessionId()&&c.isClientSilenced()){main.post(PcmRecorder.this.silenced);break;}}});}catch(RuntimeException ignored){}
        owner=this;
        }
    }
    public void start(){input.startRecording();if(input.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("无法开始录制");analysis.boundary();running=true;writer=new Thread(this::writePcm,"PcmCapture");writer.start();}
    private void writePcm(){short[] samples=new short[format.sampleRate/20];long capturedSamples=0;
        try{while(running){int n=input.read(samples,0,samples.length,AudioRecord.READ_BLOCKING);if(n<0){if(!running)break;throw new IOException("麦克风读取失败："+n);}if(n==0)continue;
            int maximum=0;for(int i=0;i<n;i++)maximum=Math.max(maximum,Math.min(32767,Math.abs((int)samples[i])));peak.accumulateAndGet(maximum,Math::max);
            if(file!=null)file.write(samples,n);capturedSamples+=n;durationMs=capturedSamples*1000/format.sampleRate;analysis.submit(samples,n,format.sampleRate);
            if(file!=null&&file.writtenBytes()>=1024L*1024*1024-16*1024*1024){running=false;main.post(limit);break;}
        }}catch(Exception e){failure=e instanceof IOException?(IOException)e:new IOException(e);running=false;main.post(error);}
        finally{try{if(file!=null)file.finish();finalized=true;}catch(Exception e){failure=e instanceof IOException?(IOException)e:new IOException(e);main.post(error);}closed=true;}
    }
    public boolean stop()throws IOException{running=false;try{input.stop();}catch(IllegalStateException ignored){}if(writer!=null){try{writer.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}if(writer.isAlive())throw new IOException("音频保存超时");}if(failure!=null&&!finalized)throw failure;return finalized&&durationMs>0;}
    public int getMaxAmplitude(){return peak.getAndSet(0);}
    public long durationMs(){return durationMs;}
    public void release(){synchronized(PcmRecorder.class){if(released)return;released=true;try{if(!closed&&writer!=null)try{stop();}catch(IOException ignored){}input.release();if(file!=null&&(writer==null||closed))file.abort();analysis.boundary();}finally{if(owner==this)owner=null;}}}
}
