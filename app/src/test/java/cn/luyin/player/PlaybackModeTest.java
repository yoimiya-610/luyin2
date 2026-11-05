package cn.luyin.player;

import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@androidx.media3.common.util.UnstableApi
public class PlaybackModeTest {
    @Test public void shuffleDoesNotChangeSavedQueueOrder(){
        ExoPlayer p=new ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build();try{
            p.setMediaItems(java.util.List.of(new MediaItem.Builder().setMediaId("a").setUri("file:///a.wav").build(),new MediaItem.Builder().setMediaId("b").setUri("file:///b.wav").build(),new MediaItem.Builder().setMediaId("c").setUri("file:///c.wav").build()));
            p.setShuffleModeEnabled(true);assertTrue(p.getShuffleModeEnabled());assertEquals("a",p.getMediaItemAt(0).mediaId);assertEquals("b",p.getMediaItemAt(1).mediaId);assertEquals("c",p.getMediaItemAt(2).mediaId);
            p.setRepeatMode(Player.REPEAT_MODE_ONE);p.setShuffleModeEnabled(false);assertEquals(Player.REPEAT_MODE_ONE,p.getRepeatMode());assertFalse(p.getShuffleModeEnabled());
        }finally{p.release();}
    }
    @Test public void playbackServiceRestoresShufflePreference(){
        android.content.Context app=RuntimeEnvironment.getApplication();app.getSharedPreferences("playback",0).edit().putBoolean("shuffle",true).putBoolean("repeat",false).commit();
        org.robolectric.android.controller.ServiceController<PlaybackService> controller=Robolectric.buildService(PlaybackService.class).create();PlaybackService service=controller.get();
        try{Player p=service.onGetSession(null).getPlayer();assertTrue(p.getShuffleModeEnabled());assertEquals(Player.REPEAT_MODE_OFF,p.getRepeatMode());p.setShuffleModeEnabled(false);}finally{controller.destroy();}
        assertFalse(app.getSharedPreferences("playback",0).getBoolean("shuffle",true));
    }
}
