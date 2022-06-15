package co.rsk.tools.processor.TrieTests;


import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.crypto.cryptohash.KeccakNative;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieTests.Unitrie.DNC.DecodedNodeCache;
import co.rsk.tools.processor.TrieTests.Unitrie.DNC.TrieWithDNCStore;
import co.rsk.tools.processor.TrieTests.Unitrie.DataSources.*;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.GlobalEncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.TrieWithENC;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.TrieWithENCStore;
import co.rsk.tools.processor.TrieTests.Unitrie.store.*;
import co.rsk.tools.processor.TrieTests.oheap.LongEOR;
import co.rsk.tools.processor.TrieTests.oheap.EncodedObjectHeap;
import co.rsk.tools.processor.TrieTests.orefheap.EncodedObjectRefHeap;
import co.rsk.tools.processor.TrieUtils.CompactTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.ByteUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class CompareTries extends Benchmark  {
    enum Database {
        LevelDB,
        RocksDB,
        FlatRefDB,// MemoryMappedByteArrayRefHeap
        FlatDB // MemoryMappedByteArrayHeap
    }

    enum HashMapDataStructure {
        NoCache,
        MaxSizeHashMap,
        MaxSizeCAHashMap,
        MaxSizeByteArrayHashMap,
        MaxSizeLinkedByteArrayHashMap
    }

    enum Test {
        readTest,
        writeTest
    }

    enum ReadMode {
        uniformlyRandom,
        pareto,
        sequential
    };
    ReadMode readMode = ReadMode.uniformlyRandom;
    long sequentialBase; // dynamically set later

    static Database database = Database.FlatDB;
    static Test test = Test.readTest;

    // For simCounter don't forget to set flushFilesystemCache to true
    StateTrieSimulator.SimMode testMode = StateTrieSimulator.SimMode.simEOAs;
    public static int subTreeCount = 64;
    boolean flushFilesystemCache = true;
    boolean createDatabase = true;
    boolean useDecodedNodeCache = true;
    boolean useNodeChain = false;
    boolean garbageCollectAndShowMemoryUsed = false;

    // To build huge trees bottom up, the best way is to use hard references
    // and shrink() every node during save
    boolean useWeakReferences = false;
    boolean shrinkNodesDuringStoreSave = false;
    boolean removeNodesDuringStoreSave = false;
    boolean saveNodesDuringConstruction = true;
    boolean pruneNodesDuringConstruction = true;
    boolean testAfterWrite = false;

    //////////////////////////////////////
    // These affect the write test AND the read test (to choose the file):
    long addMaxKeysBottomUp  = 1L * (1 << 26); // 8M keys
    long addMaxKeysTopDown = 0;//1L * (1 << 20);//1L * (1 << 20)/2; // total: 1.5M


    HashMapDataStructure hashMapDataStructure =
     //       HashMapDataStructure.NoCache;
           HashMapDataStructure.MaxSizeHashMap;
        //    HashMapDataStructure.MaxSizeByteArrayHashMap;
    //HashMapDataStructure.MaxSizeLinkedByteArrayHashMap;
    // HashMapDataStructure.MaxSizeByteArrayHashMap;

    static Class<? extends EncodedObjectStore> encodedStoreClass =
            null;
       // SoftRefEncodedObjectStore.class;
        //EncodedObjectHeap.class;
        //EncodedObjectHashMap.class;
            //HardEncodedObjectStore.class;
        //MultiSoftEncodedObjectStore.class;
        //EncodedObjectRefHeap.class;

    boolean fullyFillTopNodes = false;
    final int flushNumberOfBlocks =1000;

    //////////////////////////
    // Internal: do not set
    //////////////////////////////////////
    // For read test maxKeysBottom up must are loaded from addXXX:
    long maxKeysBottomUp  = 1L * (1 << 26);
    long maxKeysTopDown = 0;// 1L * (1 << 20);//1L * (1 << 20)/2; // total: 1.5M

    // 6.8M / 200 = 34K
    // Cost of read is 200 right now in RSK.
    final int blockGas = 6_800_000;

    final int writeCost = 20_000;
    // Each write to store takes 20K gas, which means that each block with 6.8M gas
    // can perform 340 writes.
    //
    int writesPerBlock = blockGas / writeCost;

    final int readCost = 2_000;
    final int readsPerBlock = blockGas / readCost;

    int testCacheSize = 1_000_000;
    boolean adjustCacheSize = true;

    /// ReadTest parameters
    long maxExistentReads = 1_000_000;
    //////////////////////////////////////////////////////////////////
    long max =  4096; //32L*(1<<20);// 8 Million nodes // 1_000_000;
    EncodedObjectStore ms;
    long remapTime =0;
    long remapTimeBelow50 =0;
    long remapTimeOver50 =0;


    Trie rootNode;

    //long maxKeysBottomUp ;
    //long maxKeysTopDown ;

    long randomReadsPerSecond;
    long randomExistentReadsPerSecond;
    long leafNodeCount;
    long nodeCount;
    long scannedLeafNodesPerSecond;
    long scannedNodesPerSecond;
    long timeToInsertElements;
    long timeToBuildTree;
    long leafNodeCounter;
    long blocksCreated;
    int log2Bits;


    void deleteDirectoryRecursion(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteDirectoryRecursion(entry);
                }
            }
        }
        Files.delete(path);
    }
    private long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files==null)
            return 0;

        int count = files.length;

        for (int i = 0; i < count; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            }
            else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }

    public void dumpTrieDBFolderSize() {
       log("TrieDB size: "+getFolderSize(new File(trieDBFolder.toString()))) ;
    }

    DataSourceWithCacheAndStats dsWithCache;
    long statesCacheSize;


    KeyValueDataSource dsDB;

    protected Class<? extends DataSourceWithCacheAndStats> getDSClass() {
        if (hashMapDataStructure==HashMapDataStructure.MaxSizeCAHashMap)
            return DataSourceWithCACache.class;
        else
        if (hashMapDataStructure==HashMapDataStructure.MaxSizeHashMap)
            return DataSourceWithCacheAndStats.class;
         else
        if (hashMapDataStructure==HashMapDataStructure.MaxSizeByteArrayHashMap)
        {
            return DataSourceWithBACache.class;
              }
        else
        if (hashMapDataStructure==HashMapDataStructure.MaxSizeLinkedByteArrayHashMap)
        {
            return DataSourceWithLinkedBACache.class;
             } else
        if (hashMapDataStructure==HashMapDataStructure.NoCache) {
            return DataSourceWithCacheAndStats.class;

        } else
            return null;
    }
    // This emulares rskj store building
    protected TrieStore buildTrieStore(Path trieStorePath,boolean deleteIfExists,boolean abortIfExists) {
        int statesCacheSize;

        if (ms != null)
            // We really don't need this cache. We could just remove it.
            // We give it a minimum size
            statesCacheSize = 10_000;
        else
            statesCacheSize = testCacheSize;

        if (hashMapDataStructure == HashMapDataStructure.NoCache) {
            statesCacheSize = 0;
        }
        if (adjustCacheSize) {
            // To compare apples with apples, in case we use
            // MaxSizeLinkedByteArrayHashMap, we have to increase the cache
            // by the ratio 216/138 = 1,565
            int fmul = 216;
            int fdiv = 138;
            // Because we can store more entries consuming the same memory.
            if (hashMapDataStructure == HashMapDataStructure.MaxSizeLinkedByteArrayHashMap)
                statesCacheSize = statesCacheSize * fmul / fdiv;
        }

        log("Database: " + trieStorePath.toAbsolutePath());

        if (abortIfExists) {
            if (Files.isDirectory(trieStorePath, LinkOption.NOFOLLOW_LINKS)) {

                System.out.println("Target trie db directory exists.");
                System.out.println("enter 'del' to delete it and continue");
                System.out.println("enter 'add' to add to the existing database and continue");
                // Enter data using BufferReader
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(System.in));

                // Reading data using readLine
                String cmd = null;
                try {
                    cmd = reader.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (cmd==null)
                    throw new RuntimeException("Aborting...");

                boolean isDel =(cmd.equals("del"));
                boolean isAdd =(cmd.equals("add"));
                if ((!isDel) && (!isAdd))
                    throw new RuntimeException("Invalid command. Target trie db directory exists. Remove first");
                if (isDel)
                    deleteIfExists = true;
            }

        }
        if (deleteIfExists) {
            try {
                log("deleting previous trie db");
                deleteDirectoryRecursion(trieStorePath);
                dumpTrieDBFolderSize();
            } catch (IOException e) {
                System.out.println("Could not delete database dir");
            }
        }
        if (database==Database.LevelDB) {
            //createLevelDBDatabase();
            dsDB = LevelDbDataSource.makeDataSource(trieStorePath);
        } else
        if (database==Database.RocksDB) {
            //createLevelDBDatabase();
            dsDB = RocksDbDataSource.makeDataSource(trieStorePath);
        } else
        if (database== Database.FlatDB) {
            long totalKeys = addMaxKeysBottomUp+ addMaxKeysTopDown;

            int maxNodeCount = (int) totalKeys*2;//32*1000*1000; // 32 Million nodes -> 128 Mbytes of reference cache
            long beHeapCapacity =64L*1000*1000*1000; // 64 GB
            try {
                dsDB = new DataSourceWithHeap(maxNodeCount,beHeapCapacity,trieStorePath.toString());
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        } else
        if (database== Database.FlatRefDB) {
            long totalKeys = addMaxKeysBottomUp+ addMaxKeysTopDown;

            int maxNodeCount = (int) totalKeys*2;//32*1000*1000; // 32 Million nodes -> 128 Mbytes of reference cache
            long beHeapCapacity =64L*1000*1000*1000; // 64 GB
            try {
                dsDB = new DataSourceWithRefHeap(maxNodeCount,beHeapCapacity,trieStorePath.toString());
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        KeyValueDataSource ds = dsDB;
        // in rskj flushNumberOfBlocks is 1000, so we should flush automatically every 1000
        // blocks
        if (hashMapDataStructure == HashMapDataStructure.MaxSizeCAHashMap)
            ds = new DataSourceWithCACache(ds, statesCacheSize, null);
        else if (hashMapDataStructure == HashMapDataStructure.MaxSizeHashMap)
            ds = new DataSourceWithCacheAndStats(ds, statesCacheSize, null);
        else if (hashMapDataStructure == HashMapDataStructure.MaxSizeByteArrayHashMap) {
            ds = new DataSourceWithBACache(ds, statesCacheSize, null);
        } else if (hashMapDataStructure == HashMapDataStructure.MaxSizeLinkedByteArrayHashMap) {
            ds = new DataSourceWithLinkedBACache(ds, statesCacheSize, null);
        } else if (hashMapDataStructure == HashMapDataStructure.NoCache) {
            ds = new DataSourceWithCacheAndStats(ds, 0, null);

        }
        log("statesCacheSize: " + statesCacheSize);


        dsWithCache = (DataSourceWithCacheAndStats) ds;
        log("Datasource modifiers: " + dsWithCache.getModifiers());
        this.statesCacheSize = statesCacheSize;

        return createTrieStore(ds);
    }

    TrieStore createTrieStore(KeyValueDataSource ds) {
        TrieStoreImpl imp=null;
        if (GlobalEncodedObjectStore.get() != null)
            imp =  new TrieWithENCStore(ds);
        else if (useWeakReferences)
            imp =  new TrieWithDNCStore(ds,useDecodedNodeCache,useNodeChain);
        else
            imp = new TrieStoreImpl(ds);

        imp.shrinkDuringSave = shrinkNodesDuringStoreSave;
        imp.removeDuringSave = removeNodesDuringStoreSave;

        return (TrieStore) imp;
    }

    StateTrieSimulator stateTrieSim = new StateTrieSimulator();

    public void computeAverageAccountSize() {
        stateTrieSim.computeAverageAccountSize();
        log("Average account size: "+stateTrieSim.accountSize);
    }

    public void prepare() {
        // in satoshis
        // 0.1 bitcoin
        //TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());

        stateTrieSim.setSimMode(testMode);
        computeAverageAccountSize();
        computeKeySizes();

        ms = GlobalEncodedObjectStore.get();
        remapTime=0;
        remapTimeBelow50 =0;
        remapTimeOver50 =0;
    }

    public void computeKeySizes() {

        stateTrieSim.computeKeySizes();
        log("TestMode: "+testMode.toString());
        log("varKeysize: "+ stateTrieSim.varKeySize);
        log("fixKeysize: "+ stateTrieSim.fixKeySize);
        log("keysize: "+(stateTrieSim.varKeySize+stateTrieSim.fixKeySize));

        log("valueSize: "+stateTrieSim.valueSize);
    }

    public void printMemStatsShort() {
        if (!(ms instanceof EncodedObjectHeap))
            return;

        EncodedObjectHeap ms = (EncodedObjectHeap) this.ms;

        log("InMemStore usage[%]: " + ms.getUsagePercent());
        log("InMemStore usage[Mb]: " + ms.getMemUsed() / 1000 / 1000);

        log("total remap time[s]: " + remapTime / 1000);
        log("remap time below 50% [s]: " + remapTimeBelow50 / 1000);
        log("remap time above 50% [s]: " + remapTimeOver50 / 1000);

        log("remap time per insertion[msec]: " + (remapTime * 1.0 / max));
    }

    public void printMemStats(String s) {
        if (ms==null) return;
        List<String> stats= ms.getStats();
        logList(s + " InMemStore ",stats);

        if (ms!=null) {
            if (ms.supportsGarbageCollection())
                log("-- Store percentage use: " + ms.getUsagePercent()+"%");
        }
    }


    public void garbageCollection(Trie t) {
       if (!ms.supportsGarbageCollection())
            return;


        log(":::::::::::::::::::::::::::::::::::::");
        log(":: Remapping from: "+ms.getUsagePercent()+"%");
        if (ms.getUsagePercent()>50)
            ms.getUsagePercent();
        long rstarted = System.currentTimeMillis();
        long rstartMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        log(":: Remap Used Before MB: " + rstartMbs);
        ms.beginRemap();
        log(":: Remap removing spaces: "+ ms.getGarbageCollectionDescription());
        ((TrieWithENC) t).compressEncodingsRecursivelly();
        //t.checkTree();
        long rended = System.currentTimeMillis();

        long  relapsedTime = (rended - rstarted) ;

        if (ms.getUsagePercent()<50)
            remapTimeBelow50 +=relapsedTime;
        else
            remapTimeOver50 +=relapsedTime;
        ms.endRemap();

        log(":: Remapping   to: "+ms.getUsagePercent()+"%");
        printMemStats("::");
        remapTime +=relapsedTime;
        log(":: Remap Elapsed time [msec]: " + relapsedTime);
        log(":: Remap Total Elapsed time [msec]: " + remapTime);
        log(":: Remap compressionPercent [%]: "+ ms.getCompressionPercent());
        System.gc();
        long rendMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        log(":: Remap Used After MB: " + rendMbs );
        log(":: Remap Freed MBs: " + (rstartMbs-rendMbs));
        log(":::::::::::::::::::::::::::::::::::::");
        //countNodes(rootNode);

    }

    byte[] fixedKeyPart;

    public  byte[] getExistentKey(long x) {

        pseudoRandom.setSeed(x);

        byte[] key;
        if (x<maxKeysBottomUp) {
            // For each subtree, there are specific trailing bits (leafBits)
            // From the seed we can build the subTreeBits.
            // the value of x must be reversed, because the tree is built bottom up,
            // so the first bit is the tail bit.
            int treeCounter = (int) x;
            byte[] counter = new byte[4];
            // Most significant byte first
            ObjectIO.putInt(counter,0,treeCounter);
            ExpandedTrieKeySlice eSplit = (ExpandedTrieKeySlice) ExpandedTrieKeySlice.getFactory().
                    fromEncoded(counter,32-splitBits,splitBits);
            //ExpandedTrieKeySlice erev = eks.revert();
            //PseudoRandom.moveBits(erev.encode(),0,key,fixKeySize*8,splitBits);
            byte[] leafData  = new byte[(leafBits+7)/8];
            pseudoRandom.fillRandomBits(leafData,0 ,leafBits);
            ExpandedTrieKeySlice eLeaf = (ExpandedTrieKeySlice) ExpandedTrieKeySlice.getFactory().
                    fromEncoded(leafData,0,leafBits);

            ExpandedTrieKeySlice eFix = (ExpandedTrieKeySlice) ExpandedTrieKeySlice.getFactory().
                    fromEncoded(fixedKeyPart,0,stateTrieSim.fixKeySize*8);

            ExpandedTrieKeySlice eAll = (ExpandedTrieKeySlice) eFix.append(eSplit.append(eLeaf));
            key = eAll.encode();

        } else {
            key = new byte[stateTrieSim.fixKeySize+stateTrieSim.varKeySize];
            System.arraycopy(fixedKeyPart,0,key,0,stateTrieSim.fixKeySize);
            pseudoRandom.fillRandomBytes(key,stateTrieSim.fixKeySize,stateTrieSim.varKeySize);
        }
        return key;
    }



    public  byte[] getExistentRandomKey(long i) {
        // choose according to the number of keys inserted by each method.
        // Each method (top down and bottom up) has created a set of keys.
        // Truly since keys from bottom up and top down construction are intermingled
        // it would be equally uniform if we only picked keys from the set with higher
        // amount of keys created. However, we pick from both to make if fairer if the
        // number of keys chosen overpasses the number of elements in on of the set.
        // We use the remainder "%" without introducing a huge imbalance, because our
        // set sizes are int32 ranges (not longs really).
        long allKeys = addMaxKeysBottomUp+addMaxKeysTopDown;
        long x =0;
        switch (readMode) {
            case uniformlyRandom:
                x=getExistentUniformlyRandomKeyIndex(allKeys);
            case pareto:
                x= getExistentParetoRandomKeyIndex(allKeys);
            case sequential:
                x =getExistentSequentialKeyIndex(i,allKeys);
        }

        return getExistentKey(x);
    }
    public  long getExistentSequentialKeyIndex(long i,long allKeys) {
        return (i + sequentialBase) % allKeys;

    }

    public  long getExistentParetoRandomKeyIndex(long allKeys) {
        double p = 0.8;
        if (p <= 0 || p >= 1.0)
            throw new IllegalArgumentException();
        double a = Math.log(1.0 - p) / Math.log(p);
        double x = TestUtils.getPseudoRandom().nextDouble();
        double y = (Math.pow(x, a) + 1.0 - Math.pow(1.0 - x, 1.0 / a)) / 2.0;
        return (int) (y*allKeys);
    }

    public  long getExistentUniformlyRandomKeyIndex(long allKeys) {

        long x =TestUtils.getPseudoRandom().nextLong(allKeys);
        return x;

    }
    TrieStore trieStore;

    public void createInMemoryTrieStore() {
        //trieStore = new DummyCacheTrieStore();
        dsWithCache = new DataSourceWithCacheAndStats(null,Integer.MAX_VALUE,null);
        trieStore = createTrieStore(dsWithCache);
    }

    public void openTrieStore(boolean deleteIfExists,boolean abortIfExists,String dbName) {
        Path trieDBFolderPlusSize = trieDBFolder;

        if ((dbName!=null) && (dbName.length()>0)) {
            if ((dbName.indexOf("..")>=0) || (dbName.indexOf("/")>=0))
                    throw new RuntimeException("sanity check");
            trieDBFolderPlusSize = trieDBFolder.resolve(dbName);
        }
        if (trieStore==null)
            trieStore = buildTrieStore(trieDBFolderPlusSize,deleteIfExists,abortIfExists);
    }

    public TrieStore getTrieStore() {
        return trieStore;
    }
    Path trieDBFolder =Path.of("./triestore");

    public void createOrAppendToRootNode() {
        if (rootNode ==null) {
            rootNode = trieStore.getTrieFactory().newTrie(getTrieStore());
            leafNodeCounter =0;
        }
    }

    public int getLog2Bits(long max) {
        int log2Bits = log2(max);
        if (log2Bits>32) log2Bits=32;
        return log2Bits;
    }



    public void chooseKey(byte[] key,int log2Bits,long i) {
        if (fullyFillTopNodes) {
            TrieTestUtils.fillWithCounter(key,log2Bits,stateTrieSim.fixKeySize,i);
            if (testMode!=StateTrieSimulator.SimMode.simCounter)
                TrieTestUtils.fillExtraBytesWithRandom(pseudoRandom,key,log2Bits,stateTrieSim.fixKeySize,stateTrieSim.varKeySize);
        } else
            pseudoRandom.fillRandomBytes(key,stateTrieSim.fixKeySize,stateTrieSim.varKeySize);

    }
    public void storeKeyValue(byte[] key, byte[] value) {
        // Put full byte-aligned key or bit-aligned key slice
        if (testMode!=StateTrieSimulator.SimMode.simCounter)
            rootNode = rootNode.put(key, value);
        else {
            TrieKeySlice eks = ExpandedTrieKeySlice.getFactory().
                    fromEncoded(key, 0, log2Bits);
            rootNode =  rootNode.put(eks,value,false);
        }
    }

    public void buildByInsertion() {
        log("buildByInsertion max="+max);
        maxKeysTopDown = max;
        prepare();

        start(true);
        createOrAppendToRootNode();


        // If the fixed key part has already been selected by bottom up construction
        // then do not select again!
        computeFixedKeyPart();
        byte[] key = new byte[stateTrieSim.fixKeySize+stateTrieSim.varKeySize];
        System.arraycopy(fixedKeyPart,0,key,0,stateTrieSim.fixKeySize);
        log2Bits = getLog2Bits(max);


        for (long i = 0; i < max; i++) {
            //byte[] key = TestUtils.randomBytes(varKeySize);

            pseudoRandom.setSeed(leafNodeCounter++);
            chooseKey(key,log2Bits,i);
            byte[] value = TestUtils.getPseudoRandom().randomBytes(stateTrieSim.valueSize);
            storeKeyValue(key,value);

            if (writesPerBlock>0) {
                if (i % writesPerBlock == writesPerBlock - 1) {
                    simulateNewBlock(true);
                }
            }
            if (i % 100000 == 0) {
                dumpProgress(i,max);
                logTraceInfo();
            }
            if (shouldRunGC()) {
                garbageCollection(rootNode);
            }
        }
        simulateNewBlock(true);
        flushTrie();

        stop(true);
        dumpResults();
        dumpTrieDBFolderSize();
        timeToInsertElements = elapsedTime;
        //countNodes(rootNode);
    }



    public void flushTrie() {
        log("Flushing trie to disk");
        if (getTrieStore()==null)
            return;
        getTrieStore().flush();
    }

    public void saveTrie() {
        log("Saving trie (start)");
        if (getTrieStore()==null)
            return;
        getTrieStore().saveRoot(rootNode);
        log("Saving trie (stop) rootNodeHas="+rootNode.getHash().toHexString());
    }

    public void saveSubTrie(Trie subtrieRoot) {
        log("Saving subtrie (start)");
        if (getTrieStore()==null)
            return;
        getTrieStore().save(subtrieRoot);
        log("Saving subtrie (stop)");
    }

    public void simulateNewBlock(boolean sSaveTrie) {
        // Every N writes, add one to the global clock, so elements inserted
        // get tagged with newer times
        GlobalClock.setTimestamp(GlobalClock.getTimestamp() + 1);
        if (sSaveTrie)
            saveTrie();
        destroyTree();
        // too much noise: log("New block");



        blocksCreated++;
        if (sSaveTrie)
            if (blocksCreated % flushNumberOfBlocks==0)
                flushTrie();
    }

   public boolean shouldRunGC() {
        if (ms==null)
            return false;

       return ms.heapIsAlmostFull();
   }

    public void userGarbageCollector() {
        if (ms != null) {
            garbageCollection(rootNode);
        }
    }

    public void dumpResults() {
        long elapsedTimeMs = (ended - started);
        elapsedTime = elapsedTimeMs / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            log("Added nodes/sec: " + (max *1000L/ elapsedTimeMs));
            log("Added blocks/sec: " + (blocksCreated*1000L / elapsedTimeMs));
            if (blocksCreated>0)
                log("Avg processing time/block [msec]: " +  elapsedTimeMs/blocksCreated);
        }

        log("Memory used after test: MB: " + endMbs);
        log("Consumed MBs: " + (endMbs - startMbs));
        showUsedMemory();

        printMemStatsShort();

    }

    public void computeFixedKeyPart() {
        if (fixedKeyPart == null) {
            pseudoRandom.setSeed(0); // I want to replicate this easily
            fixedKeyPart = pseudoRandom.randomBytes(stateTrieSim.fixKeySize);
            log("fixedKeyPart: "+ByteUtil.toHexString(fixedKeyPart));
        }
    }

    public void existentCounterReadNodes(Trie t,boolean includeTopDownKeys,
                                         long maxReads,int passes,
                                  boolean destroyTreeOnNewBlock,
                                  boolean destroyTreeAfterEachLookup,
                                  int counterBits) {
        byte[][] keys = null;
        boolean preloadKeys = passes>1;

        boolean visual = passes<=1;

        computeFixedKeyPart();
        showCacheStats();

        int totalKeys =(int)  (maxKeysTopDown);

        if (maxReads==0)
            maxReads = 1_000_000;
        long totalReads = maxReads*passes;
        logNewSection("random counter existent key read...("+totalReads+")");

        started = System.currentTimeMillis();
        // now we count something

        int found=0;

        boolean debugQueries = false;


        log("destroyTreeOnNewBlock: "+destroyTreeOnNewBlock);
        log("destroyTreeAfterEachLookup: "+destroyTreeAfterEachLookup);

        start(false);
        for(int p=0;p<passes;p++) {

            // Force the sequence of pseudo-random elements to be always the same.
            TestUtils.getPseudoRandom().setSeed(1);

            for (int i = 0; i < maxReads; i++) {
                if (destroyTreeOnNewBlock) {
                    if (i % readsPerBlock == readsPerBlock-1) {
                        simulateNewBlock(false);
                    }
                }
                if (visual) {
                    if (i % 50000 == 0)
                        System.out.println("iteration: " + i);
                }
                byte[] key;

                TrieKeySlice k = TrieTestUtils.getTrieKeySliceWithCounter(i  % totalKeys,counterBits,stateTrieSim.fixKeySize);

                if ((visual) && (i < 3)) {
                    log("" + i + ": fetching " + k.toString());
                }
                if (t.findReuseSlice(k) == null) {
                    System.out.println("error: " + i + " key: " + k.toString());
                    System.exit(1);
                }
                if (destroyTreeAfterEachLookup) {
                    destroyTree();
                }
            }
        }
        log("maxReads: "+maxReads);
        log("passes: "+passes);
        log("totalReads: "+totalReads);

        stop(true);

        //ended = System.currentTimeMillis();
        long elapsedTimeMs = (ended - started);
        log("Elapsed time [ms]: " + elapsedTimeMs);
        if (elapsedTimeMs!=0) {
            randomExistentReadsPerSecond =(totalReads * 1000 / elapsedTimeMs);
            log("Read existent leaf  nodes/sec: " + randomExistentReadsPerSecond);
            log("Read intermediate nodes/sec: " + randomExistentReadsPerSecond*counterBits);
        }
        showCacheStats();
        logEndSection("Finished.");
    }

    public void existentReadNodes(Trie t,boolean includeTopDownKeys,long maxReads,int passes,
                                  boolean destroyTreeOnNewBlock,
                                  boolean destroyTreeAfterEachLookup,
                                  int expectedSpeed) {
        byte[][] keys = null;
        boolean preloadKeys = passes>1;
        sequentialBase = maxKeysBottomUp;
        boolean x = false;
        if (x)
            return;

        boolean visual = passes<=1;

        computeFixedKeyPart();
        showCacheStats();

        int totalKeys =(int) (maxKeysBottomUp );
        if (includeTopDownKeys)
            totalKeys += (int) (maxKeysTopDown);

        int dumpIterations1 =50_000;
        int dumpIterations2 =500_000;

        if ((expectedSpeed<10_000) && (expectedSpeed>0)) {
            dumpIterations1 = dumpIterations1/20;
            dumpIterations2 = dumpIterations2/50;
        }
        if (maxReads==0)
            maxReads = 1_000_000;
        long totalReads = maxReads*passes;
        logNewSection("random existent key read...("+totalReads+")");

        if (preloadKeys) {
            log("Preloading keys");
            // Force the sequence of pseudo-random elements to be always the same.
            TestUtils.getPseudoRandom().setSeed(1);

            keys = new byte[(int) (totalKeys)][];
            for (int i = 0; i < keys.length; i++) {
                byte[] key;

                key = getExistentRandomKey(i);
                keys[i] = key;
            }
        }
        started = System.currentTimeMillis();
        // now we count something

        int found=0;

        boolean debugQueries = false;


        log("destroyTreeOnNewBlock: "+destroyTreeOnNewBlock);
        log("destroyTreeAfterEachLookup: "+destroyTreeAfterEachLookup);

        for(int p=0;p<passes;p++) {

            // Force the sequence of pseudo-random elements to be always the same.
            TestUtils.getPseudoRandom().setSeed(1);

            for (int i = 0; i < maxReads; i++) {
                if (destroyTreeOnNewBlock) {
                    if (i % readsPerBlock == readsPerBlock-1) {
                        if (expectedSpeed<10_000)
                            log("New block");
                        simulateNewBlock(false);
                    }
                }
                if (visual) {
                    if (i % dumpIterations1 == 0) {
                        System.out.println("iteration: " + i);
                        dumpProgress(i+p*maxReads,totalReads);
                        if (i % dumpIterations2==0) {
                            showDBStats();
                            showCacheStats();
                        }
                    }
                }
                byte[] key;

                if (preloadKeys)
                    key = keys[i % keys.length];
                else {
                    key = getExistentRandomKey(i);
                }
                if ((visual) && (i < 3)) {
                    log("" + i + ": fetching " + ByteUtil.toHexString(key));
                }
                if (debugQueries) {
                    TrieKeySlice eks = ExpandedTrieKeySlice.getFactory().
                            fromEncoded(key, 0, key.length * 8);
                    System.out.println("look for key: " + eks.toString());
                    System.out.println("iter: " + i + " key: " + ByteUtil.toHexString(key));
                }
                if (t.findReuseSlice(key) == null) {
                    System.out.println("error: " + i + " key: " + ByteUtil.toHexString(key));
                    System.exit(1);
                }
                if (destroyTreeAfterEachLookup) {
                    destroyTree();
                }
            }
        }
        log("maxReads: "+maxReads);
        log("passes: "+passes);
        log("totalReads: "+totalReads);

        ended = System.currentTimeMillis();
        long elapsedTimeMs = (ended - started);
        log("Elapsed time [ms]: " + elapsedTimeMs);
        if (elapsedTimeMs!=0) {
            randomExistentReadsPerSecond =(totalReads * 1000 / elapsedTimeMs);
            log("Read existent leaf nodes/sec: " + randomExistentReadsPerSecond);

        }
        showCacheStats();
        logEndSection("Finished.");
    }

    public void showCacheStats() {
        if (dsWithCache!=null) {
            List<String> stats =dsWithCache.getStats();
            log("Cache: ");
            logList(" ",stats);
            dsWithCache.resetHitCounters();

            log("Cached elements: "+dsWithCache.countCommittedCachedElements());
        }
        log("trieNodesRetrieved: "+TrieImpl.trieNodesRetrieved);
        TrieImpl.trieNodesRetrieved =0;
        log("DecodedNodeCache cachedNodes: "+DecodedNodeCache.get().getCachedNodes());
    }

    public void randomReadNodes(Trie t,long maxReads) {

        started = System.currentTimeMillis();
        // now we count something
        logNewSection("random read...");

        if (maxReads==0)
            maxReads = 10_000_000;

        int found=0;
        for(int i=0;i<maxReads;i++) {
            if (i % readsPerBlock==0) {
                simulateNewBlock(false);
            }
            byte[] key = TestUtils.getPseudoRandom().randomBytes(stateTrieSim.varKeySize);
            if (t.find(key)!=null)
                found++;
        }
        log("numReads: "+maxReads);
        log("found leaf nodes: "+found);

        ended = System.currentTimeMillis();
        long elapsedTimeMs = (ended - started);
        log("Elapsed time [ms]: " + elapsedTimeMs);
        if (elapsedTimeMs!=0) {
            randomReadsPerSecond =(maxReads * 1000 / elapsedTimeMs);
            log("Read leaf nodes/sec: " + randomReadsPerSecond);

        }
        logEndSection("Finished.");
    }

    class Result {
        long nodesCounted;
        long nodesPerSecond;

        public Result(long nodesCounted,
                long nodesPerSecond) {
            this.nodesCounted = nodesCounted;
            this.nodesPerSecond = nodesPerSecond;
        }
    }

    public void myupdate(long count) {
        log("calls: " + count + " ...");

    }

    class MyUpdater extends Updater {
        public void update() {
            myupdate(callCount);
        }
    }
    public void countNodes(String id,Trie t,long limit,int depthLimit,boolean leafNodes) {
        showCacheStats();
        started = System.currentTimeMillis();
        // IMPORTANT: This is the worst case when the hashmap in the cache
        // store less entries than the total number of nodes in the tree.
        // For example, if count() is called two times, while traversing the first
        // the cache will evict the elements that are needed for the next
        // pass. Therefore the required node will never be in the cache.
        // To test the cache and the eviction policy, we can execute the
        // count method with a limit, or we can execute the existent elements
        // read method twice.

        // now we count something
        String sectionMod = "";
        if (limit==0)
            limit = Long.MAX_VALUE;

        if (limit<Long.MAX_VALUE)
            sectionMod = " (with limit "+limit+")";
        if (depthLimit<Integer.MAX_VALUE)
            sectionMod = " (with depthLimit "+depthLimit+")";

        logNewSection(id+"Counting..."+sectionMod);
        long nodesCounted;
        MyUpdater myupdater = new MyUpdater();

        if (!leafNodes) {
            nodesCounted = t.countNodes(limit,depthLimit,myupdater);
            if (limit==Long.MAX_VALUE)
                nodeCount = nodesCounted;
            log("nodes: " + nodesCounted );
        } else {
            nodesCounted = t.countLeafNodes(limit,depthLimit,myupdater);
            if (limit==Long.MAX_VALUE)
                leafNodeCount = nodesCounted;
            log("leaf nodes: " + nodesCounted);
        }
        log("Counted!");
        ended = System.currentTimeMillis();
        long elapsedTimeMs = (ended - started);
        elapsedTime = elapsedTimeMs / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            long nodesPerSecond;
            nodesPerSecond = (leafNodeCount * 1000L / elapsedTimeMs);
            if (leafNodes) {
                log("Scanned leaf nodes/sec: " + nodesPerSecond);
                if (limit==Long.MAX_VALUE)
                    scannedLeafNodesPerSecond = nodesPerSecond;
            } else {
                log("Scanned inner nodes/sec: " + nodesPerSecond);
                if (limit==Long.MAX_VALUE)
                    scannedNodesPerSecond = nodesPerSecond;
            }
        }
        showCacheStats();
        logEndSection("Finished.");
    }

    public static int log2(long n){
        if(n <= 0) throw new IllegalArgumentException();
        return (int) (63 - Long.numberOfLeadingZeros(n));
    }


    int leafBits;
    int intermediateBits; // these are fixed
    int splitBits;
    int subTreeSize;


    public void computeBottomUpBitSizes(long max) {
        long width = max; // this is the total number of elements in the tree
        int log2w = log2(width);
        if ((1 << log2w) != width)
            throw new RuntimeException("only for powers of 2");

        // Intermediate bits do not contain fixed bits.
        // Intermediate bits are all variable bits
        intermediateBits =log2w;
        // leafBits are the bits that will be chosen randomly as leaf nodes prefixes
        leafBits = stateTrieSim.varKeySize *8-intermediateBits;
        if (leafBits<0)
            throw new RuntimeException("Not enough leaves");

        splitBits = intermediateBits;
    }

    public void buildbottomUp() {
        logNewSection("buildbottomUp max="+max);
        maxKeysBottomUp = max;
        prepare();
        // The idea is that we create the terminal nodes, then we pair them up
        // and keep doing this.
        // We don't store the leaf nodes, they are generated dynamically
        computeBottomUpBitSizes(maxKeysBottomUp);
        // it will be split in subTreeCount subtrees
        int stCount = subTreeCount;
        // Each node corresponds to a subtree
        Trie[] nodes = new Trie[stCount];

        // The subTreeBits bits are filled completely
        //

        int subTreeBits = log2(stCount);
        long lsubTreeSize  = maxKeysBottomUp/stCount;
        if (lsubTreeSize>Integer.MAX_VALUE)
            throw new RuntimeException("invalid width");

        subTreeSize = (int) lsubTreeSize;
        int stWidth = (int) maxKeysBottomUp/stCount;
        if (stWidth==0)
            throw new RuntimeException("Not enough leafs");

        log("Building subtrees ("+subTreeCount+" subtrees, "+getK(lsubTreeSize)+" nodes each)...");
        start(true);
        leafNodeCounter =0;
        int step = 1;
        for (int s = 0; s < stCount; s++) {
            log("Building subttree: "+(s+1)+" of "+stCount);
            nodes[s] = buildSubtree(stWidth,intermediateBits - subTreeBits, leafBits);
            if ((s % step == 0) && (s>0)) {
                dumpProgress(s,stCount);
            }
        }
        log("Merging subttrees...");
        rootNode = mergeNodes(nodes);
        System.out.println("Pre fix part: ");
        //dumpTrie();
        if (stateTrieSim.fixKeySize>0) {
            computeFixedKeyPart();
            // we set the last bit forced
            // leave 1 bit out for the node branch
            PseudoRandom.setCompactBit(fixedKeyPart,stateTrieSim.fixKeySize*8-1,false); // make sure we create the right child.
            TrieKeySlice fixSharedPath = TrieKeySliceFactoryInstance.get().fromEncoded(fixedKeyPart, 0, stateTrieSim.fixKeySize * 8 - 1);

            // now build a node at the top with the fixed part.
            // We assume here the previous rootNode had two children, so the tree
            // is well balanced
            if ((rootNode.getLeft().isEmpty()) || (rootNode.getRight().isEmpty())) {
                log("The trie is not well balanced");
            }

            rootNode = trieStore.getTrieFactory().newTrie(
                    getTrieStore(), fixSharedPath,
                    null,
                    trieStore.getNodeReferenceFactory().newReference(null, rootNode),
                    NodeReferenceImpl.empty(),
                    Uint24.ZERO,
                    null,null,null);
            System.out.println("Post fix part: ");
            //dumpTrie();
        }
        stop(true);
        dumpResults();
        timeToBuildTree = elapsedTime;
        logEndSection("Finished.");

        // This flush operation will compute all hashes for millions of nodes
        // It can take minutes
        logNewSection("Start saving (all nodes hashes computed)...");
        saveTrie();
        log("stop saving");
        log("Start flushing..");
        flushTrie();
        log("stop flushing");
        logEndSection("Finished.");
        //countNodes(t);
    }

    void destroyTreeAndLog() {
        log("Destroying the tree in memory");
        destroyTree();
    }

    void storeRootNode() {
        if (dsDB==null) return;

        Keccak256 rootNodeHash = rootNode.getHash();

        // if it's a isContentAddressableDatabase()
        // We'll store the hash in a separate file, because we cannot use a fixed key.
        // else we'll store the hash in a special node
        //
        if (dsDB instanceof DataSourceWithAuxKV) {
            ((DataSourceWithAuxKV) dsDB).kvPut("root".getBytes(StandardCharsets.UTF_8), rootNodeHash.getBytes());
        } else {
            dsDB.put("root".getBytes(StandardCharsets.UTF_8), rootNodeHash.getBytes());
        }

        log("Storing root node hash: "+rootNodeHash.toHexString());
    }

    boolean isContentAddressableDatabase() {
      return (database== Database.FlatDB) || (database== Database.FlatRefDB);
    }

    void destroyTree() {
        if (getTrieStore()==null) return;
        if (!(getTrieStore() instanceof TrieStoreImpl))
            return;

        if (GlobalEncodedObjectStore.get() == null) {
            // too mucho noise: log("Destroying the tree in memory");
            // If there is no encoded object store, we emulate
            // that a following block must be mined. So we must dispose the current trie
            // and reload it from disk or any other TrieStore cache that exists.
            Keccak256 rootNodeHash = rootNode.getHash();
            rootNode = null; // make sure nothing is left
            setRootNode(rootNodeHash.getBytes());

        } else {
            // If we have an encoded object store, we never throw away the root
            // but we still have to flush things to disk
            //El problema es que el encoded store no guarda un flag indicando si el nodo esta
            //        en disco o no. Eso hace que siempre se regrabe
        }
    }

    PseudoRandom pseudoRandom = new PseudoRandom();

    Trie buildSubtree(int width, int intermediateBits, int leafBits) {
        Trie[] nodes = new Trie[width];
        int step = 1_000_000;

        // if width is larger than 2^leafBits, then
        int leafKeyBufferSize = (leafBits+7)/8;
        for (int i = 0; i < width; i++) {
            pseudoRandom.setSeed(leafNodeCounter++);
            byte[] key = pseudoRandom.randomBytes(leafKeyBufferSize);
            TrieKeySlice keySlice = TrieKeySliceFactoryInstance.get().fromEncoded(key,
                    0, leafBits);
            //System.out.println("key slice: "+keySlice.toString());
            byte[] value = TestUtils.getPseudoRandom().randomBytes(stateTrieSim.valueSize);
            nodes[i] = trieStore.getTrieFactory().newTrie(null,
                    keySlice,
                    value,
                    NodeReferenceImpl.empty(),
                    NodeReferenceImpl.empty(),
                    new Uint24(value.length),
                    null,null,null);

            if ((i % step == 0) && (i>0)) {
                dumpProgress(i,width);
            }


        }
        Trie root = mergeNodes(nodes);
        // If the number of nodes is above 1M, we flush them, and we remove them from
        // RAM.
        boolean treeSaved = false;
        if ((saveNodesDuringConstruction) | (nodes.length>=1_000_000)) {
            saveSubTrie(root);
            treeSaved = true;
        }
        if ((pruneNodesDuringConstruction) && (treeSaved)) {
            // this is a way to prune the node, but there is a better way
            // root =pruneNode(root);
            root.setAsTranverseLimit();
            if (!saveNodesDuringConstruction)
                root.markAsSaved();
        }
        return root;
    }

    Trie pruneNode(Trie node) {

        log("Prunning subroot node hash: "+ node.getHash().toHexString());

        Optional<Trie> optRootNode = getTrieStore().retrieve(node.getHash().getBytes());
        if (!optRootNode.isPresent()) {
            System.out.println("Could not retrieve subroot node ");
            System.exit(1);
        }
        return optRootNode.get();
    }

    Trie mergeNodes(Trie[] nodes) {
        log("Creating intermediate levels...");
        TrieKeySlice emptySharedPath =TrieKeySliceFactoryInstance.get().empty();
        int level =0;
        int width = nodes.length;
        int intermediateBits = log2(width);
        int step = 1_000_000;
        while (width>1) {
            width = width/2;
            level++;
            Trie[] newNodes = new Trie[width];
            for (int i=0;i<width;i++) {
                if ((i % step == 0) && (i>0)) {
                    log("Level "+level+" of "+intermediateBits+" width: "+width);
                    dumpProgress(i,width);
                }

                NodeReferenceFactory nrf = trieStore.getNodeReferenceFactory();
                newNodes[i] = trieStore.getTrieFactory().newTrie(null,emptySharedPath,
                        null,
                        nrf.newReference(null,nodes[i*2]),
                        nrf.newReference(null,nodes[i*2+1]),
                        Uint24.ZERO,
                        null,null,null);
                nodes[i*2] = null; // try to free mem
                nodes[i*2+1] = null;

            }
            nodes = newNodes;
            newNodes = null;
        }
        return nodes[0];

    }

    public void simpleTrieTest() {
        Trie t = new TrieImpl();
        byte[] v1 = new byte[]{1};
        byte[] v2 = new byte[]{2};
        byte[] v3 = new byte[]{3};

        //v1= TestUtils.randomBytes(32);
        //v2= TestUtils.randomBytes(32);

        t = t.put("1",v1);
        t = t.put("2",v2);
        t = t.put("3",v3);
    }

    public static void myassert(boolean b) {
        if (!b)
            throw new RuntimeException("Invalid assertion");
    }

    public void test() {
        Object x;
        byte[] vec = new byte[10];
        x = vec;
        if (x instanceof byte[]) {
            System.out.println("works");
        }
    }

    public float getExpectedLoadFactor() {
        // This is ugly and I'm sure there is a better way to do it
        // but I don't know.
        Class cls =getDSClass();
        Method m = null;
        try {
            m = cls.getMethod("getDefaultLoadFactor");
        } catch (NoSuchMethodException e) {
            return 0;
        }
        String[] params = null;
        Object ret = null;
        try {
            ret = m.invoke(null, (Object[]) null);
        } catch (IllegalAccessException e) {
            return 0;
        } catch (InvocationTargetException e) {
            return 0;
        }
        return (Float) ret;

    }

    public void createLogFile(String basename,String expectedItems) {
            String name = "Results/"+basename;
            name=name+"-"+TrieKeySliceFactoryInstance.get().getClass().getSimpleName();
            String om;
            if (GlobalEncodedObjectStore.get()==null)
                om ="_";
            else
                om = GlobalEncodedObjectStore.get().getClass().getSimpleName();
            name = name + "-"+om;
            if (useDecodedNodeCache)
                name = name + "-dec_"+ getK(DecodedNodeCache.maxCacheNodes);

            if (useWeakReferences)
                name = name + "-wref";

            name = name + "-"+expectedItems;
            name = name + "-" + testMode.toString();
            name = name +"-Max_"+ getMillions( Runtime.getRuntime().maxMemory());
            name = name +"-"+hashMapDataStructure.toString();
            name = name + "-SCS_"+getK(statesCacheSize);
            name = name + "-lf_"+getExpectedLoadFactor();
            //name = name + "-lf_"+dsWithCache.getDefaultLoadFactor();
            //name = name + dsWithCache.getModifiers();

            Date date = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
            String strDate = dateFormat.format(date);
            name = name + "-"+ strDate;

            plainCreateLogFilename(name);

    }


    public static void testInMemStore() {
        EncodedObjectHeap ms  = EncodedObjectHeap.get();
        long leftOfs = ms.buildPointer(ms.getCurSpaceNum(),1000);
        long rightOfs = ms.buildPointer(ms.getCurSpaceNum(),2000);
        byte[] s1  = new byte[100];
        long s1o = ms.add(s1,leftOfs,rightOfs);
        ObjectReference s1ref = ms.retrieve(s1o);
        myassert (((LongEOR) s1ref.leftRef).ofs==leftOfs);
        myassert (((LongEOR) s1ref.rightRef).ofs==rightOfs);
        myassert (s1ref.len==s1.length);
        myassert (Arrays.equals(s1ref.getAsArray(),s1));

        System.out.println("Ok");

    }

    static public Class<? extends EncodedObjectStore> chooseEncodedStoreClass() {
        return encodedStoreClass; // Do not use a encoded store
        //encodedObjectStore = new SoftRefEncodedObjectStore();
        //encodedObjectStore = new EncodedObjectHeap();
        //encodedObjectStore = new EncodedObjectHashMap();
        // return HardEncodedObjectStore.class;
        //encodedObjectStore = new MultiSoftEncodedObjectStore();
        //encodedObjectStore = new EncodedObjectRefHeap();

        //return EncodedObjectRefHeap.class;
    }
    static public EncodedObjectStore getEncodedStore(Class<? extends EncodedObjectStore> aClass) {
        if (aClass==null)
            return null; // do not use
        EncodedObjectStore encodedObjectStore = null;
        // Here you have to choose one encoded object store.
        // uncomment a single line from the lines below:
        try {
            encodedObjectStore = aClass.newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        if (encodedObjectStore==null)
            System.exit(1);

        return encodedObjectStore;
    }

    static public EncodedObjectStore chooseEncodedStore() {
        return getEncodedStore(chooseEncodedStoreClass());
    }

    public void smallWorldTest() {
        smallWorldTest(chooseEncodedStoreClass());
    }

    public void seedTest() {
        testMode = StateTrieSimulator.SimMode.simEOAs;
        stateTrieSim.setSimMode(testMode);
        computeAverageAccountSize();
        computeKeySizes();
        computeFixedKeyPart();
    }

    public void writeTest() {
        writeTest(chooseEncodedStoreClass());
    }

    public void readTest() {
        readTest(chooseEncodedStoreClass());
    }

    public void setupGlobalClasses(EncodedObjectStore encodedObjectStore) {
        GlobalEncodedObjectStore.set(encodedObjectStore);
        //EncodedObjectHeap.default_spaceMegabytes = 500;
        TrieKeySliceFactoryInstance.setTrieKeySliceFactory(CompactTrieKeySlice.getFactory());
        //TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());
    }

    public void logGlobalClasses() {
        log("TrieKeySliceFactory classname: "+TrieKeySliceFactoryInstance.get().getClass().getName());
        GlobalEncodedObjectStore.get();

        log("Using hashmap: "+hashMapDataStructure.toString());
        if (dsWithCache!=null)
            log("Using DataSourceWithCache class: "+dsWithCache.getClass().getName());

        if (GlobalEncodedObjectStore.get()==null)
            log("ObjectMapper not present");
        else
            log("ObjectMapper classname: "+ GlobalEncodedObjectStore.get().getClass().getName());

        log("useWeakReferences: " + useWeakReferences);

        if (useDecodedNodeCache) {
            log("DecodedNodeCache size: " + DecodedNodeCache.maxCacheNodes);
        }
        else {
            log("Not using DecodedNodeCache");
        }
    }

    public String getMillions(long i) {
        String maxStr = ""+ (i/1000/1000)+"M";
        return maxStr;
    }
    public String getK(long i) {
        String maxStr = ""+ (i/1000)+"K";
        return maxStr;
    }



    public void cacheTest(Class<? extends EncodedObjectStore> aClass) {
        testMode = StateTrieSimulator.SimMode.simEOAs;
        long addMaxKeysBottomUp  = 1L * (1 << 20);
        long testCacheLimit = 1_000_000;
        long maxRandomReads = 10_000_000;
        int testCNDepthLimit = Integer.MAX_VALUE;

        log("Test cache");
        testCacheSize = 4_000; // 50% of the nodes (many nodes are embedded)
        adjustCacheSize = false;
        addMaxKeysBottomUp = (1<<14); // 16K
        testCacheLimit = 999;
        String testName;
        testName = "cachetest";
        maxRandomReads = 16_000;
        String maxStr = ""+ getMillions(addMaxKeysBottomUp );

        setupEncodedObjectStore(aClass);
        prepare();
        openTrieStore(true,false,"");
        getTrieStore();
        createLogFile(testName,maxStr);
        logGlobalClasses();

        max = addMaxKeysBottomUp ;
        buildbottomUp();

        // It's very important to destroy the tree in memory to get
        // a real measurement of how the cache works

        destroyTreeAndLog();
        showUsedMemory();
        showHashtableStats();

        // First, read random nodes to shuffle the elements of the
        // cache and make sure not all of them are in memory
        randomReadNodes(rootNode,maxRandomReads);

        // It's very important to destroy the tree in memory to get
        // a real measurement of how the cache works
        destroyTreeAndLog();
        countNodes("CacheTest: PASS 1: ",rootNode,testCacheLimit,testCNDepthLimit,false);
        destroyTreeAndLog();

        // It's possible that when scanning, items are moved to the top, which
        // causes items to be evicted? We disconnect this mechanism to test this
        // hypothesis
        boolean oldAccess =getCacheTopPriorityOnAccess();
        setCacheTopPriorityOnAccess(false);
        countNodes("CacheTest: PASS 2: ",rootNode,testCacheLimit,testCNDepthLimit,false);
        setCacheTopPriorityOnAccess(oldAccess);
        showUsedMemory();
        closeLog();

    }

    public void setupEncodedObjectStore(Class<? extends EncodedObjectStore> aClass) {
        // To store 10M accounts, you need at least 1.5 GB of storage.
        int memoryForStoreMegabytes = 2000; // 2 GB

        EncodedObjectStore encodedObjectStore =getEncodedStore(aClass);
        // I setup the global encoded object store here so that the log filename
        // takes the store class name to build the log file name
        setupGlobalClasses(encodedObjectStore);
        if (encodedObjectStore!=null) {
            encodedObjectStore.setMaxMemory(memoryForStoreMegabytes * 1000L * 1000L);
            encodedObjectStore.initialize();
        }
    }

    void logMaxKeys() {
        log("maxKeysBottomUp: "+maxKeysBottomUp);
        log("maxKeysTopDown: "+maxKeysTopDown);
    }

    public void checkFlushFilesystemCache() {
        if (flushFilesystemCache) {
            System.out.println("-----------------------------------------");
            System.out.println("Now you have to execute 'sudo purge' in your shell");
            System.out.println("-----------------------------------------");
            System.out.println("Press [ENTER] afterwards..");

            //String enter = System.console().readLine();
            // Enter data using BufferReader
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in));

            // Reading data using readLine
            try {
                String name = reader.readLine();
            } catch (IOException e) {
                System.out.println("error in stdin");
            }
            log("sudo purge performed");
        } else {
            log("NO filesystenm cache purge performed");
        }
    }

    // Requires that the database is already loaded with all the data
    public void readTest(Class<? extends EncodedObjectStore> aClass) {

        maxKeysBottomUp  = addMaxKeysBottomUp;
        maxKeysTopDown = addMaxKeysTopDown;

        long totalKeys = maxKeysBottomUp+maxKeysTopDown;

        writesPerBlock =0;
        String dbName =getDBName(addMaxKeysBottomUp,addMaxKeysTopDown,"");

        String testName ="readtest";
        String maxStr = dbName;

        setupEncodedObjectStore(aClass);
        prepare();
        if (maxKeysBottomUp>0)
            computeBottomUpBitSizes(maxKeysBottomUp);
        createLogFile(testName,maxStr);
        logMaxKeys();
        checkFlushFilesystemCache();
        openTrieStore(false,false,dbName);
        logGlobalClasses();
        dsWithCache.readOnly = true;
        setRootNode(null);
        getTrieStore();

        readTestInternal();
        dumpResultsInCSV();
        closeLog();
    }

    public void setRootNode(byte[] rootNodeHash) {
        if (rootNodeHash==null) {
            if (dsDB instanceof DataSourceWithAuxKV) {
                rootNodeHash = ((DataSourceWithAuxKV) dsDB).kvGet("root".getBytes(StandardCharsets.UTF_8));
            } else
            rootNodeHash = dsDB.get("root".getBytes(StandardCharsets.UTF_8));

            log("Reading root node hash: "+  ByteUtil.toHexString(rootNodeHash));
        }

        Optional<Trie> optRootNode = getTrieStore().retrieveRoot(rootNodeHash);
        if (!optRootNode.isPresent()) {
            System.out.println("Could not retrieve node ");
            System.exit(1);
        }
        rootNode = optRootNode.get();
        // Store it in case we need it in the future.
        if (getTrieStore().getDecodedNodeCache()!=null)
            getTrieStore().getDecodedNodeCache().storeRoot(rootNode);
    }

    public void readTestInternal() {

        boolean countNodes = false;
        boolean dotestDifferentCachePerformances = false;
        boolean testRealalistic = true;


        if (countNodes) {
            int testCNDepthLimit = 15; // 256K nodes
            countNodes("Warmup: ", rootNode, 0, testCNDepthLimit,true);
            showHashtableStats();
            countNodes("hot: ", rootNode, 0, testCNDepthLimit,true);
            showHashtableStats();
            long testCacheLimit = 1_000_000;
            cacheTestInReadTest(testCacheLimit);
        }


        if (dotestDifferentCachePerformances) {
            int passes = 10;
            //existentReadNodes(rootNode,true,maxExistentReads,1,false,false);
            //existentReadNodes(rootNode,true,maxExistentReads,passes,true,false);
            testDifferentCachePerformances(passes);
        }

        if (testRealalistic) {
            int testCNDepthLimit = 16; // 256K nodes
            // First try to fill caches to simulate a warm startup.
            countNodes("Warmup: ", rootNode, 0, testCNDepthLimit,true);
            showHashtableStats();
            countNodes("hot: ", rootNode, 0, testCNDepthLimit,true);
            showHashtableStats();
            // The idea of this test is to be 100% realistic:
            // * some nodes are retrieved from cache
            // * some nodes are fetched from disk.
            // * some part of the tree is in memory (while processing a block)
            long numReads = 500_000;
            existentReadNodes(rootNode, true, numReads, 1,
                    true,false,5_000);
            //randomReadNodes(rootNode, 0);
        }
        logTraceInfo();
    }

    public void testDifferentCachePerformances(int passes) {
        if (flushFilesystemCache) {
            // This test is about raw in-memory trie lookup. It's not realistic, because
            // most of the nodes are not in the memory trie, nor in the in-memory cache,
            // but on SSD DB.
            log("Test: SSD DB retrieval to build full trie, without filesystem cache");
            existentReadNodes(rootNode,true,maxExistentReads,1,false,false,0);

            log("Clearing all application caches");
            destroyTree();
            dsWithCache.clear();
            dsWithCache.resetHitCounters();

            // Now we test again, but we assume filesystem caches will be filled.
            // So we clean all application caches.
            log("Test: SSD DB retrieval to build full trie, with filesystem caches");
            existentReadNodes(rootNode, true, maxExistentReads, 1, false, false,0);
        } else {
            // This test is about raw in-memory trie lookup. It's not realistic, because
            // most of the nodes are not in the memory trie, nor in the in-memory cache,
            // but on SSD DB.
            log("Test: SSD DB retrieval to build full trie (unknown filesystem cache state)");
            existentReadNodes(rootNode,true,maxExistentReads,1,false,false,0);

        }

        // This test is about raw in-memory trie lookup.
        // Depending the number of nodes to read, this test can measure in-memory performance
        // or SSD performance, because some of the nodes may not in the memory trie, nor in the in-memory cache,
        // but on SSD DB.
        // Compared to the previous test, it only adds more passes, so it gets a more
        // precise measurement.
        log("Test: in-memory built trie");
        existentReadNodes(rootNode,true,maxExistentReads,passes,false,false,0);

        // We now test performance or only the higher levels of the tree.
        // by doing so we can measure the cost of trie lookup when we're caching only
        // the upper parts of the trie. The main difference is that in this test we return
        // the node depth (to create per node statistics) and we go to fixed depths.


        // This test is about in-memory node hashmap lookup. It's too not realistic, because
        // most of the nodes are not in the in-memory cache,
        // but on SSD DB.
        // The pre-condition of this test is that all nodes are in the cache.
        // This is satisfied by the first existentReadNodes() call, which loads them all.
        log("Test: in-memory cache");
        destroyTree();
        existentReadNodes(rootNode,true,maxExistentReads,passes,false,true,0);

        // This test is more realistic. It mantains some portion of the tree built in-memory
        // and goes to fetch nodes from the cache.
        log("Test: Keeping trie built during block execution");
        existentReadNodes(rootNode,true, maxExistentReads,passes,true,false,0);

    }
    public void cacheTestInReadTest(long testCacheLimit) {
        int testCNDepthLimit = Integer.MAX_VALUE;
        // It's very important to destroy the tree in memory to get
        // a real measurement of how the cache works
        destroyTreeAndLog();
        countNodes("CacheTest: PASS 1: ", rootNode, testCacheLimit, testCNDepthLimit, false);
        destroyTreeAndLog();

        // It's possible that when scanning, items are moved to the top, which
        // causes items to be evicted? We disconnect this mechanism to test this
        // hypothesis
        boolean oldAccess = getCacheTopPriorityOnAccess();
        setCacheTopPriorityOnAccess(false);
        countNodes("CacheTest: PASS 2: ", rootNode, testCacheLimit,testCNDepthLimit,false);
        setCacheTopPriorityOnAccess(oldAccess);

        showUsedMemory();
    }
    public void writeTest(Class<? extends EncodedObjectStore> aClass) {


        String testName ="writetest";
        String maxStr = ""+ getMillions(addMaxKeysBottomUp )+"_plus_"+getMillions(addMaxKeysTopDown);
        createLogFile(testName,maxStr);

        setupEncodedObjectStore(aClass);
        prepare();
        writeTestInternal(addMaxKeysBottomUp, addMaxKeysTopDown,"");
        showCacheStats();
        dumpResultsInCSV();
        closeDB();
        closeLog();
    }

    public String getDBName(long addMaxKeysBottomUp,long addMaxKeysTopDown,String tmpDbNamePrefix) {
        String dbName = testMode.toString()+""+tmpDbNamePrefix+"-"+
                getExactCountLiteral(addMaxKeysBottomUp)+"-plus-"+
                getExactCountLiteral(addMaxKeysTopDown);
        if (fullyFillTopNodes)
            dbName = dbName + "-topf_"+ getLog2Bits(addMaxKeysTopDown);
        dbName = dbName + "-wpb_"+writesPerBlock;

        if (database==Database.LevelDB) {
            dbName =dbName +"-level";
        } else
            if (database==Database.RocksDB) {
                dbName =dbName +"-rocks";
        } else
            if (database==Database.FlatDB) {
            dbName =dbName +"-flt";
        } else
            if (database==Database.FlatDB) {
                dbName =dbName +"-fltref";
            }
        return dbName;
    }

    public void writeTestInternal(
            long addMaxKeysBottomUp,long addMaxKeysTopDown,String tmpDbNamePrefix ) {


        // To be able to reconstruct existing kety, we need to know how many
        // keys are top-down and how many are bottom-up
        long totalKeys = addMaxKeysBottomUp+addMaxKeysTopDown;
        writesPerBlock = 0;
        String dbName =getDBName(addMaxKeysBottomUp,addMaxKeysTopDown,tmpDbNamePrefix);

        if (createDatabase) {
            if (tmpDbNamePrefix.length() > 0) {
                // Temporary DB. Can delete freely
                openTrieStore(true, false, dbName);
            } else
                openTrieStore(false, true, dbName);

            getTrieStore();
        } else
            createInMemoryTrieStore();

        logGlobalClasses();
        if (addMaxKeysBottomUp >0) {
            // Create a high number of accounts bottom-up
            // This is a much faster method, as it doesn't create waste in
            // memory and doesn't trigger neither our GC (and probably it
            // doesn't trigger Java's GC often)
            max = addMaxKeysBottomUp ;
            buildbottomUp();

            // It's very important to destroy the tree in memory to get
            // a real measurement of how the cache works

            destroyTreeAndLog();
            if (garbageCollectAndShowMemoryUsed)
                showUsedMemory();
            if (testAfterWrite) {
                existentReadNodes(rootNode, false, 0, 1, false, false,0);
                countNodes("BU: ", rootNode, 0, Integer.MAX_VALUE,true);
            }
            showHashtableStats();


            //System.exit(0);
        }

        if (addMaxKeysTopDown>0) {
            // Add another number of accounts, but this time by inserting
            // elements in the trie. This is slower, and creates unused nodes
            // that our GC must collect. It also creates a high number of temporal
            // Java objects that the Java GC need to periodically collect.
            // now add another 8M items to it!
            max = addMaxKeysTopDown;
            buildByInsertion();

            if ( testMode == StateTrieSimulator.SimMode.simCounter) {
                int counterBits;
                countNodes("TD: ", rootNode, 0,  Integer.MAX_VALUE,true);

                counterBits = getLog2Bits(addMaxKeysTopDown);
                existentCounterReadNodes(rootNode,false,
                        1<<counterBits,1,false,false,
                        counterBits);

            }
            logTraceInfo();
            logBlocksCreated();
        }
        storeRootNode();
    }


    public void closeDB() {
        log("Closing db...");
        dsDB.close();
        log("Closed");
    }
    public void smallWorldTest(Class<? extends EncodedObjectStore> aClass) {
        testMode = StateTrieSimulator.SimMode.simEOAs;
        long addMaxKeysBottomUp  = 1L * (1 << 20);
        long addMaxKeysTopDown = 1L * (1 << 20)/2; // total: 1.5M

        setupEncodedObjectStore(aClass);
        prepare();
        String testName ="swtest";
        String maxStr = ""+ getMillions(addMaxKeysBottomUp )+" plus "+getMillions(addMaxKeysTopDown);

        createLogFile(testName,maxStr);
        logGlobalClasses();

        writeTestInternal(addMaxKeysBottomUp, addMaxKeysTopDown,"tmp");
        readTestInternal();
        dumpResultsInCSV();
        closeDB();
        closeLog();
    }

    public void showHashtableStats() {
        log("Hashtable: ");
        List<String> stats =dsWithCache.getHashtableStats();
        logList(" ",stats);
    }

    public void showDBStats() {
        if (!(dsDB instanceof DataSourceWithAuxKV)) return;
        DataSourceWithAuxKV dsx = (DataSourceWithAuxKV) dsDB;
        log("DB stats: ");
        List<String> stats =dsx.getStats();
        logList(" ",stats);
    }

    public boolean getCacheTopPriorityOnAccess() {
        if (dsWithCache instanceof DataSourceWithLinkedBACache) {
            return ((DataSourceWithLinkedBACache) dsWithCache).getTopPriorityOnAccess();
        }
        return false;
    }

    public void setCacheTopPriorityOnAccess(boolean v) {
        if (dsWithCache instanceof  DataSourceWithLinkedBACache) {
            ((DataSourceWithLinkedBACache) dsWithCache).setTopPriorityOnAccess(v);
        }
    }

    public void logBlocksCreated() {
        log("blocks created: "+blocksCreated);
        log("writesPerBlock: "+writesPerBlock);
        log("readsPerBlock: "+readsPerBlock);

    }
    public void logTraceInfo() {
        if (getTrieStore()==null) return;

        String s = "::";
        if (!(getTrieStore() instanceof TrieStoreImpl))
            return;

        List<String> stats = ((TrieStoreImpl) getTrieStore()).getTraceInfoReport();
        if (stats==null)
            return;
        for(int i=0;i<stats.size();i++) {
            log(s + " TrieStore " + stats.get(i));
        }

    }

    public void dumpResultsInCSV() {
        log("name,rootNodeHash,leafNodeCount,maxKeysBottomUp,maxKeysTopDown,endMbs,"+
                "randomExistentReadsPerSecond,"+
                "randomReadsPerSecond,scannedLeafNodesPerSecond,timeToInsertElements,timeToBuildTree");

        log(logName+","+
                rootNode.getHash().toHexString().substring(0,4)+","+
                maxKeysBottomUp+","+
                maxKeysTopDown+","+
                endMbs+","+
                randomExistentReadsPerSecond + ","+
                randomReadsPerSecond+","+
                leafNodeCount+","+
                scannedLeafNodesPerSecond+","+
                timeToInsertElements+","+
                timeToBuildTree+","+
                nodeCount+","+
                scannedNodesPerSecond);
    }

    public void topDownTest(Class<? extends EncodedObjectStore> aClass) {
        //testMode = TestMode.testERC20Balances;
        //testMode = TestMode.testERC20LongBalances;
        boolean forceGc = false;

        String testName="";
        int memoryForStoreMegabytes = 2000; // 2 GB

        if (forceGc) {
            testName ="gctest";
            if (!aClass.equals(EncodedObjectRefHeap.class)) {
                System.out.println("This tests only works with garbage-collected stores");
                System.exit(1);
            }
            memoryForStoreMegabytes = 10;
        } else {
            testName = "tdtest";
        }
        logNewSection("Creating the store...");
        EncodedObjectStore encodedObjectStore =getEncodedStore(aClass);

        testMode = StateTrieSimulator.SimMode.simEOAs;

        setupGlobalClasses(encodedObjectStore);
        encodedObjectStore.setMaxMemory(memoryForStoreMegabytes*1000L*1000L);
        encodedObjectStore.initialize();
        logEndSection("store created.");

        max = 1000; //1L*(1<<19);
        String maxStr = ""+ getMillions(max);

        createLogFile("tdtest",maxStr);
        logGlobalClasses();


        // Create 9M accounts by inserting
        // elements in the trie. This is slower, and creates unused nodes
        // that our GC must collect. It also creates a high number of temporal
        // Java objects that the Java GC need to periodically collect.
        //

        buildByInsertion();
        existentReadNodes(rootNode,true,0,1,false,false,0);
        countNodes("",rootNode,0, Integer.MAX_VALUE,true);
        randomReadNodes(rootNode,0);
        dumpResultsInCSV();
        closeLog();
    }

    public void topdownTest() {
        topDownTest(chooseEncodedStoreClass());
    }

    public void microWorldTest(EncodedObjectStore encodedObjectStore) {
        // Creates 4 nodes bottom up, and add 4 additional nodes
        // top down
        setupGlobalClasses(encodedObjectStore);
        logGlobalClasses();
        encodedObjectStore.initialize();
        subTreeCount = 4;
        max = 4;
        buildbottomUp();
        countNodes("",rootNode,0, Integer.MAX_VALUE,true);
        dumpTrie();
        log("---------------------------");

        max = 4;
        buildByInsertion();
        countNodes("",rootNode,0, Integer.MAX_VALUE,true);
        dumpTrie();
        String[] expectedResult = new String[]{
                "0000000",
                "00000000",
                "000000000",
                "00000000000",
                "0000000000000111 -> f2",
                "0000000000011000 -> 7a",
                "0000000001",
                "00000000010",
                "0000000001001101 -> 2f",
                "0000000001011000 -> 08",
                "0000000001101111 -> 3a",
                "000000001",
                "0000000010101101 -> 3e",
                "0000000011",
                "0000000011011011 -> 4b",
                "0000000011110001 -> 0f"};

        checkTrie(expectedResult);
    }

    public String interatorElementToStr(IterationElement e) {
        String key = e.toString();
        byte[] valueBin = e.getNode().getValue();
        String value;
        if (valueBin!=null)
            value = " -> "+ByteUtil.toHexString(valueBin);
        else
            value = "";
        return(key+value);
    }

    public void checkTrie(String[] expectedResult) {
        Iterator<IterationElement> iterator = rootNode.getPreOrderIterator();
        int i =0;
        while (iterator.hasNext()) {
            IterationElement e = iterator.next();
            String keyValue = interatorElementToStr(e);
            if (!keyValue.equals(expectedResult[i])) {
                throw new RuntimeException("invalid entry "+i);
            }
            i++;
        }
        if (i!=expectedResult.length)
            throw new RuntimeException("invalid length "+i);
        log("Trie checked ok");

    }

    public void dumpTrie() {
        Iterator<IterationElement> iterator =rootNode.getPreOrderIterator();
        while (iterator.hasNext()) {
            IterationElement e =iterator.next();
            String keyValue = interatorElementToStr(e);
            log(keyValue);
        }
    }



    public  void testLoadSave() {

        // start with 32M items already loaded
        boolean create = true;
        if (create) {
            max = 16L * (1 << 20);
            buildbottomUp();
            EncodedObjectHeap.get().save("16M",
                    ((LongEOR) ((TrieWithENC) rootNode).getEncodedRef()).ofs);
            System.exit(0);
            EncodedObjectHeap.get().reset();

        }

        long rootOfs = EncodedObjectHeap.get().load("4M");
        rootNode = retrieveNode(TrieFactoryImpl.get(),new LongEOR(rootOfs));
        countNodes("",rootNode,0, Integer.MAX_VALUE,true);
        System.exit(0);
        // now add another 8M items to it!
        max = 8L * (1 << 20);
        buildByInsertion();
    }


    public static Trie retrieveNode(TrieFactory trieFactory,EncodedObjectRef encodedOfs) {
        ObjectReference r = GlobalEncodedObjectStore.get().retrieve(encodedOfs);
        Trie node = TrieBuilder.fromMessage(trieFactory, r.message, encodedOfs, r.leftRef, r.rightRef, null);
        return node;
    }

    public void testKeccak() {
        KeccakNative kn = new KeccakNative();
        byte[] test = new byte[100_000];
        int max = 10_000;
        byte[] d256 = co.rsk.tools.crypto.Keccak256Helper.keccak256java(test,0,test.length);
        System.out.println("digest10: "+ByteUtil.toHexString(d256));


        byte[] d10 = co.rsk.tools.crypto.Keccak256Helper.keccak80(test,0,test.length);
        System.out.println("digest10: "+ByteUtil.toHexString(d10));

        byte[] digest = kn.digest(test);
        System.out.println("digest native: "+ByteUtil.toHexString(digest));
        digest = Keccak256Helper.keccak256(test);
        System.out.println("digest java: "+ByteUtil.toHexString(digest));
        //System.exit(0);
        long start;
        long time;
        long end;


        start = System.currentTimeMillis();
        for (int i=0;i<max;i++) {
            byte[] digest3 = co.rsk.tools.crypto.Keccak256Helper.keccak80(test,0,test.length);
        }
        end = System.currentTimeMillis();
        time = end-start;
        System.out.println("d10 java time [ms]: "+time);

        start = System.currentTimeMillis();
        for (int i=0;i<max;i++) {
            byte[] digest3 = co.rsk.tools.crypto.Keccak256Helper.keccak256(test,0,test.length);
        }
        end = System.currentTimeMillis();
        time = end-start;
        System.out.println("d256 java time [ms]: "+time);


        start = System.currentTimeMillis();
        for (int i=0;i<max;i++) {
            byte[] digest3 = Keccak256Helper.keccak256(test);
        }
        end = System.currentTimeMillis();
        time = end-start;
        System.out.println("java time [ms]: "+time);


        start = System.currentTimeMillis();
        for (int i=0;i<max;i++) {
            byte[] digest2 = kn.digest(test);
        }
        end = System.currentTimeMillis();
        time = end-start;
        System.out.println("native time [ms]: "+time);

        System.exit(0);
    }

    public static void main (String args[]) {
        //CompactTrieKeySliceTest.test_getBitSeqAsInt();
        CompareTries c = new CompareTries();
        //c.testKeccak();
        //c.topdownTest();
        //c.buildbottomUp();
        //c.smallWorldTest();
        //c.seedTest();
        if (test==Test.readTest)
            c.readTest();
        else
        if (test==Test.writeTest)
            c.writeTest();
        //
        //c.microWorldTest(chooseEncodedStore());
        System.exit(0);
    }
}
