package cn.luyin.player;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.*;
import android.media.MediaMetadataRetriever;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public final class Library {
    private static final Object MUTATION_LOCK=new Object();
    private final Context context;
    private final android.content.SharedPreferences prefs;
    public Library(Context context) { this.context=context.getApplicationContext(); prefs=context.getSharedPreferences("library",0); }
    public synchronized List<Recording> load(String key) {
        List<Recording> result=rawLoad(key);result.removeIf(r->isHidden(r.uri));return result;
    }
    private synchronized List<Recording> rawLoad(String key) {
        List<Recording> result=new ArrayList<>();
        try { JSONArray a=new JSONArray(prefs.getString(key,"[]")); for(int i=0;i<a.length();i++)result.add(resolve(Recording.from(a.getJSONObject(i)))); } catch(JSONException ignored) {}
        return result;
    }
    public synchronized void save(String key,List<Recording> rows) {
        JSONArray a=new JSONArray(); for(Recording r:rows)a.put(r.json()); prefs.edit().putString(key,a.toString()).apply();
    }
    public void override(Recording r,String source) { prefs.edit().putString("source:"+r.uri,source).apply(); r.source=source; }
    public void addTree(Uri uri, String source) {
        Set<String> trees=new HashSet<>(prefs.getStringSet("trees",Collections.emptySet())); trees.add(uri.toString());
        prefs.edit().putStringSet("trees",trees).putString("tree:"+uri,source).apply();
    }
    public void clearTrees() { prefs.edit().remove("trees").apply(); }
    public List<Recording> scan(List<String> warnings) {
        LinkedHashMap<String,Recording> found=new LinkedHashMap<>();
        Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        try(Cursor c=querySharedAudio(collection)) {
            if(c!=null)while(c.moveToNext()) {
                int pathColumn=c.getColumnIndex("relative_path"),ownerColumn=c.getColumnIndex("owner_package_name");
                String path=pathColumn<0?null:c.getString(pathColumn),owner=ownerColumn<0?null:c.getString(ownerColumn),name=c.getString(c.getColumnIndexOrThrow("_display_name"));
                Recording r=new Recording(ContentUris.withAppendedId(collection,c.getLong(c.getColumnIndexOrThrow("_id"))).toString(),name==null?"未命名录音":name,path==null?"共享音频":path,SourceRules.classify(path,owner),c.getLong(c.getColumnIndexOrThrow("duration")),c.getLong(c.getColumnIndexOrThrow("date_modified"))*1000);
                found.put(r.uri,r);
            }
        } catch(SecurityException e) { warnings.add("尚未授权读取音频，已保留导入文件与授权文件夹。"); }
        catch(Exception e) { warnings.add("系统音频扫描失败，请重新扫描。"); }
        for(String tree:prefs.getStringSet("trees",Collections.emptySet())) {
            try { scanTree(Uri.parse(tree),DocumentsContract.getTreeDocumentId(Uri.parse(tree)),prefs.getString("tree:"+tree,SourceRules.OTHER),found,new HashSet<>(),0); }
            catch(Exception e) { warnings.add("一个文件夹无法读取，请重新授权该文件夹。"); }
        }
        for(Recording r:load("imports"))if(new File(Uri.parse(r.uri).getPath()).exists())found.put(r.uri,r);
        for(Recording r:load("owned")){if(found.containsKey(r.uri)){found.put(r.uri,r);continue;}try(Cursor c=context.getContentResolver().query(Uri.parse(r.uri),new String[]{"_id"},"is_pending=0",null,null)){if(c!=null&&c.moveToFirst())found.put(r.uri,r);}catch(Exception ignored){}}
        for(Recording r:found.values())resolve(r);
        List<Recording> rows=new ArrayList<>(found.values());rows.removeIf(r->isHidden(r.uri)); rows.sort((a,b)->Long.compare(b.date,a.date)); save("catalog",rows); return rows;
    }
    private Cursor querySharedAudio(Uri collection){
        String[] columns={"_id","_display_name","relative_path","duration","date_modified","owner_package_name"};
        try{return context.getContentResolver().query(collection,columns,"is_pending=0",null,"date_modified DESC");}
        catch(IllegalArgumentException unsupportedColumn){
            // Owner metadata is optional. A vendor provider rejecting it must not hide all recordings.
            return context.getContentResolver().query(collection,Arrays.copyOf(columns,columns.length-1),"is_pending=0",null,"date_modified DESC");
        }
    }
    public Recording resolve(Recording r) {
        for(int i=0;i<20;i++){String next=prefs.getString("uri:"+r.uri,null);if(next==null)break;r.uri=next;}
        r.name=prefs.getString("name:"+r.uri,r.name);
        r.source=prefs.getString("source:"+r.uri,r.source);return r;
    }
    public List<Preset> presets() {
        synchronized(MUTATION_LOCK){List<Preset> list=new ArrayList<>();try{
            JSONArray a=new JSONArray(prefs.getString("presets","[]"));for(int i=0;i<a.length();i++){Preset p=Preset.from(a.getJSONObject(i));for(Recording r:p.rows)resolve(r);p.rows.removeIf(r->isHidden(r.uri));list.add(p);}
        }catch(JSONException ignored){}return list;}
    }
    private void writePresets(List<Preset> list) throws IOException {
        JSONArray a=new JSONArray();try{for(Preset p:list)a.put(p.json());}catch(JSONException e){throw new IOException(e);}
        if(!prefs.edit().putString("presets",a.toString()).commit())throw new IOException("无法保存预设队列，请检查储存空间");
    }
    public void putPreset(Preset preset) throws IOException {
        synchronized(MUTATION_LOCK){List<Preset> list=presets();int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(preset.id)){index=i;break;}
            if(index<0)list.add(0,preset);else list.set(index,preset);writePresets(list);}
    }
    public void deletePreset(String id) throws IOException {synchronized(MUTATION_LOCK){List<Preset> list=presets();list.removeIf(p->p.id.equals(id));writePresets(list);}}
    public boolean isHidden(String uri){return prefs.getStringSet("hidden",Collections.emptySet()).contains(uri);}
    private void unhide(String uri){Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.emptySet()));if(hidden.remove(uri))prefs.edit().putStringSet("hidden",hidden).commit();}
    public void removeRecordings(List<Recording> rows)throws IOException{removeRecordings(rows,true);}
    public void restoreHidden(){prefs.edit().remove("hidden").apply();}
    public void removeRecordings(List<Recording> rows,boolean deleteIndexes)throws IOException{
        if(rows.isEmpty())return;synchronized(MUTATION_LOCK){Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.emptySet())),removed=new HashSet<>();for(Recording r:rows)removed.add(resolve(Recording.from(r.json())).uri);hidden.addAll(removed);
            if(!prefs.edit().putStringSet("hidden",hidden).commit())throw new IOException("录音已处理，但删除索引保存失败");
            for(String key:new String[]{"catalog","queue"})save(key,load(key));
            if(deleteIndexes)for(String key:new String[]{"imports","owned"}){List<Recording> remaining=rawLoad(key);remaining.removeIf(r->removed.contains(r.uri));save(key,remaining);}
            List<Preset> list=presets();list.removeIf(p->p.rows.isEmpty());writePresets(list);new FolderStore(context).forget(removed);
        }
    }
    public String reserveRecordingName() throws IOException {
        synchronized(MUTATION_LOCK){String day=new java.text.SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date());List<String> names=new ArrayList<>();
            try(Cursor c=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{"_display_name"},"relative_path=?",new String[]{recordingPath()},null)){if(c!=null)while(c.moveToNext())names.add(c.getString(0));}
            int next=NameRules.nextNumber(day,prefs.getInt("number:"+day,0),names);
            if(!prefs.edit().putInt("number:"+day,next).commit())throw new IOException("无法保存录音编号");return day+"-"+next;
        }
    }
    public static String recordingPath(){return (android.os.Build.VERSION.SDK_INT>=31?"Recordings/":"Music/")+"录音随听/";}
    public void addOwned(Recording r) throws IOException {
        synchronized(MUTATION_LOCK){List<Recording> list=rawLoad("owned");list.removeIf(item->item.uri.equals(r.uri));list.add(r);unhide(r.uri);JSONArray a=new JSONArray();for(Recording item:list)a.put(item.json());
            if(!prefs.edit().putString("owned",a.toString()).putString("name:"+r.uri,r.name).commit())throw new IOException("录音已写入，但索引保存失败");}
    }
    public void displayName(Recording r,String name) throws IOException {
        if(!prefs.edit().putString("name:"+r.uri,NameRules.clean(name)).commit())throw new IOException("名称保存失败");r.name=name.trim();
    }
    public Recording renameFile(Recording original,String title) throws Exception {
        synchronized(MUTATION_LOCK){Recording r=resolve(Recording.from(original.json()));String old=r.uri;Uri uri=Uri.parse(old);String originalName=r.name;
            if("file".equals(uri.getScheme()))originalName=new File(uri.getPath()).getName();
            else try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)originalName=c.getString(0);}
            String newName=NameRules.withExtension(title,originalName);
            if("file".equals(uri.getScheme())){
                File file=new File(uri.getPath()),destination=new File(file.getParentFile(),newName);
                if(!file.equals(destination)){if(destination.exists())throw new IOException("同名文件已存在");if(!file.renameTo(destination))throw new IOException("无法重命名此文件");r.uri=Uri.fromFile(destination).toString();}
            }else if(DocumentsContract.isDocumentUri(context,uri)){
                Uri renamed=DocumentsContract.renameDocument(context.getContentResolver(),uri,newName);
                if(renamed==null)throw new IOException("文件提供方不支持重命名");r.uri=renamed.toString();
            }else{
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,newName);
                if(context.getContentResolver().update(uri,values,null,null)<1)throw new IOException("文件不存在或不支持重命名");
            }
            android.content.SharedPreferences.Editor edit=prefs.edit().putString("name:"+r.uri,NameRules.clean(title));
            if(!old.equals(r.uri)){
                edit.putString("uri:"+old,r.uri).putString("source:"+r.uri,r.source);
                for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().startsWith("uri:")&&old.equals(entry.getValue()))edit.putString(entry.getKey(),r.uri);
            }
            if(!edit.commit())throw new IOException("文件已改名，但索引保存失败，请重新扫描");if(!old.equals(r.uri))new FolderStore(context).redirect(old,r.uri);r.name=title.trim();return r;
        }
    }
    private void scanTree(Uri tree,String id,String source,Map<String,Recording> found,Set<String> seen,int depth) throws Exception {
        if(depth>40||!seen.add(id))return;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] cols={"document_id","_display_name","mime_type","last_modified"};
        try(Cursor c=context.getContentResolver().query(children,cols,null,null,null)) {
            if(c==null)return;
            while(c.moveToNext()) {
                String child=c.getString(0),name=c.getString(1),mime=c.getString(2);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){scanTree(tree,child,source,found,seen,depth+1);continue;}
                if(name==null||!SourceRules.isAudio(name,mime))continue;
                Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,child);
                // External-storage document IDs expose a relative path; avoid duplicate MediaStore entries.
                int colon=child.indexOf(':'); String path=colon>=0?child.substring(colon+1):child;
                Recording duplicate=null;
                if(child.startsWith("primary:"))for(Recording r:found.values())if((r.path+r.name).equalsIgnoreCase(path)){duplicate=r;break;}
                String detected=SourceRules.classify(path,"");
                String category=SourceRules.CALL.equals(detected)?detected:source;
                if(duplicate!=null){duplicate.source=category;continue;}
                found.put(uri.toString(),new Recording(uri.toString(),name,path,category,0,c.getLong(3)));
            }
        }
    }
    public Recording importFile(Uri uri,String source) throws Exception {
        String name="导入录音",mime=context.getContentResolver().getType(uri);
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)name=c.getString(0);}
        if(!SourceRules.isAudio(name,mime))throw new IOException("请选择音频文件");
        File dir=new File(context.getFilesDir(),"recordings"); if(!dir.isDirectory()&&!dir.mkdirs()&&!dir.isDirectory())throw new IOException("无法创建导入目录");
        File temp=File.createTempFile("import-",".tmp",dir); MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=context.getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(temp)) {
            if(in==null)throw new IOException("无法打开音频"); byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);digest.update(buf,0,n);}
        } catch(Exception e){temp.delete();throw e;}
        StringBuilder hex=new StringBuilder(); for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b));
        long duration=0;MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{retriever.setDataSource(temp.getAbsolutePath());duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}catch(Exception ignored){}finally{try{retriever.release();}catch(Exception ignored){}}
        synchronized(MUTATION_LOCK){
        File dest=new File(dir,hex+".audio");
        String prior=prefs.getString("hash:"+hex,Uri.fromFile(dest).toString());Recording previous=resolve(new Recording(prior,name,"应用内导入副本",source,0,0));
        List<Recording> imports=rawLoad("imports");
        if("file".equals(Uri.parse(previous.uri).getScheme())&&new File(Uri.parse(previous.uri).getPath()).exists()){
            temp.delete();unhide(previous.uri);for(Recording r:imports)if(r.uri.equals(previous.uri))return r;
            previous.date=System.currentTimeMillis();imports.add(previous);save("imports",imports);return previous;
        }
        if(prefs.contains("uri:"+Uri.fromFile(dest)))dest=new File(dir,hex+"-"+UUID.randomUUID()+".audio");
        if(dest.exists())temp.delete(); else if(!temp.renameTo(dest)){temp.delete();throw new IOException("保存音频失败");}
        Recording row=new Recording(Uri.fromFile(dest).toString(),name,"应用内导入副本",source,duration,System.currentTimeMillis());
        prefs.edit().putString("hash:"+hex,row.uri).apply();unhide(row.uri);for(Recording r:imports)if(r.uri.equals(row.uri))return r;
        imports.add(row);save("imports",imports);return row;
        }
    }
}
