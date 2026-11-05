package cn.luyin.player;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Synchronous checkpoints keep already closed segments recoverable after process death. */
public final class CaptureJournal {
    public String id=UUID.randomUUID().toString(),title="",state="paused",message="";
    public long created=System.currentTimeMillis(),activeSince=0,total=0;
    public Recording active;
    public final List<Recording> rows=new ArrayList<>();
    public final SegmentLedger ledger=new SegmentLedger();
    public JSONObject json() throws JSONException {
        JSONArray items=new JSONArray();for(Recording r:rows)items.put(r.json());
        return new JSONObject().put("id",id).put("title",title).put("state",state).put("message",message)
            .put("created",created).put("activeSince",activeSince).put("total",total).put("rows",items)
            .put("active",active==null?JSONObject.NULL:active.json());
    }
    public void write(Context context) throws IOException {
        try{if(!context.getSharedPreferences("capture",0).edit().putString("journal",json().toString()).commit())throw new IOException("无法保存录音会话，请检查剩余空间");}
        catch(JSONException e){throw new IOException(e);}
    }
    public static CaptureJournal read(Context context) {
        String value=context.getSharedPreferences("capture",0).getString("journal",null);if(value==null)return null;
        try{JSONObject j=new JSONObject(value);CaptureJournal s=new CaptureJournal();s.id=j.getString("id");s.title=j.getString("title");s.state=j.getString("state");s.message=j.optString("message");s.created=j.getLong("created");s.activeSince=j.optLong("activeSince");s.total=j.optLong("total");
            JSONArray a=j.getJSONArray("rows");for(int i=0;i<a.length();i++){Recording r=Recording.from(a.getJSONObject(i));s.rows.add(r);s.ledger.saved.add(r.uri);}
            if(!j.isNull("active")){s.active=Recording.from(j.getJSONObject("active"));s.ledger.begin(s.active.uri);}s.ledger.finished=s.state.equals("finished");return s;
        }catch(JSONException e){return null;}
    }
}
