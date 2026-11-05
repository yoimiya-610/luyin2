package cn.luyin.player;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/** PCM16 capture with a correctly finalized RIFF header; no whole-recording buffers. */
public final class WavRecorder {
    private final AudioRecord input;
    private final ParcelFileDescriptor.AutoCloseOutputStream output;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable error,limit,silenced;
    private final AtomicInteger peak=new AtomicInteger();
    private volatile boolean running;
    private volatile IOException failure;
    private volatile long bytes;
    private Thread writer;
    public WavRecorder(Context context,ParcelFileDescriptor descriptor,Runnable error,Runnable limit,Runnable silenced)throws IOException{
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("未获得麦克风权限");
        this.error=error;this.limit=limit;this.silenced=silenced;
        int min=AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)throw new IOException("设备不支持 WAV 录音参数");
        input=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(Math.max(min*2,17640)).build();
        if(input.getState()!=AudioRecord.STATE_INITIALIZED){input.release();throw new IOException("无法初始化 WAV 录音");}
        ParcelFileDescriptor.AutoCloseOutputStream stream=null;
        try{stream=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.getFileDescriptor()));stream.write(WavHeader.pcm16(0,44100,1));output=stream;}
        catch(IOException e){input.release();if(stream!=null)stream.close();throw e;}
        input.registerAudioRecordingCallback(context.getMainExecutor(),new AudioManager.AudioRecordingCallback(){@Override public void onRecordingConfigChanged(java.util.List<AudioRecordingConfiguration> configs){if(running)for(AudioRecordingConfiguration c:configs)if(c.isClientSilenced()){main.post(WavRecorder.this.silenced);break;}}});
    }
    public void start(){input.startRecording();if(input.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("无法开始 WAV 录制");running=true;writer=new Thread(this::writePcm,"WavCapture");writer.start();}
    private void writePcm(){
        short[] samples=new short[4410];byte[] buffer=new byte[samples.length*2];
        try{while(running){int n=input.read(samples,0,samples.length,AudioRecord.READ_BLOCKING);if(!running)break;if(n<0)throw new IOException("WAV 麦克风读取失败："+n);if(n==0)continue;int maximum=0;
            for(int i=0;i<n;i++){int value=samples[i];maximum=Math.max(maximum,Math.min(32767,Math.abs(value)));buffer[i*2]=(byte)value;buffer[i*2+1]=(byte)(value>>8);}peak.accumulateAndGet(maximum,Math::max);output.write(buffer,0,n*2);bytes+=n*2L;
            if(bytes>=1024L*1024*1024-44){running=false;main.post(limit);break;}
        }}catch(Exception e){failure=e instanceof IOException?(IOException)e:new IOException(e);running=false;main.post(error);}
        finally{try{FileChannel channel=output.getChannel();channel.position(0);ByteBuffer header=ByteBuffer.wrap(WavHeader.pcm16(bytes,44100,1));while(header.hasRemaining())channel.write(header);output.flush();}catch(IOException e){failure=e;}finally{try{output.close();}catch(IOException ignored){}}}
    }
    public boolean stop()throws IOException{
        running=false;try{input.stop();}catch(IllegalStateException ignored){}
        if(writer!=null){try{writer.join(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}if(writer.isAlive())throw new IOException("WAV 保存超时");}
        if(failure!=null)throw failure;return bytes>0;
    }
    public int getMaxAmplitude(){return peak.getAndSet(0);}
    public void release(){try{stop();}catch(IOException ignored){}input.release();if(writer==null)try{output.close();}catch(IOException ignored){}}
}
