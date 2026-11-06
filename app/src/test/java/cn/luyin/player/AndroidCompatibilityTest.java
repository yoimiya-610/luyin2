package cn.luyin.player;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35},qualifiers="w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@androidx.media3.common.util.UnstableApi
public class AndroidCompatibilityTest {
    private Context context;
    private boolean rejectOwner,omitOptional,deny;
    private int attempts;
    @Before public void prepare(){
        context=RuntimeEnvironment.getApplication();
        ContentProvider provider=new ContentProvider(){
            public boolean onCreate(){return true;}
            public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){attempts++;if(deny)throw new SecurityException("denied");if(rejectOwner&&Arrays.asList(projection).contains("owner_package_name"))throw new IllegalArgumentException("unsupported column");MatrixCursor c=omitOptional?new MatrixCursor(new String[]{"date_modified","duration","_display_name","_id"}):new MatrixCursor(new String[]{"date_modified","relative_path","_id","_display_name","duration"});c.addRow(omitOptional?new Object[]{12,2300,"voice.m4a",7}:new Object[]{12,"MIUI/sound_recorder/",7,"voice.m4a",2300});return c;}
            public String getType(Uri uri){return "audio/mp4";}public Uri insert(Uri uri,ContentValues values){return uri;}public int update(Uri uri,ContentValues values,String s,String[] a){return 0;}public int delete(Uri uri,String s,String[] a){return 0;}
        };
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="media";info.applicationInfo=context.getApplicationInfo();provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal("media",provider);
    }
    @Test public void missingOwnerColumnFallsBackWithoutLosingAudio(){rejectOwner=true;List<String> warnings=new ArrayList<>();List<Recording> rows=new Library(context).scan(warnings);assertEquals(2,attempts);assertTrue(warnings.isEmpty());assertEquals(1,rows.size());assertEquals(SourceRules.RECORDER,rows.get(0).source);assertEquals("voice.m4a",rows.get(0).name);assertEquals(2300,rows.get(0).duration);assertEquals(12000,rows.get(0).date);}
    @Test public void providersMayReorderColumnsAndOmitOptionalMetadata(){omitOptional=true;List<Recording> rows=new Library(context).scan(new ArrayList<>());assertEquals(1,rows.size());assertEquals(SourceRules.OTHER,rows.get(0).source);assertEquals("共享音频",rows.get(0).path);assertEquals("voice.m4a",rows.get(0).name);}
    @Test public void storagePermissionDenialKeepsImportedFiles()throws Exception{
        deny=true;File file=new File(context.getFilesDir(),"import.wav");try(FileOutputStream out=new FileOutputStream(file)){out.write(WavHeader.pcm16(0,44100,1));}
        // Java's file URI normalizes Windows drive separators. Android 10's Uri.fromFile assumes Unix paths.
        Uri uri=Uri.parse(file.toURI().toString());assertTrue(new File(uri.getPath()).exists());
        Library library=new Library(context);library.save("imports",List.of(new Recording(uri.toString(),"import.wav","",SourceRules.OTHER,1,1)));
        List<String> warnings=new ArrayList<>();assertEquals("import retained",1,library.scan(warnings).size());assertEquals("denied query count",1,attempts);assertEquals("permission warning",1,warnings.size());
    }
    @Test public void recordingDirectoryMatchesPlatformVersion(){assertEquals((Build.VERSION.SDK_INT>=31?"Recordings/":"Music/")+"录音随听/",Library.recordingPath());}
    @Test public void studioStartsOnMinimumAndTargetAndroid(){
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindService(new ComponentName(context,PlaybackService.class),new Binder());
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible();try{View root=controller.get().getWindow().getDecorView();View studio=description(root,"录音室页面");assertNotNull(studio);studio.performClick();assertNotNull(description(root,"实时声谱图，横轴时间，纵轴频率，颜色表示 dBFS 幅度"));}finally{controller.pause().stop().destroy();}
    }
    private View description(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=description(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
}
