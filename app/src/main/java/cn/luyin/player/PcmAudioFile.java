package cn.luyin.player;

import android.media.*;
import android.os.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;

/** One PCM input, with either PCM RIFF output or the platform's AAC/AMR codec. */
public final class PcmAudioFile {
    private final CaptureFormat format;
    private final ParcelFileDescriptor descriptor;
    private ParcelFileDescriptor.AutoCloseOutputStream output;
    private MediaCodec codec;
    private MediaMuxer muxer;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    private boolean codecStarted,muxerStarted,finished,eos;
    private int track=-1;
    private long samples,bytes;
    public PcmAudioFile(ParcelFileDescriptor descriptor,CaptureFormat format)throws IOException{
        this.descriptor=descriptor;this.format=format;
        try{
            if(format==CaptureFormat.WAV){output=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.getFileDescriptor()));output.write(WavHeader.pcm16(0,format.sampleRate,1));return;}
            String mime=format==CaptureFormat.AMR?MediaFormat.MIMETYPE_AUDIO_AMR_NB:MediaFormat.MIMETYPE_AUDIO_AAC;
            MediaFormat config=MediaFormat.createAudioFormat(mime,format.sampleRate,1);config.setInteger(MediaFormat.KEY_BIT_RATE,format.bitRate);config.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,format.sampleRate/10*2);config.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            if(format!=CaptureFormat.AMR)config.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            codec=MediaCodec.createEncoderByType(mime);codec.configure(config,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();codecStarted=true;
            if(format==CaptureFormat.M4A)muxer=new MediaMuxer(descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            else{output=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.getFileDescriptor()));if(format==CaptureFormat.AMR)output.write("#!AMR\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
        }catch(Exception e){abort();throw new IOException("无法初始化 "+format.label+" 编码器",e);}
    }
    public void write(short[] pcm,int length)throws IOException{
        if(finished)throw new IOException("录音文件已关闭");
        if(format==CaptureFormat.WAV){byte[] buffer=new byte[length*2];for(int i=0;i<length;i++){buffer[2*i]=(byte)pcm[i];buffer[2*i+1]=(byte)(pcm[i]>>8);}output.write(buffer);bytes+=buffer.length;samples+=length;return;}
        int offset=0;long deadline=SystemClock.elapsedRealtime()+800;
        while(offset<length){int index=codec.dequeueInputBuffer(10000);if(index<0){drain(false);if(SystemClock.elapsedRealtime()>deadline)throw new IOException("音频编码器输入超时");continue;}
            ByteBuffer input=codec.getInputBuffer(index);if(input==null)throw new IOException("编码器输入不可用");input.clear();input.order(ByteOrder.LITTLE_ENDIAN);int n=Math.min(length-offset,input.remaining()/2);if(n<1)throw new IOException("音频编码器输入缓冲过小");for(int i=0;i<n;i++)input.putShort(pcm[offset+i]);
            codec.queueInputBuffer(index,0,n*2,samples*1000000/format.sampleRate,0);samples+=n;offset+=n;drain(false);
        }
    }
    private void drain(boolean wait)throws IOException{
        long deadline=SystemClock.elapsedRealtime()+(wait?1800:800);
        while(!eos){int index=codec.dequeueOutputBuffer(info,wait?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){if(!wait)return;if(SystemClock.elapsedRealtime()>deadline)throw new IOException("音频编码器结束超时");continue;}
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(muxer!=null){if(muxerStarted)throw new IOException("编码格式意外变化");track=muxer.addTrack(codec.getOutputFormat());muxer.start();muxerStarted=true;}continue;}
            if(index<0)continue;
            try{ByteBuffer data=codec.getOutputBuffer(index);boolean config=(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0;
                if(info.size>0&&!config){if(data==null)throw new IOException("编码器输出不可用");data.position(info.offset);data.limit(info.offset+info.size);
                    if(muxer!=null){if(!muxerStarted)throw new IOException("M4A 容器未初始化");muxer.writeSampleData(track,data,info);}
                    else{if(format==CaptureFormat.AAC)output.write(AdtsHeader.forFrame(info.size,format.sampleRate,1));byte[] payload=new byte[info.size];data.get(payload);output.write(payload);}bytes+=info.size;
                }
                eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            }finally{codec.releaseOutputBuffer(index,false);}
            if(SystemClock.elapsedRealtime()>deadline&&!eos)throw new IOException("音频编码器输出超时");
        }
    }
    public void finish()throws IOException{
        if(finished)return;
        try{
            if(format==CaptureFormat.WAV){FileChannel channel=output.getChannel();channel.position(0);ByteBuffer header=ByteBuffer.wrap(WavHeader.pcm16(bytes,format.sampleRate,1));while(header.hasRemaining())channel.write(header);output.flush();}
            else{long deadline=SystemClock.elapsedRealtime()+800;int index;while((index=codec.dequeueInputBuffer(10000))<0){drain(false);if(SystemClock.elapsedRealtime()>deadline)throw new IOException("无法结束音频编码");}codec.queueInputBuffer(index,0,0,samples*1000000/format.sampleRate,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drain(true);if(bytes==0)throw new IOException("片段过短，编码器未生成音频");if(muxer!=null&&muxerStarted){muxer.stop();muxerStarted=false;}if(output!=null)output.flush();}
        }finally{finished=true;abort();}
    }
    public long writtenBytes(){long size=descriptor.getStatSize();return Math.max(bytes,size);}
    public long durationMs(){return samples*1000/format.sampleRate;}
    public void abort(){if(codec!=null){try{if(codecStarted)codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}codec=null;}if(muxer!=null){try{muxer.release();}catch(Exception ignored){}muxer=null;}if(output!=null){try{output.close();}catch(IOException ignored){}output=null;}}
}
