package cn.luyin.player;

import java.util.*;

/** A session never references an unfinished file as a saved segment. */
public final class SegmentLedger {
    public final List<String> saved=new ArrayList<>();
    public String active="";
    public boolean finished;
    public void begin(String uri) {
        if(finished||!active.isEmpty()||uri==null||uri.isEmpty())throw new IllegalStateException("当前不能开始新片段");
        active=uri;
    }
    public void complete(boolean valid) {
        if(active.isEmpty())throw new IllegalStateException("没有正在录制的片段");
        if(valid&&!saved.contains(active))saved.add(active);
        active="";
    }
    public void finish() {
        if(!active.isEmpty())throw new IllegalStateException("必须先保存当前片段");
        finished=true;
    }
}
