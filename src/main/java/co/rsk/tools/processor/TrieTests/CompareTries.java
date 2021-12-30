package co.rsk.tools.processor.TrieTests;

//import co.rsk.tools.processor.TrieTests.sepAttempt.InMemTrie;

import co.rsk.core.Coin;
import co.rsk.core.types.ints.Uint24;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieTests.ohard.HardEncodedObjectStore;
import co.rsk.tools.processor.TrieTests.oheap.LongEOR;
import co.rsk.tools.processor.TrieTests.oheap.EncodedObjectHeap;
import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import org.ethereum.core.AccountState;
import org.ethereum.core.Denomination;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Iterator;

public class CompareTries {
    int accountSize;
    int valueSize;
    int fixKeySize;
    int varKeySize;
    long max = 32L*(1<<20);// 8 Million nodes // 1_000_000;
    EncodedObjectStore ms;
    long remapTime =0;
    long remapTimeBelow50 =0;
    long remapTimeOver50 =0;

    long startMbs;
    long started;
    long ended;
    long endMbs;
    Trie rootNode;
    enum TestMode {
        testERC20Balances,
        testEOAs,
        microTest
    }
    TestMode testMode = TestMode.microTest;

    public void prepare() {
        // in satoshis
        // 0.1 bitcoin
        TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());

        AccountState a = new AccountState(BigInteger.valueOf(100),
                new Coin(
                        Denomination.satoshisToWeis(10_1000_1000)));

        accountSize = a.getEncoded().length;

        System.out.println("Average account size: "+accountSize);
        if (testMode==TestMode.microTest) {
            fixKeySize =1;
            varKeySize = 1;// test
            valueSize = 1;
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

            // A cell address contains 10+20 account bytes, plus 10+32.
            // + 1 domain
            // + 1 intermediate (always fixed byte, we can skip for these tests)
            valueSize = accountSize;
            varKeySize = 10 + 20;//+10+32;
        }
        System.out.println("varKeysize: "+ varKeySize);
        System.out.println("fixKeysize: "+ fixKeySize);
        System.out.println("keysize: "+(varKeySize+fixKeySize));

        System.out.println("valueSize: "+valueSize);

        ms = GlobalEncodedObjectStore.get();
        remapTime=0;
        remapTimeBelow50 =0;
        remapTimeOver50 =0;
    }


    public void start() {
        startMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        System.out.println("Used Before MB: " + startMbs);

        started = System.currentTimeMillis();
        System.out.println("Filling...");
    }

    public void printMemStatsShort() {
        if (!(ms instanceof EncodedObjectHeap))
            return;

        EncodedObjectHeap ms = (EncodedObjectHeap) this.ms;

        System.out.println("InMemStore usage[%]: " + ms.getUsagePercent());
        System.out.println("InMemStore usage[Mb]: " + ms.getMemUsed() / 1000 / 1000);

        System.out.println("total remap time[s]: " + remapTime / 1000);
        System.out.println("remap time below 50% [s]: " + remapTimeBelow50 / 1000);
        System.out.println("remap time above 50% [s]: " + remapTimeOver50 / 1000);

        System.out.println("remap time per insertion[msec]: " + (remapTime * 1.0 / max));
    }

    public void printMemStats(String s) {
            if (!(ms instanceof EncodedObjectHeap))
                return;

            EncodedObjectHeap ms = (EncodedObjectHeap) this.ms;
            System.out.println(s+" InMemStore usage[%]: " + ms.getUsagePercent());
            System.out.println(s+" InMemStore usage[Mb]: " + ms.getMemUsed() / 1000 / 1000);
            System.out.println(s+" InMemStore alloc[Mb]: " + ms.getMemAllocated() / 1000 / 1000);
            System.out.println(s+" InMemStore   max[Mb]: " + ms.getMemMax() / 1000 / 1000);
            System.out.println(s+" InMemStore  Empty spaces: " + ms.getEmptySpacesCount()+" ("+ms.getEmptySpacesDesc()+")");
            System.out.println(s+" InMemStore Filled spaces: " + ms.getFilledSpacesCount()+" ("+ms.getFilledSpacesDesc()+")");
            System.out.println(s+" InMemStore cur space    : " + ms.getCurSpaceNum());
            System.out.println(s+" InMemStore cur space usage[%]: " + ms.getCurSpace().getUsagePercent());
        }

    public void dumpProgress(long i,long amax) {
        System.out.println("item " + i + " (" + (i * 100 / amax) + "%)");
        printMemStats("--");
        long ended = System.currentTimeMillis();
        long  elapsedTime = (ended - started) / 1000;
        System.out.println("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            System.out.println("-- Added nodes/sec: "+(i/elapsedTime)); // 18K
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        //System.gc();
        System.out.println("-- Jave Mem Used[MB]: " + endMbs);
        System.out.println("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));
    }

    public void garbageCollection(Trie t) {
        if (!(ms instanceof EncodedObjectHeap))
            return;

        EncodedObjectHeap ms = (EncodedObjectHeap) this.ms;

        System.out.println(":::::::::::::::::::::::::::::::::::::");
        System.out.println(":: Remapping from: "+ms.getUsagePercent()+"%");
        long rstarted = System.currentTimeMillis();
        long rstartMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        System.out.println(":: Remap Used Before MB: " + rstartMbs);
        ms.beginRemap();
        System.out.println(":: Remap removing spaces: "+ ms.getRemovingSpaccesDesc());
        t.compressEncodingsRecursivelly();
        //t.checkTree();
        long rended = System.currentTimeMillis();

        long  relapsedTime = (rended - rstarted) ;

        if (ms.getUsagePercent()<50)
            remapTimeBelow50 +=relapsedTime;
        else
            remapTimeOver50 +=relapsedTime;
        ms.endRemap();

        System.out.println(":: Remapping   to: "+ms.getUsagePercent()+"%");
        printMemStats("::");
        remapTime +=relapsedTime;
        System.out.println(":: Remap Elapsed time [msec]: " + relapsedTime);
        System.out.println(":: Remap compressionPercent [%]: "+ ms.getCompressionPercent());
        System.gc();
        long rendMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        System.out.println(":: Remap Used After MB: " + rendMbs );
        System.out.println(":: Remap Freed MBs: " + (rstartMbs-rendMbs));
        System.out.println(":::::::::::::::::::::::::::::::::::::");

    }

    public void buildByInsertion() {
        prepare();

        start();
        if (rootNode ==null)
            rootNode = new Trie();

        byte[] fixPart = TestUtils.randomBytes(fixKeySize);
        byte[] key = new byte[fixKeySize+varKeySize];

        for (long i = 0; i < max; i++) {
            //byte[] key = TestUtils.randomBytes(varKeySize);
            TestUtils.fillRandomBytes(key,fixKeySize,varKeySize);
            byte[] value = TestUtils.randomBytes(valueSize);
            rootNode = rootNode.put(key, value);
            if (i % 100000 == 0) {
                dumpProgress(i,max);
            }
            if (shouldRunGC()) {
                garbageCollection(rootNode);
            }
        }
        stop();
        dumpResults();
        //countNodes(rootNode);
    }

   public boolean shouldRunGC() {
       if (!(ms instanceof EncodedObjectHeap))
           return false;

       EncodedObjectHeap ms = (EncodedObjectHeap) this.ms;
       return ms.heapIsAlmostFull();
   }

    public void stop() {
        ended = System.currentTimeMillis();
        System.gc();
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
    }

    public void dumpResults() {

        long elapsedTime = (ended - started) / 1000;
        System.out.println("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0)
            System.out.println("Added nodes/sec: " + (max / elapsedTime));

        System.out.println("Used After MB: " + endMbs);
        System.out.println("Comsumed MBs: " + (endMbs - startMbs));

        printMemStatsShort();

    }

    public void countNodes(Trie t) {

        started = System.currentTimeMillis();
        // now we count something
        System.out.println("Counting...");
        //System.out.println("nodes: "+t.countNodes());
        System.out.println("leaf nodes: "+t.countLeafNodes());

        ended = System.currentTimeMillis();
        long elapsedTime = (ended - started) / 1000;
        System.out.println("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0)
            System.out.println("Scanned leaf nodes/sec: "+(max/elapsedTime)); // 18K

        System.out.println("Finished.");
    }

    public static int log2(long n){
        if(n <= 0) throw new IllegalArgumentException();
        return (int) (63 - Long.numberOfLeadingZeros(n));
    }
    public static int subTreeCount = 64;

    public void buildbottomUp() {
        prepare();
        // The idea is that we create the terminal nodes, then we pair them up
        // and keep doing this.
        // We don't store the leaf nodes, they are generated dynamically
        long width = max;
        int log2w = log2(width);
        if ((1 << log2w) != width)
            throw new RuntimeException("only for powers of 2");

        int intermediateBits =log2w;
        int leafBits = varKeySize *8-intermediateBits;
        if (leafBits<0)
            throw new RuntimeException("Not enough leafts");

        // it will be split in 8 subtrees
        int stCount = subTreeCount;
        Trie[] nodes = new Trie[stCount];
        int subTreeBits = log2(stCount);
        long stWidthLong = width/stCount;
        if (stWidthLong>Integer.MAX_VALUE)
            throw new RuntimeException("invalid width");

        int stWidth = (int) width/stCount;
        if (stWidth==0)
            throw new RuntimeException("Not enough leafts");

        System.out.println("Building subttrees...");
        start();
        int step = 1;
        for (int s = 0; s < stCount; s++) {
            System.out.println("Building subttree: "+(s+1)+" of "+stCount);
            nodes[s] = buildSubtree(stWidth,intermediateBits - subTreeBits, leafBits);
            if ((s % step == 0) && (s>0)) {
                dumpProgress(s,stCount);
            }
        }
        System.out.println("Merging subttrees...");
        rootNode = mergeNodes(nodes);

        byte[] encodedFixKey = new byte[fixKeySize*8];
        // leave 1 bit out for the node branch
        TrieKeySlice fixSharedPath =TrieKeySliceFactoryInstance.get().fromEncoded(encodedFixKey,0,fixKeySize*8-1);

        // now build a node at the top with the fixed part.
        // We assume here the previous rootNode had two children, so the tree
        // is well balanced
        if ((rootNode.getLeft().isEmpty()) || (rootNode.getRight().isEmpty())) {
            System.out.println("The trie is not well balanced");
        }

        rootNode = new Trie(null,fixSharedPath,
                null,
                new NodeReference(null,rootNode,null,null),
                NodeReference.empty(),
                Uint24.ZERO,
                null);
        stop();
        dumpResults();
        //countNodes(t);
    }

    Trie buildSubtree(int width, int intermediateBits, int leafBits) {
        Trie[] nodes = new Trie[width];
        int step = 1_000_000;

        for (int i = 0; i < width; i++) {
            byte[] key = TestUtils.randomBytes(leafBits);
            byte[] value = TestUtils.randomBytes(valueSize);
            nodes[i] = new Trie(null,
                    TrieKeySliceFactoryInstance.get().fromEncoded(key,
                            0, leafBits),
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
        System.out.println("Creating intermediate levels...");
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
                    System.out.println("Level "+level+" of "+intermediateBits+" width: "+width);
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

    static public EncodedObjectStore chooseEncodedStore() {

        EncodedObjectStore encodedObjectStore;
        // Here you have to choose one encoded object store.
        // uncomment a single line from the lines below:

        //encodedObjectStore = new SoftRefEncodedObjectStore();
        //encodedObjectStore = new EncodedObjectHeap();
        //encodedObjectStore = new EncodedObjectHashMap();
        encodedObjectStore = new HardEncodedObjectStore();
        //encodedObjectStore = new MultiSoftEncodedObjectStore();
        return encodedObjectStore;
    }

    public void smallWorldTest() {
        smallWorldTest(chooseEncodedStore());
    }

    public void setupGlobalClasses(EncodedObjectStore encodedObjectStore) {
        GlobalEncodedObjectStore.set(encodedObjectStore);
        EncodedObjectHeap.default_spaceMegabytes = 500;
        //TrieKeySliceFactoryInstance.setTrieKeySliceFactory(CompactTrieKeySlice.getFactory());
        TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());

        System.out.println("TrieKeySliceFactory classname: "+TrieKeySliceFactoryInstance.get().getClass().getName());
        GlobalEncodedObjectStore.get();
        if (GlobalEncodedObjectStore.get()==null)
            System.out.println("ObjectMapper not present");
        else
            System.out.println("ObjectMapper classname: "+ GlobalEncodedObjectStore.get().getClass().getName());
    }

    public void smallWorldTest(EncodedObjectStore encodedObjectStore) {
        testMode = TestMode.testERC20Balances;
        setupGlobalClasses(encodedObjectStore);

        // Create a high number of accounts bottom-up
        // This is a much faster method, as it doesn't create waste in
        // memory and doesn't trigger neither our GC (and probably it
        // doesn't trigger Java's GC often)
        max = 16L * (1 << 20);
        buildbottomUp();
        countNodes(rootNode);
        System.exit(0);

        // Add another number of accounts, but this time by inserting
        // elements in the trie. This is slower, and creates unused nodes
        // that our GC must collect. It also creates a high number of temporal
        // Java objects that the Java GC need to periodically collect.
        // now add another 8M items to it!
        max = 1L*(1<<20);
        buildByInsertion();
        countNodes(rootNode);
    }

    public void microWorldTest(EncodedObjectStore encodedObjectStore) {
        // Creates 4 nodes bottom up, and add 4 additional nodes
        // top down
        setupGlobalClasses(encodedObjectStore);
        subTreeCount = 4;
        max = 4;
        buildbottomUp();
        countNodes(rootNode);
        dumpTrie();
        System.out.println("---------------------------");

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
        System.out.println("Trie checked ok");

    }

    public void dumpTrie() {
        Iterator<IterationElement> iterator =rootNode.getPreOrderIterator();
        while (iterator.hasNext()) {
            IterationElement e =iterator.next();
            String keyValue = interatorElementToStr(e);
            System.out.println(keyValue);
        }
    }

    public static void main (String args[]) {
        //testInMemStore();
        CompareTries c = new CompareTries();
        c.smallWorldTest();
        //c.microWorldTest(chooseEncodedStore());
        System.exit(0);
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
}
