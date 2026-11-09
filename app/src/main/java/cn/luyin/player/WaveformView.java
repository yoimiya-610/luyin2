package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Displays the last twelve seconds of real microphone peak levels. */
public final class WaveformView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] samples=new float[0];
    private boolean recording;
    private boolean offline;
    private double start, end, cursor;
    public WaveformView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("实时录音声音波形，尚未开始录音");}
    public void setSamples(float[] values,boolean active){if(recording==active&&java.util.Arrays.equals(samples,values))return;samples=values;recording=active;setContentDescription(active?"实时麦克风声音波形":"声音波形已暂停");invalidate();}
    void setWindow(AnalysisWindow.Result window) {
        offline=true;
        samples=window.peaks;
        start=window.startUs/1_000_000.0;
        end=window.endUs/1_000_000.0;
        setContentDescription("已保存录音的声音波形，横轴为录音时间");
        invalidate();
    }
    void setCursor(double seconds){cursor=seconds;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        float left=4*density,right=getWidth()-4*density,center=getHeight()*.45f,span=getHeight()*.33f;
        paint.setStrokeWidth(density);paint.setColor(0x2648b98d);
        for(int i=0;i<=4;i++){float x=left+(right-left)*i/4;canvas.drawLine(x,10*density,x,getHeight()-30*density,paint);}
        canvas.drawLine(left,center,right,center,paint);
        int slots=offline?Math.max(1,samples.length):120;
        float step=(right-left)/slots;int offset=slots-samples.length;
        float stroke=Math.max(density,step*.58f);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(offline?Math.min(4*density,stroke):stroke);
        for(int i=0;i<samples.length;i++){
            float x=left+(offset+i+.5f)*step,h=offline?samples[i]*span:Math.max(density,samples[i]*span);
            paint.setColor(recording?0xffa7edbd:0xff7ca99a);canvas.drawLine(x,center-h,x,center+h,paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);paint.setTextSize(10*density);paint.setColor(0xff96b4a8);
        if(offline&&end>start){
            float x=left+(float)((cursor-start)/(end-start))*(right-left);
            paint.setColor(0xffffdf98);paint.setStrokeWidth(density);
            canvas.drawLine(x,4*density,x,getHeight()-24*density,paint);
            paint.setColor(0xff96b4a8);
        }
        canvas.drawText(offline?AnalysisWindow.time(start):"12 秒前",left,getHeight()-5*density,paint);
        if(!offline){paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("6 秒前",getWidth()/2f,getHeight()-5*density,paint);}
        paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(offline?AnalysisWindow.time(end):"现在",right,getHeight()-5*density,paint);paint.setTextAlign(Paint.Align.LEFT);
    }
}
