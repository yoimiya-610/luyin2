package cn.luyin.player;

import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@androidx.media3.common.util.UnstableApi
public class StudioAnalysisTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private View root;
    @Before public void start(){
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindService(new android.content.ComponentName(RuntimeEnvironment.getApplication(),PlaybackService.class),new Binder());
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();activity=controller.get();root=activity.getWindow().getDecorView();layout(1233,2673);
    }
    @After public void close(){controller.pause().stop().destroy();}
    private void layout(int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    private View find(View view,String label){if((view instanceof TextView&&label.contentEquals(((TextView)view).getText()))||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private <T> T type(View view,Class<T> kind){if(kind.isInstance(view))return kind.cast(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){T found=type(((ViewGroup)view).getChildAt(i),kind);if(found!=null)return found;}return null;}
    private void studio(){find(root,"录音室页面").performClick();layout(1233,2673);}
    @Test public void filtersHaveCompleteBoundsAndNoNativeShadow(){
        FlowLayout flow=type(root,FlowLayout.class);assertNotNull(flow);assertEquals(5,flow.getChildCount());
        for(int i=0;i<flow.getChildCount();i++){Button chip=(Button)flow.getChildAt(i);assertTrue(chip.getLeft()>=0);assertTrue(chip.getRight()<=flow.getWidth());assertTrue(chip.getBottom()<=flow.getHeight());assertTrue(chip.getHeight()>=144);assertNull(chip.getStateListAnimator());assertEquals(0,chip.getElevation(),0);}
    }
    @Test public void spectrumSelectionPersistsAndSwitchesVisibleGraph(){studio();find(root,"声谱图").performClick();assertEquals("spectrum",activity.getSharedPreferences("analysis_settings",0).getString("display",""));assertEquals(View.VISIBLE,type(root,SpectrogramView.class).getVisibility());assertEquals(View.GONE,type(root,WaveformView.class).getVisibility());find(root,"波形").performClick();assertEquals(View.VISIBLE,type(root,WaveformView.class).getVisibility());}
    @Test public void analysisOptionsCanBeTurnedOffIndependently(){studio();find(root,"分析设置").performClick();android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();d.getListView().performItemClick(d.getListView().getAdapter().getView(1,null,d.getListView()),1,1);assertFalse(activity.getSharedPreferences("analysis_settings",0).getBoolean("harmonics",true));assertTrue(activity.getSharedPreferences("analysis_settings",0).getBoolean("pitch",true));assertEquals(View.GONE,((View)find(root,"泛音幅度 · 相对基频 dB").getParent()).getVisibility());d.dismiss();}
    @Test public void manualLandscapeRequestsSensorOrientation(){studio();find(root,"横屏").performClick();assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,activity.getRequestedOrientation());}
    @Test public void landscapeUsesFullWidthAndPreservesStudioAndDisplay(){
        studio();find(root,"声谱图").performClick();Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();RuntimeEnvironment.setQualifiers("w891dp-h411dp-land-xxhdpi");
        controller=Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume().visible();activity=controller.get();root=activity.getWindow().getDecorView();layout(2673,1233);
        SpectrogramView spectrum=type(root,SpectrogramView.class);assertEquals(View.VISIBLE,spectrum.getVisibility());assertTrue(spectrum.getWidth()>2400);assertTrue(spectrum.getHeight()>500);assertNotNull(find(root,"竖屏"));assertNotNull(find(root,"录音"));assertNotNull(find(root,"钢琴"));assertFalse(find(root,"录音室页面").isShown());
    }
    @Test public void pianoReleasesGlissandoAndCancelNotes(){
        List<Integer> on=new ArrayList<>(),off=new ArrayList<>();PianoKeyboardView keyboard=new PianoKeyboardView(activity,new PianoKeyboardView.Listener(){public void on(int note){on.add(note);}public void off(int note){off.add(note);}});keyboard.layout(0,0,1400,400);
        touch(keyboard,MotionEvent.ACTION_DOWN,30,350);touch(keyboard,MotionEvent.ACTION_MOVE,130,350);touch(keyboard,MotionEvent.ACTION_CANCEL,130,350);assertEquals(Arrays.asList(60,62),on);assertEquals(Arrays.asList(60,62),off);
    }
    private void touch(View view,int action,float x,float y){MotionEvent event=MotionEvent.obtain(0,1,action,x,y,0);view.onTouchEvent(event);event.recycle();}
    @Test public void pianoDialogClosesWhenActivityPauses(){studio();find(root,"钢琴").performClick();PianoDialog d=(PianoDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(d.isShowing());controller.pause();assertFalse(d.isShowing());controller.resume();}
    // Robolectric's default dup(FileDescriptor) copies to an unrelated temporary file.
    // This test supplies a second handle to the same file; it does not validate native dup semantics.
    @Implements(ParcelFileDescriptor.class)
    public static class SameFileDup extends org.robolectric.shadows.ShadowParcelFileDescriptor {
        static File file;static FileDescriptor expected;
        @Implementation protected static ParcelFileDescriptor dup(FileDescriptor source)throws IOException{assertSame(expected,source);return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE);}
    }
    @Test @Config(shadows=SameFileDup.class) public void wavSinkWritesActualPcmAndFinalizesHeader()throws Exception{
        File f=new File(activity.getCacheDir(),"capture.wav");try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE)){
            SameFileDup.file=f;SameFileDup.expected=fd.getFileDescriptor();
            PcmAudioFile sink=new PcmAudioFile(fd,CaptureFormat.WAV);short[] pcm={0,32767,-32768,1234};sink.write(pcm,pcm.length);sink.finish();sink.finish();byte[] bytes=Files.readAllBytes(f.toPath());assertEquals(52,bytes.length);assertEquals("RIFF",new String(bytes,0,4,"US-ASCII"));assertEquals(8,bytes[40]);assertArrayEquals(new byte[]{0,0,-1,127,0,-128,-46,4},Arrays.copyOfRange(bytes,44,52));
        }
    }
}
