package cn.luyin.player;

/** Bounded peak envelope. Values come from MediaRecorder, never from animation noise. */
public final class WaveformBuffer {
    private final float[] values;
    private int next,count;
    public WaveformBuffer(int capacity){if(capacity<1)throw new IllegalArgumentException();values=new float[capacity];}
    public synchronized void add(int amplitude){
        int peak=Math.max(0,Math.min(32767,amplitude));
        values[next]=(float)(Math.log1p(peak)/Math.log1p(32767));
        next=(next+1)%values.length;count=Math.min(count+1,values.length);
    }
    public synchronized float[] snapshot(){
        float[] result=new float[count];int start=(next-count+values.length)%values.length;
        for(int i=0;i<count;i++)result[i]=values[(start+i)%values.length];return result;
    }
    public synchronized void clear(){next=0;count=0;java.util.Arrays.fill(values,0);}
}
