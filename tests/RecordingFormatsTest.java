package cn.luyin.player;

import java.nio.*;
import java.util.*;

public final class RecordingFormatsTest {
    private static int count;
    private static void check(boolean v){count++;if(!v)throw new AssertionError("check "+count);}
    public static void main(String[] args){
        byte[] header=WavHeader.pcm16(88200,44100,1);ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);check(header.length==44);check(new String(header,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"));check(h.getInt(4)==88236);check(h.getShort(20)==1);check(h.getShort(22)==1);check(h.getInt(24)==44100);check(h.getInt(28)==88200);check(h.getShort(32)==2);check(h.getShort(34)==16);check(h.getInt(40)==88200);
        check(ByteBuffer.wrap(WavHeader.pcm16(176400,44100,2)).order(ByteOrder.LITTLE_ENDIAN).getInt(28)==176400);
        for(long invalid:new long[]{-1,3,0xffffffffL}){boolean rejected=false;try{WavHeader.pcm16(invalid,44100,1);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);}
        check(CaptureFormat.from("legacy")==CaptureFormat.M4A);check(CaptureFormat.from(null)==CaptureFormat.M4A);check(CaptureFormat.AMR.sampleRate==8000);check(CaptureFormat.WAV.mime.equals("audio/wav"));check(CaptureFormat.values().length==4);
        check(NameRules.nextNumber("20261106",0,List.of("20261106-2.aac","20261106-7.amr","20261106-4.wav"))==8);
        System.out.println("Passed "+count+" recording-format and WAV-container checks.");
    }
}
