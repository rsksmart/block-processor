package co.rsk.tools.processor.TrieTests;

//import co.rsk.tools.processor.TrieTests.sepAttempt.InMemTrie;

import co.rsk.core.Coin;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieTests.Unitrie.store.CAHashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.DataSourceWithCACache;
import co.rsk.tools.processor.TrieTests.Unitrie.store.TrieStoreImpl;
import co.rsk.tools.processor.TrieTests.ohard.HardEncodedObjectStore;
import co.rsk.tools.processor.TrieTests.oheap.LongEOR;
import co.rsk.tools.processor.TrieTests.oheap.EncodedObjectHeap;
import co.rsk.tools.processor.TrieTests.orefheap.EncodedObjectRefHeap;
import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import org.ethereum.core.AccountState;
import org.ethereum.core.Denomination;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DataSourceWithCache;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

public class CompareTries {
    final int flushNumberOfBlocks =1000;
    // Each write to store takes 20K gas, which means that each block with 6.8M gas
    // can perform 340 writes.
    //
    final int writesPerBlock = 340;

    // 6.8M / 200 = 34K
    // Cost of read is 200 right now in RSK.
    final int readsPerBlock = 34000;


    int accountSize;
    int valueSize;
    int fixKeySize;
    int varKeySize;
    long max =  4096; //32L*(1<<20);// 8 Million nodes // 1_000_000;
    EncodedObjectStore ms;
    long remapTime =0;
    long remapTimeBelow50 =0;
    long remapTimeOver50 =0;

    long startMbs;
    long started;
    long elapsedTime;

    long ended;
    long endMbs;
    Trie rootNode;

    long maxKeysBottomUp ;
    long maxKeysTopDown ;

    long randomReadsPerSecond;
    long randomExistentReadsPerSecond;
    long leafNodeCount;
    long scannedLeafNodesPerSecond;
    long timeToInsertElements;
    long timeToBuildTree;
    long leafNodeCounter;
    long blocksCreated;

    String logName;

    enum TestMode {
        testERC20LongBalances,
        testERC20Balances,

        testEOAs,
        microTest
    }
    TestMode testMode = TestMode.microTest;

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
    final boolean useCACache = true;

    // This emulares rskj store building
    protected TrieStore buildTrieStore(Path trieStorePath) {
        int statesCacheSize;

        if (ms!=null)
            // We really don't need this cache. We could just remove it.
            // We give it a minimum size
            statesCacheSize = 10_000;
        else
            statesCacheSize = 1_000_000;
        try {
            log("deleting previous trie db");
            deleteDirectoryRecursion(trieStorePath.getParent());
            dumpTrieDBFolderSize();
        } catch (IOException e) {
            System.out.println("Could not delete database dir");
        }
        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(trieStorePath);

        // in rskj flushNumberOfBlocks is 1000, so we should flush automatically every 1000
        // blocks
        if (useCACache)
            ds = new DataSourceWithCACache(ds, statesCacheSize, null);
        else
           ds = new DataSourceWithCache(ds, statesCacheSize, null);


        return (TrieStore) new TrieStoreImpl(ds);
    }

    public void prepare() {
        // in satoshis
        // 0.1 bitcoin
        TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());

        AccountState a = new AccountState(BigInteger.valueOf(100),
                new Coin(
                        Denomination.satoshisToWeis(10_1000_1000)));

        accountSize = a.getEncoded().length;

        log("Average account size: "+accountSize);
        if (testMode==TestMode.microTest) {
            fixKeySize =1;
            varKeySize = 1;// test
            valueSize = 1;
        } else
            if (testMode==TestMode.testERC20LongBalances) {

                fixKeySize =10+20+1;
                // Solidity key is the hash of something, so it occupies 32 bytes
                varKeySize = 10+32; // 10 bytes randomizer + 10 bytes address

                // The number of decimals is 10^18 = 2^60.
                // The number of tokens created is 100 billion = 2^37
                // total: 2^97 (rounded to bytes == 13 bytes)
                valueSize = 13;
            } else
        if (testMode==TestMode.testERC20Balances) {
            // We assume the contract uses an optimized ERC20 balance
            // Thereare 1 billion tokens (2^30).
            // The minimum unit is one billion. (2^30)
            // This is less than 2^64 (8 bytes)

            // We omit the first byte (domain separation) because it only
            // creates a single node in the trie.
            // Storage addresses are 20-byte in length.
            fixKeySize =10+20+1;

            // Here we assume an efficient packing on storage addresses.
            // Solidity will not efficiently pack addresses, as it will
            // turn them 32 bytes long by hashing.
            varKeySize = 10+20; // 10 bytes randomizer + 10 bytes address
            valueSize = 8;
        } else {
            // accountSize = 12
            accountSize = 12;
            fixKeySize =1;
            // A cell address contains 10+20 account bytes, plus 10+32.
            // + 1 domain
            // + 1 intermediate (always fixed byte, we can skip for these tests)
            valueSize = accountSize;
            varKeySize = 10 + 20;//+10+32;
        }
        log("TestMode: "+testMode.toString());
        log("varKeysize: "+ varKeySize);
        log("fixKeysize: "+ fixKeySize);
        log("keysize: "+(varKeySize+fixKeySize));

        log("valueSize: "+valueSize);

        ms = GlobalEncodedObjectStore.get();
        remapTime=0;
        remapTimeBelow50 =0;
        remapTimeOver50 =0;
    }


    public void start() {
        //System.gc();
        log("-- Java Total Memory [MB]: " +  Runtime.getRuntime().totalMemory() / 1024/ 1024);
        log("-- Java Free Memory [MB]: " +  Runtime.getRuntime().freeMemory() / 1024/ 1024);
        log("-- Java Max Memory [MB]: " +  Runtime.getRuntime().maxMemory()/ 1024/ 1024);
        startMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        log("Used Before MB: " + startMbs);

        started = System.currentTimeMillis();
        log("Filling...");
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
            for(int i=0;i<stats.size();i++) {
                log(s + " InMemStore " + stats.get(i));
            }
    }

    public void dumpProgress(long i,long amax) {
        log("item " + i + " (" + (i * 100 / amax) + "%)");
        printMemStats("--");
        long ended = System.currentTimeMillis();
        elapsedTime = (ended - started) / 1000;
        log("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            log("-- Added nodes/sec: "+(i/elapsedTime)); // 18K
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        //System.gc();
        log("-- Jave Mem Used[MB]: " + endMbs);
        log("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));
        if (GlobalEncodedObjectStore.get()!=null) {
            EncodedObjectStore ms =GlobalEncodedObjectStore.get();
            if (ms.supportsGarbageCollection())
                log("-- Store percentage use: " + ms.getUsagePercent()+"%");
        }
    }
    public void dumpMemProgress(long i,long amax) {
        log("item " + i + " (" + (i * 100 / amax) + "%)");

        long ended = System.currentTimeMillis();
        elapsedTime = (ended - started) / 1000;
        log("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            log("-- Added nodes/sec: "+(i/elapsedTime)); // 18K
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        log("-- Jave Mem Used[MB]: " + endMbs);
        log("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));

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
        t.compressEncodingsRecursivelly();
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
                    fromEncoded(fixedKeyPart,0,fixKeySize*8);

            ExpandedTrieKeySlice eAll = (ExpandedTrieKeySlice) eFix.append(eSplit.append(eLeaf));
            key = eAll.encode();

        } else {
            key = new byte[fixKeySize+varKeySize];
            System.arraycopy(fixedKeyPart,0,key,0,fixKeySize);
            pseudoRandom.fillRandomBytes(key,fixKeySize,varKeySize);
        }
        return key;
    }

    public  byte[] getExistentRandomKey() {
        // choose according to the number of keys inserted by each method.
        // Each method (top down and bottom up) has created a set of keys.
        // Truly since keys from bottom up and top down construction are intermingled
        // it would be equally uniform if we only picked keys from the set with higher
        // amount of keys created. However, we pick from both to make if fairer if the
        // number of keys chosen overpasses the number of elements in on of the set.
        // We use the remainder "%" without introducing a huge imbalance, because our
        // set sizes are int32 ranges (not longs really).
        long allKeys = maxKeysBottomUp+maxKeysTopDown;
        long x =TestUtils.getPseudoRandom().nextLong(allKeys);
        return getExistentKey(x);
    }
    TrieStore trieStore;

    public TrieStore getTrieStore() {
        if (trieStore==null)
            trieStore = buildTrieStore(trieDBFolder);
        return trieStore;
    }
    Path trieDBFolder =Path.of("./triestore/state");

        public void createOrAppendToRootNode() {
        if (rootNode ==null) {
            rootNode = new Trie(getTrieStore());
            leafNodeCounter =0;
        }
    }

    public void buildByInsertion() {
        maxKeysTopDown = max;
        prepare();

        start();
        createOrAppendToRootNode();


        // If the fixed key part has already been selected by bottom up construction
        // then do not select again!
        if (fixedKeyPart==null) {
            fixedKeyPart = TestUtils.getPseudoRandom().randomBytes(fixKeySize);
        }
        byte[] key = new byte[fixKeySize+varKeySize];
        System.arraycopy(fixedKeyPart,0,key,0,fixKeySize);
        for (long i = 0; i < max; i++) {
            //byte[] key = TestUtils.randomBytes(varKeySize);
            pseudoRandom.setSeed(leafNodeCounter++);
            pseudoRandom.fillRandomBytes(key,fixKeySize,varKeySize);
            byte[] value = TestUtils.getPseudoRandom().randomBytes(valueSize);
            rootNode = rootNode.put(key, value);
            if (i % writesPerBlock==0) {
                simulateNewBlock(true);

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

        stop();
        dumpResults();
        dumpTrieDBFolderSize();
        timeToInsertElements = elapsedTime;
        //countNodes(rootNode);
    }
    public void flushTrie() {
        getTrieStore().flush();
    }

    public void saveTrie() {
        getTrieStore().save(rootNode);
    }

    public void simulateNewBlock(boolean sSaveTrie) {
        // Every N writes, add one to the global clock, so elements inserted
        // get tagged with newer times
        GlobalClock.setTimestamp(GlobalClock.getTimestamp() + 1);
        if (GlobalEncodedObjectStore.get() == null) {
            // If there is no encoded object store, we emulate
            // that a following block must be mined. So we must dispose the current trie
            // and reload it from disk or any other TrieStore cache that exists.
            Keccak256 rootNodeHash = rootNode.getHash();
            if (sSaveTrie)
                saveTrie();
            rootNode = null; // make sure nothing is left
            Optional<Trie> optRootNode = getTrieStore().retrieve(rootNodeHash.getBytes());
            if (!optRootNode.isPresent()) {
                System.out.println("Could not retrieve node ");
                System.exit(1);
            }
            rootNode = optRootNode.get();
        } else {
            // If we have an encoded object store, we never throw away the root
            // but we still have to flush things to disk
            //El problema es que el encoded store no guarda un flag indicando si el nodo esta
            //        en disco o no. Eso hace que siempre se regrabe

            if (sSaveTrie)
                saveTrie();
        }
        blocksCreated++;
        if (blocksCreated % flushNumberOfBlocks==0)
            flushTrie();
    }

   public boolean shouldRunGC() {
        if (ms==null)
            return false;

       return ms.heapIsAlmostFull();
   }

    public void stop() {
        ended = System.currentTimeMillis();
        System.out.println("Forced system garbage collection");
        System.gc();
        System.out.println("Forced store garbage collection");
        if (GlobalEncodedObjectStore.get()!=null) {
            garbageCollection(rootNode);
        }
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
    }

    public void dumpResults() {

        elapsedTime = (ended - started) / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            log("Added nodes/sec: " + (max / elapsedTime));
            log("Added blocks/sec: " + (blocksCreated / elapsedTime));
            if (blocksCreated>0)
                log("Avg processing time/block [msec]: " +  (ended - started)/blocksCreated);
        }

        log("Memory used after test: MB: " + endMbs);
        log("Consumed MBs: " + (endMbs - startMbs));

        printMemStatsShort();

    }

    public void dumpMemResults(int max) {

        elapsedTime = (ended - started) / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            log("Added nodes/sec: " + (max / elapsedTime));
        }

        log("Memory used after test: MB: " + endMbs);
        log("Consumed MBs: " + (endMbs - startMbs));

    }

    public void existentReadNodes(Trie t) {



        byte[][] keys = null;
        boolean preloadKeys = false;
        boolean sequentialKeys = false;
        long sequentialBase = maxKeysBottomUp;
        boolean x = false;
        if (x)
            return;
        if (preloadKeys) {
            keys = new byte[(int) (maxKeysTopDown + maxKeysBottomUp)][];
            for (int i = 0; i < keys.length; i++) {
                byte[] key = getExistentRandomKey();
                keys[i] = key;
            }
        }
        started = System.currentTimeMillis();
        // now we count something
        log("random existent key read...");

        long maxReads = 1_000_000;
        int found=0;

        boolean debugQueries = false;
        int totalKeys =(int) (maxKeysTopDown + maxKeysBottomUp);
        for(int i=0;i<maxReads;i++) {
            if (i % readsPerBlock==0) {
                simulateNewBlock(false);
            }
            if (i % 50000==0)
                System.out.println("iteration: "+i);
            byte[] key;
            if (sequentialKeys)
                key = getExistentKey((i+sequentialBase) % totalKeys);
            else
            if (preloadKeys)
                key = keys[i % keys.length];
            else {
                key = getExistentRandomKey();
            }
            if (debugQueries) {
                TrieKeySlice eks = ExpandedTrieKeySlice.getFactory().
                        fromEncoded(key, 0, key.length * 8);
                System.out.println("look for key: " + eks.toString());
                System.out.println("iter: "+i+" key: "+ByteUtil.toHexString(key));
            }
            if (t.find(key)==null) {
                System.out.println("error: "+i+" key: "+ByteUtil.toHexString(key));
                System.exit(1);
            }

        }
        log("numExistentReads: "+maxReads);

        ended = System.currentTimeMillis();
        long elapsedTimeMs = (ended - started);
        log("Elapsed time [ms]: " + elapsedTimeMs);
        if (elapsedTimeMs!=0) {
            randomExistentReadsPerSecond =(maxReads * 1000 / elapsedTimeMs);
            log("Read existent leaf nodes/sec: " + randomExistentReadsPerSecond);

        }
        log("Finished.");
    }
    public void randomReadNodes(Trie t) {

        started = System.currentTimeMillis();
        // now we count something
        log("random read...");

        long maxReads = 10_000_000;
        int found=0;
        for(int i=0;i<maxReads;i++) {
            if (i % readsPerBlock==0) {
                simulateNewBlock(false);
            }
            byte[] key = TestUtils.getPseudoRandom().randomBytes(varKeySize);
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
        log("Finished.");
    }

    public void countNodes(Trie t) {

        started = System.currentTimeMillis();
        // now we count something
        log("Counting...");

        //log("nodes: "+t.countNodes());
        leafNodeCount = t.countLeafNodes();
        log("Counted!");

        log("leaf nodes: "+leafNodeCount);

        ended = System.currentTimeMillis();
        elapsedTime = (ended - started) / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            scannedLeafNodesPerSecond = (max / elapsedTime);
            log("Scanned leaf nodes/sec: " + scannedLeafNodesPerSecond); // 18K
        }
        log("Finished.");
    }

    public static int log2(long n){
        if(n <= 0) throw new IllegalArgumentException();
        return (int) (63 - Long.numberOfLeadingZeros(n));
    }
    public static int subTreeCount = 64;

    int leafBits;
    int intermediateBits; // these are fixed
    int splitBits;
    int subTreeSize;


    public void buildbottomUp() {
        maxKeysBottomUp = max;
        prepare();
        // The idea is that we create the terminal nodes, then we pair them up
        // and keep doing this.
        // We don't store the leaf nodes, they are generated dynamically
        long width = max; // this is the total number of elements in the tree
        int log2w = log2(width);
        if ((1 << log2w) != width)
            throw new RuntimeException("only for powers of 2");

        // Intermediate bits do not contain fixed bits.
        // Intermediate bits are all variable bits
        intermediateBits =log2w;
        // leafBits are the bits that will be chosen randombly as leaf nodes prefixes
        leafBits = varKeySize *8-intermediateBits;
        if (leafBits<0)
            throw new RuntimeException("Not enough leaves");

        splitBits = intermediateBits;
        // it will be split in subTreeCount subtrees
        int stCount = subTreeCount;
        // Each node corresponds to a subtree
        Trie[] nodes = new Trie[stCount];

        // The subTreeBits bits are filled completely
        //

        int subTreeBits = log2(stCount);
        long lsubTreeSize  = width/stCount;
        if (lsubTreeSize>Integer.MAX_VALUE)
            throw new RuntimeException("invalid width");

        subTreeSize = (int) lsubTreeSize;
        int stWidth = (int) width/stCount;
        if (stWidth==0)
            throw new RuntimeException("Not enough leafts");

        log("Building subttrees...");
        start();
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
        if (fixKeySize>0) {
            fixedKeyPart = TestUtils.getPseudoRandom().randomBytes(fixKeySize);
            // we set the last bit forced
            // leave 1 bit out for the node branch
            PseudoRandom.setCompactBit(fixedKeyPart,fixKeySize*8-1,false); // make sure we create the right child.
            TrieKeySlice fixSharedPath = TrieKeySliceFactoryInstance.get().fromEncoded(fixedKeyPart, 0, fixKeySize * 8 - 1);

            // now build a node at the top with the fixed part.
            // We assume here the previous rootNode had two children, so the tree
            // is well balanced
            if ((rootNode.getLeft().isEmpty()) || (rootNode.getRight().isEmpty())) {
                log("The trie is not well balanced");
            }

            rootNode = new Trie(getTrieStore(), fixSharedPath,
                    null,
                    new NodeReference(null, rootNode, null, null),
                    NodeReference.empty(),
                    Uint24.ZERO,
                    null);
            System.out.println("Post fix part: ");
            //dumpTrie();
        }
        stop();
        dumpResults();
        timeToBuildTree = elapsedTime;

        // This flush operation will compute all hashes for millions of nodes
        // It can take minutes
        log("Start saving (all nodes hashes computed)...");
        saveTrie();
        log("stop saving");
        log("Start flushing..");
        flushTrie();
        log("stop flushing");

        //countNodes(t);
        existentReadNodes(rootNode);
    }
    PseudoRandom pseudoRandom = new PseudoRandom();

    Trie buildSubtree(int width, int intermediateBits, int leafBits) {
        Trie[] nodes = new Trie[width];
        int step = 1_000_000;

        // if width is larger than 2^leaftBits, then
        int leafKeyBufferSize = (leafBits+7)/8;
        for (int i = 0; i < width; i++) {
            pseudoRandom.setSeed(leafNodeCounter++);
            byte[] key = pseudoRandom.randomBytes(leafKeyBufferSize);
            TrieKeySlice keySlice = TrieKeySliceFactoryInstance.get().fromEncoded(key,
                    0, leafBits);
            //System.out.println("key slice: "+keySlice.toString());
            byte[] value = TestUtils.getPseudoRandom().randomBytes(valueSize);
            nodes[i] = new Trie(null,
                    keySlice,
                    value,
                    NodeReference.empty(),
                    NodeReference.empty(),
                    new Uint24(value.length),
                    null);

            if ((i % step == 0) && (i>0)) {
                dumpProgress(i,width);
            }


        }
        Trie root = mergeNodes(nodes);
        return root;
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

                newNodes[i] = new Trie(null,emptySharedPath,
                        null,
                        new NodeReference(null,nodes[i*2],null,null),
                        new NodeReference(null,nodes[i*2+1],null,null),
                        Uint24.ZERO,
                        null);
                nodes[i*2] = null; // try to free mem
                nodes[i*2+1] = null;

            }
            nodes = newNodes;
            newNodes = null;
        }
        return nodes[0];

    }

    public void simpleTrieTest() {
        Trie t = new Trie();
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
    FileWriter myWriter;

    public void test() {
        Object x;
        byte[] vec = new byte[10];
        x = vec;
        if (x instanceof byte[]) {
            System.out.println("works");
        }
    }
    class ComputeKeccakKey implements Function<byte[],ByteArrayWrapper> {

        public ByteArrayWrapper apply(byte[]  data) {
            return new ByteArrayWrapper(Keccak256Helper.keccak256(data));
        }
    }

    class ComputeHashFromKeccakKey implements CAHashMap.getHashcode<ByteArrayWrapper> {
        public  int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
            return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
        }
        public int hashCodeFromHashDigest(byte[] bytes) {
            // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
            return intFromBytes(bytes[28], bytes[29], bytes[30], bytes[31]);
        }
        public int getHashcode(ByteArrayWrapper  data) {
            return hashCodeFromHashDigest(data.getData());
        }
    }

    public void testCACacheMem() {
        ComputeKeccakKey  computeKey = new ComputeKeccakKey();
        ComputeHashFromKeccakKey computeHashFromKeccakKey = new ComputeHashFromKeccakKey();
        float loadFActor = 0.3f;
        boolean testCAHashMap = false;
        boolean testLinkedHashMap = true;
        CAHashMap<ByteArrayWrapper, byte[]> camap =null;
        AbstractMap<ByteArrayWrapper, byte[]> map=null;

        int vmax = 10_000_000;
        int initialSize = (int) (vmax/loadFActor);
        String testClass ="";
        start();
        if (testCAHashMap) {
            camap = new CAHashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFActor, computeKey, computeHashFromKeccakKey);
            map = camap;
        } else
        if (testLinkedHashMap) {
            map =  new LinkedHashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFActor);
        } else {
            map =  new HashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFActor);
        }
        testClass = map.getClass().getName();

        for (int i=0;i<vmax;i++) {
            byte[] v1 = new byte[50];
            v1[0]= (byte) (i & 0xff);
            v1[1] =(byte)((i>>8)& 0xff);
            v1[2] =(byte)((i>>16)& 0xff);
            v1[3] =(byte)((i>>24)& 0xff);


            if (testCAHashMap)
                camap.put(v1);
            else {
                ByteArrayWrapper k1 = computeKey.apply(v1);
                map.put(k1, v1);
            }
            if (i % 100_0000==0) {
                dumpMemProgress(i,vmax);
            }
        }
        stop();
        dumpMemResults(vmax);
        System.out.println("Class: "+testClass);
        if (testCAHashMap) {

            System.out.println("hashMapCount: " + camap.hashMapCount);
            System.out.println("camap.size : " + camap.size());
        } else {
            System.out.println("HashMap test");
            System.out.println("map.size : " + map.size());
        }
    }
    public void testCACache() {
        ComputeKeccakKey  computeKey = new ComputeKeccakKey();
        ComputeHashFromKeccakKey computeHashFromKeccakKey = new ComputeHashFromKeccakKey();
        CAHashMap<ByteArrayWrapper, byte[]> map =
                new CAHashMap<ByteArrayWrapper, byte[]>(10,0.3f,computeKey,computeHashFromKeccakKey);
        int max = 10000;
        ByteArrayWrapper[] k = new ByteArrayWrapper[max];
        byte[][] v = new byte[max][];
        for (int i=0;i<max;i++) {
            byte[] v1 = new byte[]{ (byte) (i & 0xff), (byte)((i>>8)& 0xff), (byte)((i>>16)& 0xff)};
            ByteArrayWrapper k1 = computeKey.apply(v1);
            k[i] = k1;
            v[i] = v1;
            map.put(v1);
        }

        System.out.println("hashMapCount: "+map.hashMapCount);
        System.out.println("map.size : "+map.size());

        for (int i=0;i<max;i++) {
            checkEqual(map.get(k[i]), v[i]);
        }
        for (int i=0;i<max/2;i++) {
            map.remove(k[i]);
        }
        for (int i=0;i<max/2;i++) {
            checkEqual(map.get(k[i]), null);
        }
        byte[] x = map.get(k[max/2]);

        for (int i=max/2;i<max;i++) {
            checkEqual(map.get(k[i]), v[i]);
        }
        System.out.println("checked "+max);
    }

    public void checkEqual(byte[] a, byte[] b) {
        if ((a==null) && (b==null))
            return;
        if ((a!=null) && (b!=null)) {
            if (FastByteComparisons.compareTo(
                    a, 0, a.length,
                    b, 0, b.length) != 0)
                throw new RuntimeException("mismatch");
        } else
            throw new RuntimeException("mismatch2");
    }

    public void createLogFile(String basename,String expectedItems) {
        try {
            String name = "Results/"+basename;
            name=name+"-"+TrieKeySliceFactoryInstance.get().getClass().getSimpleName();
            String om;
            if (GlobalEncodedObjectStore.get()==null)
                om ="_";
            else
                om = GlobalEncodedObjectStore.get().getClass().getSimpleName();
            name = name + "-"+om;
            name = name + "-"+expectedItems;
            name = name + "-" + testMode.toString();
            name = name +"-Max_"+ getMillions( Runtime.getRuntime().maxMemory());
            Date date = Calendar.getInstance().getTime();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh.mm.ss");
            String strDate = dateFormat.format(date);
            name = name + "-"+ strDate;
            logName = name;

            File myObj = new File(name+".txt");
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("File already exists.");
            }
            System.out.println("File path: "+myObj.getAbsolutePath());

            myWriter = new FileWriter(myObj);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void closeLog() {

        try {
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }

    long startMillis = System.currentTimeMillis();

    public void log(String s) {
        long stime = System.currentTimeMillis()-startMillis;
        long sec = stime /1000;
        long mil = stime % 1000;
        String strDate =""+sec+"."+mil+": ";

        System.out.println(strDate+": "+s);
        if (myWriter==null) return;

        try {
            myWriter.write(s+"\n");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

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
        return null; // Do not use a encoded store
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

    public void setupGlobalClasses(EncodedObjectStore encodedObjectStore) {
        GlobalEncodedObjectStore.set(encodedObjectStore);
        //EncodedObjectHeap.default_spaceMegabytes = 500;
        //TrieKeySliceFactoryInstance.setTrieKeySliceFactory(CompactTrieKeySlice.getFactory());
        TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());
    }

    public void logGlobalClasses() {
        log("TrieKeySliceFactory classname: "+TrieKeySliceFactoryInstance.get().getClass().getName());
        GlobalEncodedObjectStore.get();
        if (GlobalEncodedObjectStore.get()==null)
            log("ObjectMapper not present");
        else
            log("ObjectMapper classname: "+ GlobalEncodedObjectStore.get().getClass().getName());
    }

    public String getMillions(long i) {
        String maxStr = ""+ (i/1000/1000)+"M";
        return maxStr;
    }



    public void smallWorldTest(Class<? extends EncodedObjectStore> aClass) {
        //testMode = TestMode.testERC20Balances;
        //testMode = TestMode.testERC20LongBalances;
        testMode = TestMode.testEOAs;
        long addMaxKeysBottomUp  = 4L * (1 << 20);
        long addMaxKeysTopDown = 1L * (1 << 20);
        boolean testExistentKeys = false;

        if (testExistentKeys) {
            testMode = TestMode.microTest;
            addMaxKeysBottomUp = 64;
            addMaxKeysTopDown = 0;
            subTreeCount = 8;
        }

        // To store 10M accounts, you need at least 1.5 GB of storage.
        int memoryForStoreMegabytes = 2000; // 2 GB
        String maxStr = ""+ getMillions(addMaxKeysBottomUp )+" plus "+getMillions(addMaxKeysTopDown);

        EncodedObjectStore encodedObjectStore =getEncodedStore(aClass);
        // I setup the global encoded object store here so that the log filename
        // takes the store class name to build the log file name
        setupGlobalClasses(encodedObjectStore);
        if (encodedObjectStore!=null) {
            encodedObjectStore.setMaxMemory(memoryForStoreMegabytes * 1000L * 1000L);
            encodedObjectStore.initialize();
        }
        createLogFile("swtest",maxStr);
        logGlobalClasses();




        if (addMaxKeysBottomUp >0) {
            // Create a high number of accounts bottom-up
            // This is a much faster method, as it doesn't create waste in
            // memory and doesn't trigger neither our GC (and probably it
            // doesn't trigger Java's GC often)
            max = addMaxKeysBottomUp ;
            buildbottomUp();
            countNodes(rootNode);
            //System.exit(0);
        }
        // Add another number of accounts, but this time by inserting
        // elements in the trie. This is slower, and creates unused nodes
        // that our GC must collect. It also creates a high number of temporal
        // Java objects that the Java GC need to periodically collect.
        // now add another 8M items to it!
        max = addMaxKeysTopDown;
        buildByInsertion();
        logTraceInfo();
        logBlocksCreated();
        countNodes(rootNode);

        existentReadNodes(rootNode);
        randomReadNodes(rootNode);
        logTraceInfo();
        dumpResultsInCSV();
        closeLog();
    }

    public void logBlocksCreated() {
        log("blocks created: "+blocksCreated);
        log("writesPerBlock: "+writesPerBlock);
        log("readsPerBlock: "+readsPerBlock);

    }
    public void logTraceInfo() {
        String s = "::";
        List<String> stats = ((TrieStoreImpl) getTrieStore()).getTraceInfoReport();
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
                timeToBuildTree);
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
        System.out.println("Creating the store...");
        EncodedObjectStore encodedObjectStore =getEncodedStore(aClass);

        testMode = TestMode.testEOAs;

        setupGlobalClasses(encodedObjectStore);
        encodedObjectStore.setMaxMemory(memoryForStoreMegabytes*1000L*1000L);
        encodedObjectStore.initialize();

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
        existentReadNodes(rootNode);
        countNodes(rootNode);
        randomReadNodes(rootNode);
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
        countNodes(rootNode);
        dumpTrie();
        log("---------------------------");

        max = 4;
        buildByInsertion();
        countNodes(rootNode);
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
                    ((LongEOR) rootNode.getEncodedRef()).ofs);
            System.exit(0);
            EncodedObjectHeap.get().reset();

        }

        long rootOfs = EncodedObjectHeap.get().load("4M");
        rootNode = retrieveNode(new LongEOR(rootOfs));
        countNodes(rootNode);
        System.exit(0);
        // now add another 8M items to it!
        max = 8L * (1 << 20);
        buildByInsertion();
    }


    public static Trie retrieveNode(EncodedObjectRef encodedOfs) {
        ObjectReference r = GlobalEncodedObjectStore.get().retrieve(encodedOfs);
        Trie node = Trie.fromMessage(r.message, encodedOfs, r.leftRef, r.rightRef, null);
        return node;
    }

    public static void main (String args[]) {
        //testInMemStore();
        CompareTries c = new CompareTries();
        //c.testCACacheMem();
        //c.topdownTest();
        //c.buildbottomUp();
        c.smallWorldTest();
        //c.microWorldTest(chooseEncodedStore());
        System.exit(0);
    }
}
