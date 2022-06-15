package co.rsk.tools.processor.TrieTests.Unitrie.DataSources;

import co.rsk.tools.processor.TrieTests.MyBAKeyValueRelation;
import co.rsk.tools.processor.TrieTests.Unitrie.DataSources.DataSourceWithCacheAndStats;
import co.rsk.tools.processor.TrieTests.Unitrie.LinkedByteArrayRefHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.MaxSizeLinkedByteArrayHashMap;
import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataSourceWithLinkedBACache extends DataSourceWithCacheAndStats {

    // I cannot set a default value because it's loaded after the super constructor
    protected boolean topPriorityOnAccess;

    public String getInitial(boolean v) {
        if (v) return "T"; else return "F";
    }

    public String getModifiers() {
        return "-tpoa_"+getInitial(topPriorityOnAccess);
    }

    public boolean getTopPriorityOnAccess() {
        return topPriorityOnAccess;
    }

    public void setTopPriorityOnAccess(boolean v) {
        topPriorityOnAccess = v;
        if (innerCache!=null)
            innerCache.setTopPriorityOnAccess(v);
    }

    public DataSourceWithLinkedBACache(KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithLinkedBACache(KeyValueDataSource base, int cacheSize,
                                 CacheSnapshotHandler cacheSnapshotHandler) {
        super(base,cacheSize,cacheSnapshotHandler);
        setTopPriorityOnAccess(true);
    }

    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                               CacheSnapshotHandler cacheSnapshotHandler) {
        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
        float loadFActor = getDefaultLoadFactor();

        int initialSize = (int) (cacheSize/loadFActor);
        LinkedByteArrayRefHeap sharedBaHeap = new LinkedByteArrayRefHeap(cacheSize,8);
        MaxSizeLinkedByteArrayHashMap bamap =  new MaxSizeLinkedByteArrayHashMap(initialSize,loadFActor,
                myKR,0,
                sharedBaHeap,cacheSize, topPriorityOnAccess);

        Map<ByteArrayWrapper, byte[]> cache =bamap;
        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }
        innerCache = bamap;
        return cache;
    }
    MaxSizeLinkedByteArrayHashMap innerCache;

    public long countCommittedCachedElements() {
        int c = innerCache.countElements();
        if (c!=innerCache.size()) {
            System.out.println("Size mismatch: "+c+" "+innerCache.size());
        }
        return c;
    }
    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        if (committedCache!=null) {
            list.add("longestFilledRun: "+innerCache.longestFilledRun());
            list.add("averageFilledRun: "+innerCache.averageFilledRun());
        }

        return list;
    }

    // It seems that load factors below 0.3f do not increase the speed
    // much. With 0.3f, the average key-hashes per lookup is 1.1.
    // With 0.1f, it is 1.05, only a 5% difference.
    static public float getDefaultLoadFactor() {
        return 0.3f;
    }

}
