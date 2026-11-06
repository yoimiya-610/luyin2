package cn.luyin.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.*;

/** Wraps complete filter chips instead of clipping the last item at a scroll viewport edge. */
public final class FlowLayout extends ViewGroup {
    public FlowLayout(Context context){super(context);setClipChildren(false);setClipToPadding(false);}
    public FlowLayout(Context context,AttributeSet attrs){super(context,attrs);setClipChildren(false);setClipToPadding(false);}
    @Override protected LayoutParams generateDefaultLayoutParams(){return new MarginLayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs){return new MarginLayoutParams(getContext(),attrs);}
    @Override protected LayoutParams generateLayoutParams(LayoutParams source){return new MarginLayoutParams(source);}
    @Override protected boolean checkLayoutParams(LayoutParams p){return p instanceof MarginLayoutParams;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int limit=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),x=0,row=0,height=0,max=0;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;measureChildWithMargins(child,widthSpec,0,heightSpec,0);MarginLayoutParams p=(MarginLayoutParams)child.getLayoutParams();int w=child.getMeasuredWidth()+p.leftMargin+p.rightMargin,h=child.getMeasuredHeight()+p.topMargin+p.bottomMargin;if(x>0&&x+w>limit){max=Math.max(max,x);height+=row;x=0;row=0;}x+=w;row=Math.max(row,h);}max=Math.max(max,x);height+=row;setMeasuredDimension(resolveSize(max+getPaddingLeft()+getPaddingRight(),widthSpec),resolveSize(height+getPaddingTop()+getPaddingBottom(),heightSpec));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){int limit=getWidth()-getPaddingRight(),x=getPaddingLeft(),y=getPaddingTop(),row=0;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;MarginLayoutParams p=(MarginLayoutParams)child.getLayoutParams();int w=child.getMeasuredWidth()+p.leftMargin+p.rightMargin,h=child.getMeasuredHeight()+p.topMargin+p.bottomMargin;if(x>getPaddingLeft()&&x+w>limit){x=getPaddingLeft();y+=row;row=0;}int l=x+p.leftMargin,t=y+p.topMargin;child.layout(l,t,l+child.getMeasuredWidth(),t+child.getMeasuredHeight());x+=w;row=Math.max(row,h);}
    }
}
