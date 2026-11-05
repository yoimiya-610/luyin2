package cn.luyin.player;

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class RecordingDeletion {
    public static final class Result {
        public final List<Recording> removed=new ArrayList<>(),consent=new ArrayList<>(),failed=new ArrayList<>();
        public PendingIntent legacyConsent;
    }
    private final Context context;
    public RecordingDeletion(Context context){this.context=context.getApplicationContext();}
    public Result delete(List<Recording> input,boolean hideOnly)throws IOException{
        Result result=new Result();Library library=new Library(context);Set<String> seen=new HashSet<>();CaptureJournal session=CaptureJournal.read(context);
        for(Recording original:input){Recording r=library.resolve(Recording.from(original.json()));if(!seen.add(r.uri))continue;
            if(session!=null&&session.active!=null&&session.active.uri.equals(r.uri)){result.failed.add(r);continue;}
            if(hideOnly){result.removed.add(r);continue;}
            Uri uri=Uri.parse(r.uri);
            try{
                if("file".equals(uri.getScheme())){
                    File file=new File(uri.getPath()).getCanonicalFile(),root=context.getFilesDir().getCanonicalFile();
                    if(!file.toPath().startsWith(root.toPath()))throw new SecurityException("不是应用管理的文件");
                    if(file.exists()&&!file.delete())throw new IOException("文件删除失败");
                }else if(DocumentsContract.isDocumentUri(context,uri)){
                    if(!DocumentsContract.deleteDocument(context.getContentResolver(),uri))throw new IOException("文件提供方拒绝删除");
                }else if(context.getContentResolver().delete(uri,null,null)==0){try(android.database.Cursor c=context.getContentResolver().query(uri,new String[]{"_id"},null,null,null)){if(c!=null&&c.moveToFirst())throw new IOException("文件提供方未删除文件");}}
                result.removed.add(r);
            }catch(SecurityException e){
                if(Build.VERSION.SDK_INT>=30&&"media".equals(uri.getAuthority()))result.consent.add(r);
                else if(e instanceof RecoverableSecurityException){if(result.legacyConsent==null)result.legacyConsent=((RecoverableSecurityException)e).getUserAction().getActionIntent();result.consent.add(r);}
                else result.failed.add(r);
            }catch(Exception e){result.failed.add(r);}
        }
        library.removeRecordings(result.removed,!hideOnly);new android.os.Handler(android.os.Looper.getMainLooper()).post(PlaybackService::removeHidden);return result;
    }
}
