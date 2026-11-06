package cn.luyin.player;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Non-dimming keyboard sheet: microphone analysis remains visible and keeps running behind it. */
@androidx.media3.common.util.UnstableApi
public final class PianoDialog extends Dialog {
    private final PianoPlayer player;
    private final PianoKeyboardView keyboard;
    private final android.content.SharedPreferences prefs;
    private final TextView range;
    private int base;
    private int dp(int n){return (int)(n*getContext().getResources().getDisplayMetrics().density+.5f);}
    public PianoDialog(Activity activity){super(activity);requestWindowFeature(Window.FEATURE_NO_TITLE);prefs=activity.getSharedPreferences("piano",0);base=prefs.getInt("base",60);player=new PianoPlayer(activity);PlaybackService.pauseForCapture();
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(8),dp(12),dp(12));body.setBackgroundColor(0xfff5f7f4);
        LinearLayout controls=new LinearLayout(activity);controls.setGravity(Gravity.CENTER_VERTICAL);controls.addView(button("−8度",()->shift(-12)));range=text("",13);range.setGravity(Gravity.CENTER);controls.addView(range,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(button("＋8度",()->shift(12)));controls.addView(button("关闭",this::dismiss));body.addView(controls);
        LinearLayout volumeRow=new LinearLayout(activity);volumeRow.setGravity(Gravity.CENTER_VERTICAL);TextView note=text("钢琴",14);volumeRow.addView(note,new LinearLayout.LayoutParams(dp(100),dp(42)));SeekBar volume=new SeekBar(activity);volume.setContentDescription("钢琴音量");volume.setMax(100);volume.setProgress(prefs.getInt("volume",50));player.volume(volume.getProgress()/100.0);volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){prefs.edit().putInt("volume",b.getProgress()).apply();}public void onProgressChanged(SeekBar b,int n,boolean user){player.volume(n/100.0);}});volumeRow.addView(volume,new LinearLayout.LayoutParams(0,dp(42),1));body.addView(volumeRow);
        keyboard=new PianoKeyboardView(activity,new PianoKeyboardView.Listener(){public void on(int midi){note.setText(PitchDetector.note(PianoKeys.frequency(midi))+" "+String.format(java.util.Locale.ROOT,"%.1f",PianoKeys.frequency(midi))+"Hz");if(!player.noteOn(midi))Toast.makeText(activity,"钢琴音频输出暂不可用",Toast.LENGTH_SHORT).show();}public void off(int midi){player.noteOff(midi);}});keyboard.setBase(base);keyboard.setBackgroundColor(0xff173d32);boolean landscape=activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;body.addView(keyboard,new LinearLayout.LayoutParams(-1,dp(landscape?140:220)));setContentView(body);updateRange();setOnDismissListener(d->{keyboard.releaseAll();player.stop();});
    }
    private TextView text(String value,int size){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(0xff193c33);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button button(String title,Runnable action){Button b=new Button(getContext());b.setText(title);b.setTextSize(12);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setOnClickListener(v->action.run());b.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(48)));return b;}
    private void shift(int delta){base=Math.max(24,Math.min(84,base+delta));keyboard.setBase(base);prefs.edit().putInt("base",base).apply();updateRange();}
    private void updateRange(){range.setText(PitchDetector.note(PianoKeys.frequency(base))+" – "+PitchDetector.note(PianoKeys.frequency(base+23)));}
    @Override public void show(){super.show();if(getWindow()!=null){getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().setGravity(Gravity.BOTTOM);getWindow().setLayout(-1,-2);}}
}
