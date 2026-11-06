package cn.luyin.player;

/** YIN cumulative mean normalized difference, with interpolation and confidence rejection. */
public final class PitchDetector {
    public static final double MIN_HZ=40,MAX_HZ=1800;
    public static final class Pitch {public final double hz,confidence;Pitch(double hz,double confidence){this.hz=hz;this.confidence=confidence;}}
    public static Pitch estimate(double[] samples,int sampleRate,double rmsDb){
        if(rmsDb<-60||samples.length<512)return new Pitch(0,0);
        int window=samples.length/2,maxLag=Math.min(window-1,(int)Math.ceil(sampleRate/MIN_HZ)+1);
        double[] normalized=new double[maxLag+1];normalized[0]=1;double sum=0;
        for(int tau=1;tau<=maxLag;tau++){double difference=0;for(int i=0;i<window;i++){double delta=samples[i]-samples[i+tau];difference+=delta*delta;}sum+=difference;normalized[tau]=sum>1e-18?difference*tau/sum:1;}
        int lag=-1;
        for(int tau=2;tau<maxLag;tau++)if(normalized[tau]<.15){while(tau+1<=maxLag&&normalized[tau+1]<normalized[tau])tau++;lag=tau;break;}
        if(lag<0)return new Pitch(0,0);
        double refined=lag;
        if(lag>1&&lag<maxLag){double a=normalized[lag-1],b=normalized[lag],c=normalized[lag+1],den=a-2*b+c;if(Math.abs(den)>1e-12)refined+=Math.max(-1,Math.min(1,.5*(a-c)/den));}
        double hz=sampleRate/refined,confidence=Math.max(0,Math.min(1,1-normalized[lag]));
        if(hz<MIN_HZ||hz>MAX_HZ||confidence<.85)return new Pitch(0,confidence);
        return new Pitch(hz,confidence);
    }
    public static double cents(double hz,double reference){return hz>0&&reference>0?1200*Math.log(hz/reference)/Math.log(2):Double.NaN;}
    public static String note(double hz){if(hz<=0||!Double.isFinite(hz))return "—";int midi=(int)Math.round(69+12*Math.log(hz/440)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};return names[Math.floorMod(midi,12)]+(Math.floorDiv(midi,12)-1);}
    public static double noteCents(double hz){if(hz<=0)return Double.NaN;double midi=69+12*Math.log(hz/440)/Math.log(2);return (midi-Math.round(midi))*100;}
    private PitchDetector(){}
}
