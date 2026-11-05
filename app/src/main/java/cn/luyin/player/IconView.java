package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small native icons share one stroke weight and align on a 24 dp grid. */
public final class IconView extends View {
    public static final int LIBRARY=0,MIC=1,PLAY=2,PRESETS=3,MORE=4,PAUSE=5,PREVIOUS=6,NEXT=7,WAVE=8,EXPORT=9;
    private final int kind;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;
    public IconView(Context context,int kind,int color){super(context);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void setColor(int color){this.color=color;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.save();float size=Math.min(getWidth(),getHeight());c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.65f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
            case LIBRARY:c.drawRoundRect(4,3.5f,20,20.5f,3,3,p);c.drawLine(8,8,16,8,p);c.drawLine(8,12,16,12,p);c.drawLine(8,16,13,16,p);break;
            case MIC:c.drawRoundRect(9,3,15,14,3,3,p);c.drawArc(6,6,18,18,0,180,false,p);c.drawLine(12,18,12,21,p);c.drawLine(9,21,15,21,p);break;
            case PLAY:p.setStyle(Paint.Style.FILL);Path triangle=new Path();triangle.moveTo(8,4);triangle.lineTo(20,12);triangle.lineTo(8,20);triangle.close();c.drawPath(triangle,p);break;
            case PRESETS:c.drawRoundRect(3,6,21,21,3,3,p);c.drawLine(7,3,17,3,p);c.drawLine(7,11,17,11,p);c.drawLine(7,16,13,16,p);break;
            case MORE:p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(12,6+i*6,1.4f,p);break;
            case PAUSE:p.setStyle(Paint.Style.FILL);c.drawRoundRect(6,5,10,19,1,1,p);c.drawRoundRect(14,5,18,19,1,1,p);break;
            case PREVIOUS:case NEXT:c.save();if(kind==PREVIOUS){c.translate(24,0);c.scale(-1,1);}p.setStyle(Paint.Style.FILL);Path t=new Path();t.moveTo(5,5);t.lineTo(16,12);t.lineTo(5,19);t.close();c.drawPath(t,p);c.drawRoundRect(18,5,20,19,.7f,.7f,p);c.restore();break;
            case WAVE:for(int i=0;i<5;i++){float h=new float[]{3,7,10,5,2}[i];float x=4+i*4;c.drawLine(x,12-h,x,12+h,p);}break;
            case EXPORT:c.drawLine(12,3,12,15,p);c.drawLine(8,7,12,3,p);c.drawLine(16,7,12,3,p);c.drawPath(exportTray(),p);break;
        }
        c.restore();
    }
    private Path exportTray(){Path path=new Path();path.moveTo(5,12);path.lineTo(5,20);path.lineTo(19,20);path.lineTo(19,12);return path;}
}
