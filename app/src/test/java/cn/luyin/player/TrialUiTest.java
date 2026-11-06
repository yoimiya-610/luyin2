package cn.luyin.player;

import android.Manifest;
import android.content.ComponentName;
import android.os.Binder;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@androidx.media3.common.util.UnstableApi
public class TrialUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private View root;
    private TrialMonitor monitor;
    private int starts,stops;
    @Before public void start(){
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindService(new ComponentName(RuntimeEnvironment.getApplication(),PlaybackService.class),new Binder());
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();activity=controller.get();root=activity.getWindow().getDecorView();
        ((TrialMonitor)ReflectionHelpers.getField(activity,"trial")).close();monitor=new TrialMonitor((a,e,s)->new TrialMonitor.Engine(){public void start(){starts++;}public void stop(){stops++;}public void release(){}public int amplitude(){return 12000;}public long duration(){return 2100;}});ReflectionHelpers.setField(activity,"trial",monitor);find(root,"录音室页面").performClick();
    }
    @After public void close(){CaptureService.running=false;controller.pause().stop().destroy();}
    private View find(View view,String label){if((view instanceof TextView&&label.contentEquals(((TextView)view).getText()))||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    @Test public void independentTrialButtonStartsStopsAndAllowsGraphSwitching(){find(root,"开始试音").performClick();assertEquals(1,starts);assertTrue(monitor.running());assertNotNull(find(root,"● 试音中 · 不保存"));assertFalse(find(root,"●  开始长录音").isEnabled());find(root,"声谱图").performClick();assertEquals("spectrum",activity.getSharedPreferences("analysis_settings",0).getString("display",""));find(root,"停止试音").performClick();assertFalse(monitor.running());assertEquals(1,stops);assertNotNull(find(root,"试音已停止"));assertNotNull(find(root,"00:02"));assertTrue(find(root,"●  开始长录音").isEnabled());}
    @Test public void leavingStudioStopsMicrophone(){find(root,"开始试音").performClick();find(root,"录音库页面").performClick();assertFalse(monitor.running());assertEquals(1,stops);}
    @Test public void scientificAxisCanSwitchWhileTrialKeepsRunning(){find(root,"开始试音").performClick();find(root,"切换音高轨迹坐标").performClick();android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();d.getListView().performItemClick(d.getListView().getAdapter().getView(1,null,d.getListView()),1,1);PitchTraceView trace=ReflectionHelpers.getField(activity,"pitchTrace");assertTrue(trace.isNoteAxis());assertTrue(monitor.running());assertEquals(1,starts);assertEquals(0,stops);}
    @Test public void backgroundingStopsMicrophone(){find(root,"开始试音").performClick();controller.pause();assertFalse(monitor.running());assertEquals(1,stops);controller.resume();assertFalse(monitor.running());}
    @Test public void recreationRetainsTrialWithoutRestartOrFileSave(){find(root,"开始试音").performClick();controller.recreate();activity=controller.get();root=activity.getWindow().getDecorView();assertSame(monitor,ReflectionHelpers.getField(activity,"trial"));assertTrue(monitor.running());assertEquals(1,starts);assertEquals(0,stops);assertNotNull(find(root,"停止试音"));assertNull(CaptureJournal.read(activity));}
    @Test public void ongoingLongSessionRejectsTrialEvenWhenPaused()throws Exception{CaptureJournal journal=new CaptureJournal();journal.state="paused";journal.write(activity);CaptureService.running=true;ReflectionHelpers.callInstanceMethod(activity,"updateCapture");assertFalse(find(root,"开始试音").isEnabled());ReflectionHelpers.callInstanceMethod(activity,"toggleTrial");assertFalse(monitor.running());assertEquals(0,starts);}
}
