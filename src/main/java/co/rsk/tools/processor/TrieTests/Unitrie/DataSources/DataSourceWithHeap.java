package co.rsk.tools.processor.TrieTests.Unitrie.DataSources;

import co.rsk.tools.processor.TrieTests.MyBAKeyValueRelation;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArray40HashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArrayHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.TrieCACacheRelation;
import org.ethereum.db.ByteArrayWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DataSourceWithHeap extends DataSourceWithAuxKV {
    AbstractByteArrayHashMap bamap;
    ByteArrayHeap sharedBaHeap;
    EnumSet<AbstractByteArrayHashMap.CreationFlag> creationFlags;
    int dbVersion;

    Path mapPath;
    Path dbPath;


    public enum LockType {
        Exclusive,
        RW,
        None
    }

    public DataSourceWithHeap(int maxNodeCount, long beHeapCapacity,
                              String databaseName,LockType lockType,
                              EnumSet<AbstractByteArrayHashMap.CreationFlag> creationFlags,
                              int dbVersion) throws IOException {
        super(databaseName);
        this.creationFlags = creationFlags;
        this.dbVersion = dbVersion;
        mapPath = Paths.get(databaseName, "hash.map");
        dbPath = Paths.get(databaseName, "store");

        Map<ByteArrayWrapper, byte[]> iCache = makeCommittedCache(maxNodeCount,beHeapCapacity);
        if (lockType==LockType.RW)
            this.committedCache =RWLockedCollections.rwSynchronizedMap(iCache);
        else
        if (lockType==LockType.Exclusive)
            this.committedCache = Collections.synchronizedMap(iCache);
        else
            this.committedCache = iCache;

    }


    public String getModifiers() {
        return "";
    }

    public String getName() {
        return "DataSourceWithHeap-"+databaseName;
    }


    public void close() {
        dbLock.writeLock().lock();
        try {
            flush();
            try {
                bamap.save();
                sharedBaHeap.save(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
            committedCache.clear();
            dsKV.close();
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    ByteArrayHeap createByteArrayHeap(float loadFactor, long maxNodeCount, long maxCapacity) throws IOException {
        ByteArrayHeap baHeap = new ByteArrayHeap();
        baHeap.setMaxMemory(maxCapacity); //730_000_000L); // 500 Mb / 1 GB
        Files.createDirectories(Paths.get(databaseName));

        baHeap.setFileName(dbPath.toString());
        baHeap.setFileMapping(true);
        baHeap.initialize();
        if (baHeap.fileExists())
            baHeap.load(); // We throw away the root...
        return baHeap;

    }

    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int maxNodeCount, long beHeapCapacity) throws IOException {
        if (maxNodeCount==0) return null;

        Map<ByteArrayWrapper, byte[]> cache;

        TrieCACacheRelation myKeyValueRelation = new TrieCACacheRelation();

        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();

        float loadFActor =getDefaultLoadFactor();
        int initialSize = (int) (maxNodeCount/loadFActor);

        // Since we are not compressing handles, we must prepare for wost case
        // First we create a heap: this is where all values will be stored
        // in a continuous "stream" of data.
        sharedBaHeap =
                createByteArrayHeap(loadFActor,maxNodeCount,beHeapCapacity);

        // Now we create the map, which is like an index to locate the
        // information in the heap. ·"39" is the number of bits supported
        // in the datatype that references offsets in the heap.
        // 2^39 bytes is equivalent to 512 Gigabytes.
        //
        this.bamap =  new ByteArray40HashMap(initialSize,loadFActor,myKR,
                (long) beHeapCapacity,
                sharedBaHeap,0,creationFlags,dbVersion,0);

        this.bamap.setPath(mapPath);
        if (bamap.dataFileExists()) {
            bamap.load();
        }
        return bamap;
    }

    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        list.add("slotChecks: " +bamap.tableSlotChecks);
        list.add("lookups: " +bamap.tableLookups);
        list.add("slotchecks per lookup: " +1.0*bamap.tableSlotChecks/bamap.tableLookups);
        return list;
    }

}
