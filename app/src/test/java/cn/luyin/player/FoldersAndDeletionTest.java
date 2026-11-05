package cn.luyin.player;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@androidx.media3.common.util.UnstableApi
public class FoldersAndDeletionTest {
    private Context context;
    private Library library;
    private FolderStore folders;
    private Recording a,b;
    private File first,second;
    @Before public void prepare()throws Exception{
        context=RuntimeEnvironment.getApplication();library=new Library(context);folders=new FolderStore(context);
        first=new File(context.getFilesDir(),"a.wav");second=new File(context.getFilesDir(),"b.wav");try(FileOutputStream out=new FileOutputStream(first)){out.write(WavHeader.pcm16(0,44100,1));}try(FileOutputStream out=new FileOutputStream(second)){out.write(WavHeader.pcm16(0,44100,1));}
        a=new Recording(Uri.fromFile(first).toString(),"a.wav","",SourceRules.APP,1000,1);b=new Recording(Uri.fromFile(second).toString(),"b.wav","",SourceRules.OTHER,2000,2);
    }
    @Test public void mixedSourcesCanBeMovedTogetherWithoutMovingFiles()throws Exception{AudioFolder f=folders.create("课堂");folders.move(List.of(a,b),f.id);assertEquals(f.id,new FolderStore(context).folderOf(a.uri));assertEquals(f.id,folders.folderOf(b.uri));assertTrue(first.exists());assertTrue(second.exists());assertTrue(folders.matches(a.uri,f.id));assertFalse(folders.matches(a.uri,FolderStore.UNFILED));}
    @Test public void renamingFolderKeepsMembership()throws Exception{AudioFolder f=folders.create("课堂");folders.move(List.of(a),f.id);folders.rename(f.id,"学习");assertEquals("学习",folders.list().get(0).name);assertEquals(f.id,folders.folderOf(a.uri));}
    @Test public void deletingFolderReturnsItemsToUnfiledAndKeepsFiles()throws Exception{AudioFolder f=folders.create("课堂");folders.move(List.of(a,b),f.id);folders.delete(f.id);assertTrue(folders.list().isEmpty());assertEquals(FolderStore.UNFILED,folders.folderOf(a.uri));assertTrue(first.exists());assertTrue(second.exists());}
    @Test public void duplicateAndReservedFolderNamesAreRejected()throws Exception{folders.create("会议");assertThrows(IllegalArgumentException.class,()->folders.create("会议"));assertThrows(IllegalArgumentException.class,()->folders.create("未分类"));assertThrows(IllegalArgumentException.class,()->folders.create("../录音"));}
    @Test public void fileRenameKeepsFolderMembership()throws Exception{AudioFolder f=folders.create("课堂");folders.move(List.of(a),f.id);library.save("imports",List.of(a));Recording renamed=library.renameFile(a,"第一课");assertNotEquals(a.uri,renamed.uri);assertEquals(f.id,folders.folderOf(renamed.uri));assertEquals(renamed.uri,library.load("imports").get(0).uri);}
    @Test public void deletingAudioRemovesDuplicateQueueAndPresetReferences()throws Exception{library.save("imports",List.of(a,b));library.save("queue",List.of(a,b,a));library.putPreset(new Preset("p","预设",0,List.of(a,b)));RecordingDeletion.Result result=new RecordingDeletion(context).delete(List.of(a,a),false);assertEquals(1,result.removed.size());assertFalse(first.exists());assertTrue(second.exists());assertEquals(1,library.load("queue").size());assertEquals(b.uri,library.load("queue").get(0).uri);assertEquals(1,library.presets().get(0).rows.size());assertTrue(library.isHidden(a.uri));}
    @Test public void hidingAndRestoringAnImportKeepsItsFileAndIndex()throws Exception{library.save("imports",List.of(a));new RecordingDeletion(context).delete(List.of(a),true);assertTrue(first.exists());assertTrue(library.load("imports").isEmpty());library.restoreHidden();assertEquals(a.uri,library.load("imports").get(0).uri);}
    @Test public void deletingAnotherFileDoesNotDiscardHiddenImportIndex()throws Exception{library.save("imports",List.of(a,b));new RecordingDeletion(context).delete(List.of(a),true);new RecordingDeletion(context).delete(List.of(b),false);library.restoreHidden();assertEquals(1,library.load("imports").size());assertEquals(a.uri,library.load("imports").get(0).uri);}
    @Test public void activeRecordingCannotBeDeletedOrHidden()throws Exception{CaptureJournal s=new CaptureJournal();s.active=a;s.ledger.begin(a.uri);s.state="recording";s.write(context);RecordingDeletion.Result result=new RecordingDeletion(context).delete(List.of(a),false);assertEquals(1,result.failed.size());assertTrue(first.exists());assertFalse(library.isHidden(a.uri));}
    @Test public void arbitraryFileOutsideAppStorageIsNotDeleted()throws Exception{File outside=new File(context.getCacheDir(),"not-managed.wav");try(FileOutputStream out=new FileOutputStream(outside)){out.write(1);}a.uri=Uri.fromFile(outside).toString();RecordingDeletion.Result result=new RecordingDeletion(context).delete(List.of(a),false);assertEquals(1,result.failed.size());assertTrue(outside.exists());}
    @Test public void hiddenSessionSegmentsAreNotRecoveredIntoPresets()throws Exception{CaptureJournal s=new CaptureJournal();s.rows.add(a);s.rows.add(b);s.ledger.saved.add(a.uri);s.ledger.saved.add(b.uri);s.total=3000;s.write(context);new RecordingDeletion(context).delete(List.of(a),true);CaptureJournal restored=CaptureJournal.read(context);assertEquals(1,restored.rows.size());assertEquals(2000,restored.total);assertEquals(b.uri,restored.rows.get(0).uri);}
    @Test public void oldJournalDefaultsToM4a()throws Exception{context.getSharedPreferences("capture",0).edit().putString("journal","{\"id\":\"old\",\"title\":\"旧录音\",\"state\":\"paused\",\"created\":1,\"rows\":[],\"active\":null}").commit();assertEquals(CaptureFormat.M4A,CaptureJournal.read(context).format);}
    @Test public void externalMediaDeleteRequiresConsentWhenDenied()throws Exception{registerMedia(true,false);Recording r=new Recording("content://media/external/audio/media/55","系统录音.m4a","",SourceRules.RECORDER,1000,0);RecordingDeletion.Result result=new RecordingDeletion(context).delete(List.of(r),false);assertEquals(1,result.consent.size());assertTrue(result.removed.isEmpty());assertFalse(library.isHidden(r.uri));}
    @Test public void providerReturningZeroMustNotPretendItDeletedAnExistingFile()throws Exception{registerMedia(false,true);Recording r=new Recording("content://media/external/audio/media/55","系统录音.m4a","",SourceRules.RECORDER,1000,0);RecordingDeletion.Result result=new RecordingDeletion(context).delete(List.of(r),false);assertEquals(1,result.failed.size());assertFalse(library.isHidden(r.uri));}
    @Test public void renamedImportCanBeDeletedAndImportedAgain()throws Exception{
        ContentProvider provider=new ContentProvider(){public boolean onCreate(){return true;}public Cursor query(Uri u,String[] p,String s,String[] a,String sort){MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});c.addRow(new Object[]{"课程.wav"});return c;}public String getType(Uri u){return "audio/wav";}public Uri insert(Uri u,ContentValues v){return u;}public int update(Uri u,ContentValues v,String s,String[] a){return 1;}public int delete(Uri u,String s,String[] a){return 0;}@Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(first,ParcelFileDescriptor.MODE_READ_ONLY);}};
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="import.test";info.applicationInfo=context.getApplicationInfo();provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal("import.test",provider);
        Uri source=Uri.parse("content://import.test/lecture");Recording imported=library.importFile(source,SourceRules.OTHER);Recording renamed=library.renameFile(imported,"重命名课程");new RecordingDeletion(context).delete(List.of(renamed),false);assertTrue(first.exists());
        Recording again=library.importFile(source,SourceRules.OTHER);assertFalse(library.isHidden(again.uri));assertTrue(new File(Uri.parse(again.uri).getPath()).exists());assertEquals(1,library.load("imports").size());assertEquals(again.uri,library.load("imports").get(0).uri);
    }
    private void registerMedia(boolean denied,boolean zero){ContentProvider provider=new ContentProvider(){public boolean onCreate(){return true;}public Cursor query(Uri u,String[] p,String s,String[] a,String sort){MatrixCursor c=new MatrixCursor(new String[]{"_id"});c.addRow(new Object[]{55});return c;}public String getType(Uri u){return "audio/mp4";}public Uri insert(Uri u,ContentValues v){return u;}public int update(Uri u,ContentValues v,String s,String[] a){return 1;}public int delete(Uri u,String s,String[] a){if(denied)throw new SecurityException("needs consent");return zero?0:1;}};android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="media";info.applicationInfo=context.getApplicationInfo();provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal("media",provider);}
}
