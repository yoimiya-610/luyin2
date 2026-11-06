package cn.luyin.player;

import java.util.*;

/** Single-worker PCM analysis: anti-alias FIR, Hann STFT, YIN pitch and harmonic peaks. */
public final class AcousticAnalyzer {
    private final List<SoundFrame> history=new ArrayList<>();
    private int sourceRate,rate,factor,phase,firIndex,next,count,hop;
    private long segment=-1;
    private double[] signal,firDelay,coefficients;
    private double lastFrameSeconds=-1;
    public void clear(){history.clear();sourceRate=0;segment=-1;lastFrameSeconds=-1;}
    private void reset(int sampleRate,long segment){
        sourceRate=sampleRate;this.segment=segment;factor=Math.max(1,(sampleRate+15999)/16000);rate=sampleRate/factor;int size=rate>10000?4096:2048;
        signal=new double[size];next=count=phase=firIndex=hop=0;lastFrameSeconds=-1;
        int taps=factor==1?1:63;coefficients=new double[taps];firDelay=new double[taps];
        if(taps==1)coefficients[0]=1;else{double cutoff=.45/factor,sum=0;for(int i=0;i<taps;i++){int k=i-taps/2;double sinc=k==0?2*cutoff:Math.sin(2*Math.PI*cutoff*k)/(Math.PI*k);coefficients[i]=sinc*(.5-.5*Math.cos(2*Math.PI*i/(taps-1)));sum+=coefficients[i];}for(int i=0;i<taps;i++)coefficients[i]/=sum;}
    }
    public void accept(short[] pcm,int length,int sampleRate,double endSeconds,long segment,boolean needPitch){
        if(length<0||length>pcm.length||sampleRate<4000)throw new IllegalArgumentException("invalid PCM");if(sourceRate!=sampleRate||this.segment!=segment)reset(sampleRate,segment);
        for(int i=0;i<length;i++){
            firDelay[firIndex]=pcm[i]/32768.0;firIndex=(firIndex+1)%firDelay.length;
            if(++phase<factor)continue;phase=0;double value=0;
            for(int t=0;t<coefficients.length;t++)value+=coefficients[t]*firDelay[(firIndex-1-t+firDelay.length)%firDelay.length];
            signal[next]=value;next=(next+1)%signal.length;count=Math.min(signal.length,count+1);hop++;
            double seconds=endSeconds-(length-i-1)/(double)sampleRate;
            if(count==signal.length&&hop>=rate/10&&(lastFrameSeconds<0||seconds-lastFrameSeconds>=.099)){
                hop=0;lastFrameSeconds=seconds;SoundFrame frame=analyze(seconds,needPitch);history.add(frame);while(!history.isEmpty()&&(history.size()>125||history.get(0).seconds<seconds-12))history.remove(0);
            }
        }
    }
    private SoundFrame analyze(double seconds,boolean needPitch){
        int n=signal.length;double[] samples=new double[n],real=new double[n],imag=new double[n];double mean=0;
        for(int i=0;i<n;i++){samples[i]=signal[(next+i)%n];mean+=samples[i];}mean/=n;double power=0,windowSum=0;
        for(int i=0;i<n;i++){samples[i]-=mean;power+=samples[i]*samples[i];double window=.5-.5*Math.cos(2*Math.PI*i/(n-1));windowSum+=window;real[i]=samples[i]*window;}
        double rms=20*Math.log10(Math.max(1e-6,Math.sqrt(power/n)));AudioFft.forward(real,imag);float[] spectrum=new float[n/2+1];double[] amplitudes=new double[spectrum.length];
        for(int k=0;k<spectrum.length;k++){amplitudes[k]=Math.hypot(real[k],imag[k])*(k==0||k==n/2?1:2)/windowSum;spectrum[k]=(float)(20*Math.log10(Math.max(1e-6,amplitudes[k])));}
        PitchDetector.Pitch pitch=needPitch?PitchDetector.estimate(samples,rate,rms):new PitchDetector.Pitch(0,0);
        float[] harmonics=new float[8];Arrays.fill(harmonics,Float.NaN);
        if(pitch.hz>0){double fundamental=peak(amplitudes,pitch.hz*n/rate),strongest=0;for(double a:amplitudes)strongest=Math.max(strongest,a);if(fundamental>Math.max(1e-5,strongest*.001))for(int h=1;h<=8;h++)if(h*pitch.hz<rate/2.0){double a=peak(amplitudes,h*pitch.hz*n/rate);harmonics[h-1]=(float)(20*Math.log10(Math.max(1e-6,a)/fundamental));}}
        StabilityMetrics stability=StabilityMetrics.measure(history,segment,seconds,pitch.hz,rms);
        return new SoundFrame(segment,seconds,rate,n,pitch.hz,pitch.confidence,rms,spectrum,harmonics,stability);
    }
    private static double peak(double[] amplitudes,double bin){int center=(int)Math.round(bin),radius=Math.max(1,(int)Math.ceil(bin*.01));double maximum=0;for(int k=Math.max(1,center-radius);k<=Math.min(amplitudes.length-1,center+radius);k++)maximum=Math.max(maximum,amplitudes[k]);return maximum;}
    public List<SoundFrame> history(){return Collections.unmodifiableList(new ArrayList<>(history));}
    public SoundFrame latest(){return history.isEmpty()?null:history.get(history.size()-1);}
}
