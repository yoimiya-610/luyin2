package cn.luyin.player;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONObject;

public final class Recording {
    public String uri, name, path, source;
    public long duration, date;
    public Recording(String uri, String name, String path, String source, long duration, long date) {
        this.uri=uri; this.name=name; this.path=path; this.source=source; this.duration=duration; this.date=date;
    }
    public MediaItem item() {
        return new MediaItem.Builder().setMediaId(uri).setUri(Uri.parse(uri))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(name).setArtist(source).build()).build();
    }
    public JSONObject json() {
        JSONObject j=new JSONObject();
        try { j.put("uri",uri).put("name",name).put("path",path).put("source",source).put("duration",duration).put("date",date); } catch(Exception ignored) {}
        return j;
    }
    public static Recording from(JSONObject j) {
        return new Recording(j.optString("uri"),j.optString("name"),j.optString("path"),j.optString("source",SourceRules.OTHER),j.optLong("duration"),j.optLong("date"));
    }
}
