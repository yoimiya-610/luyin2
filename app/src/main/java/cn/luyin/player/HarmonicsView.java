package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class HarmonicsView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private SoundFrame frame;
    public HarmonicsView(Context context){super(context);setContentDescription("H1 基频，H2 至 H8 泛音，相对基频幅度 dB");}
    public void setFrame(SoundFrame frame){if(this.frame!=frame){this.frame=frame;invalidate();}}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float dp=getResources().getDisplayMetrics().density,step=getWidth()/8f,top=22*dp,bottom=getHeight()-22*dp;paint.setTextSize(9*dp);paint.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<8;i++){float x=(i+.5f)*step;float db=frame==null?Float.NaN:frame.harmonicsDb[i];paint.setColor(0xffe5ede2);canvas.drawRoundRect(x-step*.28f,top,x+step*.28f,bottom,3*dp,3*dp,paint);
            if(!Float.isNaN(db)){float h=Math.max(0,Math.min(1,(db+60)/80f))*(bottom-top);paint.setColor(i==0?0xff21785c:0xff80bd91);canvas.drawRoundRect(x-step*.28f,bottom-h,x+step*.28f,bottom,3*dp,3*dp,paint);}
            paint.setColor(0xff64746a);canvas.drawText(Float.isNaN(db)?"—":String.format(java.util.Locale.ROOT,"%+.0f",db),x,14*dp,paint);canvas.drawText("H"+(i+1),x,getHeight()-5*dp,paint);
        }paint.setTextAlign(Paint.Align.LEFT);
    }
}
