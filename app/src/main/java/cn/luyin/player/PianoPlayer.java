package cn.luyin.player;

import android.content.Context;
import android.media.*;

public final class PianoPlayer {
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private final PianoSynth synth;
    private final int rate;
    private AudioTrack track;
    private volatile boolean running;
    private Thread thread;
    public PianoPlayer(Context context){manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int nativeRate=AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);rate=nativeRate>=22050?nativeRate:48000;synth=new PianoSynth(rate);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)stop();}).build();
    }
    public boolean noteOn(int midi){try{if(!running){stop();start();}synth.noteOn(midi);return true;}catch(RuntimeException e){stop();return false;}}
    public void noteOff(int midi){synth.noteOff(midi);}
    public void volume(double volume){synth.volume(volume);}
    private void start(){if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw new IllegalStateException("音频输出被占用");int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<0)throw new IllegalStateException("不支持钢琴输出");
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(Math.max(min,2048)).setTransferMode(AudioTrack.MODE_STREAM).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("钢琴输出初始化失败");track.play();running=true;
        final AudioTrack output=track;thread=new Thread(()->{short[] buffer=new short[256];try{while(running){synth.render(buffer,buffer.length);int at=0;while(at<buffer.length&&running){int n=output.write(buffer,at,buffer.length-at,AudioTrack.WRITE_BLOCKING);if(n<=0){running=false;break;}at+=n;}}}catch(RuntimeException ignored){running=false;}finally{if(track==output)new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(track==output)stop();});}},"PianoOutput");thread.start();
    }
    public void stop(){running=false;synth.allOff();if(track!=null){try{track.pause();track.flush();}catch(IllegalStateException ignored){}if(thread!=null&&thread!=Thread.currentThread())try{thread.join(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}track.release();track=null;}manager.abandonAudioFocusRequest(focus);}
}
