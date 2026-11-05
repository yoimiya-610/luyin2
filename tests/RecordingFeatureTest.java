package cn.luyin.player;

import java.util.*;

public class RecordingFeatureTest {
    private static int count;
    private static void equal(Object actual,Object expected){count++;if(!Objects.equals(actual,expected))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void rejects(Runnable action){count++;try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("Invalid action was accepted");}
    public static void main(String[] args){
        equal(NameRules.nextNumber("20261104",0,List.of()),1);
        equal(NameRules.nextNumber("20261104",3,List.of("20261104-1.m4a","20261104-7.m4a")),8);
        equal(NameRules.nextNumber("20261105",0,List.of("20261104-99.m4a")),1);
        equal(NameRules.nextNumber("20261104",10,List.of("已重命名.m4a")),11);
        equal(NameRules.nextNumber("20261104",0,List.of("20261104-abc.m4a","20261104-2.M4A")),3);
        rejects(()->NameRules.nextNumber("20261104",Integer.MAX_VALUE,List.of()));
        equal(NameRules.clean("  会议片段  "),"会议片段");
        for(String bad:List.of("","..","../录音","a\\b","a:b","a\nb\nc","a\u0000b","a".repeat(101)))rejects(()->NameRules.clean(bad));
        equal(NameRules.withExtension("课堂第一段","20261104-1.m4a"),"课堂第一段.m4a");
        equal(NameRules.withExtension("课堂.M4A","20261104-1.m4a"),"课堂.M4A");
        SegmentLedger session=new SegmentLedger();session.begin("segment-1");
        equal(session.saved.size(),0);rejects(()->session.begin("segment-2"));rejects(session::finish);
        session.complete(true);equal(session.saved,List.of("segment-1"));equal(session.active,"");
        session.begin("too-short");session.complete(false);equal(session.saved,List.of("segment-1"));
        session.begin("segment-2");session.complete(true);equal(session.saved,List.of("segment-1","segment-2"));
        session.begin("segment-2");session.complete(true);equal(session.saved.size(),2);
        session.finish();equal(session.finished,true);session.finish();equal(session.saved.size(),2);rejects(()->session.begin("late"));
        SegmentLedger recovering=new SegmentLedger();recovering.saved.add("closed");recovering.begin("unfinished");recovering.complete(false);recovering.finish();equal(recovering.saved,List.of("closed"));
        SegmentLedger empty=new SegmentLedger();rejects(()->empty.complete(true));empty.finish();equal(empty.saved.size(),0);
        equal(SourceRules.classify("Recordings/录音随听/",""),SourceRules.APP);
        equal(SourceRules.classify("Recordings/","cn.luyin.player"),SourceRules.APP);
        System.out.println("Passed "+count+" naming, session boundary, recovery and source checks.");
    }
}
