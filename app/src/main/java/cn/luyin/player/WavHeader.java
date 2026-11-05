package cn.luyin.player;

import java.nio.*;
import java.nio.charset.StandardCharsets;

public final class WavHeader {
    public static byte[] pcm16(long dataBytes,int sampleRate,int channels){
        if(dataBytes<0||dataBytes>0xffffffffL-36||sampleRate<1||channels<1||channels>2||dataBytes%(channels*2)!=0)throw new IllegalArgumentException("无效 WAV 参数");
        ByteBuffer b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt((int)(36+dataBytes)).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)channels).putInt(sampleRate).putInt(sampleRate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt((int)dataBytes);return b.array();
    }
    private WavHeader(){}
}
