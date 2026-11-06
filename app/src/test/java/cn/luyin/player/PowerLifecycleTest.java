package cn.luyin.player;

import android.content.*;
import android.os.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,35},qualifiers="w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@androidx.media3.common.util.UnstableApi
public class PowerLifecycleTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void start(){Shadows.shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindService(new ComponentName(RuntimeEnvironment.getApplication(),PlaybackService.class),new Binder());controller=Robolectric.buildActivity(MainActivity.class).setup().visible();activity=controller.get();}
    @After public void close(){CaptureService.running=false;controller.pause().stop().destroy();}
    private boolean enabled(){LiveAnalysis analysis=ReflectionHelpers.getStaticField(CaptureService.class,"analysis");return ReflectionHelpers.getField(analysis,"enabled");}
    private void page(int page){ReflectionHelpers.callInstanceMethod(activity,"showPage",ReflectionHelpers.ClassParameter.from(int.class,page));}
    @Test public void analysisRunsOnlyForVisibleStudio(){assertFalse(enabled());page(1);assertTrue(enabled());page(0);assertFalse(enabled());page(1);assertTrue(enabled());controller.pause();assertFalse(enabled());controller.resume();assertTrue(enabled());}
    @Test public void waveformOnlyModeKeepsPeakSamplingWithoutFft(){page(1);activity.getSharedPreferences("analysis_settings",0).edit().putBoolean("pitch",false).putBoolean("harmonics",false).putBoolean("stability",false).putString("display","wave").commit();ReflectionHelpers.callInstanceMethod(activity,"configureAnalysis");assertFalse(enabled());assertTrue((Boolean)ReflectionHelpers.getStaticField(CaptureService.class,"visualizationEnabled"));controller.pause();assertFalse((Boolean)ReflectionHelpers.getStaticField(CaptureService.class,"visualizationEnabled"));controller.resume();}
    @Test public void backgroundAnalysisPauseDoesNotEndLongRecording()throws Exception{page(1);CaptureJournal journal=new CaptureJournal();journal.state="recording";journal.active=new Recording("content://media/external/audio/media/9","test","",SourceRules.APP,0,1);journal.write(activity);CaptureService.running=true;String before=activity.getSharedPreferences("capture",0).getString("journal","");controller.pause();assertFalse(enabled());assertTrue(CaptureService.running);assertEquals(before,activity.getSharedPreferences("capture",0).getString("journal",""));controller.resume();assertTrue(enabled());}
    @Test public void rotationKeepsAnalysisEnabledWithoutResettingSegment(){page(1);long segment=CaptureService.analysisSnapshot().segment;controller.recreate();activity=controller.get();assertTrue(enabled());assertEquals(segment,CaptureService.analysisSnapshot().segment);}
    @Test public void pausedPlayerHasNoPeriodicCheckpoint(){ServiceController<PlaybackService> service=Robolectric.buildService(PlaybackService.class).create();try{Handler handler=ReflectionHelpers.getField(service.get(),"handler");Runnable checkpoint=ReflectionHelpers.getField(service.get(),"checkpoint");assertFalse(handler.hasCallbacks(checkpoint));checkpoint.run();assertFalse(handler.hasCallbacks(checkpoint));}finally{service.destroy();}}
}
