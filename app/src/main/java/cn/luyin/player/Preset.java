package cn.luyin.player;

import org.json.*;
import java.util.*;

public final class Preset {
    public String id,title;
    public long created;
    public final List<Recording> rows=new ArrayList<>();
    public Preset(String id,String title,long created,List<Recording> rows){this.id=id;this.title=title;this.created=created;this.rows.addAll(rows);}
    public JSONObject json() throws JSONException {
        JSONArray items=new JSONArray();for(Recording r:rows)items.put(r.json());
        return new JSONObject().put("id",id).put("title",title).put("created",created).put("rows",items);
    }
    public static Preset from(JSONObject j) throws JSONException {
        List<Recording> items=new ArrayList<>();JSONArray a=j.getJSONArray("rows");
        for(int i=0;i<a.length();i++)items.add(Recording.from(a.getJSONObject(i)));
        return new Preset(j.getString("id"),j.getString("title"),j.optLong("created"),items);
    }
}
