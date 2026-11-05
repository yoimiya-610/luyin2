package cn.luyin.player;

import android.content.*;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** App-local folders work across shared media, document providers, and private imported copies. */
public final class FolderStore {
    public static final String ALL="*",UNFILED="";
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    public FolderStore(Context context){prefs=context.getSharedPreferences("folders",0);}
    public List<AudioFolder> list(){synchronized(LOCK){List<AudioFolder> result=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("list","[]"));for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);result.add(new AudioFolder(j.getString("id"),j.getString("name")));}}catch(JSONException ignored){}return result;}}
    private void write(List<AudioFolder> list)throws IOException{JSONArray a=new JSONArray();try{for(AudioFolder f:list)a.put(new JSONObject().put("id",f.id).put("name",f.name));}catch(JSONException e){throw new IOException(e);}if(!prefs.edit().putString("list",a.toString()).commit())throw new IOException("文件夹保存失败");}
    private String name(String name){String title=NameRules.clean(name);if(title.equals("未分类")||title.equals("全部文件夹"))throw new IllegalArgumentException("请使用其他文件夹名称");return title;}
    public AudioFolder create(String name)throws IOException{synchronized(LOCK){String title=name(name);List<AudioFolder> list=list();for(AudioFolder f:list)if(f.name.equalsIgnoreCase(title))throw new IllegalArgumentException("文件夹名称已存在");AudioFolder folder=new AudioFolder(UUID.randomUUID().toString(),title);list.add(folder);write(list);return folder;}}
    public void rename(String id,String name)throws IOException{synchronized(LOCK){String title=name(name);List<AudioFolder> list=list();for(AudioFolder f:list)if(!f.id.equals(id)&&f.name.equalsIgnoreCase(title))throw new IllegalArgumentException("文件夹名称已存在");boolean found=false;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(id)){list.set(i,new AudioFolder(id,title));found=true;}if(!found)throw new IllegalArgumentException("文件夹不存在");write(list);}}
    public void delete(String id)throws IOException{synchronized(LOCK){List<AudioFolder> list=list();list.removeIf(f->f.id.equals(id));write(list);SharedPreferences.Editor e=prefs.edit();for(Map.Entry<String,?> item:prefs.getAll().entrySet())if(item.getKey().startsWith("item:")&&id.equals(item.getValue()))e.remove(item.getKey());if(!e.commit())throw new IOException("文件夹分类更新失败");}}
    public String folderOf(String uri){return prefs.getString("item:"+uri,UNFILED);}
    public void move(List<Recording> rows,String id)throws IOException{synchronized(LOCK){if(!id.isEmpty()&&list().stream().noneMatch(f->f.id.equals(id)))throw new IllegalArgumentException("文件夹不存在");SharedPreferences.Editor e=prefs.edit();for(Recording r:rows){if(id.isEmpty())e.remove("item:"+r.uri);else e.putString("item:"+r.uri,id);}if(!e.commit())throw new IOException("移动分类失败");}}
    public boolean matches(String uri,String folder){return ALL.equals(folder)||folderOf(uri).equals(folder);}
    public void redirect(String oldUri,String newUri){synchronized(LOCK){String folder=folderOf(oldUri);if(!folder.isEmpty())prefs.edit().putString("item:"+newUri,folder).remove("item:"+oldUri).commit();}}
    public void forget(Set<String> uris){synchronized(LOCK){SharedPreferences.Editor e=prefs.edit();for(String uri:uris)e.remove("item:"+uri);e.commit();}}
}
