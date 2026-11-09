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
    @Test public void recorderHasDedicatedWaveformAndControls(){navigate("录音室");assertNotNull(text(root,"●  开始长录音"));assertNotNull(text(root,"结束并保存整组队列"));assertNotNull(text(root,"波形"));assertNotNull(text(root,"声谱图"));assertNotNull(text(root,"录音格式"));assertNull(text(root,"＋ 导入"));assertNull(text(root,"管理队列"));View start=text(root,"●  开始长录音");assertTrue("record button has touch target",start.getHeight()>=144);}
    @Test public void queueAndPresetsAreSeparatePages(){navigate("播放");assertNotNull(text(root,"播放队列"));assertNotNull(text(root,"管理队列"));assertNull(text(root,"●  开始长录音"));navigate("预设");assertNotNull(text(root,"预设队列"));assertNotNull(text(root,"＋ 保存当前"));assertNull(text(root,"管理队列"));}
    @Test public void activePageSurvivesRecreation(){navigate("录音室");Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();controller=Robolectric.buildActivity(MainActivity.class).create(state).start().restoreInstanceState(state).resume().visible();activity=controller.get();root=activity.getWindow().getDecorView();layout();assertNotNull(text(root,"●  开始长录音"));assertNull(text(root,"＋ 导入"));}
    @Test public void recordingNotificationOpensStudio(){activity.onNewIntent(new android.content.Intent(activity,MainActivity.class).putExtra("page","studio"));layout();assertNotNull(text(root,"●  开始长录音"));assertNull(text(root,"＋ 导入"));navigate("录音库");assertNotNull(text(root,"＋ 导入"));}
    @Test public void playbackNotificationOpensPlayer(){activity.onNewIntent(new android.content.Intent(activity,MainActivity.class).putExtra("page","player"));layout();assertNotNull(text(root,"播放队列"));assertNotNull(text(root,"管理队列"));assertNull(text(root,"●  开始长录音"));}
    @Test public void navigationLabelsCenterBelowIconsOnOneBaseline(){int baseline=-1;for(String name:new String[]{"录音库","录音室","播放","预设"}){ViewGroup item=(ViewGroup)description(root,name+"页面");TextView label=null;IconView icon=null;for(int i=0;i<item.getChildCount();i++){if(item.getChildAt(i)instanceof TextView)label=(TextView)item.getChildAt(i);if(item.getChildAt(i)instanceof IconView)icon=(IconView)item.getChildAt(i);}assertNotNull(label);assertNotNull(icon);assertEquals(Gravity.CENTER_HORIZONTAL,label.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK);int[] labelAt=new int[2],iconAt=new int[2];label.getLocationOnScreen(labelAt);icon.getLocationOnScreen(iconAt);assertEquals(labelAt[0]+label.getWidth()/2,iconAt[0]+icon.getWidth()/2,1);int b=labelAt[1]+label.getBaseline();if(baseline<0)baseline=b;else assertEquals(baseline,b);}}
    @Test public void permanentPagesDoNotContainInstructionalSubtitles(){assertNull(text(root,"整理声音，按你的顺序听。"));assertNull(text(root,"录音随听  /  LOCAL AUDIO"));navigate("预设");assertNull(text(root,"把常听的顺序，留给下一次。"));navigate("播放");assertNull(text(root,"点片段播放，点更多调整顺序或导出。"));}
    @Test public void chosenRecordingFormatPersists(){navigate("录音室");View format=text(root,"M4A / AAC");assertNotNull(format);format.performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(3,null,dialog.getListView()),3,3);assertEquals("WAV",activity.getSharedPreferences("capture_settings",0).getString("format",null));layout();assertNotNull(text(root,"WAV / PCM"));}
    @Test public void savedRecordingMenuOpensOfflineAnalysisForThatRecording(){
        Recording row=new Recording("file:///test.wav","分析测试","",SourceRules.APP,12000,0);
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"recordingActions",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(Recording.class,row));
        ListView menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getListView();
        int last=menu.getCount()-1;
        assertEquals("离线分析",menu.getAdapter().getItem(last));
        menu.performItemClick(menu.getAdapter().getView(last,null,menu),last,last);
        android.content.Intent opened=Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(OfflineAnalysisActivity.class.getName(),opened.getComponent().getClassName());
        assertEquals(row.uri,opened.getStringExtra("recording_uri"));
    }
    @Test public void aboutShowsInstalledVersionAndRepositoryFromEveryPage() throws Exception {
        android.content.pm.PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
        String expectedVersion=activity.getString(R.string.about_version,info.versionName,info.getLongVersionCode());
        for(String page:new String[]{"录音库","录音室","播放","预设"}){
            navigate(page);
            android.app.AlertDialog dialog=openAbout();
            CharSequence message=Shadows.shadowOf(dialog).getMessage();
            assertTrue(message.toString().contains(expectedVersion));
            assertTrue(message.toString().contains("https://github.com/yoimiya-610/luyin2"));
            dialog.dismiss();
        }
    }
    @Test public void aboutCopiesRepositoryAndOpensItWithAndroid(){
        String url="https://github.com/yoimiya-610/luyin2";
        openAbout().getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.content.ClipboardManager clipboard=activity.getSystemService(android.content.ClipboardManager.class);
        android.content.ClipData copied=clipboard.getPrimaryClip();
        assertNotNull(copied);
        assertEquals(url,copied.getItemAt(0).getText());
        openAbout().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        android.content.Intent opened=Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(android.content.Intent.ACTION_VIEW,opened.getAction());
        assertEquals(url,opened.getDataString());
    }
    private android.app.AlertDialog openAbout(){
        description(root,"当前页面选项").performClick();
        ListView menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getListView();
        int last=menu.getCount()-1;
        assertEquals("关于",menu.getAdapter().getItem(last));
        menu.performItemClick(menu.getAdapter().getView(last,null,menu),last,last);
        return org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    }
}
