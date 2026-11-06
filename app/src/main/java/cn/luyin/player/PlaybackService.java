package cn.luyin.player;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;
    private ExoPlayer player;
    private int consecutiveErrors;
    private static PlaybackService active;
    public static void pauseForCapture(){if(active!=null&&active.player!=null)active.player.pause();}
    public static void removeHidden(){if(active==null||active.player==null)return;Library library=new Library(active);for(int i=active.player.getMediaItemCount()-1;i>=0;i--)if(library.isHidden(active.player.getMediaItemAt(i).mediaId))active.player.removeMediaItem(i);}
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable checkpoint=new Runnable(){public void run(){savePosition();handler.postDelayed(this,5000);}};
    @Override public void onCreate() {
        super.onCreate();
        player=new ExoPlayer.Builder(this).build();
        active=this;
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),true);
        player.setHandleAudioBecomingNoisy(true); player.setWakeMode(C.WAKE_MODE_LOCAL);
        List<Recording> queue=new Library(this).load("queue");List<MediaItem> items=new ArrayList<>();for(Recording r:queue)items.add(r.item());
        android.content.SharedPreferences prefs=getSharedPreferences("playback",0);
        if(!items.isEmpty())player.setMediaItems(items,Math.max(0,Math.min(prefs.getInt("index",0),items.size()-1)),Math.max(0,prefs.getLong("position",0)));
        player.setRepeatMode(prefs.getBoolean("repeat",false)?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);
        player.setShuffleModeEnabled(prefs.getBoolean("shuffle",false));
        player.addListener(new Player.Listener(){
            @Override public void onEvents(Player p,Player.Events events){savePosition();}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY)consecutiveErrors=0;}
            @Override public void onPlayerError(PlaybackException error){handlePlaybackError();}
        });
        PendingIntent intent=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("page","player"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        session=new MediaSession.Builder(this,player).setSessionActivity(intent).build();
        handler.post(checkpoint);
    }
    private void handlePlaybackError(){
        if(!player.getPlayWhenReady())return;
        if(++consecutiveErrors>=player.getMediaItemCount()||!player.hasNextMediaItem()){player.pause();return;}
        player.seekToNextMediaItem();player.prepare();
    }
    private void savePosition(){if(player!=null)getSharedPreferences("playback",0).edit().putInt("index",Math.max(0,player.getCurrentMediaItemIndex())).putLong("position",Math.max(0,player.getCurrentPosition())).putBoolean("repeat",player.getRepeatMode()==Player.REPEAT_MODE_ONE).putBoolean("shuffle",player.getShuffleModeEnabled()).apply();}
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo info){return session;}
    @Override public void onTaskRemoved(Intent rootIntent){if(!player.getPlayWhenReady()||player.getPlaybackState()==Player.STATE_ENDED)stopSelf();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);savePosition();if(active==this)active=null;if(session!=null)session.release();if(player!=null)player.release();super.onDestroy();}
}
