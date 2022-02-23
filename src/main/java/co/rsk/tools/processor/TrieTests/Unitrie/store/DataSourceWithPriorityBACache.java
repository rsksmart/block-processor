package co.rsk.tools.processor.TrieTests.Unitrie.store;

import co.rsk.tools.processor.TrieTests.MyBAKeyValueRelation;
import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Map;

public class DataSourceWithPriorityBACache extends DataSourceWithCACache {

    public DataSourceWithPriorityBACache(KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithPriorityBACache(KeyValueDataSource base, int cacheSize,
                                 CacheSnapshotHandler cacheSnapshotHandler) {
        super(base,cacheSize,cacheSnapshotHandler);
    }


    // We need to limit the CAHashMap cache.

    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                               CacheSnapshotHandler cacheSnapshotHandler) {
        TrieCACacheRelation myKeyValueRelation = new TrieCACacheRelation();

        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
        int avgElementSize =88;
        long beHeapCapacity;
        boolean removeInBulk = true;
        float loadFActor = 0.3f;
        int initialSize = (int) (cacheSize/loadFActor);
        if (removeInBulk)
            beHeapCapacity =(long) cacheSize*avgElementSize*11/10;
        else
            beHeapCapacity =(long) cacheSize*avgElementSize*14/10;

        PrioritizedByteArrayHashMap bamap =  new PrioritizedByteArrayHashMap(initialSize,loadFActor,myKR,(long) beHeapCapacity,null,cacheSize);
        bamap.removeInBulk = removeInBulk;

        Map<ByteArrayWrapper, byte[]> cache =bamap;
        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }

        return cache;
    }
}
