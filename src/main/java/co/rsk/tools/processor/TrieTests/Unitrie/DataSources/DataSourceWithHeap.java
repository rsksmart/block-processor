package co.rsk.tools.processor.TrieTests.Unitrie.DataSources;

import co.rsk.tools.processor.TrieTests.MyBAKeyValueRelation;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArray39HashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArray63HashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArrayHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.ByteArrayRefHashMap;
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

    Path mapPath;
    Path dbPath;

    public DataSourceWithHeap(int maxNodeCount, long beHeapCapacity, String databaseName) throws IOException {
        super(databaseName);
        mapPath = Paths.get(databaseName, "hash.map");
        dbPath = Paths.get(databaseName, "store");

        Map<ByteArrayWrapper, byte[]> iCache = makeCommittedCache(maxNodeCount,beHeapCapacity);
        this.committedCache = Collections.synchronizedMap(iCache);

    }


    public String getModifiers() {
        return "";
    }

    public String getName() {
        return "DataSourceWithHeap-"+databaseName;
    }


    public void close() {
        flush();
        try {
            bamap.saveToFile(mapPath.toString());
            sharedBaHeap.save(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        committedCache.clear();
        dsKV.close();
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

        sharedBaHeap =
                createByteArrayHeap(loadFActor,maxNodeCount,beHeapCapacity);


        this.bamap =  new ByteArray39HashMap(initialSize,loadFActor,myKR,
                (long) beHeapCapacity,
                sharedBaHeap,0);

        File f = mapPath.toFile();
        if(f.exists() && !f.isDirectory()) {
            bamap.readFromFile(f.getAbsolutePath(), false);
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
