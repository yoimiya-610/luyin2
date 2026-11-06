package cn.luyin.player;

import android.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35})
public class TrialMonitorTest {
    private TrialMonitor trial;
    private Context context;
    private final List<Fake> engines=new ArrayList<>();
    private final List<Runnable> errors=new ArrayList<>(),silences=new ArrayList<>();
    private boolean fail;
    private final class Fake implements TrialMonitor.Engine {
        int starts,stops,releases;
        public void start(){starts++;if(fail)throw new IllegalStateException("occupied");}
        public void stop(){stops++;}public void release(){releases++;}public int amplitude(){return 16000;}public long duration(){return 1234;}
    }
    @Before public void prepare(){context=RuntimeEnvironment.getApplication();trial=new TrialMonitor((analysis,error,silenced)->{Fake fake=new Fake();engines.add(fake);errors.add(error);silences.add(silenced);return fake;});}
    @After public void close(){trial.close();}
    @Test public void startStopAndRestartNeverWriteRecordingData()throws Exception{
        Library library=new Library(context);String reserved=library.reserveRecordingName();String prefs=context.getSharedPreferences("library",0).getAll().toString();String[] files=context.getFilesDir().list();
        trial.start();assertTrue(trial.running());Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(trial.waveform().length>0);trial.stop();assertFalse(trial.running());assertTrue(trial.hasSession());assertEquals(1234,trial.duration());assertEquals(1,engines.get(0).stops);assertEquals(1,engines.get(0).releases);assertNull(CaptureJournal.read(context));assertTrue(library.presets().isEmpty());assertEquals(prefs,context.getSharedPreferences("library",0).getAll().toString());assertArrayEquals(files,context.getFilesDir().list());assertEquals(reserved.substring(0,reserved.indexOf('-'))+"-2",library.reserveRecordingName());
        trial.start();assertEquals(2,engines.size());assertEquals(0,trial.waveform().length);trial.stop();
    }
    @Test public void repeatedStartStopIsIdempotent()throws Exception{trial.start();trial.start();assertEquals(1,engines.size());trial.stop();trial.stop();assertEquals(1,engines.get(0).releases);}
    @Test public void priorRecordingsAndPresetsRemainUnchanged()throws Exception{Library library=new Library(context);Recording row=new Recording("content://media/external/audio/media/17","old","",SourceRules.APP,5000,1);CaptureJournal journal=new CaptureJournal();journal.state="finished";journal.rows.add(row);journal.write(context);library.putPreset(new Preset("old-preset","old",1,List.of(row)));library.save("queue",List.of(row));String capture=context.getSharedPreferences("capture",0).getString("journal","");String prefs=context.getSharedPreferences("library",0).getAll().toString();trial.start();trial.stop();assertEquals(capture,context.getSharedPreferences("capture",0).getString("journal",""));assertEquals(prefs,context.getSharedPreferences("library",0).getAll().toString());}
    @Test public void microphoneInterruptionStopsAndReleases()throws Exception{trial.start();silences.get(0).run();assertFalse(trial.running());assertTrue(trial.message().contains("占用"));assertEquals(1,engines.get(0).releases);}
    @Test public void staleCallbacksCannotStopNewTrial()throws Exception{trial.start();trial.stop();trial.start();errors.get(0).run();assertTrue(trial.running());errors.get(1).run();assertFalse(trial.running());assertTrue(trial.message().contains("失败"));}
    @Test public void startFailureReleasesWithoutActiveSession()throws Exception{fail=true;assertThrows(IllegalStateException.class,()->trial.start());assertFalse(trial.running());assertFalse(trial.hasSession());assertEquals(1,engines.get(0).releases);fail=false;trial.start();assertTrue(trial.running());}
    @Test public void memoryOnlyRecorderDoesNotCreateEncoderOrFileAndExcludesOtherMicrophoneClients()throws Exception{
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);LiveAnalysis analysis=new LiveAnalysis();PcmRecorder first=PcmRecorder.monitor(context,analysis,()->{},()->{});
        try{assertNull(ReflectionHelpers.getField(first,"file"));assertThrows(IOException.class,()->PcmRecorder.monitor(context,analysis,()->{},()->{}));}finally{first.release();analysis.close();}
        LiveAnalysis nextAnalysis=new LiveAnalysis();PcmRecorder next=PcmRecorder.monitor(context,nextAnalysis,()->{},()->{});first.release();assertThrows(IOException.class,()->PcmRecorder.monitor(context,nextAnalysis,()->{},()->{}));next.release();nextAnalysis.close();
    }
    @Test public void missingPermissionCannotAcquireMicrophone()throws Exception{Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO);LiveAnalysis analysis=new LiveAnalysis();try{assertThrows(SecurityException.class,()->PcmRecorder.monitor(context,analysis,()->{},()->{}));}finally{analysis.close();}}
}
