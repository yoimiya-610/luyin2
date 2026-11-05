package cn.luyin.player;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.*;
import java.util.concurrent.*;

/** Process-wide ownership keeps rotation from starting a second export or losing its status. */
public final class ExportJob {
    public static final class Status {
        public final long id;
        public final boolean running;
        public final String message;
        Status(long id,boolean running,String message){this.id=id;this.running=running;this.message=message;}
    }
    private static final ExecutorService executor=Executors.newSingleThreadExecutor();
    private static volatile Status current=new Status(0,false,"");
    public static Status status(){return current;}
    public static synchronized boolean start(Context context,List<Recording> input,Uri destination){
        if(current.running)return false;
        if(input.isEmpty())throw new IllegalArgumentException("没有可导出的片段");
        for(Recording r:input)if(destination.equals(Uri.parse(r.uri)))throw new IllegalArgumentException("导出位置不能与原文件相同");
        Context app=context.getApplicationContext();List<Recording> rows=new ArrayList<>();for(Recording r:input)rows.add(Recording.from(r.json()));
        long id=System.currentTimeMillis();if(id<=current.id)id=current.id+1;
        current=new Status(id,true,"正在导出 "+rows.size()+" 段录音，可继续使用其他页面");final long jobId=id;
        app.getSharedPreferences("export_status",0).edit().putBoolean("running",true).putLong("id",id).commit();
        executor.execute(()->{
            String message;
            try{new RecordingExporter(app).export(rows,destination);message="已导出 "+rows.size()+" 段录音到所选位置";}
            catch(Exception e){boolean cleaned=false;try{cleaned=DocumentsContract.deleteDocument(app.getContentResolver(),destination);}catch(Exception ignored){}
                message="导出失败：文件不可读、空间不足或保存授权失效"+(cleaned?"，已清理不完整文件":"，目标文件可能不完整");}
            app.getSharedPreferences("export_status",0).edit().putBoolean("running",false).apply();
            current=new Status(jobId,false,message);
        });return true;
    }
    public static synchronized void restoreInterrupted(Context context){
        if(current.id!=0||current.running)return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("export_status",0);
        if(prefs.getBoolean("running",false)){
            current=new Status(Math.max(1,prefs.getLong("id",0)),false,"上次导出因应用进程结束而中断，目标文件可能不完整，请重新导出");prefs.edit().putBoolean("running",false).apply();
        }
    }
    private ExportJob(){}
}
