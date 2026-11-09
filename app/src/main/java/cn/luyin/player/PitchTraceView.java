package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class PitchTraceView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private LiveAnalysis.Snapshot snapshot;
    private AnalysisWindow.Result window;
    private double cursor;
    private boolean notes;
    public PitchTraceView(Context context){super(context);setContentDescription("最近五秒音高轨迹，40 至 1800 Hz");}
    public void setSnapshot(LiveAnalysis.Snapshot value){if(snapshot!=value){snapshot=value;invalidate();}}
    public void setNoteAxis(boolean notes){if(this.notes!=notes){this.notes=notes;describeAxis();invalidate();}}
    void setWindow(AnalysisWindow.Result value){window=value;describeAxis();invalidate();}
    void setCursor(double seconds){cursor=seconds;invalidate();}
    private void describeAxis(){
        String scope=window==null?"最近五秒":"已保存录音的";
        setContentDescription(scope+(notes?"音高轨迹，科学音高记谱，A4 等于 440 Hz":"音高轨迹，40 至 1800 Hz"));
    }
    public boolean isNoteAxis(){return notes;}
    private float y(double hz,float top,float height){return top+(float)(PitchAxis.position(hz)*height);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float dp=getResources().getDisplayMetrics().density,left=31*dp,top=8*dp,width=getWidth()-left-4*dp,height=getHeight()-30*dp;if(width<=0||height<=0)return;paint.setTextSize(9*dp);paint.setStrokeWidth(dp);
        if(notes){paint.setColor(0xfff0f3ef);for(int midi=28;midi<=93;midi++){float yy=y(PitchAxis.frequency(midi),top,height);canvas.drawLine(left,yy,left+width,yy,paint);}}
        for(PitchAxis.Tick tick:PitchAxis.ticks(notes)){float yy=y(tick.hz,top,height);paint.setColor(0xff738278);canvas.drawText(tick.label,0,yy+3*dp,paint);paint.setColor(0xffe0e9df);canvas.drawLine(left,yy,left+width,yy,paint);}
        SoundFrame last=snapshot==null?null:snapshot.latest();
        double start=window!=null?window.startUs/1_000_000.0:last!=null?last.seconds-5:-5;
        double end=window!=null?window.endUs/1_000_000.0:last!=null?last.seconds:0;
        java.util.List<SoundFrame> frames=window!=null?window.frames:last!=null?snapshot.frames:java.util.Collections.emptyList();
        if(!frames.isEmpty()&&end>start){path.reset();boolean connected=false;long segment=-1;double previous=-100;for(SoundFrame f:frames){if(f.seconds<start||f.seconds>end||f.pitchHz<=0){connected=false;continue;}float x=left+(float)((f.seconds-start)/(end-start)*width),yy=y(f.pitchHz,top,height);if(!connected||segment!=f.segment||f.seconds-previous>.25)path.moveTo(x,yy);else path.lineTo(x,yy);segment=f.segment;previous=f.seconds;connected=true;}paint.setColor(0xff21785c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*dp);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
        if(window!=null&&end>start){
            float x=left+(float)((cursor-start)/(end-start))*width;
            paint.setColor(0xffb47a24);paint.setStrokeWidth(dp);canvas.drawLine(x,top,x,top+height,paint);
        }
        paint.setColor(0xff64746a);canvas.drawText(window==null?"-5s":AnalysisWindow.time(start),left,getHeight()-4*dp,paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(window==null?(notes?"音名":"Hz"):AnalysisWindow.time(end),getWidth()-4*dp,getHeight()-4*dp,paint);paint.setTextAlign(Paint.Align.LEFT);
    }
}
