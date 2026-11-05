package cn.luyin.player;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class ExportWaveformTest {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static void equal(Object actual,Object expected){check(Objects.equals(actual,expected),"Expected "+expected+", got "+actual);}
    private static void fails(IoAction action){count++;try{action.run();}catch(IOException expected){return;}throw new AssertionError("I/O failure was hidden");}
    private interface IoAction{void run() throws IOException;}
    public static void main(String[] args) throws Exception {
        WaveformBuffer wave=new WaveformBuffer(3);equal(wave.snapshot().length,0);
        wave.add(0);wave.add(100);wave.add(32767);float[] samples=wave.snapshot();equal(samples.length,3);check(samples[0]==0,"silence must remain zero");check(samples[1]>0&&samples[1]<1,"measured peak normalization");check(samples[2]==1,"full-scale peak");
        samples[0]=.7f;check(wave.snapshot()[0]==0,"snapshot must not mutate history");wave.add(-50);samples=wave.snapshot();check(samples[0]>0&&samples[1]==1&&samples[2]==0,"ring overwrite and ordering");wave.add(100000);check(wave.snapshot()[2]==1,"clamp invalid high peak");wave.clear();equal(wave.snapshot().length,0);
        equal(ExportFiles.fileName("课堂片段","audio/mp4","hashed.audio"),"课堂片段.m4a");
        equal(ExportFiles.fileName("讲座.MP3","audio/mpeg","hashed.audio"),"讲座.MP3");
        equal(ExportFiles.fileName("录音","audio/aac-adts","x.audio"),"录音.aac");
        equal(ExportFiles.fileName("音频.mp4","audio/mp4","音频.mp4"),"音频.mp4");
        equal(ExportFiles.fileName("语音.opus","audio/ogg","语音.opus"),"语音.opus");
        equal(ExportFiles.fileName("../../private/片段","audio/wav","x.wav"),"_.._private_片段.wav");
        equal(ExportFiles.fileName("","unknown","x.flac"),"录音.flac");
        equal(ExportFiles.fileName("片段","unknown","x.audio"),"片段.audio");
        check(!ExportFiles.fileName("a\nb\nc","audio/amr","x.amr").contains("\n"),"no control characters in export names");
        byte[] original=new byte[180000];new Random(51).nextBytes(original);ByteArrayOutputStream single=new ByteArrayOutputStream();
        equal(ExportFiles.copy(new ByteArrayInputStream(original),single),(long)original.length);check(Arrays.equals(single.toByteArray(),original),"single audio export is byte identical");
        ByteArrayOutputStream archive=new ByteArrayOutputStream();List<String> names=List.of("第一段.m4a","第一段.m4a","../../escape.wav");
        ExportFiles.zip(names,i->new ByteArrayInputStream(new byte[]{(byte)i,0,5}),archive);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive.toByteArray()),java.nio.charset.StandardCharsets.UTF_8)){
            for(int i=0;i<3;i++){ZipEntry e=zip.getNextEntry();check(e!=null,"all selected segments exported");check(e.getName().startsWith(String.format(Locale.ROOT,"%03d-",i+1)),"selection order encoded in names");check(!e.getName().contains("/"),"ZIP path traversal blocked");check(Arrays.equals(zip.readAllBytes(),new byte[]{(byte)i,0,5}),"segment bytes preserved");}check(zip.getNextEntry()==null,"no extra entries");
        }
        List<String> many=new ArrayList<>();for(int i=0;i<1001;i++)many.add("片段.m4a");ByteArrayOutputStream big=new ByteArrayOutputStream();ExportFiles.zip(many,i->new ByteArrayInputStream(new byte[0]),big);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(big.toByteArray()))){String previous="";int n=0;ZipEntry e;while((e=zip.getNextEntry())!=null){check(e.getName().compareTo(previous)>0,"archive names sort correctly above 999 segments");previous=e.getName();n++;}equal(n,1001);}
        fails(()->ExportFiles.copy(null,new ByteArrayOutputStream()));
        fails(()->ExportFiles.copy(new ByteArrayInputStream(new byte[]{1}),new OutputStream(){public void write(int b)throws IOException{throw new IOException("disk full");}}));
        fails(()->ExportFiles.zip(List.of("missing.m4a"),i->{throw new FileNotFoundException("missing");},new ByteArrayOutputStream()));
        System.out.println("Passed "+count+" waveform, export integrity, ZIP ordering and I/O failure checks.");
    }
}
