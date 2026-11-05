package cn.luyin.player;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
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
public class PageNavigationTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private View root;
    @Before public void start(){
        // These tests cover navigation and layout, not the platform's media-session Binder.
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindService(new android.content.ComponentName(RuntimeEnvironment.getApplication(),PlaybackService.class),new android.os.Binder());
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible();activity=controller.get();root=activity.getWindow().getDecorView();layout();
    }
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    private void layout(){int width=1233,height=2673;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);}
    private View description(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=description(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private View text(View view,String label){if(view instanceof TextView&&label.contentEquals(((TextView)view).getText())&&visible(view))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private boolean visible(View view){if(view.getVisibility()!=View.VISIBLE)return false;return !(view.getParent() instanceof View)||visible((View)view.getParent());}
    private void navigate(String label){View target=description(root,label+"页面");assertNotNull(target);target.performClick();layout();}
    @Test public void libraryDoesNotStackRecorderControls(){assertNotNull(text(root,"录音库"));assertNotNull(text(root,"＋ 导入"));assertNull(text(root,"●  开始长录音"));assertNull(text(root,"管理队列"));}
    @Test public void recorderHasDedicatedWaveformAndControls(){navigate("录音室");assertNotNull(text(root,"●  开始长录音"));assertNotNull(text(root,"结束并保存整组队列"));assertNotNull(text(root,"实时声音波形  ·  最近 12 秒有效录音"));assertNull(text(root,"＋ 导入"));assertNull(text(root,"管理队列"));View start=text(root,"●  开始长录音");assertTrue("record button has touch target",start.getHeight()>=144);}
    @Test public void queueAndPresetsAreSeparatePages(){navigate("播放");assertNotNull(text(root,"随听播放器"));assertNotNull(text(root,"管理队列"));assertNull(text(root,"●  开始长录音"));navigate("预设");assertNotNull(text(root,"预设队列"));assertNotNull(text(root,"＋ 保存当前"));assertNull(text(root,"管理队列"));}
    @Test public void activePageSurvivesRecreation(){navigate("录音室");Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume().visible();activity=controller.get();root=activity.getWindow().getDecorView();layout();assertNotNull(text(root,"●  开始长录音"));assertNull(text(root,"＋ 导入"));}
    @Test public void recordingNotificationOpensStudio(){activity.onNewIntent(new android.content.Intent(activity,MainActivity.class).putExtra("page","studio"));layout();assertNotNull(text(root,"●  开始长录音"));assertNull(text(root,"＋ 导入"));navigate("录音库");assertNotNull(text(root,"＋ 导入"));}
    @Test public void playbackNotificationOpensPlayer(){activity.onNewIntent(new android.content.Intent(activity,MainActivity.class).putExtra("page","player"));layout();assertNotNull(text(root,"随听播放器"));assertNotNull(text(root,"管理队列"));assertNull(text(root,"●  开始长录音"));}
}
