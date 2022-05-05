package co.rsk.tools.processor.TrieTests.Unitrie.store;

import co.rsk.tools.processor.TrieTests.Logger;
import co.rsk.tools.processor.TrieTests.LoggerFactory;
import co.rsk.tools.processor.TrieTests.MyBAKeyValueRelation;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArrayRefHeap;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithHeap implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithheap");

    protected final Map<ByteArrayWrapper, byte[]> committedCache ;
    ByteArrayHashMap bamap;
    ByteArrayRefHeap sharedBaHeap;

    Path mapPath;
    Path dbPath;

    public boolean readOnly = false;
    boolean dump = false;

    long hits;
    long misses;
    long puts;
    long gets;
    String databaseName;

    public DataSourceWithHeap( int maxNodeCount, long beHeapCapacity,String databaseName) throws IOException {
        this.databaseName = databaseName;
        mapPath = Paths.get(databaseName, "hash.map");
        dbPath = Paths.get(databaseName, "store");

        Map<ByteArrayWrapper, byte[]> iCache = makeCommittedCache(maxNodeCount,beHeapCapacity);
        this.committedCache = Collections.synchronizedMap(iCache);
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        gets++;

        value = committedCache.get(wrappedKey);
        if (value != null) {
            hits++;
        } else
            misses++;


        if (dump) {
            if (value != null)
                System.out.println("Reading base key: " + wrappedKey.toString().substring(0, 8) +
                        " value " +
                        ByteUtil.toHexString(value).substring(0, 8) + ".. length " + value.length);
            else
                System.out.println("Reading base key: " + wrappedKey.toString().substring(0, 8) +
                        " failed");
        }
        return value;
    }

    public void checkReadOnly() {
        if (readOnly)
            throw new RuntimeException("read only DB");
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        checkReadOnly();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        return put(wrappedKey, value);
    }


    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        checkReadOnly();
        Objects.requireNonNull(value);
        puts++;
        if (dump) {
            System.out.println("Writing key " + wrappedKey.toString().substring(0, 8) +
                    " value " +
                    ByteUtil.toHexString(value).substring(0, 8) + ".. length " + value.length);
        }


        this.putKeyValue(wrappedKey, value);

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        committedCache.put(key, value);

    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {

        // always mark for deletion if we don't know the state in the underlying store
        this.putKeyValue(wrappedKey, null);
        return;
    }

    @Override
    public Set<byte[]> keys() {
        Stream<ByteArrayWrapper> committedKeys = null;

        committedKeys = committedCache.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(Map.Entry::getKey);

        // note that toSet doesn't work with byte[], so we have to do this extra step
        return committedKeys
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toSet());
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        rows.forEach(this::put);
        keysToRemove.forEach(this::delete);
    }

    @Override
    public void flush() {

    }

    public String getModifiers() {
        return "";
    }

    public String getName() {
            return "DataSourceWithHeap-"+databaseName;
    }

    public void init() {

    }

    public boolean isAlive() {
        return true;
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
    }

    ByteArrayRefHeap createByteArrayHeap(float loadFactor,long maxNodeCount, long maxCapacity) throws IOException {
        ByteArrayRefHeap baHeap = new ByteArrayRefHeap();
        baHeap.setMaxMemory(maxCapacity); //730_000_000L); // 500 Mb / 1 GB

        int expectedReferences = (int) (maxNodeCount*loadFactor+1);
        baHeap.setMaxReferences(expectedReferences);
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


        this.bamap =  new ByteArrayHashMap(initialSize,loadFActor,myKR,
                (long) beHeapCapacity,
                sharedBaHeap,0);

        File f = mapPath.toFile();
        if(f.exists() && !f.isDirectory()) {
            bamap.readFromFile(f.getAbsolutePath(), false);
        }
        return bamap;
    }

    public long countCommittedCachedElements() {
        if (committedCache!=null) {
            return committedCache.size();
        } else
            return 0;
    }

    public void resetHitCounters() {
        hits=0;
        misses=0;
        puts=0;
        gets=0;
    }

    public List<String> getHashtableStats() {
        List<String> list = new ArrayList<>();
        list.add("slotChecks: " +bamap.tableSlotChecks);
        list.add("lookups: " +bamap.tableLookups);
        list.add("slotchecks per lookup: " +1.0*bamap.tableSlotChecks/bamap.tableLookups);
        return list;
    }

    public List<String> getStats() {
        List<String> list = new ArrayList<>();
        list.add("puts: " + puts);
        list.add("gets: " + gets);
        long total = (hits + misses);
        list.add("committed cache hit [%]: " + hits * 100 /
                total);

        list.add("Hits: " + hits);
        list.add("Misses: " + misses);
        list.add("committedCache.size(): " + committedCache.size());

        return list;
    }

    static public float getDefaultLoadFactor() {
        return 0.5f; // This is MaxSizeHashMap default.
    }

    public void clear() {
    }
}
