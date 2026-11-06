package cn.luyin.player;

import android.Manifest;
import android.content.*;
import android.database.*;
import android.media.AudioRecord;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@androidx.media3.common.util.UnstableApi
public class ReviewRegressionTest {
    private final Context app=RuntimeEnvironment.getApplication();
    private CountDownLatch reading,allowRead;
    @Test @Config(shadows=StudioAnalysisTest.SameFileDup.class) public void microphoneFailureKeepsFinalizedWavPrefix()throws Exception{
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
        CountDownLatch failed=new CountDownLatch(1);ShadowAudioRecord.setSource(new ShadowAudioRecord.AudioRecordSource(){int reads;public int readInShortArray(short[] a,int offset,int size,boolean blocking){if(reads++==0){Arrays.fill(a,offset,offset+size,(short)321);return size;}failed.countDown();return AudioRecord.ERROR_DEAD_OBJECT;}});
        File file=new File(app.getCacheDir(),"prefix.wav");LiveAnalysis analysis=new LiveAnalysis();
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE)){
            StudioAnalysisTest.SameFileDup.file=file;StudioAnalysisTest.SameFileDup.expected=fd.getFileDescriptor();PcmRecorder recorder=new PcmRecorder(app,fd,CaptureFormat.WAV,analysis,()->{},()->{},()->{});
            try{recorder.start();assertTrue(failed.await(3,TimeUnit.SECONDS));assertTrue("valid prefix survives microphone loss",recorder.stop());assertEquals(50,recorder.durationMs());assertEquals(44+4410,file.length());}finally{recorder.release();}
        }finally{ShadowAudioRecord.clearSource();analysis.close();}
    }
    @Test public void failedFinalizationIsStillRejected()throws Exception{
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);LiveAnalysis analysis=new LiveAnalysis();PcmRecorder recorder=PcmRecorder.monitor(app,analysis,()->{},()->{});try{ReflectionHelpers.setField(recorder,"failure",new IOException("finalization failed"));assertThrows(IOException.class,()->recorder.stop());}finally{recorder.release();analysis.close();}
    }
    @Test @Config(shadows=StudioAnalysisTest.SameFileDup.class) public void reusedWavBufferDoesNotAppendStaleSamples()throws Exception{File file=new File(app.getCacheDir(),"short-write.wav");try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE)){StudioAnalysisTest.SameFileDup.file=file;StudioAnalysisTest.SameFileDup.expected=fd.getFileDescriptor();PcmAudioFile sink=new PcmAudioFile(fd,CaptureFormat.WAV);sink.write(new short[]{1,2,3,4},4);sink.write(new short[]{5},1);sink.finish();assertEquals(54,file.length());byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());assertEquals(10,bytes[40]);assertEquals(5,bytes[52]);}}
    @Test public void trialReleasesEvenWhenDurationInspectionFails()throws Exception{
        int[] released={0};TrialMonitor monitor=new TrialMonitor((a,e,s)->new TrialMonitor.Engine(){public void start(){}public void stop(){}public long duration(){throw new IllegalStateException();}public int amplitude(){return 0;}public void release(){released[0]++;}});try{monitor.start();monitor.stop();assertEquals(1,released[0]);assertFalse(monitor.running());}finally{monitor.close();}
    }
    @Test public void importsWithNullDisplayNameRemainUsable()throws Exception{
        registerImports();Recording row=new Library(app).importFile(Uri.parse("content://review.test/one"),SourceRules.OTHER);assertEquals("导入录音",row.name);assertEquals(1,new Library(app).load("imports").size());
    }
    @Test public void concurrentImportsDoNotOverwriteEachOthersIndex()throws Exception{
        registerImports();ExecutorService pool=Executors.newFixedThreadPool(2);try{Future<Recording> a=pool.submit(()->new Library(app).importFile(Uri.parse("content://review.test/one"),SourceRules.OTHER));Future<Recording> b=pool.submit(()->new Library(app).importFile(Uri.parse("content://review.test/two"),SourceRules.OTHER));assertNotEquals(a.get(5,TimeUnit.SECONDS).uri,b.get(5,TimeUnit.SECONDS).uri);assertEquals(2,new Library(app).load("imports").size());}finally{pool.shutdownNow();}
    }
    @Test public void slowImportDoesNotBlockRecordingNameReservation()throws Exception{registerImports();reading=new CountDownLatch(1);allowRead=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);try{Future<Recording> importing=pool.submit(()->new Library(app).importFile(Uri.parse("content://review.test/one"),SourceRules.OTHER));assertTrue(reading.await(2,TimeUnit.SECONDS));assertNotNull(pool.submit(()->new Library(app).reserveRecordingName()).get(1,TimeUnit.SECONDS));allowRead.countDown();assertNotNull(importing.get(3,TimeUnit.SECONDS));}finally{allowRead.countDown();pool.shutdownNow();}}
    private void registerImports()throws Exception{
        for(String name:new String[]{"one","two"})try(FileOutputStream out=new FileOutputStream(new File(app.getCacheDir(),name))){out.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        ContentProvider provider=new ContentProvider(){public boolean onCreate(){return true;}public Cursor query(Uri u,String[] p,String s,String[] a,String sort){MatrixCursor c=new MatrixCursor(new String[]{"_display_name"});c.addRow(new Object[]{null});return c;}public String getType(Uri u){return "audio/wav";}public Uri insert(Uri u,ContentValues v){return u;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}public int delete(Uri u,String s,String[] a){return 0;}public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(reading!=null){reading.countDown();try{if(!allowRead.await(4,TimeUnit.SECONDS))throw new FileNotFoundException("test timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FileNotFoundException("interrupted");}}return ParcelFileDescriptor.open(new File(app.getCacheDir(),u.getLastPathSegment()),ParcelFileDescriptor.MODE_READ_ONLY);}};
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="review.test";info.applicationInfo=app.getApplicationInfo();provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal("review.test",provider);
    }
}
