package cn.luyin.player;

public class SourceRulesTest {
    private static int count=0;
    private static void equal(Object actual,Object expected){count++;if(!actual.equals(expected))throw new AssertionError("Expected "+expected+", got "+actual);}
    public static void main(String[] args){
        equal(SourceRules.classify("MIUI/sound_recorder/",""),SourceRules.RECORDER);
        equal(SourceRules.classify("MIUI/sound_recorder/call_rec/","com.android.soundrecorder"),SourceRules.CALL);
        equal(SourceRules.classify("Recordings/","com.android.soundrecorder"),SourceRules.RECORDER);
        equal(SourceRules.classify("Music/",""),SourceRules.OTHER);
        equal(SourceRules.classify("Download/",""),SourceRules.OTHER);
        equal(SourceRules.classify("Recordings/",""),SourceRules.OTHER);
        equal(SourceRules.classify("MIUI\\Sound_Recorder\\",""),SourceRules.RECORDER);
        equal(SourceRules.classify("Recordings/CallRecordings/",""),SourceRules.CALL);
        equal(SourceRules.classify(null,null),SourceRules.OTHER);
        equal(SourceRules.classify("Music/not_sound_recorder/",""),SourceRules.OTHER);
        equal(SourceRules.isAudio("lecture.M4A",null),true);
        equal(SourceRules.isAudio("note.bin","audio/amr"),true);
        equal(SourceRules.isAudio("photo.jpg","image/jpeg"),false);
        equal(SourceRules.isAudio("lecture.mp3.exe",null),false);
        System.out.println("Passed "+count+" source classification and format checks.");
    }
}
