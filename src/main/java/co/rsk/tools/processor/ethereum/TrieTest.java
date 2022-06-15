package co.rsk.tools.processor.ethereum;


import co.rsk.tools.processor.TrieTests.*;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieTests.Unitrie.DataSources.DataSourceWithBACache;
import co.rsk.tools.processor.TrieTests.Unitrie.DataSources.DataSourceWithCACache;
import co.rsk.tools.processor.TrieTests.Unitrie.DataSources.DataSourceWithCacheAndStats;
import co.rsk.tools.processor.TrieTests.Unitrie.DataSources.DataSourceWithLinkedBACache;
import co.rsk.tools.processor.TrieTests.Unitrie.store.*;
import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.ByteUtil;
import co.rsk.tools.ethereum.trie.TrieImpl;

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
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TrieTest extends Benchmark {
    enum HashMapDataStructure {
        NoCache,
        MaxSizeHashMap,
        MaxSizeCAHashMap,
        MaxSizeByteArrayHashMap,
        MaxSizeLinkedByteArrayHashMap
    }

    boolean flushFilesystemCache = true;
    HashMapDataStructure hashMapDataStructure =
            //        HashMapDataStructure.NoCache;
                   HashMapDataStructure.MaxSizeHashMap;
            //HashMapDataStructure.MaxSizeLinkedByteArrayHashMap;
    // HashMapDataStructure.MaxSizeByteArrayHashMap;


    TrieStore trieStore;
    DataSourceWithCacheAndStats dsWithCache;
    long statesCacheSize;
    int testCacheSize = 1_000_000;
    boolean adjustCacheSize = true;

    KeyValueDataSource dsDB;
    TrieImpl rootNode;

    final int flushNumberOfBlocks =1000;
    // Each write to store takes 20K gas, which means that each block with 6.8M gas
    // can perform 340 writes.
    // set writesPerBlock to build a DB without increments
    int writesPerBlock = 340;

    // 6.8M / 200 = 34K
    // Cost of read is 200 right now in RSK.
    final int readsPerBlock = 34000;

    long timeToInsertElements;
    long randomReadsPerSecond;
    long randomExistentReadsPerSecond;
    /* s must be an even-length string. */

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }


    byte[] k1 = hexStringToByteArray("383851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3eba");
    byte[] k2 = hexStringToByteArray("38d89cb8196f0efb6892f94d68fccc2c35f0b84609e5f12c55dd85aba8d5d9");
    byte[] k3 = hexStringToByteArray("38f8b91a4b9045c5e6175f1001eac32f7fcd5eccda5c6e62fc4e6385080f7b");
    byte[] v1 = hexStringToByteArray("3851d9d47acb933dbe70399b");
    byte[] v2 = hexStringToByteArray("f6c92da33af01d4fb770e98c");
    byte[] v3 = hexStringToByteArray("0325f41d3ebaf8986da712c8");

    void logput(int i, byte[] key,byte[] value) {
        log(""+i+": writting "+ByteUtil.toHexString(key));
        log(""+i+": value: "+ByteUtil.toHexString(value));
    }

    public void  test2() {
        openTrieStore(true,false,"tmp");
        SourceBridge sb = new SourceBridge(dsWithCache);

        TrieImpl t = new TrieImpl(sb);

        byte[] data = new byte[5];
        logput(0,k1,v1);
        t.put(k1,v1);
        //System.out.println(ByteUtil.toHexString( t.getRootHash()));
        logput(1,k2,v2);
        t.put(k2,v2);
        //System.out.println(ByteUtil.toHexString( t.getRootHash()));
        logput(2,k3,v3);
        t.put(k3,v3);
        //System.out.println(ByteUtil.toHexString( t.getRootHash()));
        byte[] root = t.getRootHash();
        sb.flush();
        dsWithCache.clear();
        t = new TrieImpl(sb,root);
        System.out.println("Values: ");
        System.out.println(ByteUtil.toHexString(t.get(k1)));
        System.out.println(ByteUtil.toHexString(t.get(k2)));
        System.out.println(ByteUtil.toHexString(t.get(k3)));
        //System.out.println(ByteUtil.toHexString( t.getRootHash()));

    }
    public void test1() {
        openTrieStore(true,false,"tmp");
        SourceBridge sb = new SourceBridge(dsWithCache);

        TrieImpl t = new TrieImpl(sb);
        byte[] key1 = new byte[1];
        byte[] key2 = new byte[2];
        byte[] data = new byte[5];
        t.put(key1,data);
        System.out.println(ByteUtil.toHexString( t.getRootHash()));
        t.put(key2,data);
        System.out.println(ByteUtil.toHexString( t.getRootHash()));

        System.out.println(ByteUtil.toHexString(t.get(key1)));
        System.out.println(ByteUtil.toHexString(t.get(key2)));
        System.out.println(ByteUtil.toHexString( t.getRootHash()));

    }
    SourceBridge sb;

    public SourceBridge getSourceBridge() {
        if (sb==null) {
            sb = new SourceBridge(dsWithCache);
        }
        return sb;
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
        String name = "Results-Ethereum/eth_"+basename;
        //name=name+"-"+TrieKeySliceFactoryInstance.get().getClass().getSimpleName();
        name = name + "-"+expectedItems;
        name = name + "-" + testMode.toString();
        name = name + "-" + blockchain.toString();
        name = name +"-MaxMem_"+ getMillions( Runtime.getRuntime().maxMemory());
        name = name +"-"+hashMapDataStructure.toString();
        name = name + "-SCS_"+getK(statesCacheSize);
        name = name + "-lf_"+getExpectedLoadFactor();

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        String strDate = dateFormat.format(date);
        name = name + "-"+ strDate;

        plainCreateLogFilename(name);

    }

    public void logGlobalClasses() {

        log("Using hashmap: "+hashMapDataStructure.toString());
        log("Using DataSourceWithCache class: "+dsWithCache.getClass().getName());

    }

    public String getMillions(long i) {
        String maxStr = ""+ (i/1000/1000)+"M";
        return maxStr;
    }
    public String getK(long i) {
        String maxStr = ""+ (i/1000)+"K";
        return maxStr;
    }


    // Requires that the database is already loaded with all the data
    public void readTest() {
        testMode = StateTrieSimulator.SimMode.simEOAs;
        blockchain = StateTrieSimulator.Blockchain.Ethereum;

        maxKeysTopDown = 1L * (1 << 20); // total: 1M
        long totalKeys = maxKeysTopDown;
        writesPerBlock =0;
        String dbName = testMode.toString()+
                "-"+blockchain.toString()+
                "-"+maxKeysTopDown;
        dbName = dbName + "-wpb_"+writesPerBlock;

        String testName ="readtest";
        String maxStr = "";

        prepare();

        createLogFile(testName,maxStr);
        logMaxKeys();
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

        openTrieStore(false,false,dbName);
        logGlobalClasses();
        dsWithCache.readOnly = true;
        setRootNode(null);
        getTrieStore();

        readTestInternal();
        //dumpResultsInCSV();
        closeLog();
    }

    public void readTestInternal() {
        showUsedMemory();
        long maxExistentReads = 300_000;
        int passes = 10;
        if (flushFilesystemCache) {
            // This test is about raw in-memory trie lookup. It's not realistic, because
            // most of the nodes are not in the memory trie, nor in the in-memory cache,
            // but on SSD DB.
            logTest("Test: SSD DB retrieval to build full trie, without filesystem cache");
            existentReadNodes(rootNode,maxExistentReads,1,false,false);

            log("Clearing all application caches");
            destroyTree();
            dsWithCache.clear();
            dsWithCache.resetHitCounters();

            // Now we test again, but we assume filesystem caches will be filled.
            // So we clean all application caches.
            logTest("Test: SSD DB retrieval to build full trie, with filesystem caches");
            existentReadNodes(rootNode,  maxExistentReads, 1, false,false);
        } else {
            // This test is about raw in-memory trie lookup. It's not realistic, because
            // most of the nodes are not in the memory trie, nor in the in-memory cache,
            // but on SSD DB.
            logTest("Test: SSD DB retrieval to build full trie");
            existentReadNodes(rootNode, maxExistentReads, 1, false, false);
        }
        // This test is about raw in-memory trie lookup. It's not realistic, because
        // most of the nodes are not in the memory trie, nor in the in-memory cache,
        // but on SSD DB.
        logTest("Test: in-memory built trie");
        existentReadNodes(rootNode,maxExistentReads,passes,false,false);

        // This test is about in-memory node cache lookup. It's too not realistic, because
        // most of the nodes are not in the in-memory cache,
        // but on SSD DB.
        // The pre-condition of this test is that all nodes are in the cache.
        // This is satisfied by the first existentReadNodes() call, which loads them all.
        logTest("Test: in-memory cache");
        destroyTree();
        existentReadNodes(rootNode,maxExistentReads,passes,false,true);

        // This test is more realistic. It mantains some portion of the tree built in-memory
        // and goes to fetch nodes from the cache.
        logTest("Test: Keeping trie built during block execution");
        existentReadNodes(rootNode,maxExistentReads,passes,true,false);

        randomReadNodes(rootNode, 0);
        //logTraceInfo();
    }

    public void logTest(String s) {
        log("------------------------------------------------------");
        log(s);
    }

    public void randomReadNodes(TrieImpl t,long maxReads) {

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
            if (t.get(key)!=null)
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

    public void existentReadNodes(TrieImpl t,long maxReads,int passes,
                                  boolean destroyTreeOnNewBlock,
                                  boolean destroyTreeAfterEachLookup) {
        byte[][] keys = null;
        boolean preloadKeys = passes>1;
        boolean sequentialKeys = true;
        long sequentialBase = 0;
        boolean x = false;
        if (x)
            return;
        boolean visual = passes<=1;
        computeFixedKeyPart();
        showCacheStats();

        int totalKeys = (int) (maxKeysTopDown);

        if (maxReads==0)
            maxReads = 1_000_000;

        long totalReads = maxReads*passes;
        logNewSection("random existent key read...("+totalReads+")");

        if (preloadKeys) {
            log("Preloading keys");
            // Force the sequence of pseudo-random elements to be always the same.
            TestUtils.getPseudoRandom().setSeed(1);

            keys = new byte[(int) (totalKeys )][];
            for (int i = 0; i < keys.length; i++) {
                byte[] key;
                if (sequentialKeys)
                    key = getExistentKey((i + sequentialBase) % totalKeys);
                else
                    key = getExistentRandomKey();
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
                        simulateNewBlock(false);
                    }
                }
                if (visual) {
                    if (i % 50000 == 0)
                        System.out.println("iteration: " + i);
                }
                byte[] key;

                if (sequentialKeys)
                    key = getExistentKey((i + sequentialBase) % totalKeys);
                else if (preloadKeys)
                    key = keys[i % keys.length];
                else {
                    key = getExistentRandomKey();
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
                byte[] rr =t.get(key);
                if (rr== null) {
                    System.out.println("error: " + i + " key: " + ByteUtil.toHexString(key));
                    System.exit(1);
                }
                //if (ti.)
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
    }

    public void logList(String s,List<String> stats) {
        for(int i=0;i<stats.size();i++) {
            log(s + stats.get(i));
        }
    }

    void logMaxKeys() {

        log("maxKeysTopDown: "+maxKeysTopDown);
    }


    public void createOrAppendToRootNode() {
        if (rootNode ==null) {

            rootNode = new TrieImpl(getSourceBridge());
            leafNodeCounter =0;
        }
    }

    long maxKeysTopDown ;
    long max;
    long leafNodeCounter;
    long blocksCreated;

    StateTrieSimulator stateTrieSim = new StateTrieSimulator();
    StateTrieSimulator.SimMode testMode = StateTrieSimulator.SimMode.simEOAs;
    StateTrieSimulator.Blockchain blockchain = StateTrieSimulator.Blockchain.RSK;// StateTrieSimulator.Blockchain.Ethereum;

    public void computeAverageAccountSize() {
        stateTrieSim.computeAverageAccountSize();
        log("Average account size: "+stateTrieSim.accountSize);
    }

    public void prepare() {
        // in satoshis
        // 0.1 bitcoin
        //TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());

        stateTrieSim.setSimMode(testMode);
        stateTrieSim.setBlockchain(blockchain);
        computeAverageAccountSize();
        computeKeySizes();

    }

    public void computeKeySizes() {

        stateTrieSim.computeKeySizes();
        log("TestMode: "+testMode.toString());
        log("varKeysize: "+ stateTrieSim.varKeySize);
        log("fixKeysize: "+ stateTrieSim.fixKeySize);
        log("keysize: "+(stateTrieSim.varKeySize+stateTrieSim.fixKeySize));

        log("valueSize: "+stateTrieSim.valueSize);
    }
    public void computeFixedKeyPart() {
        if (fixedKeyPart == null) {
            pseudoRandom.setSeed(0); // I want to replicate this easily
            fixedKeyPart = pseudoRandom.randomBytes(stateTrieSim.fixKeySize);
            log("fixedKeyPart: "+ByteUtil.toHexString(fixedKeyPart));
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
        for (long i = 0; i < max; i++) {

            //byte[] key = TestUtils.randomBytes(varKeySize);
            pseudoRandom.setSeed(leafNodeCounter++);
            pseudoRandom.fillRandomBytes(key,stateTrieSim.fixKeySize,stateTrieSim.varKeySize);

            byte[] value = TestUtils.getPseudoRandom().randomBytes(stateTrieSim.valueSize);
            if (i<3) {
                log(""+i+": writting "+ByteUtil.toHexString(key));
                log(""+i+": value: "+ByteUtil.toHexString(value));
            }
            byte[] okey = key.clone();
            rootNode.put(okey, value);
            if (rootNode.get(key)==null)
                throw new RuntimeException("error");

            if (writesPerBlock>0) {
                if (i % writesPerBlock == writesPerBlock - 1) {
                    simulateNewBlock(true);
                }
            }
            if (i % 100000 == 0) {
                dumpProgress(i,max);
                //logTraceInfo();
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

    }

    public void flushTrie() {
            sb.flush();
   //     getSourceBridge().flush(); // ??
    }

    public void saveTrie() {
        rootNode.getRootHash(); // Saves everything to disk
    }

    void destroyTree() {
            byte[] rootNodeHash = rootNode.getRootHash();
            rootNode = null; // make sure nothing is left
            setRootNode(rootNodeHash);
    }

    public void setRootNode(byte[] rootNodeHash) {
        if (rootNodeHash==null) {
            rootNodeHash = dsDB.get("root".getBytes(StandardCharsets.UTF_8));
            log("Reading root node hash: "+  ByteUtil.toHexString(rootNodeHash));
        }

        rootNode =new TrieImpl(getSourceBridge(),rootNodeHash);
    }

    public void simulateNewBlock(boolean sSaveTrie) {

        if (sSaveTrie)
            saveTrie();
        destroyTree();
        // too much noise: log("New block");

        blocksCreated++;
        if (sSaveTrie)
            if (blocksCreated % flushNumberOfBlocks==0)
                flushTrie();
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

    Path trieDBFolder =Path.of("./ethtriestore");

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
    long startMillis = System.currentTimeMillis();


    public void dumpTrieDBFolderSize() {
        log("TrieDB size: "+getFolderSize(new File(trieDBFolder.toString()))) ;
    }


    protected Class<? extends DataSourceWithCacheAndStats> getDSClass() {
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeCAHashMap)
            return DataSourceWithCACache.class;
        else
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeHashMap)
            return DataSourceWithCacheAndStats.class;
        else
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeByteArrayHashMap)
        {
            return DataSourceWithBACache.class;
        }
        else
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeLinkedByteArrayHashMap)
        {
            return DataSourceWithLinkedBACache.class;
        } else
        if (hashMapDataStructure== HashMapDataStructure.NoCache) {
            return DataSourceWithCacheAndStats.class;

        } else
            return null;
    }
    // This emulares rskj store building
    protected TrieStore buildTrieStore(Path trieStorePath,boolean deleteIfExists,boolean abortIfExists) {
        int statesCacheSize;

            statesCacheSize = testCacheSize ;

        if (hashMapDataStructure== HashMapDataStructure.NoCache) {
            statesCacheSize=0;
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

        log("Database: "+trieStorePath.toAbsolutePath());
        if (abortIfExists) {
            if (Files.isDirectory(trieStorePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new RuntimeException("Target trie db directory exists. Remove first");
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
        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(trieStorePath);
        dsDB = ds;
        // in rskj flushNumberOfBlocks is 1000, so we should flush automatically every 1000
        // blocks
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeCAHashMap)
            ds = new DataSourceWithCACache(ds, statesCacheSize, null);
        else
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeHashMap)
            ds = new DataSourceWithCacheAndStats(ds, statesCacheSize, null);
        else
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeByteArrayHashMap)
        {
            ds = new DataSourceWithBACache(ds, statesCacheSize, null);
        }
        else
        if (hashMapDataStructure== HashMapDataStructure.MaxSizeLinkedByteArrayHashMap)
        {
            ds = new DataSourceWithLinkedBACache(ds, statesCacheSize, null);
        } else
        if (hashMapDataStructure== HashMapDataStructure.NoCache) {
            ds = new DataSourceWithCacheAndStats(ds, 0, null);

        }
        log("statesCacheSize: "+statesCacheSize);


        dsWithCache = (DataSourceWithCacheAndStats) ds;
        log("Datasource modifiers: "+dsWithCache.getModifiers());
        this.statesCacheSize = statesCacheSize;
        return (TrieStore) new TrieStoreImpl(ds);
    }
    byte[] fixedKeyPart;

    PseudoRandom pseudoRandom = new PseudoRandom();


    public  byte[] getExistentKey(long x) {

        pseudoRandom.setSeed(x);

        byte[] key;

        key = new byte[stateTrieSim.fixKeySize + stateTrieSim.varKeySize];
        System.arraycopy(fixedKeyPart, 0, key, 0, stateTrieSim.fixKeySize);
        pseudoRandom.fillRandomBytes(key, stateTrieSim.fixKeySize, stateTrieSim.varKeySize);
        return key;
    }

    public  byte[] getExistentRandomKey() {
        long allKeys = maxKeysTopDown;
        long x =TestUtils.getPseudoRandom().nextLong(allKeys);
        return getExistentKey(x);
    }

    public void simpleEthereumTrieWriteTest1() {
        KeyValueDataSource ds = new HashMapDB();
        ds = new DataSourceWithCacheAndStats(ds, 100, null);
        dsWithCache = (DataSourceWithCacheAndStats) ds;

        rootNode = new TrieImpl(getSourceBridge());
        byte[] key1 = new byte[32];
        byte[] value1 = new byte[78]; // an account
        key1[0] = 1;
        value1[0] = 1;

        rootNode.put(key1, value1);
        byte[] key2 = new byte[32];
        byte[] value2 = new byte[78]; // an account
        key2[0] = 2;
        value2[0] = 2;

        rootNode.put(key2, value2);

        System.out.println(rootNode.dumpTrie());
    }

    public void smallEthereumTrieWriteTest() {
        // This is the maximum key size that can be embedded in parent nodes
        // Since 20 bytes is enough to store a ERC20 key, this means that
        // ERC20 optimization can also be applied to Ethereum, but amount
        // must be 64 bits max.
        //
        ethereumTrieWriteTest(20,8);
    }

    public void simpleEthereumTrieWriteTest() {
        ethereumTrieWriteTest(32,78);
    }
    public void ethereumTrieWriteTest(int keySize,int dataSize) {
        KeyValueDataSource ds = new HashMapDB();
        ds = new DataSourceWithCacheAndStats(ds, 100, null);
        dsWithCache = (DataSourceWithCacheAndStats) ds;

        rootNode = new TrieImpl(getSourceBridge());
        for(int i = 0;i<16;i++) {
            byte[] key1 = new byte[keySize];
            byte[] value1 = new byte[dataSize]; // an account
            key1[0] = (byte) i;
            value1[0] = (byte) i;

            rootNode.put(key1, value1);
        }

        System.out.println(rootNode.dumpTrie());
    }

    public void writeTest() {
        testMode = StateTrieSimulator.SimMode.simERC20Balances;
        blockchain = StateTrieSimulator.Blockchain.RSK;//StateTrieSimulator.Blockchain.Ethereum;

        long addMaxKeysTopDown = 1L * (1 << 20); // total: 1M

        String testName ="writetest";
        String maxStr = ""+ getMillions(addMaxKeysTopDown);
        createLogFile(testName,maxStr);

        prepare();
        writeTestInternal(addMaxKeysTopDown,"");
        //dumpResultsInCSV();
        //readTestInternal();
        closeLog();
    }

    public void writeTestInternal(
            long addMaxKeysTopDown,String tmpDbNamePrefix ) {

        testMode = StateTrieSimulator.SimMode.simEOAs;
        writesPerBlock =0;
        // To be able to reconstruct existing kety, we need to know how many
        // keys are top-down and how many are bottom-up
        long totalKeys = addMaxKeysTopDown;

        String dbName = tmpDbNamePrefix+
                testMode.toString()+
                "-"+blockchain.toString()+
                "-"+addMaxKeysTopDown;
        dbName = dbName + "-wpb_"+writesPerBlock;

        if (tmpDbNamePrefix.length()>0) {
            // Temporary DB. Can delete freely
            openTrieStore(true,false,dbName);
        } else
            openTrieStore(false,true,dbName);

        getTrieStore();

        logGlobalClasses();

        if (addMaxKeysTopDown>0) {

            max = addMaxKeysTopDown;
            buildByInsertion();
            showCacheStats();
            logBlocksCreated();
        }
        storeRootNode();
    }

    void storeRootNode() {
        byte[] rootNodeHash = rootNode.getRootHash();

        // We'll store the hash in a special node
        dsDB.put("root".getBytes(StandardCharsets.UTF_8),rootNodeHash);
        log("Storing root node hash: "+ByteUtil.toHexString(rootNodeHash));
    }

    public void logBlocksCreated() {
        log("blocks created: "+blocksCreated);
        log("writesPerBlock: "+writesPerBlock);
        log("readsPerBlock: "+readsPerBlock);

    }

    public static void main (String args[]) {
        TrieTest c = new TrieTest();
        //c.smallEthereumTrieWriteTest();
        //c.simpleEthereumTrieWriteTest();
        //c.writeTest();
        c.readTest();
        //c.test1();
        //c.test2();
    }
}
