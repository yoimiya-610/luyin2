package cn.luyin.player;

/** Geometry and equal-tempered tuning shared by touch UI and tests. */
public final class PianoKeys {
    public static final int[] WHITE={0,2,4,5,7,9,11};
    public static boolean hasBlackAfter(int white){int degree=white%7;return degree!=2&&degree!=6;}
    public static int whiteNote(int index,int base){return base+(index/7)*12+WHITE[index%7];}
    public static double frequency(int midi){return 440*Math.pow(2,(midi-69)/12.0);}
    public static int hit(float x,float y,float width,float height,int base){
        if(x<0||y<0||x>=width||y>=height||width<=0||height<=0)return -1;float key=width/14;
        if(y<height*.62f)for(int i=0;i<13;i++)if(hasBlackAfter(i)){float center=(i+1)*key;if(x>=center-key*.31f&&x<center+key*.31f)return whiteNote(i,base)+1;}
        return whiteNote(Math.min(13,(int)(x/key)),base);
    }
    private PianoKeys(){}
}
