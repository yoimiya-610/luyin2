package cn.luyin.player;

import java.util.Locale;

/** Conservative source detection: an audio MIME type alone never proves recorder origin. */
public final class SourceRules {
    public static final String RECORDER = "录音机", CALL = "通话录音", OTHER = "其他音频", APP = "本机录音";
    public static String classify(String path, String owner) {
        String p = ("/" + path + "/").replace('\\', '/').toLowerCase(Locale.ROOT);
        if("cn.luyin.player".equals(owner)||p.contains("/录音随听/"))return APP;
        if (p.contains("/call_rec/") || p.contains("/callrecordings/") || p.contains("/call recordings/") || p.contains("/通话录音/")) return CALL;
        if ("com.android.soundrecorder".equals(owner) || "com.miui.soundrecorder".equals(owner)
                || p.contains("/sound_recorder/") || p.contains("/soundrecorder/") || p.contains("/录音机/")) return RECORDER;
        return OTHER;
    }
    public static boolean isAudio(String name, String mime) {
        if (mime != null && mime.startsWith("audio/")) return true;
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a|aac|wav|amr|3gp|ogg|opus|flac)$");
    }
    private SourceRules() {}
}
