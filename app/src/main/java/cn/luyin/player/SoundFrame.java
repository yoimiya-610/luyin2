package cn.luyin.player;

/** Immutable measurement; arrays are newly allocated by the analysis worker, never rewritten. */
public final class SoundFrame {
    public final long segment;
    public final double seconds,pitchHz,confidence,rmsDb;
    public final int sampleRate,fftSize;
    public final float[] spectrumDb,harmonicsDb;
    public final StabilityMetrics stability;
    public SoundFrame(long segment,double seconds,int rate,int fftSize,double pitch,double confidence,double rms,float[] spectrum,float[] harmonics,StabilityMetrics stability){this.segment=segment;this.seconds=seconds;sampleRate=rate;this.fftSize=fftSize;pitchHz=pitch;this.confidence=confidence;rmsDb=rms;spectrumDb=spectrum;harmonicsDb=harmonics;this.stability=stability;}
}
