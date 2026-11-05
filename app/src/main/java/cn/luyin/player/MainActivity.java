package cn.luyin.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private final int BG=0xfff4f7f4, INK=0xff173b35, TEAL=0xff197568, MUTED=0xff647970;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Library library;
    private List<Recording> catalog=new ArrayList<>(),queue=new ArrayList<>();
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private MediaController player;
    private ListenableFuture<MediaController> future;
    private LinearLayout rows,filters;
    private TextView status,now,clock,selection;
    private Button play,repeat,libraryTab,queueTab;
    private SeekBar seek;
    private EditText search;
    private TextView captureSummary,captureDetail;
    private Button captureToggle,captureFinish;
    private String captureFingerprint="";
    private Recording pendingRename;
    private String pendingRenameTitle;
    private String category="全部",treeSource=SourceRules.RECORDER;
    private boolean queueView=false,scanning=false,dragging=false,destroyed=false;
    private final Runnable refresh=()->scan();
    private final android.database.ContentObserver observer=new android.database.ContentObserver(handler){@Override public void onChange(boolean selfChange){handler.removeCallbacks(refresh);handler.postDelayed(refresh,1200);}};
    private final Runnable ticker=new Runnable(){public void run(){updatePlayer();updateCapture();handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);library=new Library(this);catalog=library.load("catalog");queue=library.load("queue");
        if(state!=null){treeSource=state.getString("treeSource",SourceRules.RECORDER);category=state.getString("category","全部");queueView=state.getBoolean("queueView");ArrayList<String> saved=state.getStringArrayList("selected");if(saved!=null)selected.addAll(saved);}
        try{String row=getPreferences(0).getString("renameRow",null);if(row!=null){pendingRename=Recording.from(new org.json.JSONObject(row));pendingRenameTitle=getPreferences(0).getString("renameTitle",null);}}catch(Exception ignored){}
        buildUi();connect();render();scan();handleIncoming(getIntent());handler.post(ticker);
        if(!CaptureService.running)worker.execute(()->{try{String message=CaptureService.recoverInterrupted(this);if(!message.isEmpty())runOnUiThread(()->{if(!destroyed){toast(message);scan();}});}catch(Exception e){runOnUiThread(()->toast("上次录音恢复失败，请保留数据并重试"));}});
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout line(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTextColor(TEAL);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(10),0,dp(10),0);b.setOnClickListener(v->action.run());return b;}
    private void equally(LinearLayout row,View v){row.addView(v,new LinearLayout.LayoutParams(0,dp(48),1));}
    private void buildUi(){
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),0,dp(18),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());top=bars.top;bottom=bars.bottom;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}v.setPadding(dp(18),top+dp(12),dp(18),bottom+dp(8));return insets;});
        TextView eyebrow=text("LOCAL AUDIO  /  REDMI",11,TEAL);eyebrow.setLetterSpacing(.12f);root.addView(eyebrow);
        LinearLayout title=line();TextView h=text("录音随听",29,INK);h.setTypeface(null,Typeface.BOLD);title.addView(h,new LinearLayout.LayoutParams(0,dp(52),1));title.addView(button("使用帮助",this::help));root.addView(title);
        status=text("本地录音 · 按你的顺序听",12,MUTED);root.addView(status);
        LinearLayout actions=line();equally(actions,button("扫描录音",this::requestScan));equally(actions,button("授权文件夹",this::chooseTree));equally(actions,button("导入音频",this::chooseFiles));root.addView(actions);
        LinearLayout recordingActions=line();equally(recordingActions,button("● 长录音",this::recordingDialog));equally(recordingActions,button("预设队列",this::showPresets));equally(recordingActions,button("保存队列",this::saveQueuePreset));root.addView(recordingActions);
        captureSummary=text("长录音：暂停即保存一段，结束自动保存队列",11,TEAL);root.addView(captureSummary);
        LinearLayout tabs=line();libraryTab=button("录音库",()->{queueView=false;render();});queueTab=button("播放队列",()->{queueView=true;render();});equally(tabs,libraryTab);equally(tabs,queueTab);root.addView(tabs);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索录音名称");root.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){renderRows();}public void afterTextChanged(android.text.Editable s){}});
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);filters=line();
        for(String c:new String[]{"全部",SourceRules.APP,SourceRules.RECORDER,SourceRules.CALL,SourceRules.OTHER})filters.addView(button(c,()->{category=c;renderRows();}));horizontal.addView(filters);root.addView(horizontal);
        LinearLayout tools=line();selection=text("勾选录音，按选择顺序加入队列",11,MUTED);tools.addView(selection,new LinearLayout.LayoutParams(0,-2,1));tools.addView(button("操作 ▾",this::showActions));root.addView(tools);
        ScrollView scroll=new ScrollView(this);rows=column();scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout panel=column();panel.setPadding(dp(14),dp(12),dp(14),dp(8));panel.setBackground(background(0xffe4eee8,20));
        TextView caption=text("正在播放",11,TEAL);panel.addView(caption);now=text("选几段录音，开始随听",17,INK);now.setMaxLines(2);now.setTypeface(null,Typeface.BOLD);panel.addView(now);
        seek=new SeekBar(this);panel.addView(seek,new LinearLayout.LayoutParams(-1,dp(32)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){if(player!=null&&player.getDuration()>0)player.seekTo(player.getDuration()*b.getProgress()/1000);dragging=false;}public void onProgressChanged(SeekBar b,int p,boolean from){}});seek.setMax(1000);
        clock=text("00:00 / 00:00",11,MUTED);panel.addView(clock);
        LinearLayout controls=line();equally(controls,button("上一首",()->{if(player!=null)player.seekToPreviousMediaItem();}));play=button("播放",this::togglePlay);equally(controls,play);equally(controls,button("下一首",()->{if(player!=null)player.seekToNextMediaItem();}));repeat=button("顺序播放",()->{if(player!=null){player.setRepeatMode(player.getRepeatMode()==Player.REPEAT_MODE_ONE?Player.REPEAT_MODE_OFF:Player.REPEAT_MODE_ONE);updatePlayer();}});equally(controls,repeat);panel.addView(controls);root.addView(panel);
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
        play.setText(player.isPlaying()?"暂停":"播放");repeat.setText(player.getRepeatMode()==Player.REPEAT_MODE_ONE?"单曲循环 ✓":"顺序播放");
        long duration=Math.max(0,player.getDuration()),position=Math.max(0,player.getCurrentPosition());clock.setText(time(position)+" / "+time(duration));
        seek.setEnabled(duration>0);if(!dragging)seek.setProgress(duration>0?(int)(position*1000/duration):0);
    }
    private static String time(long ms){long s=Math.max(0,ms/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    private boolean canPlay(){CaptureJournal s=CaptureJournal.read(this);if(CaptureService.running&&s!=null&&s.active!=null){toast("请先暂停录音再播放，避免把扬声器声音录入");return false;}return true;}
    private void togglePlay(){if(player==null){toast("播放器正在连接");return;}if(player.isPlaying()){player.pause();return;}if(!canPlay())return;if(player.getMediaItemCount()==0){if(queue.isEmpty()){toast("先勾选录音并加入队列");return;}applyQueue(0,true);return;}if(player.getPlaybackState()==Player.STATE_ENDED)player.seekTo(0,0);player.prepare();player.play();}
    private void render(){libraryTab.setText(queueView?"录音库":"录音库 ✓");queueTab.setText("播放队列 ("+queue.size()+")"+(queueView?" ✓":""));search.setVisibility(queueView?View.GONE:View.VISIBLE);filters.setVisibility(queueView?View.GONE:View.VISIBLE);renderRows();}
    private void renderRows(){if(rows==null)return;rows.removeAllViews();selection.setText(queueView?"上下移动调整顺序，点击录音立即播放":"已选 "+selected.size()+" 段 · 按勾选顺序加入");
        List<Recording> list=queueView?queue:catalog;int visible=0;
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(int i=0;i<list.size();i++){
            Recording r=list.get(i);final int index=i;
            if(!queueView&&(!category.equals("全部")&&!r.source.equals(category)||!r.name.toLowerCase(Locale.ROOT).contains(q)))continue;
            visible++;LinearLayout card=column();card.setPadding(dp(10),dp(8),dp(10),dp(8));card.setBackground(background(Color.WHITE,14));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(8);rows.addView(card,cp);
            LinearLayout top=line();
            if(!queueView){CheckBox cb=new CheckBox(this);cb.setChecked(selected.contains(r.uri));cb.setContentDescription("选择 "+r.name);cb.setOnCheckedChangeListener((b,checked)->{if(checked)selected.add(r.uri);else selected.remove(r.uri);selection.setText("已选 "+selected.size()+" 段 · 按勾选顺序加入");});top.addView(cb);}else{TextView n=text(String.format(Locale.ROOT,"%02d",i+1),16,TEAL);n.setPadding(0,0,dp(10),0);top.addView(n);}
            LinearLayout info=column();TextView name=text(r.name,15,INK);name.setTypeface(null,Typeface.BOLD);name.setMaxLines(2);info.addView(name);
            TextView meta=text(r.source+"  ·  "+(r.duration>0?time(r.duration):"时长待播放读取"),11,MUTED);meta.setPadding(0,dp(5),0,0);info.addView(meta);top.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
            if(queueView){info.setOnClickListener(v->{if(player!=null&&canPlay()){player.seekTo(index,0);player.prepare();player.play();}});info.setOnLongClickListener(v->{renameDialog(r);return true;});LinearLayout move=line();equally(move,button("↑ 上移",()->move(index,-1)));equally(move,button("↓ 下移",()->move(index,1)));equally(move,button("移出队列",()->remove(index)));card.addView(move);}
            else{info.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(r.name).setMessage("来源："+r.source+"\n位置："+r.path+"\n\n长按名称可重命名或修正来源。").setPositiveButton("加入队列",(d,w)->{queue.add(r);persistAndAppend(r);render();}).setNeutralButton("重命名",(d,w)->renameDialog(r)).setNegativeButton("关闭",null).show());info.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(r.name).setItems(new String[]{"重命名","修正录音来源"},(d,w)->{if(w==0)renameDialog(r);else sourceDialog("修正录音来源",source->{library.override(r,source);library.save("catalog",catalog);renderRows();});}).show();return true;});}
        }
        if(visible==0){TextView empty=text(queueView?"队列还是空的\n\n在录音库勾选文件，再点“操作”加入。":"没有找到符合条件的音频\n\n允许音频权限后扫描，或授权录音文件夹。\n录音机私有文件可通过“分享”导入本应用。",15,MUTED);empty.setPadding(dp(12),dp(28),dp(12),dp(28));rows.addView(empty);}
    }
    private void showActions(){
        String[] options=queueView?new String[]{"从第一首开始播放","清空队列"}:new String[]{"将所选加入队尾","用所选替换队列并播放","选择当前筛选全部","取消全部选择","按名称排序","按时间排序（最新在前）"};
        new AlertDialog.Builder(this).setTitle(queueView?"队列操作":"录音库操作").setItems(options,(d,w)->{
            if(queueView){if(w==0)applyQueue(0,true);else new AlertDialog.Builder(this).setMessage("清空播放队列？音频文件会保留。").setPositiveButton("清空",(a,b)->{queue.clear();library.save("queue",queue);if(player!=null)player.clearMediaItems();render();}).setNegativeButton("取消",null).show();return;}
            if(w<=1){if(player==null){toast("播放器正在连接");return;}List<Recording> chosen=new ArrayList<>();for(String uri:selected)for(Recording r:catalog)if(uri.equals(r.uri)){chosen.add(r);break;}if(chosen.isEmpty()){toast("请先勾选录音");return;}
                if(w==1){if(player==null){toast("播放器正在连接");return;}queue.clear();queue.addAll(chosen);applyQueue(0,true);}else{queue.addAll(chosen);library.save("queue",queue);if(player!=null){List<MediaItem> items=new ArrayList<>();for(Recording r:chosen)items.add(r.item());player.addMediaItems(items);}}
                selected.clear();queueView=true;
            }else if(w==2){String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);for(Recording r:catalog)if((category.equals("全部")||r.source.equals(category))&&r.name.toLowerCase(Locale.ROOT).contains(q))selected.add(r.uri);}
            else if(w==3)selected.clear();else if(w==4)catalog.sort((a,b)->a.name.compareToIgnoreCase(b.name));else catalog.sort((a,b)->Long.compare(b.date,a.date));render();
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
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==30){if(result==RESULT_OK&&pendingRename!=null&&pendingRenameTitle!=null)renameFile(pendingRename,pendingRenameTitle,false);else toast("未授权修改文件，可选择仅修改软件内名称");return;}if(result!=RESULT_OK||data==null)return;
        if(request==20&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);if((data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION);library.addTree(data.getData(),treeSource);scan();}catch(Exception e){toast("文件夹授权失败，请重新选择");}}
        else if(request==21){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());importUris(uris);}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIncoming(intent);}
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
            library.putPreset(new Preset(UUID.randomUUID().toString(),name,System.currentTimeMillis(),new ArrayList<>(queue)));toast("预设队列已保存，可从“预设队列”重新载入");
        });
    }
    private void showPresets(){
        List<Preset> presets=library.presets();if(presets.isEmpty()){toast("暂无预设。保存当前队列，或结束一次长录音后自动生成。");return;}
        String[] names=new String[presets.size()];for(int i=0;i<names.length;i++){Preset p=presets.get(i);names[i]=p.title+"  ·  "+p.rows.size()+" 段";}
        new AlertDialog.Builder(this).setTitle("预设队列").setItems(names,(d,w)->presetActions(presets.get(w))).setNegativeButton("关闭",null).show();
    }
    private void presetActions(Preset preset){
        new AlertDialog.Builder(this).setTitle(preset.title).setItems(new String[]{"载入队列","载入并播放","追加到当前队尾","重命名预设","删除预设（保留录音）"},(d,w)->{
            if(w<=2){if(player==null){toast("播放器正在连接");return;}if(w==1&&!canPlay())return;
                if(w<2){queue.clear();queue.addAll(preset.rows);applyQueue(0,w==1);}else{queue.addAll(preset.rows);library.save("queue",queue);List<MediaItem> items=new ArrayList<>();for(Recording r:preset.rows)items.add(r.item());player.addMediaItems(items);}
                queueView=true;render();toast("已载入 "+preset.rows.size()+" 段录音");
            }else if(w==3)askName("重命名预设",preset.title,name->{preset.title=name;library.putPreset(preset);toast("预设已重命名");});
            else new AlertDialog.Builder(this).setMessage("删除“"+preset.title+"”？录音文件会保留。").setPositiveButton("删除",(a,b)->{try{library.deletePreset(preset.id);toast("预设已删除");}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("取消",null).show();
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
        scan();render();
    }
    private void recordingDialog(){
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));captureDetail=text("",14,INK);captureDetail.setPadding(0,dp(8),0,dp(12));content.addView(captureDetail);
        captureToggle=button("开始长录音",this::toggleCapture);content.addView(captureToggle);captureFinish=button("结束录音并保存预设队列",()->{
            new AlertDialog.Builder(this).setMessage("结束本次长录音？会保存当前片段，并将本次全部有效片段保存为一个预设队列。").setPositiveButton("结束并保存",(d,w)->sendCapture(CaptureService.FINISH)).setNegativeButton("继续录音",null).show();
        });content.addView(captureFinish);
        new AlertDialog.Builder(this).setTitle("长录音 · 分段保存").setView(content).setNegativeButton("返回（录音会继续）",null).setOnDismissListener(d->{captureDetail=null;captureToggle=null;captureFinish=null;}).show();updateCapture();
    }
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
        CaptureJournal s=CaptureJournal.read(this);boolean active=CaptureService.running&&s!=null&&!s.state.equals("finished");
        boolean recording=active&&s.active!=null;String summary;
        if(s==null)summary="长录音：暂停即保存一段，结束自动保存队列";
        else if(active){long current=recording?Math.max(0,SystemClock.elapsedRealtime()-s.activeSince):0;summary=(recording?"● 录音中":"Ⅱ 已暂停")+" · 已保存 "+s.rows.size()+" 段 · 累计 "+time(s.total+current);}
        else summary=s.message.isEmpty()?"长录音已结束":s.message;
        captureSummary.setText(summary);
        if(captureDetail!=null){captureDetail.setText(summary+(recording?"\n当前："+s.active.name:"")+(active&&!s.message.isEmpty()?"\n"+s.message:"")+"\n\n暂停会保存当前片段；继续会创建新片段。文件保存至 "+Library.recordingPath()+"。");captureToggle.setText(recording?"暂停并保存当前片段":active?"继续录音（新建片段）":"开始新的长录音");captureFinish.setEnabled(active);}
        String fingerprint=s==null?"":s.id+":"+s.rows.size()+":"+s.state;
        if(!fingerprint.equals(captureFingerprint)){boolean had=!captureFingerprint.isEmpty();captureFingerprint=fingerprint;if(had)scan();}
    }
    private void help(){new AlertDialog.Builder(this).setTitle("红米 K80 使用说明").setMessage("1. 点击“扫描录音”，允许音乐和音频权限；进入应用时也会自动扫描已授权的音频。\n\n2. 录音机来源通过小米目录和所属应用识别；通话录音单独分类。无法确认来源的文件归入其他音频，长按名称可修正。\n\n3. 扫描不到时，授权 MIUI/sound_recorder 等实际录音文件夹。Android/data 私有目录无法直接授权；请在系统录音机选中文件，分享给“录音随听”，或先导出到共享文件夹。\n\n4. 按想听的顺序勾选，点“操作”加入队列；队列内可上下移动。顺序模式播完停止；单曲循环反复播放当前录音。\n\n5. 支持熄屏播放和通知栏控制。如后台被系统中止，可在应用电池设置中选择无限制。连续播放会自动切换；音频文件本身的静音仍会保留。\n\n所有音频仅在本机处理；导入会保存一份本地副本，不改动原录音。").setPositiveButton("知道了",null).show();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();if(library!=null&&!scanning)scan();try{getContentResolver().registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,observer);}catch(SecurityException ignored){}}
    @Override protected void onPause(){getContentResolver().unregisterContentObserver(observer);handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("treeSource",treeSource);state.putString("category",category);state.putBoolean("queueView",queueView);state.putStringArrayList("selected",new ArrayList<>(selected));super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);worker.shutdown();if(future!=null)MediaController.releaseFuture(future);super.onDestroy();}
}
