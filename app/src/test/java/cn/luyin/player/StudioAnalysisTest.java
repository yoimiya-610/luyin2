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
import org.robolectric.util.ReflectionHelpers;
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
    private SoundFrame spectrumFrame(double time){float[] db=new float[2049];Arrays.fill(db,-90);db[123]=-3;return new SoundFrame(1,time,14700,4096,440,1,-9,db,new float[8],null);}
    @Test public void spectrogramReusesMeasuredColumnsAndEvictsOldOnes(){SpectrogramView view=new SpectrogramView(activity);SoundFrame a=spectrumFrame(1),b=spectrumFrame(1.05);view.setSnapshot(new LiveAnalysis.Snapshot(1,List.of(a)),false);Map<SoundFrame,int[]> first=org.robolectric.util.ReflectionHelpers.getField(view,"columns");int[] cached=first.get(a);view.setSnapshot(new LiveAnalysis.Snapshot(1,List.of(a,b)),true);Map<SoundFrame,int[]> next=org.robolectric.util.ReflectionHelpers.getField(view,"columns");assertSame(cached,next.get(a));assertEquals(2,next.size());android.graphics.Bitmap bitmap=org.robolectric.util.ReflectionHelpers.getField(view,"bitmap");assertEquals(240,bitmap.getWidth());view.setSnapshot(new LiveAnalysis.Snapshot(2,Collections.emptyList()),false);assertTrue(((Map<?,?>)org.robolectric.util.ReflectionHelpers.getField(view,"columns")).isEmpty());}
    @Test public void renamingRedirectsSelectionAndRefreshRemainsSafe(){java.util.Set<String> selected=org.robolectric.util.ReflectionHelpers.getField(activity,"selected");selected.add("file:///old.wav");activity.getSharedPreferences("library",0).edit().putString("uri:file:///old.wav","file:///new.wav").commit();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"afterRename");assertTrue(selected.contains("file:///new.wav"));assertFalse(selected.contains("file:///old.wav"));studio();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"updateCapture");}
    @Test public void unchangedGraphModeReusesButtonBackground(){studio();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"updateAnalysis");android.graphics.drawable.Drawable original=find(root,"波形").getBackground();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"updateAnalysis");assertSame(original,find(root,"波形").getBackground());}
    @Test public void varyingHarmonicsAndPitchKeepStudioBoundsStable(){
        studio();
        TextView pitch=ReflectionHelpers.getField(activity,"pitchReadout");
        pitch.setTextSize(28);
        HarmonicsView graph=type(root,HarmonicsView.class);
        View stability=ReflectionHelpers.getField(activity,"stabilityCard");
        View clips=ReflectionHelpers.getField(activity,"studioClips");
        LiveAnalysis analysis=ReflectionHelpers.getStaticField(CaptureService.class,"analysis");
        LiveAnalysis.Snapshot original=analysis.snapshot();
        try{
            setAnalysisFrame(analysis,0,Float.NaN);
            layout(960,2400);
            List<android.graphics.Rect> expected=locations(graph,stability,clips,pitch);
            for(int i=0;i<8;i++){
                double hz=i%2==0?1108.73:0;
                SoundFrame frame=setAnalysisFrame(analysis,hz,i%2==0?-5:-55);
                layout(960,2400);
                assertEquals(expected,locations(graph,stability,clips,pitch));
                assertSame(frame,ReflectionHelpers.getField(graph,"frame"));
                assertEquals(1,pitch.getLineCount());
            }
        }finally{ReflectionHelpers.setField(analysis,"snapshot",original);}
    }
    private SoundFrame setAnalysisFrame(LiveAnalysis analysis,double hz,float harmonic){
        float[] db=new float[8];Arrays.fill(db,harmonic);
        StabilityMetrics stability=hz>0?StabilityMetrics.measure(Collections.nCopies(8,spectrumFrame(1)),1,2,hz,-9):null;
        SoundFrame frame=new SoundFrame(1,2,8000,2048,hz,1,-9,new float[1025],db,stability);
        ReflectionHelpers.setField(analysis,"snapshot",new LiveAnalysis.Snapshot(1,List.of(frame)));
        ReflectionHelpers.callInstanceMethod(activity,"updateAnalysis");
        return frame;
    }
    private static List<android.graphics.Rect> locations(View... views){
        List<android.graphics.Rect> bounds=new ArrayList<>();
        for(View view:views){
            int[] at=new int[2];view.getLocationInWindow(at);
            bounds.add(new android.graphics.Rect(at[0],at[1],at[0]+view.getWidth(),at[1]+view.getHeight()));
        }
        return bounds;
    }
    private void chooseNotes(View panel,boolean notes){find(panel,"切换音高轨迹坐标").performClick();android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();int at=notes?1:0;d.getListView().performItemClick(d.getListView().getAdapter().getView(at,null,d.getListView()),at,at);}
    @Test public void scientificPitchAxisSwitchesAndPersistsAcrossRecreation(){studio();PitchTraceView trace=type(root,PitchTraceView.class);int oldHeight=trace.getLayoutParams().height;chooseNotes(root,true);assertTrue(trace.isNoteAxis());assertTrue(trace.getLayoutParams().height>oldHeight);assertNotNull(find(root,"坐标：音名"));assertTrue(activity.getSharedPreferences("analysis_settings",0).getBoolean("pitch_note_axis",false));controller.recreate();activity=controller.get();root=activity.getWindow().getDecorView();assertTrue(type(root,PitchTraceView.class).isNoteAxis());chooseNotes(root,false);assertFalse(type(root,PitchTraceView.class).isNoteAxis());assertEquals(oldHeight,type(root,PitchTraceView.class).getLayoutParams().height);assertFalse(activity.getSharedPreferences("analysis_settings",0).getBoolean("pitch_note_axis",true));}
    @Test public void axisSwitchPreservesMeasuredSnapshot(){studio();PitchTraceView trace=type(root,PitchTraceView.class);LiveAnalysis.Snapshot snapshot=new LiveAnalysis.Snapshot(3,Collections.emptyList());trace.setSnapshot(snapshot);chooseNotes(root,true);assertSame(snapshot,org.robolectric.util.ReflectionHelpers.getField(trace,"snapshot"));chooseNotes(root,false);assertSame(snapshot,org.robolectric.util.ReflectionHelpers.getField(trace,"snapshot"));}
    @Test public void landscapeAnalysisDialogOffersScientificAxis(){studio();Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();RuntimeEnvironment.setQualifiers("w891dp-h411dp-land-xxhdpi");controller=Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume().visible();activity=controller.get();root=activity.getWindow().getDecorView();find(root,"数据").performClick();android.app.AlertDialog data=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View panel=data.getWindow().getDecorView();chooseNotes(panel,true);assertTrue(type(panel,PitchTraceView.class).isNoteAxis());assertTrue(data.isShowing());data.dismiss();}
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
