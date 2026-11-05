package cn.luyin.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.IOException;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class CaptureService extends Service {
    public static final String START="capture.start",PAUSE="capture.pause",RESUME="capture.resume",FINISH="capture.finish";
    public static volatile boolean running=false;
    private static final String CHANNEL="long_recording";
    private static final int NOTIFICATION=2001;
    private MediaRecorder recorder;
    private ParcelFileDescriptor descriptor;
    private PowerManager.WakeLock wakeLock;
    private CaptureJournal journal;
    private Library library;
    private boolean finishing;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable renewWakeLock=new Runnable(){public void run(){if(recorder!=null&&journal!=null&&journal.active!=null){wakeLock.acquire(10*60*1000L);handler.postDelayed(this,60*1000L);}}};

    @Override public void onCreate(){
        super.onCreate();synchronized(CaptureService.class){running=true;}library=new Library(this);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"长录音",NotificationManager.IMPORTANCE_LOW));
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RecordingFlow:Capture");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        try{
            if(START.equals(action)){
                if(journal!=null&&!journal.state.equals("finished"))return START_NOT_STICKY;
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("未获得麦克风权限");
                recoverSavedSession(this);
                journal=new CaptureJournal();journal.title=new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+" 长录音";
                journal.write(this);
                if(Build.VERSION.SDK_INT>=30)startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                else startForeground(NOTIFICATION,notification());
                startSegment();
            }else if(journal!=null&&!journal.state.equals("finished")){
                if(PAUSE.equals(action)&&journal.active!=null){closeSegment();journal.state="paused";journal.message="片段已保存，点击继续录音开始下一段";journal.write(this);updateNotification();}
                else if(RESUME.equals(action)&&journal.active==null)startSegment();
                else if(FINISH.equals(action))finishSession("");
            }else stopSelf();
        }catch(Exception e){
            String message="录音已停止："+(e.getMessage()==null?"麦克风、空间或系统状态异常":e.getMessage());
            if(journal!=null){try{finishSession(message);}catch(Exception ignored){journal.state="paused";journal.message=message+"；已保存片段将在下次打开时恢复";try{journal.write(this);}catch(Exception ignoredAgain){}}}
            android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void startSegment() throws Exception {
        if(journal.active!=null)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("麦克风权限已撤销");
        PlaybackService.pauseForCapture();
        String name=library.reserveRecordingName();ContentValues values=new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME,name+".m4a");values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp4");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH,Library.recordingPath());values.put(MediaStore.Audio.Media.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法创建录音文件");
        journal.active=new Recording(uri.toString(),name,Library.recordingPath(),SourceRules.APP,0,System.currentTimeMillis());
        journal.ledger.begin(uri.toString());journal.state="recording";journal.message="";journal.activeSince=SystemClock.elapsedRealtime();
        try{
            journal.write(this);descriptor=getContentResolver().openFileDescriptor(uri,"rw");if(descriptor==null)throw new IOException("无法打开录音文件");
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(44100);recorder.setAudioChannels(1);recorder.setAudioEncodingBitRate(128000);
            final MediaRecorder current=recorder;
            recorder.registerAudioRecordingCallback(getMainExecutor(),new AudioManager.AudioRecordingCallback(){
                @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs){
                    if(current!=recorder||finishing)return;
                    for(AudioRecordingConfiguration config:configs)if(config.isClientSilenced()){
                        try{closeSegment();journal.state="paused";journal.message="麦克风被系统或其他应用占用，已暂停；请回到应用确认后继续";journal.write(CaptureService.this);updateNotification();android.widget.Toast.makeText(CaptureService.this,journal.message,android.widget.Toast.LENGTH_LONG).show();}
                        catch(Exception e){try{finishSession("麦克风被占用，已保存可用片段");}catch(Exception ignored){stopSelf();}}return;
                    }
                }
            });
            recorder.setOutputFile(descriptor.getFileDescriptor());
            recorder.setMaxFileSize(1024L*1024*1024);
            recorder.setOnErrorListener((mr,what,extra)->{if(mr==recorder&&!finishing)try{finishSession("录音设备发生错误，已保存可用片段");}catch(Exception e){stopSelf();}});
            recorder.setOnInfoListener((mr,what,extra)->{
                if(mr==recorder&&what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED&&!finishing){try{closeSegment();startSegment();}catch(Exception e){try{finishSession("自动分段失败，已保存可用片段");}catch(Exception ignored){stopSelf();}}}
            });
            recorder.prepare();recorder.start();journal.activeSince=SystemClock.elapsedRealtime();journal.write(this);wakeLock.acquire(10*60*1000L);handler.postDelayed(renewWakeLock,60*1000L);updateNotification();
        }catch(Exception e){releaseRecorder();try{getContentResolver().delete(uri,null,null);}catch(Exception ignored){}journal.ledger.complete(false);journal.active=null;journal.activeSince=0;journal.state="paused";journal.write(this);throw e;}
    }
    private void closeSegment() throws Exception {
        if(journal.active==null)return;
        Recording segment=journal.active;long elapsed=Math.max(0,SystemClock.elapsedRealtime()-journal.activeSince);boolean stopped=false;
        try{if(recorder!=null){recorder.stop();stopped=true;}}catch(RuntimeException ignored){}finally{releaseRecorder();}
        if(stopped){segment.duration=duration(this,Uri.parse(segment.uri));if(segment.duration<=0)segment.duration=elapsed;}
        if(stopped&&segment.duration>0){
            // Checkpoint the closed file before publishing it. Recovery can safely repeat publishing.
            journal.ledger.complete(true);journal.rows.add(segment);journal.total+=segment.duration;
            journal.active=null;journal.activeSince=0;journal.write(this);
            publish(this,segment);library.addOwned(segment);
        }else{
            try{getContentResolver().delete(Uri.parse(segment.uri),null,null);}catch(Exception ignored){}
            journal.ledger.complete(false);journal.active=null;journal.activeSince=0;journal.message="刚才片段过短或录制失败，未生成有效文件";journal.write(this);
            android.widget.Toast.makeText(this,journal.message,android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void finishSession(String message) throws Exception {
        if(finishing)return;finishing=true;
        try{
            closeSegment();
            List<Recording> available=new ArrayList<>();int missing=0;
            for(Recording r:journal.rows){try{publish(this,r);}catch(java.io.FileNotFoundException deleted){missing++;continue;}library.addOwned(r);available.add(r);}
            if(!available.isEmpty())library.putPreset(new Preset(journal.id,journal.title,journal.created,available));
            journal.ledger.finish();journal.state="finished";journal.message=message.isEmpty()?(available.isEmpty()?"录音已结束，没有可保存的有效片段":"已保存 "+available.size()+" 段录音，并生成预设队列"):message;
            if(missing>0)journal.message+="；另有 "+missing+" 段已被外部删除";
            journal.write(this);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }finally{finishing=false;}
    }
    private static long duration(Context context,Uri uri){
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{retriever.setDataSource(context,uri);String value=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);return value==null?0:Long.parseLong(value);}catch(Exception ignored){return 0;}finally{try{retriever.release();}catch(Exception ignored){}}
    }
    private static void publish(Context context,Recording r) throws IOException {
        ContentValues values=new ContentValues();values.put(MediaStore.Audio.Media.IS_PENDING,0);values.put(MediaStore.Audio.Media.DURATION,r.duration);
        if(context.getContentResolver().update(Uri.parse(r.uri),values,null,null)<1)throw new java.io.FileNotFoundException("录音文件已丢失");
    }
    public static synchronized String recoverInterrupted(Context context) throws Exception {
        if(running)return "";return recoverSavedSession(context);
    }
    private static String recoverSavedSession(Context context) throws Exception {
        CaptureJournal s=CaptureJournal.read(context);if(s==null||s.state.equals("finished"))return "";
        Library library=new Library(context);
        if(s.active!=null){Recording r=s.active;r.duration=duration(context,Uri.parse(r.uri));
            if(r.duration>0){s.ledger.complete(true);s.rows.add(r);s.total+=r.duration;}else{s.ledger.complete(false);try{context.getContentResolver().delete(Uri.parse(r.uri),null,null);}catch(Exception ignored){}}
            s.active=null;s.activeSince=0;s.write(context);
        }
        List<Recording> available=new ArrayList<>();for(Recording r:s.rows){try{publish(context,r);library.addOwned(r);available.add(r);}catch(Exception ignored){}}
        if(!available.isEmpty())library.putPreset(new Preset(s.id,s.title,s.created,available));
        s.ledger.finish();s.state="finished";s.message="上次录音意外中断，已恢复 "+available.size()+" 个有效片段";s.write(context);return s.message;
    }
    private void releaseRecorder(){
        handler.removeCallbacks(renewWakeLock);
        if(recorder!=null){try{recorder.release();}catch(Exception ignored){}recorder=null;}
        if(descriptor!=null){try{descriptor.close();}catch(Exception ignored){}descriptor=null;}
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
    }
    private PendingIntent command(String action){return PendingIntent.getService(this,action.hashCode(),new Intent(this,CaptureService.class).setAction(action),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private Notification notification(){
        boolean recording=journal!=null&&journal.state.equals("recording");
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_app).setContentTitle(recording?"长录音正在录制":"长录音已暂停")
            .setContentText("已保存 "+(journal==null?0:journal.rows.size())+" 段 · "+(recording?"暂停即保存当前段":"继续将新建片段"))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,recording?"暂停并保存":"继续录音",command(recording?PAUSE:RESUME)).build())
            .addAction(new Notification.Action.Builder(null,"结束并保存队列",command(FINISH)).build()).build();
    }
    private void updateNotification(){if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        if(journal!=null&&!journal.state.equals("finished"))try{finishSession("录音服务已停止，已保存可用片段");}catch(Exception ignored){}
        releaseRecorder();running=false;super.onDestroy();
    }
}
