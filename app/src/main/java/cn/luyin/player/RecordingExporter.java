package cn.luyin.player;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

public final class RecordingExporter {
    private final Context context;
    public RecordingExporter(Context context){this.context=context.getApplicationContext();}
    public String mime(Recording r){
        Uri uri=Uri.parse(r.uri);String type=context.getContentResolver().getType(uri);
        if(type!=null&&type.startsWith("audio/"))return type;
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{retriever.setDataSource(context,uri);type=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);if(type!=null&&type.startsWith("audio/"))return type;}
        catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
        return "application/octet-stream";
    }
    public String name(Recording r,String mime){
        String original=r.name;Uri uri=Uri.parse(r.uri);
        if("file".equals(uri.getScheme()))original=new File(uri.getPath()).getName();
        else try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())original=c.getString(0);}catch(Exception ignored){}
        return ExportFiles.fileName(r.name,mime,original);
    }
    public void export(List<Recording> rows,Uri destination) throws IOException {
        if(rows.isEmpty())throw new IOException("没有可导出的录音");
        for(Recording r:rows)if(destination.equals(Uri.parse(r.uri)))throw new IOException("导出位置不能与原文件相同");
        try(OutputStream out=context.getContentResolver().openOutputStream(destination,"wt")){
            if(out==null)throw new IOException("无法打开导出位置");
            if(rows.size()==1){try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(rows.get(0).uri))){ExportFiles.copy(in,out);}}
            else{List<String> names=new ArrayList<>();for(Recording r:rows)names.add(name(r,mime(r)));ExportFiles.zip(names,i->context.getContentResolver().openInputStream(Uri.parse(rows.get(i).uri)),out);}
        }
    }
}
