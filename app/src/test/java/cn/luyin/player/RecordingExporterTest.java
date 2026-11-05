package cn.luyin.player;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class RecordingExporterTest {
    private Context context;
    private File input,output;
    private byte[] bytes;
    private Recording recording;
    private java.util.concurrent.CountDownLatch readingStarted,allowRead;
    @Before public void prepare() throws Exception {
        context=RuntimeEnvironment.getApplication();input=new File(context.getCacheDir(),"input.m4a");output=new File(context.getCacheDir(),"export.out");bytes=new byte[131333];new Random(17).nextBytes(bytes);try(FileOutputStream out=new FileOutputStream(input)){out.write(bytes);}
        ContentProvider provider=new ContentProvider(){
            public boolean onCreate(){return true;}
            public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});c.addRow(new Object[]{"original.m4a"});return c;}
            public String getType(Uri uri){return "audio/mp4";}
            public Uri insert(Uri uri,ContentValues values){return uri;}
            public int update(Uri uri,ContentValues values,String selection,String[] args){return 1;}
            public int delete(Uri uri,String selection,String[] args){return 1;}
            @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(uri.getLastPathSegment().equals("missing"))throw new FileNotFoundException("source missing");boolean source=uri.getLastPathSegment().equals("input");if(source&&allowRead!=null){readingStarted.countDown();try{allowRead.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FileNotFoundException("interrupted");}}File file=source?input:output;return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode));}
        };
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="recording.test";info.applicationInfo=context.getApplicationInfo();provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal("recording.test",provider);
        recording=new Recording("content://recording.test/input","软件里重命名的录音","",SourceRules.APP,1000,0);
    }
    @Test public void singleExportUsesContentResolverAndKeepsFormat()throws Exception{RecordingExporter exporter=new RecordingExporter(context);assertEquals("audio/mp4",exporter.mime(recording));assertEquals("软件里重命名的录音.m4a",exporter.name(recording,exporter.mime(recording)));exporter.export(List.of(recording),Uri.parse("content://recording.test/output"));assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(output.toPath()));}
    @Test public void duplicateQueueEntriesExportAsTwoOrderedFiles()throws Exception{new RecordingExporter(context).export(List.of(recording,recording),Uri.parse("content://recording.test/output"));try(ZipInputStream zip=new ZipInputStream(new FileInputStream(output))){assertEquals("001-软件里重命名的录音.m4a",zip.getNextEntry().getName());assertArrayEquals(bytes,zip.readAllBytes());assertEquals("002-软件里重命名的录音.m4a",zip.getNextEntry().getName());assertArrayEquals(bytes,zip.readAllBytes());assertNull(zip.getNextEntry());}}
    @Test public void missingSourceFailsInsteadOfReportingSuccess()throws Exception{recording.uri="content://recording.test/missing";assertThrows(IOException.class,()->new RecordingExporter(context).export(List.of(recording),Uri.parse("content://recording.test/output")));}
    @Test public void originalCannotBeExportDestination()throws Exception{assertThrows(IOException.class,()->new RecordingExporter(context).export(List.of(recording),Uri.parse(recording.uri)));assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(input.toPath()));}
    @Test public void exportJobSerializesWorkAcrossActivities()throws Exception{
        readingStarted=new java.util.concurrent.CountDownLatch(1);allowRead=new java.util.concurrent.CountDownLatch(1);
        assertTrue(ExportJob.start(context,List.of(recording),Uri.parse("content://recording.test/output")));
        try{assertTrue(readingStarted.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(ExportJob.status().running);assertFalse(ExportJob.start(context,List.of(recording),Uri.parse("content://recording.test/output")));ExportJob.restoreInterrupted(context);assertTrue("recreating an activity must not mark a live export interrupted",ExportJob.status().running);}finally{allowRead.countDown();}
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(ExportJob.status().running&&System.nanoTime()<deadline)Thread.sleep(10);
        assertFalse(ExportJob.status().running);assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(output.toPath()));
    }
}
