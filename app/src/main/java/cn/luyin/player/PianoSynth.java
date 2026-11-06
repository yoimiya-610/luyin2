package cn.luyin.player;

import java.util.*;

/** Polyphonic struck-string style synthesis; no samples or network sound bank required. */
public final class PianoSynth {
    private static final int TABLE=4096;
    private static final double[] SINE=new double[TABLE+1];
    static{for(int i=0;i<=TABLE;i++)SINE[i]=Math.sin(2*Math.PI*i/TABLE);}
    private static final double[] PARTIALS={1,.5,.24,.13,.07,.04};
    private static final class Voice {
        int held=1,age;double envelope=1,release=1,decay;final double[] phases=new double[6],steps=new double[6];
        Voice(int midi,int rate){double hz=PianoKeys.frequency(midi);decay=Math.exp(-1/(rate*(midi<60?2.8:1.6)));for(int i=0;i<steps.length;i++){int h=i+1;steps[i]=hz*h*Math.sqrt((1+.00012*h*h)/(1+.00012))/rate;}}
    }
    private final LinkedHashMap<Integer,Voice> voices=new LinkedHashMap<>();
    private final int sampleRate;
    private final double releaseDecay;
    private double volume=.5;
    public PianoSynth(int rate){if(rate<8000)throw new IllegalArgumentException();sampleRate=rate;releaseDecay=Math.exp(-1/(rate*.075));}
    public synchronized void volume(double volume){this.volume=Math.max(0,Math.min(1,volume));}
    public synchronized void noteOn(int midi){if(midi<24||midi>108)return;Voice old=voices.get(midi);if(old!=null&&old.held>0){old.held++;return;}if(voices.size()>=16){Integer first=voices.keySet().iterator().next();voices.remove(first);}voices.put(midi,new Voice(midi,sampleRate));}
    public synchronized void noteOff(int midi){Voice v=voices.get(midi);if(v!=null)v.held=Math.max(0,v.held-1);}
    public synchronized void allOff(){voices.clear();}
    public synchronized int voiceCount(){return voices.size();}
    public synchronized void render(short[] output,int length){
        Arrays.fill(output,0,length,(short)0);if(voices.isEmpty())return;double scale=.22*volume/Math.sqrt(voices.size());
        for(int n=0;n<length;n++){double mixed=0;for(Voice v:voices.values()){
            double sound=0;for(int h=0;h<v.phases.length;h++){if(v.steps[h]>=.5)continue;double at=v.phases[h]*TABLE;int index=(int)at;double alpha=at-index;double sine=SINE[index]*(1-alpha)+SINE[index+1]*alpha;sound+=sine*PARTIALS[h];v.phases[h]+=v.steps[h];v.phases[h]-=Math.floor(v.phases[h]);}
            v.envelope*=v.decay;if(v.held==0)v.release*=releaseDecay;double attack=Math.min(1,v.age++/(sampleRate*.004));mixed+=sound*v.envelope*v.release*attack;
        }mixed*=scale;mixed=mixed/(1+Math.abs(mixed));output[n]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(mixed*32767)));}
        voices.values().removeIf(v->v.release*v.envelope<.0001);
    }
}
