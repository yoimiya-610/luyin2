package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A real time-frequency heatmap, 12 seconds wide. Unmeasured time stays blank. */
public final class SpectrogramView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds=new RectF();
    private final Path trace=new Path();
    private final Bitmap bitmap=Bitmap.createBitmap(240,256,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[240*256];
    private static final int[] COLORS={0xff102f2a,0xff225675,0xff6560a4,0xffc97091,0xfff2b865,0xfffbf3b4};
    private LiveAnalysis.Snapshot snapshot;
    private AnalysisWindow.Result window;
    private double cursor;
    private boolean pitch;
    private java.util.IdentityHashMap<SoundFrame,int[]> columns=new java.util.IdentityHashMap<>();
    public SpectrogramView(Context context){super(context);setContentDescription("实时声谱图，横轴时间，纵轴频率，颜色表示 dBFS 幅度");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    public void setSnapshot(LiveAnalysis.Snapshot value,boolean pitch){if(snapshot==value&&this.pitch==pitch)return;snapshot=value;this.pitch=pitch;rebuild();invalidate();}
    void setWindow(AnalysisWindow.Result value) {
        window=value;
        pitch=true;
        setContentDescription("已保存录音的声谱图，横轴为录音时间，纵轴为频率，颜色表示 dBFS 幅度");
        rebuild();
        invalidate();
    }
    void setCursor(double seconds){cursor=seconds;invalidate();}
    private java.util.List<SoundFrame> frames(){
        return window!=null?window.frames:snapshot==null?java.util.Collections.emptyList():snapshot.frames;
    }
    private double start(SoundFrame latest){return window!=null?window.startUs/1_000_000.0:latest==null?-12:latest.seconds-12;}
    private double end(SoundFrame latest){return window!=null?window.endUs/1_000_000.0:latest==null?0:latest.seconds;}
    private static double topHz(SoundFrame frame){return frame==null?6000:Math.min(6000,frame.sampleRate/2.0);}
    private void rebuild(){
        java.util.Arrays.fill(pixels,0xff102f2a);
        java.util.List<SoundFrame> frames=frames();
        SoundFrame latest=frames.isEmpty()?null:frames.get(frames.size()-1);
        java.util.IdentityHashMap<SoundFrame,int[]> retained=new java.util.IdentityHashMap<>();
        double start=start(latest),span=end(latest)-start;
        if(span>0){
            for(SoundFrame f:frames){
                double half=window==null?0:.03;
                int first=Math.max(0,(int)Math.round((f.seconds-half-start)/span*239));
                int last=Math.min(239,(int)Math.round((f.seconds+half-start)/span*239));
                if(last<first)continue;
                int[] column=columns.get(f);if(column==null)column=column(f,topHz(window==null?f:latest));retained.put(f,column);
                for(int y=0;y<256;y++)java.util.Arrays.fill(pixels,y*240+first,y*240+last+1,column[y]);
            }
        }columns=retained;bitmap.setPixels(pixels,0,240,0,0,240,256);
    }
    private int[] column(SoundFrame f,double topHz){int[] result=new int[256];
        for(int y=0;y<256;y++){double hz=(255-y)*topHz/255,half=topHz/510;int low=Math.max(0,(int)Math.floor((hz-half)*f.fftSize/f.sampleRate)),high=Math.min(f.spectrumDb.length-1,(int)Math.ceil((hz+half)*f.fftSize/f.sampleRate));float level=-120;for(int k=low;k<=high;k++)level=Math.max(level,f.spectrumDb[k]);result[y]=color(level);}return result;
    }
    private static int color(float db){float t=Math.max(0,Math.min(1,(db+90)/90f));float p=t*(COLORS.length-1);int i=Math.min(COLORS.length-2,(int)p);float a=p-i;int c=COLORS[i],d=COLORS[i+1];return Color.rgb((int)(Color.red(c)*(1-a)+Color.red(d)*a),(int)(Color.green(c)*(1-a)+Color.green(d)*a),(int)(Color.blue(c)*(1-a)+Color.blue(d)*a));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float dp=getResources().getDisplayMetrics().density;float left=34*dp,top=4*dp,right=getWidth()-5*dp,bottom=getHeight()-24*dp;if(right<=left||bottom<=top)return;
        bounds.set(left,top,right,bottom);paint.setFilterBitmap(false);canvas.drawBitmap(bitmap,null,bounds,paint);
        java.util.List<SoundFrame> frames=frames();SoundFrame latest=frames.isEmpty()?null:frames.get(frames.size()-1);
        double maxHz=topHz(latest),start=start(latest),end=end(latest),span=end-start;
        paint.setTextSize(9*dp);paint.setStrokeWidth(dp);paint.setColor(0xffa8c9ba);
        for(int i=0;i<=3;i++){float y=bottom-(bottom-top)*i/3;canvas.drawText(i==0?"0":String.format(java.util.Locale.ROOT,"%.1fk",maxHz*i/3000),0,y+3*dp,paint);paint.setColor(0x228cbaac);canvas.drawLine(left,y,right,y,paint);paint.setColor(0xffa8c9ba);}
        canvas.drawText(window==null?"-12s":AnalysisWindow.time(start),left,getHeight()-5*dp,paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(window==null?"0s":AnalysisWindow.time(end),right,getHeight()-5*dp,paint);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("-90 至 0 dBFS",(left+right)/2,getHeight()-5*dp,paint);paint.setTextAlign(Paint.Align.LEFT);
        if(pitch&&latest!=null&&span>0){trace.reset();boolean connected=false;long segment=-1;double previous=-100;for(SoundFrame f:frames){if(f.pitchHz<=0||f.pitchHz>maxHz){connected=false;continue;}float x=(float)(left+(f.seconds-start)/span*(right-left)),y=(float)(bottom-f.pitchHz/maxHz*(bottom-top));if(x<left||x>right){connected=false;continue;}if(!connected||segment!=f.segment||f.seconds-previous>.25)trace.moveTo(x,y);else trace.lineTo(x,y);connected=true;segment=f.segment;previous=f.seconds;}paint.setColor(0xffffffff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*dp);canvas.drawPath(trace,paint);paint.setStyle(Paint.Style.FILL);}
        if(window!=null&&span>0){
            float x=(float)(left+(cursor-start)/span*(right-left));
            paint.setColor(0xffffdf98);paint.setStrokeWidth(dp);canvas.drawLine(x,top,x,bottom,paint);
        }
    }
}
