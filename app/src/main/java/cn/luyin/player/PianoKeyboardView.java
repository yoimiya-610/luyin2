package cn.luyin.player;

import android.content.Context;
import android.graphics.*;
import android.util.SparseIntArray;
import android.view.*;

public final class PianoKeyboardView extends View {
    public interface Listener {void on(int midi);void off(int midi);}
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray touches=new SparseIntArray();
    private int base=60;
    public PianoKeyboardView(Context context,Listener listener){super(context);this.listener=listener;setContentDescription("双八度钢琴键盘，支持多点触控");setFocusable(true);setFocusableInTouchMode(true);}
    public void setBase(int midi){releaseAll();base=Math.max(24,Math.min(84,midi));invalidate();}
    public void releaseAll(){for(int i=0;i<touches.size();i++)listener.off(touches.valueAt(i));touches.clear();invalidate();}
    private boolean pressed(int note){for(int i=0;i<touches.size();i++)if(touches.valueAt(i)==note)return true;return false;}
    private void move(int id,float x,float y){int note=PianoKeys.hit(x,y,getWidth(),getHeight(),base),old=touches.get(id,-1);if(note==old)return;if(old>=0)listener.off(old);if(note>=0){touches.put(id,note);listener.on(note);}else touches.delete(id);invalidate();}
    @Override public boolean onTouchEvent(MotionEvent event){int action=event.getActionMasked(),index=event.getActionIndex();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)move(event.getPointerId(index),event.getX(index),event.getY(index));
        else if(action==MotionEvent.ACTION_MOVE){for(int i=0;i<event.getPointerCount();i++)move(event.getPointerId(i),event.getX(i),event.getY(i));}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){int id=event.getPointerId(index),note=touches.get(id,-1);if(note>=0)listener.off(note);touches.delete(id);invalidate();if(action==MotionEvent.ACTION_UP)performClick();}
        else if(action==MotionEvent.ACTION_CANCEL)releaseAll();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){releaseAll();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float key=getWidth()/14f,dp=getResources().getDisplayMetrics().density;paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(10*dp);
        for(int i=0;i<14;i++){int note=PianoKeys.whiteNote(i,base);paint.setStyle(Paint.Style.FILL);paint.setColor(pressed(note)?0xffb4edc6:0xfffcfcf7);c.drawRoundRect(i*key+dp,dp,(i+1)*key-dp,getHeight()-dp,5*dp,5*dp,paint);paint.setColor(0xff63786c);c.drawText(PitchDetector.note(PianoKeys.frequency(note)),(i+.5f)*key,getHeight()-12*dp,paint);}
        for(int i=0;i<13;i++)if(PianoKeys.hasBlackAfter(i)){float x=(i+1)*key;int note=PianoKeys.whiteNote(i,base)+1;paint.setColor(pressed(note)?0xff5da281:0xff173d32);c.drawRoundRect(x-key*.31f,0,x+key*.31f,getHeight()*.62f,4*dp,4*dp,paint);}
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
