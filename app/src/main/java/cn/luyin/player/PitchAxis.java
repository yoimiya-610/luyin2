package cn.luyin.player;

import java.util.*;

/** The same continuous log-frequency scale with Hz or scientific-pitch tick labels. */
public final class PitchAxis {
    public static final class Tick {
        public final double hz;
        public final String label;
        private Tick(double hz,String label){this.hz=hz;this.label=label;}
    }
    private static final List<Tick> HZ=Collections.unmodifiableList(Arrays.asList(new Tick(100,"100"),new Tick(400,"400"),new Tick(1600,"1600")));
    private static final List<Tick> NOTES;
    static{List<Tick> ticks=new ArrayList<>();for(int midi:new int[]{28,36,48,60,69,72,84,93}){double hz=frequency(midi);ticks.add(new Tick(hz,PitchDetector.note(hz)));}NOTES=Collections.unmodifiableList(ticks);}
    public static double frequency(int midi){return 440*Math.pow(2,(midi-69)/12.0);}
    public static double position(double hz){if(!Double.isFinite(hz)||hz<PitchDetector.MIN_HZ||hz>PitchDetector.MAX_HZ)return Double.NaN;return Math.log(PitchDetector.MAX_HZ/hz)/Math.log(PitchDetector.MAX_HZ/PitchDetector.MIN_HZ);}
    public static List<Tick> ticks(boolean notes){return notes?NOTES:HZ;}
    private PitchAxis(){}
}
