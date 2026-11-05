package cn.luyin.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private static final int LIBRARY=0,STUDIO=1,PLAYER=2,PRESETS=3;
    private final int BG=0xfff5f7f4,INK=0xff193c33,TEAL=0xff21785c,MUTED=0xff64746a,FOREST=0xff153f34,MINT=0xffb4edc6;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Library library;
    private List<Recording> catalog=new ArrayList<>(),queue=new ArrayList<>(),visibleCatalog=new ArrayList<>();
    private List<Preset> savedPresets=new ArrayList<>();
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private MediaController player;
    private ListenableFuture<MediaController> future;
    private TextView status,now,clock,selection,pageTitle,pageSubtitle,miniTitle,miniSubtitle;
    private TextView captureSummary,captureDetail,studioTimer,studioPhase,studioName,studioCount,studioTotal;
    private TextView exportBanner;
    private Button play,repeat,captureToggle,captureFinish,miniPlay;
    private SeekBar seek;
    private EditText search;
    private LinearLayout filters,selectionBar,miniPlayer,studioClips;
    private final View[] pages=new View[4];
    private final LinearLayout[] navItems=new LinearLayout[4];
    private final IconView[] navIcons=new IconView[4];
    private final TextView[] navLabels=new TextView[4];
    private RecordingAdapter libraryAdapter,queueAdapter;
    private PresetAdapter presetAdapter;
    private WaveformView waveformView;
    private int page=LIBRARY;
    private String captureFingerprint="",studioFingerprint=null;
    private Recording pendingRename;
    private String pendingRenameTitle;
    private String category="全部",treeSource=SourceRules.RECORDER;
    private boolean queueView=false,scanning=false,dragging=false,destroyed=false,exporting=false;
    private final Runnable refresh=()->scan();
    private final android.database.ContentObserver observer=new android.database.ContentObserver(handler){@Override public void onChange(boolean selfChange){handler.removeCallbacks(refresh);handler.postDelayed(refresh,1200);}};
    private final Runnable ticker=new Runnable(){public void run(){updatePlayer();updateCapture();updateExport();handler.postDelayed(this,250);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);library=new Library(this);catalog=library.load("catalog");queue=library.load("queue");ExportJob.restoreInterrupted(this);
        if(state!=null){treeSource=state.getString("treeSource",SourceRules.RECORDER);category=state.getString("category","全部");page=state.getInt("page",state.getBoolean("queueView")?PLAYER:LIBRARY);ArrayList<String> saved=state.getStringArrayList("selected");if(saved!=null)selected.addAll(saved);}
        if(state==null&&"studio".equals(getIntent().getStringExtra("page")))page=STUDIO;
        if(state==null&&"player".equals(getIntent().getStringExtra("page")))page=PLAYER;
        getIntent().removeExtra("page");
        try{String row=getPreferences(0).getString("renameRow",null);if(row!=null){pendingRename=Recording.from(new org.json.JSONObject(row));pendingRenameTitle=getPreferences(0).getString("renameTitle",null);}}catch(Exception ignored){}
        buildUi();connect();render();scan();handleIncoming(getIntent());
        if(!CaptureService.running)worker.execute(()->{try{String message=CaptureService.recoverInterrupted(this);if(!message.isEmpty())runOnUiThread(()->{if(!destroyed){toast(message);scan();}});}catch(Exception e){runOnUiThread(()->{if(!destroyed)toast("上次录音恢复失败，请保留数据并重试");});}});
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("tnum");return v;}
    private TextView bold(String value,int size,int color){TextView v=text(value,size,color);v.setTypeface(null,Typeface.BOLD);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout line(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void surface(View v,int color,int radius){GradientDrawable d=background(color,radius);if(color==Color.WHITE)d.setStroke(dp(1),0xffe5ebe4);v.setBackground(d);}
    private void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTextColor(TEAL);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),0,dp(12),0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2221785c),background(0xffe6eee5,14),null));b.setOnClickListener(v->action.run());return b;
    }
    private Button primary(String label,Runnable action){Button b=button(label,action);b.setTextColor(Color.WHITE);b.setTypeface(null,Typeface.BOLD);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),background(TEAL,18),null));return b;}
    private void equally(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),0,dp(3),0);row.addView(v,p);}
    private View iconButton(int kind,String label,Runnable action){
        FrameLayout frame=new FrameLayout(this);IconView icon=new IconView(this,kind,TEAL);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);frame.addView(icon,p);frame.setContentDescription(label);frame.setFocusable(true);frame.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2221785c),background(Color.TRANSPARENT,18),null));frame.setOnClickListener(v->action.run());frame.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return frame;
    }
    private LinearLayout padded(){LinearLayout v=column();v.setPadding(dp(20),0,dp(20),0);return v;}
    private ListView list(){ListView v=new ListView(this);v.setDivider(null);v.setClipToPadding(false);v.setPadding(0,dp(8),0,dp(16));v.setSelector(android.R.color.transparent);return v;}
    private View emptyList(ListView list,String title,String message){
        FrameLayout frame=new FrameLayout(this);frame.addView(list,new FrameLayout.LayoutParams(-1,-1));LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(28),dp(24),dp(28));
        IconView icon=new IconView(this,IconView.WAVE,0xff9cb0a0);empty.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));gap(empty,16);TextView h=bold(title,19,INK);h.setGravity(Gravity.CENTER);empty.addView(h);gap(empty,10);TextView body=text(message,13,MUTED);body.setGravity(Gravity.CENTER);body.setLineSpacing(dp(4),1);empty.addView(body);frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);return frame;
    }
    private void buildUi(){
        LinearLayout root=column();root.setBackgroundColor(BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());top=b.top;bottom=b.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}v.setPadding(0,top,0,bottom);return insets;});
        LinearLayout header=line();header.setPadding(dp(20),dp(8),dp(12),dp(12));LinearLayout titles=column();TextView brand=text("录音随听  /  LOCAL AUDIO",10,TEAL);brand.setLetterSpacing(.1f);titles.addView(brand);gap(titles,5);pageTitle=bold("录音库",28,INK);titles.addView(pageTitle);pageSubtitle=text("整理声音，按你的顺序听。",12,MUTED);gap(titles,4);titles.addView(pageSubtitle);header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));header.addView(iconButton(IconView.MORE,"当前页面选项",this::pageMenu));root.addView(header);
        exportBanner=text("",12,TEAL);exportBanner.setPadding(dp(20),dp(8),dp(20),dp(8));exportBanner.setBackgroundColor(0xffe7f0e4);exportBanner.setVisibility(View.GONE);root.addView(exportBanner);
        FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        pages[LIBRARY]=buildLibraryPage();pages[STUDIO]=buildStudioPage();pages[PLAYER]=buildPlayerPage();pages[PRESETS]=buildPresetPage();for(View v:pages)content.addView(v,new FrameLayout.LayoutParams(-1,-1));
        miniPlayer=line();miniPlayer.setPadding(dp(20),dp(8),dp(16),dp(8));surface(miniPlayer,Color.WHITE,0);
        IconView miniIcon=new IconView(this,IconView.WAVE,TEAL);miniPlayer.addView(miniIcon,new LinearLayout.LayoutParams(dp(24),dp(30)));LinearLayout miniInfo=column();miniInfo.setPadding(dp(14),0,dp(8),0);miniTitle=bold("选几段录音，开始随听",13,INK);miniTitle.setSingleLine(true);miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);miniInfo.addView(miniTitle);miniSubtitle=text("点击打开播放器",11,MUTED);miniInfo.addView(miniSubtitle);miniInfo.setOnClickListener(v->showPage(PLAYER));miniPlayer.addView(miniInfo,new LinearLayout.LayoutParams(0,-2,1));miniPlay=button("播放",this::togglePlay);miniPlayer.addView(miniPlay,new LinearLayout.LayoutParams(dp(64),dp(48)));root.addView(miniPlayer);
        LinearLayout nav=line();nav.setPadding(dp(8),dp(4),dp(8),dp(3));nav.setBackgroundColor(Color.WHITE);String[] labels={"录音库","录音室","播放","预设"};int[] icons={IconView.LIBRARY,IconView.MIC,IconView.PLAY,IconView.PRESETS};
        for(int i=0;i<4;i++){final int target=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);navIcons[i]=new IconView(this,icons[i],MUTED);item.addView(navIcons[i],new LinearLayout.LayoutParams(dp(23),dp(23)));gap(item,3);navLabels[i]=text(labels[i],11,MUTED);item.addView(navLabels[i]);item.setContentDescription(labels[i]+"页面");item.setFocusable(true);item.setOnClickListener(v->showPage(target));navItems[i]=item;nav.addView(item,new LinearLayout.LayoutParams(0,dp(58),1));}root.addView(nav);
    }
    private View buildLibraryPage(){
        LinearLayout body=padded();LinearLayout heading=line();status=text("正在整理本地录音…",12,MUTED);heading.addView(status,new LinearLayout.LayoutParams(0,-2,1));heading.addView(button("＋ 导入",this::chooseFiles));body.addView(heading);gap(body,12);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索录音名称");search.setPadding(dp(16),0,dp(16),0);surface(search,Color.WHITE,16);body.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){renderRows();}public void afterTextChanged(android.text.Editable s){}});
        gap(body,12);HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);filters=line();for(String c:new String[]{"全部",SourceRules.APP,SourceRules.RECORDER,SourceRules.CALL,SourceRules.OTHER}){Button chip=button(c,()->{category=c;renderRows();});chip.setTag(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.rightMargin=dp(6);filters.addView(chip,p);}horizontal.addView(filters);body.addView(horizontal);
        selectionBar=line();selection=text("",12,TEAL);selectionBar.addView(selection,new LinearLayout.LayoutParams(0,-2,1));selectionBar.addView(button("选项",this::showActions));body.addView(selectionBar);
        ListView list=list();libraryAdapter=new RecordingAdapter(false);list.setAdapter(libraryAdapter);body.addView(emptyList(list,"声音，从这里开始","点右上角选项扫描或授权录音文件夹，\n也可以导入音频，或前往录音室开始录制。"),new LinearLayout.LayoutParams(-1,0,1));return body;
    }
    private View buildStudioPage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout body=padded();body.setPadding(dp(20),dp(4),dp(20),dp(24));scroll.addView(body);
        LinearLayout hero=column();hero.setPadding(dp(20),dp(18),dp(20),dp(16));surface(hero,FOREST,28);
        studioPhase=text("准备就绪",12,MINT);hero.addView(studioPhase);gap(hero,12);studioTimer=bold("00:00",42,Color.WHITE);studioTimer.setGravity(Gravity.CENTER);hero.addView(studioTimer);studioName=text("开始一段新的声音记录",12,0xffa7c5b6);studioName.setGravity(Gravity.CENTER);hero.addView(studioName);
        gap(hero,10);waveformView=new WaveformView(this);hero.addView(waveformView,new LinearLayout.LayoutParams(-1,dp(112)));gap(hero,4);TextView waveHint=text("实时声音波形  ·  最近 12 秒有效录音",10,0xff96b4a8);waveHint.setGravity(Gravity.CENTER);hero.addView(waveHint);body.addView(hero);
        gap(body,16);LinearLayout stats=line();studioCount=bold("0 段已保存",14,INK);studioTotal=text("累计 00:00",13,MUTED);stats.addView(studioCount,new LinearLayout.LayoutParams(0,-2,1));stats.addView(studioTotal);body.addView(stats);gap(body,12);
        captureSummary=text("暂停即保存一段，结束自动生成预设队列。",12,TEAL);captureSummary.setLineSpacing(dp(3),1);body.addView(captureSummary);captureDetail=text("",12,MUTED);captureDetail.setLineSpacing(dp(3),1);body.addView(captureDetail);gap(body,16);
        captureToggle=primary("开始长录音",this::toggleCapture);body.addView(captureToggle,new LinearLayout.LayoutParams(-1,dp(60)));gap(body,10);captureFinish=button("结束并保存整组队列",this::confirmFinish);body.addView(captureFinish,new LinearLayout.LayoutParams(-1,dp(48)));gap(body,24);
        LinearLayout section=line();section.addView(bold("本次录音片段",17,INK),new LinearLayout.LayoutParams(0,-2,1));section.addView(iconButton(IconView.EXPORT,"导出本次已保存片段",()->{CaptureJournal s=CaptureJournal.read(this);if(s!=null)beginExport(s.rows);else toast("还没有已保存的片段");}));body.addView(section);studioClips=column();body.addView(studioClips);return scroll;
    }
    private View buildPlayerPage(){
        LinearLayout body=padded();ListView list=list();LinearLayout header=column();
        LinearLayout hero=column();hero.setPadding(dp(22),dp(22),dp(22),dp(20));surface(hero,Color.WHITE,26);
        LinearLayout caption=line();caption.addView(text("正在播放",11,TEAL),new LinearLayout.LayoutParams(0,-2,1));repeat=button("顺序播放",()->{if(player!=null){player.setRepeatMode(player.getRepeatMode()==Player.REPEAT_MODE_ONE?Player.REPEAT_MODE_OFF:Player.REPEAT_MODE_ONE);updatePlayer();}});caption.addView(repeat);hero.addView(caption);gap(hero,16);
        now=bold("选几段录音，开始随听",23,INK);now.setMaxLines(3);hero.addView(now);gap(hero,18);seek=new SeekBar(this);seek.setMax(1000);seek.setProgressTintList(ColorStateList.valueOf(TEAL));hero.addView(seek,new LinearLayout.LayoutParams(-1,dp(36)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*b.getProgress()/1000);dragging=false;}public void onProgressChanged(SeekBar b,int p,boolean from){}});
        clock=text("00:00 / 00:00",12,MUTED);hero.addView(clock);gap(hero,14);LinearLayout controls=line();controls.setGravity(Gravity.CENTER);controls.addView(iconButton(IconView.PREVIOUS,"上一首",()->{if(player!=null)player.seekToPreviousMediaItem();}));play=primary("播放",this::togglePlay);LinearLayout.LayoutParams playP=new LinearLayout.LayoutParams(dp(120),dp(56));playP.setMargins(dp(20),0,dp(20),0);controls.addView(play,playP);controls.addView(iconButton(IconView.NEXT,"下一首",()->{if(player!=null)player.seekToNextMediaItem();}));hero.addView(controls);header.addView(hero);gap(header,16);
        LinearLayout section=line();section.addView(bold("接下来播放",17,INK),new LinearLayout.LayoutParams(0,-2,1));section.addView(button("管理队列",this::showActions));header.addView(section);TextView hint=text("点片段播放，点更多调整顺序或导出。",12,MUTED);header.addView(hint);gap(header,8);
        list.addHeaderView(header,null,false);queueAdapter=new RecordingAdapter(true);list.setAdapter(queueAdapter);body.addView(list,new LinearLayout.LayoutParams(-1,-1));return body;
    }
    private View buildPresetPage(){
        LinearLayout body=padded();LinearLayout intro=line();TextView note=text("把常听的顺序，留给下一次。",12,MUTED);intro.addView(note,new LinearLayout.LayoutParams(0,-2,1));intro.addView(button("＋ 保存当前",this::saveQueuePreset));body.addView(intro);gap(body,12);
        ListView list=list();presetAdapter=new PresetAdapter();list.setAdapter(presetAdapter);body.addView(emptyList(list,"留下一组声音","结束长录音会自动保存整组片段，\n也可以把当前播放队列保存为预设。"),new LinearLayout.LayoutParams(-1,0,1));return body;
    }
    private void showPage(int target){page=Math.max(LIBRARY,Math.min(PRESETS,target));queueView=page==PLAYER;if(page!=LIBRARY){search.clearFocus();android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);}render();if(page==STUDIO)updateCapture();}
    private void pageMenu(){
        if(page==LIBRARY)new AlertDialog.Builder(this).setTitle("录音库").setItems(new String[]{"扫描本地录音","授权录音文件夹","导入音频","选择与排序","使用帮助"},(d,w)->{if(w==0)requestScan();else if(w==1)chooseTree();else if(w==2)chooseFiles();else if(w==3)showActions();else help();}).show();
        else if(page==PLAYER)showActions();else if(page==PRESETS)new AlertDialog.Builder(this).setItems(new String[]{"保存当前播放队列","使用帮助"},(d,w)->{if(w==0)saveQueuePreset();else help();}).show();else help();
    }
    private void connect(){
        future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        future.addListener(()->{try{player=future.get();if(destroyed)return;player.addListener(new Player.Listener(){
            @Override public void onEvents(Player p,Player.Events events){updatePlayer();if(events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION))renderRows();}
            @Override public void onPlayerError(PlaybackException e){toast("这段音频无法播放：文件可能已移动、授权失效或格式不支持");}
        });updatePlayer();}catch(Exception e){toast("播放器连接失败，请重开应用");}},getMainExecutor());
    }
    private void updatePlayer(){if(player==null||destroyed)return;
        MediaItem item=player.getCurrentMediaItem();now.setText(item==null?"选几段录音，开始随听":item.mediaMetadata.title);
        play.setText(player.isPlaying()?"暂停":"播放");miniPlay.setText(player.isPlaying()?"暂停":"播放");miniTitle.setText(item==null?"选几段录音，开始随听":item.mediaMetadata.title);miniSubtitle.setText(item==null?"点击打开播放器":player.isPlaying()?"正在播放 · 点击查看队列":"已暂停 · 点击查看队列");repeat.setText(player.getRepeatMode()==Player.REPEAT_MODE_ONE?"单曲循环 ✓":"顺序播放");
        long duration=Math.max(0,player.getDuration()),position=Math.max(0,player.getCurrentPosition());clock.setText(time(position)+" / "+time(duration));
        seek.setEnabled(duration>0);if(!dragging)seek.setProgress(duration>0?(int)(position*1000/duration):0);
    }
    private static String time(long ms){long s=Math.max(0,ms/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    private boolean canPlay(){CaptureJournal s=CaptureJournal.read(this);if(CaptureService.running&&s!=null&&s.active!=null){toast("请先暂停录音再播放，避免把扬声器声音录入");return false;}return true;}
    private void togglePlay(){if(player==null){toast("播放器正在连接");return;}if(player.isPlaying()){player.pause();return;}if(!canPlay())return;if(player.getMediaItemCount()==0){if(queue.isEmpty()){toast("先勾选录音并加入队列");return;}applyQueue(0,true);return;}if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(0,0);player.prepare();player.play();}

    private void render(){
        if(pageTitle==null)return;queueView=page==PLAYER;
        String[] names={"录音库","录音室","随听播放器","预设队列"};String[] subtitles={"整理声音，按你的顺序听。","暂停留一段，继续下一段。","让片段连起来，让声音继续。","保存顺序，随时重新开始。"};pageTitle.setText(names[page]);pageSubtitle.setText(subtitles[page]);
        for(int i=0;i<pages.length;i++){pages[i].setVisibility(i==page?View.VISIBLE:View.GONE);navIcons[i].setColor(i==page?TEAL:MUTED);navLabels[i].setTextColor(i==page?TEAL:MUTED);navLabels[i].setTypeface(null,i==page?Typeface.BOLD:Typeface.NORMAL);surface(navItems[i],i==page?0xffe8f1e6:Color.TRANSPARENT,18);navItems[i].setSelected(i==page);}
        miniPlayer.setVisibility(page==LIBRARY||page==PRESETS?View.VISIBLE:View.GONE);savedPresets=library.presets();presetAdapter.notifyDataSetChanged();renderRows();
    }
    private void updateSelection(){selection.setText("已选 "+selected.size()+" 段 · 按勾选顺序");selectionBar.setVisibility(selected.isEmpty()?View.GONE:View.VISIBLE);}
    private void renderRows(){
        if(libraryAdapter==null)return;String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);visibleCatalog=new ArrayList<>();
        for(Recording r:catalog)if((category.equals("全部")||r.source.equals(category))&&r.name.toLowerCase(Locale.ROOT).contains(q))visibleCatalog.add(r);
        for(int i=0;i<filters.getChildCount();i++){Button b=(Button)filters.getChildAt(i);boolean active=category.equals(b.getTag());b.setTextColor(active?Color.WHITE:TEAL);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2221785c),background(active?TEAL:0xffe8eee6,14),null));b.setSelected(active);}
        updateSelection();libraryAdapter.notifyDataSetChanged();queueAdapter.notifyDataSetChanged();
    }
    private final class RecordingAdapter extends BaseAdapter {
        private final boolean isQueue;
        RecordingAdapter(boolean queue){isQueue=queue;}
        public int getCount(){return isQueue?Math.max(1,queue.size()):visibleCatalog.size();}
        public Recording getItem(int position){return (isQueue?queue:visibleCatalog).get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View recycled,ViewGroup parent){
            if(isQueue&&queue.isEmpty()){TextView empty=text("队列还没有片段\n\n去录音库勾选，或从预设载入一组声音。",14,MUTED);empty.setPadding(dp(14),dp(28),dp(14),dp(28));empty.setOnClickListener(v->showPage(LIBRARY));return empty;}
            return recordingRow(getItem(position),position,isQueue,false);
        }
    }
    private View recordingRow(Recording r,int index,boolean inQueue,boolean studio){
        LinearLayout wrapper=column();wrapper.setPadding(0,0,0,dp(9));LinearLayout card=line();card.setPadding(dp(6),dp(10),dp(4),dp(10));surface(card,Color.WHITE,18);wrapper.addView(card,new LinearLayout.LayoutParams(-1,-2));
        if(!inQueue&&!studio){CheckBox check=new CheckBox(this);check.setButtonTintList(ColorStateList.valueOf(TEAL));check.setChecked(selected.contains(r.uri));check.setContentDescription("选择 "+r.name);check.setOnCheckedChangeListener((b,checked)->{if(checked)selected.add(r.uri);else selected.remove(r.uri);updateSelection();});card.addView(check,new LinearLayout.LayoutParams(dp(42),dp(48)));}
        else{TextView order=bold(String.format(Locale.ROOT,"%02d",index+1),14,TEAL);order.setGravity(Gravity.CENTER);card.addView(order,new LinearLayout.LayoutParams(dp(42),dp(48)));}
        LinearLayout info=column();info.setPadding(dp(3),dp(3),dp(4),dp(3));TextView title=bold(r.name,14,INK);title.setMaxLines(2);info.addView(title);gap(info,6);
        boolean current=inQueue&&player!=null&&player.getCurrentMediaItemIndex()==index;
        TextView meta=text((current?"正在播放 · ":"")+r.source+"  ·  "+(r.duration>0?time(r.duration):"时长待读取"),11,current?TEAL:MUTED);info.addView(meta);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        info.setOnClickListener(v->{if(inQueue){if(player!=null&&canPlay()){player.seekTo(index,0);player.prepare();player.play();}}else recordingActions(r);});
        info.setOnLongClickListener(v->{renameDialog(r);return true;});card.addView(iconButton(IconView.MORE,r.name+"的更多操作",()->{if(inQueue)queueItemActions(r,index);else recordingActions(r);}));return wrapper;
    }
    private void recordingActions(Recording r){
        new AlertDialog.Builder(this).setTitle(r.name).setItems(new String[]{"加入播放队尾","导出录音","重命名","修正来源","文件信息"},(d,w)->{
            if(w==0){queue.add(r);persistAndAppend(r);render();toast("已加入播放队尾");}else if(w==1)beginExport(Collections.singletonList(r));else if(w==2)renameDialog(r);else if(w==3)sourceDialog("修正录音来源",source->{library.override(r,source);library.save("catalog",catalog);renderRows();});else new AlertDialog.Builder(this).setTitle(r.name).setMessage("来源："+r.source+"\n位置："+r.path+"\n时长："+time(r.duration)).setPositiveButton("关闭",null).show();
        }).show();
    }
    private void queueItemActions(Recording r,int index){new AlertDialog.Builder(this).setTitle(r.name).setItems(new String[]{"播放这段","上移","下移","导出录音","重命名","移出队列"},(d,w)->{if(w==0){if(player!=null&&canPlay()){player.seekTo(index,0);player.prepare();player.play();}}else if(w==1)move(index,-1);else if(w==2)move(index,1);else if(w==3)beginExport(Collections.singletonList(r));else if(w==4)renameDialog(r);else remove(index);}).show();}
    private final class PresetAdapter extends BaseAdapter {
        public int getCount(){return savedPresets.size();}public Preset getItem(int i){return savedPresets.get(i);}public long getItemId(int i){return i;}
        public View getView(int i,View old,ViewGroup parent){Preset p=getItem(i);LinearLayout wrapper=column();wrapper.setPadding(0,0,0,dp(12));LinearLayout card=line();card.setPadding(dp(16),dp(18),dp(6),dp(18));surface(card,Color.WHITE,22);
            IconView icon=new IconView(MainActivity.this,IconView.PRESETS,TEAL);card.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(34)));LinearLayout info=column();info.setPadding(dp(16),0,dp(8),0);TextView title=bold(p.title,15,INK);title.setMaxLines(2);info.addView(title);gap(info,7);info.addView(text(p.rows.size()+" 段  ·  "+new java.text.SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(p.created)),11,MUTED));card.addView(info,new LinearLayout.LayoutParams(0,-2,1));info.setOnClickListener(v->presetActions(p));card.addView(iconButton(IconView.MORE,p.title+"的操作",()->presetActions(p)));wrapper.addView(card);return wrapper;}
    }


    private List<Recording> selectedRows(){List<Recording> chosen=new ArrayList<>();for(String uri:selected)for(Recording r:catalog)if(uri.equals(r.uri)){chosen.add(r);break;}return chosen;}
    private void showActions(){
        if(page==PLAYER){new AlertDialog.Builder(this).setTitle("播放队列 · "+queue.size()+" 段").setItems(new String[]{"从第一首开始播放","保存为预设","导出整个队列","清空队列"},(d,w)->{if(w==0)applyQueue(0,true);else if(w==1)saveQueuePreset();else if(w==2)beginExport(queue);else new AlertDialog.Builder(this).setMessage("清空播放队列？音频文件会保留。").setPositiveButton("清空",(a,b)->{queue.clear();library.save("queue",queue);if(player!=null)player.clearMediaItems();render();}).setNegativeButton("取消",null).show();}).show();return;}
        new AlertDialog.Builder(this).setTitle("录音选择与排序").setItems(new String[]{"将所选加入队尾","用所选替换队列并播放","导出所选录音","选择当前筛选全部","取消全部选择","按名称排序","按时间排序（最新在前）"},(d,w)->{
            if(w<=1){if(player==null){toast("播放器正在连接");return;}List<Recording> chosen=selectedRows();if(chosen.isEmpty()){toast("请先勾选录音");return;}if(w==1&&!canPlay())return;
                if(w==1){queue.clear();queue.addAll(chosen);applyQueue(0,true);}else{queue.addAll(chosen);library.save("queue",queue);List<MediaItem> items=new ArrayList<>();for(Recording r:chosen)items.add(r.item());player.addMediaItems(items);}selected.clear();showPage(PLAYER);
            }else if(w==2)beginExport(selectedRows());else if(w==3){for(Recording r:visibleCatalog)selected.add(r.uri);renderRows();}else if(w==4){selected.clear();renderRows();}else{if(w==5)catalog.sort((a,b)->a.name.compareToIgnoreCase(b.name));else catalog.sort((a,b)->Long.compare(b.date,a.date));renderRows();}
        }).show();
    }

    private void persistAndAppend(Recording r){if(player==null){queue.remove(queue.size()-1);toast("播放器正在连接，请稍后重试");return;}library.save("queue",queue);player.addMediaItem(r.item());}
    private void applyQueue(int index,boolean start){if(queue.isEmpty()){toast("队列为空");return;}if(player==null){toast("播放器正在连接");return;}if(start&&!canPlay())return;List<MediaItem> items=new ArrayList<>();for(Recording r:queue)items.add(r.item());library.save("queue",queue);player.setMediaItems(items,index,0);player.prepare();if(start)player.play();render();}
    private void move(int i,int delta){int target=i+delta;if(target<0||target>=queue.size())return;if(player==null){toast("播放器正在连接");return;}Collections.swap(queue,i,target);player.moveMediaItem(i,target);library.save("queue",queue);render();}
    private void remove(int i){if(player==null)return;queue.remove(i);player.removeMediaItem(i);library.save("queue",queue);render();}
    private String permission(){return Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;}
    private void requestScan(){if(checkSelfPermission(permission())!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{permission()},10);else scan();}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] results){super.onRequestPermissionsResult(request,p,results);if(request==11){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)sendCapture(CaptureService.START);else permissionHelp("录音需要麦克风权限，请在系统设置中允许后重试。");return;}scan();if(results.length>0&&results[0]!=PackageManager.PERMISSION_GRANTED)permissionHelp("未获得音频读取权限。仍可授权文件夹或导入文件；如需自动扫描，请在系统设置中允许“音乐和音频”权限。");}
    private void permissionHelp(String message){new AlertDialog.Builder(this).setMessage(message).setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("稍后",null).show();}
    private void scan(){if(scanning)return;scanning=true;status.setText("正在扫描本地音频…");worker.execute(()->{List<String> warnings=new ArrayList<>();List<Recording> result=library.scan(warnings);runOnUiThread(()->{if(destroyed)return;scanning=false;catalog=result;status.setText("发现 "+catalog.size()+" 个音频 · 全部在本机处理");render();if(!warnings.isEmpty())toast(String.join("\n",warnings));});});}
    private interface SourceAction{void run(String source);}
    private void sourceDialog(String title,SourceAction action){String[] choices={SourceRules.APP,SourceRules.RECORDER,SourceRules.CALL,SourceRules.OTHER};new AlertDialog.Builder(this).setTitle(title).setItems(choices,(d,w)->action.run(choices[w])).setNegativeButton("取消",null).show();}
    private void chooseTree(){sourceDialog("这个文件夹存放哪类音频？",source->{treeSource=source;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,20);});}
    private void chooseFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,21);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==40){if(result==RESULT_OK&&data!=null&&data.getData()!=null)performExport(data.getData());return;}if(request==30){if(result==RESULT_OK&&pendingRename!=null&&pendingRenameTitle!=null)renameFile(pendingRename,pendingRenameTitle,false);else toast("未授权修改文件，可选择仅修改软件内名称");return;}if(result!=RESULT_OK||data==null)return;
        if(request==20&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);if((data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION);library.addTree(data.getData(),treeSource);scan();}catch(Exception e){toast("文件夹授权失败，请重新选择");}}
        else if(request==21){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());importUris(uris);}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if("studio".equals(intent.getStringExtra("page")))showPage(STUDIO);else if("player".equals(intent.getStringExtra("page")))showPage(PLAYER);intent.removeExtra("page");handleIncoming(intent);}
    private void handleIncoming(Intent intent){List<Uri> uris=new ArrayList<>();if(Intent.ACTION_SEND.equals(intent.getAction())){Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(uri!=null)uris.add(uri);}else if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){ArrayList<Uri> all=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(all!=null)uris.addAll(all);}if(!uris.isEmpty()){intent.setAction(Intent.ACTION_MAIN);importUris(uris);}}
    private void importUris(List<Uri> uris){if(uris.isEmpty())return;sourceDialog("导入音频的来源",source->{status.setText("正在复制导入音频，请稍候…");worker.execute(()->{int success=0,failed=0;for(Uri uri:uris)try{library.importFile(uri,source);success++;}catch(Exception e){failed++;}final String message="已导入 "+success+" 段"+(failed>0?"，失败 "+failed+" 段（授权、空间或格式问题）":"");runOnUiThread(()->{if(!destroyed){toast(message);scan();}});});});}
    private interface NameAction{void accept(String name) throws Exception;}
    private void askName(String title,String initial,NameAction action){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setSelectAllOnFocus(true);input.setPadding(dp(20),dp(12),dp(20),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String value=NameRules.clean(input.getText().toString());action.accept(value);dialog.dismiss();}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();
    }
    private void saveQueuePreset(){
        if(queue.isEmpty()){toast("队列为空，请先选曲");return;}
        askName("保存当前队列为预设","我的队列 "+new java.text.SimpleDateFormat("MMdd-HHmm",Locale.ROOT).format(new Date()),name->{
            library.putPreset(new Preset(UUID.randomUUID().toString(),name,System.currentTimeMillis(),new ArrayList<>(queue)));render();toast("预设队列已保存，可从“预设队列”重新载入");
        });
    }
    private void showPresets(){showPage(PRESETS);}

    private void presetActions(Preset preset){
        new AlertDialog.Builder(this).setTitle(preset.title).setItems(new String[]{"载入队列","载入并播放","追加到当前队尾","导出整组录音","重命名预设","删除预设（保留录音）"},(d,w)->{
            if(w<=2){if(player==null){toast("播放器正在连接");return;}if(w==1&&!canPlay())return;
                if(w<2){queue.clear();queue.addAll(preset.rows);applyQueue(0,w==1);}else{queue.addAll(preset.rows);library.save("queue",queue);List<MediaItem> items=new ArrayList<>();for(Recording r:preset.rows)items.add(r.item());player.addMediaItems(items);}showPage(PLAYER);toast("已载入 "+preset.rows.size()+" 段录音");
            }else if(w==3)beginExport(preset.rows);else if(w==4)askName("重命名预设",preset.title,name->{preset.title=name;library.putPreset(preset);render();toast("预设已重命名");});
            else new AlertDialog.Builder(this).setMessage("删除“"+preset.title+"”？录音文件会保留。").setPositiveButton("删除",(a,b)->{try{library.deletePreset(preset.id);render();toast("预设已删除");}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("取消",null).show();
        }).setNegativeButton("关闭",null).show();
    }

    private void renameDialog(Recording r){
        askName("重命名录音",r.name,name->new AlertDialog.Builder(this).setTitle("重命名方式")
            .setMessage("修改原文件名会同步更新本软件的队列引用。其他应用的文件可能需要系统授权；也可仅修改本软件显示的名称。")
            .setPositiveButton("修改文件名",(d,w)->renameFile(r,name,true))
            .setNeutralButton("仅软件内名称",(d,w)->{try{library.displayName(r,name);afterRename();}catch(Exception e){toast(e.getMessage());}})
            .setNegativeButton("取消",null).show());
    }
    private void renameFile(Recording r,String title,boolean requestPermission){
        worker.execute(()->{try{library.renameFile(r,title);runOnUiThread(()->{if(!destroyed){afterRename();toast("录音已重命名，队列引用已更新");}});}
            catch(Exception e){runOnUiThread(()->{if(destroyed)return;
                if(requestPermission&&e instanceof SecurityException){try{
                    PendingIntent consent=null;Uri uri=Uri.parse(r.uri);
                    if(Build.VERSION.SDK_INT>=30&&"media".equals(uri.getAuthority()))consent=android.provider.MediaStore.createWriteRequest(getContentResolver(),Collections.singletonList(uri));
                    else if(e instanceof RecoverableSecurityException)consent=((RecoverableSecurityException)e).getUserAction().getActionIntent();
                    if(consent!=null){pendingRename=r;pendingRenameTitle=title;getPreferences(0).edit().putString("renameRow",r.json().toString()).putString("renameTitle",title).apply();startIntentSenderForResult(consent.getIntentSender(),30,null,0,0,0);return;}
                }catch(Exception ignored){}}
                new AlertDialog.Builder(this).setTitle("原文件无法重命名").setMessage("文件可能只读、授权失效或同名文件已存在。可重新授权文件夹，或仅修改软件内名称。\n"+String.valueOf(e.getMessage()))
                    .setPositiveButton("仅修改软件内名称",(d,w)->{try{library.displayName(r,title);afterRename();}catch(Exception failed){toast(failed.getMessage());}}).setNegativeButton("取消",null).show();
            });}
        });
    }
    private void afterRename(){
        queue=library.load("queue");library.save("queue",queue);
        if(player!=null)for(int i=0;i<player.getMediaItemCount();i++){
            MediaItem item=player.getMediaItemAt(i);Recording r=library.resolve(new Recording(item.mediaId,String.valueOf(item.mediaMetadata.title),"",String.valueOf(item.mediaMetadata.artist),0,0));
            if(!r.uri.equals(item.mediaId)||!r.name.contentEquals(item.mediaMetadata.title==null?"":item.mediaMetadata.title))player.replaceMediaItem(i,r.item());
        }
        studioFingerprint=null;scan();render();
    }

    private void recordingDialog(){showPage(STUDIO);}
    private void confirmFinish(){new AlertDialog.Builder(this).setTitle("结束本次长录音？").setMessage("当前片段将保存，本次全部有效片段会按顺序生成预设队列。").setPositiveButton("结束并保存",(d,w)->sendCapture(CaptureService.FINISH)).setNegativeButton("暂不结束",null).show();}

    private void beginExport(List<Recording> input){
        if(input.isEmpty()){toast("请先选择要导出的录音");return;}
        if(exporting||ExportJob.status().running){toast("正在导出，请等待完成");return;}
        final List<Recording> snapshot=new ArrayList<>();for(Recording r:input)snapshot.add(library.resolve(Recording.from(r.json())));
        Runnable choose=()->{
            exporting=true;worker.execute(()->{try{
                RecordingExporter exporter=new RecordingExporter(this);String mime=snapshot.size()==1?exporter.mime(snapshot.get(0)):"application/zip";
                String name=snapshot.size()==1?exporter.name(snapshot.get(0),mime):"录音随听-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".zip";
                runOnUiThread(()->{exporting=false;if(destroyed)return;try{library.save("pendingExport",snapshot);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,40);}catch(Exception e){toast("无法打开系统保存窗口，请检查文件管理器");}});
            }catch(Exception e){runOnUiThread(()->{exporting=false;if(!destroyed)toast("导出准备失败，文件可能已移动或授权失效");});}});
        };
        if(snapshot.size()>1)new AlertDialog.Builder(this).setTitle("导出 "+snapshot.size()+" 段录音").setMessage("将打包为 ZIP，按当前顺序编号并保留各段名称和音频格式。接下来选择保存位置，原文件会保留。").setPositiveButton("选择保存位置",(d,w)->choose.run()).setNegativeButton("取消",null).show();else choose.run();
    }
    private void performExport(Uri destination){
        List<Recording> pending=library.load("pendingExport");if(pending.isEmpty()){toast("导出任务已失效，请重新选择录音");return;}
        for(Recording r:pending)if(destination.equals(Uri.parse(r.uri))){toast("导出位置不能与原文件相同");return;}
        try{if(!ExportJob.start(this,pending,destination)){toast("另一项导出正在进行");return;}library.save("pendingExport",Collections.emptyList());updateExport();}catch(Exception e){toast(e.getMessage());}
    }
    private void updateExport(){ExportJob.Status job=ExportJob.status();exportBanner.setVisibility(job.running?View.VISIBLE:View.GONE);if(job.running)exportBanner.setText(job.message);else if(job.id!=0&&job.id!=getPreferences(0).getLong("exportSeen",0)){getPreferences(0).edit().putLong("exportSeen",job.id).apply();toast(job.message);}}

    private void toggleCapture(){
        CaptureJournal s=CaptureJournal.read(this);
        if(CaptureService.running&&s!=null&&!s.state.equals("finished")){sendCapture(s.active!=null?CaptureService.PAUSE:CaptureService.RESUME);return;}
        List<String> permissions=new ArrayList<>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.RECORD_AUDIO);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!permissions.isEmpty()){requestPermissions(permissions.toArray(new String[0]),11);return;}sendCapture(CaptureService.START);
    }
    private void sendCapture(String action){
        if((CaptureService.START.equals(action)||CaptureService.RESUME.equals(action))&&player!=null)player.pause();
        Intent intent=new Intent(this,CaptureService.class).setAction(action);
        try{if(CaptureService.START.equals(action))startForegroundService(intent);else if(CaptureService.running)startService(intent);else{toast("没有进行中的录音");return;}}catch(Exception e){toast("无法启动录音，请确认麦克风权限并在应用前台重试");}
    }

    private void updateCapture(){
        CaptureJournal s=CaptureJournal.read(this);boolean active=CaptureService.running&&s!=null&&!s.state.equals("finished"),recording=active&&s.active!=null;long current=recording?Math.max(0,SystemClock.elapsedRealtime()-s.activeSince):0;
        studioPhase.setText(recording?"● 正在录音":active?"Ⅱ 已暂停":s!=null&&!s.rows.isEmpty()?"本次已结束":"准备就绪");studioPhase.setTextColor(recording?0xffd3f3bb:MINT);studioTimer.setText(time(recording?current:s==null?0:s.total));studioName.setText(recording?s.active.name:active?"累计有效录音时长":s!=null&&!s.rows.isEmpty()?"本次录音累计时长":"开始一段新的声音记录");
        navLabels[STUDIO].setText(recording?"录音中":"录音室");navIcons[STUDIO].setColor(recording?0xffbc5947:page==STUDIO?TEAL:MUTED);
        studioCount.setText((s==null?0:s.rows.size())+" 段已保存");studioTotal.setText("累计 "+time((s==null?0:s.total)+current));
        String summary=s==null?"暂停即保存一段，结束自动生成预设队列。":active?(recording?"正在录制，暂停后保存当前片段。":"已暂停，继续后将创建新片段。"):(s.message.isEmpty()?"长录音已结束":s.message);
        captureSummary.setText(summary);captureDetail.setText(active&&!s.message.isEmpty()?s.message:recording?"切换页面或熄屏，录音仍会继续。":"录音保存在手机本地，可在片段菜单中导出。");captureToggle.setText(recording?"Ⅱ  暂停并保存片段":active?"●  继续录制下一段":"●  开始长录音");captureFinish.setEnabled(active);captureFinish.setAlpha(active?1f:.45f);
        if(page==STUDIO)waveformView.setSamples(CaptureService.waveformSamples(),recording);
        String fingerprint=s==null?"":s.id+":"+s.rows.size()+":"+s.state;
        if(!fingerprint.equals(studioFingerprint)){studioFingerprint=fingerprint;studioClips.removeAllViews();if(s==null||s.rows.isEmpty()){TextView empty=text("保存后的片段会出现在这里。",12,MUTED);empty.setPadding(0,dp(8),0,dp(16));studioClips.addView(empty);}else{for(int i=Math.max(0,s.rows.size()-6);i<s.rows.size();i++)studioClips.addView(recordingRow(library.resolve(Recording.from(s.rows.get(i).json())),i,false,true));if(s.rows.size()>6)studioClips.addView(button("查看全部本机录音",()->{category=SourceRules.APP;showPage(LIBRARY);}));}}
        if(!fingerprint.equals(captureFingerprint)){boolean had=!captureFingerprint.isEmpty();captureFingerprint=fingerprint;if(had)scan();}
    }


    private void help(){new AlertDialog.Builder(this).setTitle("录音随听 · 使用帮助").setMessage("录音库：搜索、按来源筛选、勾选片段。右上角选项可扫描录音、授权文件夹或选择排序。片段更多菜单提供导出与重命名。\n\n录音室：开始长录音后显示真实麦克风的实时声音波形。暂停立即保存一段，继续创建新段，结束自动保存整组预设。熄屏或离开页面不停止录音。\n\n播放：顺序连续播放或单曲循环。片段更多菜单可上下移动，队列管理可保存预设或导出整组录音。\n\n预设：重新载入常听顺序，或管理每次长录音生成的整组片段。\n\n导出：单段保持原音频格式，多段打包为按顺序编号的 ZIP，通过系统文件选择器保存。不会删除原录音。\n\n小米录音机私有目录无法直接读取时，请从系统录音机分享导入。若 HyperOS 限制后台运行，可在应用省电策略中设为无限制。").setPositiveButton("知道了",null).show();}

    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();if(library!=null&&!scanning)scan();handler.removeCallbacks(ticker);handler.post(ticker);try{getContentResolver().registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,observer);}catch(SecurityException ignored){}}
    @Override protected void onPause(){getContentResolver().unregisterContentObserver(observer);handler.removeCallbacks(refresh);handler.removeCallbacks(ticker);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("page",page);state.putString("treeSource",treeSource);state.putString("category",category);state.putBoolean("queueView",queueView);state.putStringArrayList("selected",new ArrayList<>(selected));super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);worker.shutdown();if(future!=null)MediaController.releaseFuture(future);super.onDestroy();}
}
