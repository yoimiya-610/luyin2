package cn.luyin.player;

import java.util.*;
import java.util.concurrent.*;

/** Bounded asynchronous analysis cannot block the recorder/encoder thread. */
public final class LiveAnalysis {
    public static final class Snapshot {
        public final long segment;
        public final List<SoundFrame> frames;
        Snapshot(long segment,List<SoundFrame> frames){this.segment=segment;this.frames=frames;}
        public SoundFrame latest(){return frames.isEmpty()?null:frames.get(frames.size()-1);}
    }
    private static final class Chunk {
        final short[] pcm;final int rate;final long session,segment;final double seconds;final boolean pitch;
        Chunk(short[] pcm,int rate,long session,long segment,double seconds,boolean pitch){this.pcm=pcm;this.rate=rate;this.session=session;this.segment=segment;this.seconds=seconds;this.pitch=pitch;}
    }
    private final ArrayBlockingQueue<Chunk> queue=new ArrayBlockingQueue<>(2);
    private volatile Snapshot snapshot=new Snapshot(0,Collections.emptyList());
    private long session,segment;
    private double seconds;
    private volatile boolean enabled=true,needPitch=true;
    public LiveAnalysis(){Thread worker=new Thread(this::consume,"SoundAnalysis");worker.setDaemon(true);worker.start();}
    public synchronized void newSession(){session++;segment++;seconds=0;queue.clear();snapshot=new Snapshot(segment,Collections.emptyList());}
    public synchronized void boundary(){segment++;queue.clear();snapshot=new Snapshot(segment,snapshot.frames);}
    public synchronized void configure(boolean enabled,boolean needPitch){if(this.enabled!=enabled||this.needPitch!=needPitch)boundary();this.enabled=enabled;this.needPitch=needPitch;}
    public synchronized void submit(short[] pcm,int length,int rate){
        seconds+=length/(double)rate;if(!enabled)return;short[] copy=Arrays.copyOf(pcm,length);
        if(queue.remainingCapacity()==0){queue.clear();segment++;snapshot=new Snapshot(segment,snapshot.frames);}
        queue.offer(new Chunk(copy,rate,session,segment,seconds,needPitch));
    }
    private void consume(){AcousticAnalyzer analyzer=new AcousticAnalyzer();long seenSession=-1;while(true){try{
        Chunk chunk=queue.take();if(seenSession!=chunk.session){analyzer.clear();seenSession=chunk.session;}
        analyzer.accept(chunk.pcm,chunk.pcm.length,chunk.rate,chunk.seconds,chunk.segment,chunk.pitch);
        synchronized(this){if(chunk.session==session&&chunk.segment==segment)snapshot=new Snapshot(segment,analyzer.history());}
    }catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(RuntimeException ignored){/* Analysis never stops recording. */}}}
    public Snapshot snapshot(){return snapshot;}
}
