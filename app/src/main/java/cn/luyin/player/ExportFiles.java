package cn.luyin.player;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Byte-for-byte export: no transcoding, buffering of whole recordings, or unsafe ZIP paths. */
public final class ExportFiles {
    public interface Source { InputStream open(int index) throws IOException; }
    public static String fileName(String title,String mime,String original){
        String base=title==null?"录音":title.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");
        while(base.startsWith("."))base=base.substring(1);
        if(base.isEmpty())base="录音";
        int end=Math.min(base.length(),70);if(end>0&&Character.isHighSurrogate(base.charAt(end-1)))end--;base=base.substring(0,end);
        String ext=extension(mime,original);
        return !ext.isEmpty()&&!base.toLowerCase(Locale.ROOT).endsWith(ext)?base+ext:base;
    }
    private static String extension(String mime,String original){
        String m=mime==null?"":mime.toLowerCase(Locale.ROOT);
        String lower=original==null?"":original.toLowerCase(Locale.ROOT);
        if((m.equals("audio/mp4")||m.equals("audio/x-m4a"))&&lower.endsWith(".mp4"))return ".mp4";
        if((m.equals("audio/ogg")||m.equals("application/ogg"))&&lower.endsWith(".opus"))return ".opus";
        switch(m){
            case "audio/mp4":case "audio/x-m4a":return ".m4a";
            case "audio/mpeg":case "audio/mp3":return ".mp3";
            case "audio/wav":case "audio/x-wav":return ".wav";
            case "audio/amr":case "audio/amr-wb":return ".amr";
            case "audio/aac":case "audio/aac-adts":return ".aac";
            case "audio/flac":case "audio/x-flac":return ".flac";
            case "audio/ogg":case "application/ogg":return ".ogg";
            case "audio/opus":return ".opus";
            case "audio/3gpp":case "audio/3gpp2":return ".3gp";
            default:if(original!=null){int dot=lower.lastIndexOf('.');if(dot>=0){String ext=lower.substring(dot);if(ext.matches("\\.(m4a|mp3|wav|amr|aac|flac|ogg|opus|3gp|mp4|wma|aiff|aif|ape|mka|webm|awb|alac)"))return ext;}}return ".audio";
        }
    }
    public static long copy(InputStream in,OutputStream out) throws IOException {
        if(in==null||out==null)throw new IOException("无法打开录音或导出文件");byte[] buffer=new byte[65536];long bytes=0;int n;
        while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("导出已中断");out.write(buffer,0,n);bytes+=n;}return bytes;
    }
    public static void zip(List<String> names,Source source,OutputStream target) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(target,java.nio.charset.StandardCharsets.UTF_8)){
            zip.setLevel(0); // Audio is already compressed; avoid spending CPU and battery recompressing it.
            for(int i=0;i<names.size();i++){
                String safe=names.get(i).replaceAll("[\\\\/\\p{Cntrl}]","_");while(safe.startsWith("."))safe=safe.substring(1);
                if(safe.isEmpty())safe="录音.audio";
                int width=Math.max(3,Integer.toString(names.size()).length());
                String entry=String.format(Locale.ROOT,"%0"+width+"d-%s",i+1,safe);zip.putNextEntry(new ZipEntry(entry));
                try(InputStream in=source.open(i)){copy(in,zip);}zip.closeEntry();
            }
        }
    }
    private ExportFiles(){}
}
