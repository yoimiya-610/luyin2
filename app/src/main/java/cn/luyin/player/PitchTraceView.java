package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class PitchTraceView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private LiveAnalysis.Snapshot snapshot;
    public PitchTraceView(Context context){super(context);setContentDescription("最近五秒音高轨迹，40 至 1800 Hz");}
    public void setSnapshot(LiveAnalysis.Snapshot value){if(snapshot!=value){snapshot=value;invalidate();}}
    private float y(double hz,float top,float height){return top+(float)(Math.log(1800/hz)/Math.log(1800.0/40)*height);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float dp=getResources().getDisplayMetrics().density,left=31*dp,top=8*dp,width=getWidth()-left-4*dp,height=getHeight()-30*dp;if(width<=0||height<=0)return;paint.setTextSize(9*dp);paint.setStrokeWidth(dp);
        for(int hz:new int[]{100,400,1600}){float yy=y(hz,top,height);paint.setColor(0xff738278);canvas.drawText(Integer.toString(hz),0,yy+3*dp,paint);paint.setColor(0xffe0e9df);canvas.drawLine(left,yy,left+width,yy,paint);}
        SoundFrame last=snapshot==null?null:snapshot.latest();if(last!=null){path.reset();boolean connected=false;long segment=-1;double previous=-100;for(SoundFrame f:snapshot.frames){if(f.seconds<last.seconds-5||f.pitchHz<=0){connected=false;continue;}float x=left+(float)((f.seconds-(last.seconds-5))/5*width),yy=y(f.pitchHz,top,height);if(!connected||segment!=f.segment||f.seconds-previous>.25)path.moveTo(x,yy);else path.lineTo(x,yy);segment=f.segment;previous=f.seconds;connected=true;}paint.setColor(0xff21785c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*dp);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);}
        paint.setColor(0xff64746a);canvas.drawText("-5s",left,getHeight()-4*dp,paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("Hz",getWidth()-4*dp,getHeight()-4*dp,paint);paint.setTextAlign(Paint.Align.LEFT);
    }
}
