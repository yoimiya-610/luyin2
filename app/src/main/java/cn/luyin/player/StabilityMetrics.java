package cn.luyin.player;

import java.util.*;

/** Descriptive variation over a 5 s continuous segment; no invented health/stability score. */
public final class StabilityMetrics {
    public final int frames,voicedFrames;
    public final double pitchStdCents,levelStdDb,voicedFraction;
    private StabilityMetrics(int count,int voiced,double pitch,double level){frames=count;voicedFrames=voiced;pitchStdCents=pitch;levelStdDb=level;voicedFraction=count>0?(double)voiced/count:Double.NaN;}
    public static StabilityMetrics measure(List<SoundFrame> history,long segment,double seconds,double hz,double rms){
        List<Double> pitch=new ArrayList<>(),level=new ArrayList<>();int count=1;
        if(hz>0)pitch.add(1200*Math.log(hz/440)/Math.log(2));if(rms>-60)level.add(rms);
        for(SoundFrame f:history)if(f.segment==segment&&f.seconds>=seconds-5&&f.seconds<=seconds){count++;if(f.pitchHz>0)pitch.add(1200*Math.log(f.pitchHz/440)/Math.log(2));if(f.rmsDb>-60)level.add(f.rmsDb);}
        return new StabilityMetrics(count,pitch.size(),pitch.size()>=8?std(pitch):Double.NaN,level.size()>=8?std(level):Double.NaN);
    }
    private static double std(List<Double> values){double mean=0;for(double v:values)mean+=v;mean/=values.size();double variance=0;for(double v:values)variance+=(v-mean)*(v-mean);return Math.sqrt(variance/values.size());}
}
