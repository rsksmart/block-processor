package co.rsk.tools.processor.TrieTests;

//import co.rsk.tools.processor.TrieTests.sepAttempt.InMemTrie;

import co.rsk.core.Coin;
import co.rsk.core.types.ints.Uint24;
import co.rsk.tools.processor.TrieTests.oheap.ObjectReference;
import co.rsk.tools.processor.TrieTests.oheap.ObjectHeap;
import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import org.ethereum.core.AccountState;
import org.ethereum.core.Denomination;

import java.math.BigInteger;
import java.util.Arrays;

public class CompareTries {
    int accountSize;
    int valueSize;
    int keySize;
    long max = 32L*(1<<20);// 8 Million nodes // 1_000_000;
    ObjectHeap ms;
    long remapTime =0;
    long remapTimeBelow50 =0;
    long remapTimeOver50 =0;

    long startMbs;
    long started;
    long ended;
    long endMbs;
    Trie t;
    public void prepare() {
        // in satoshis
        // 0.1 bitcoin
        TrieKeySliceFactoryInstance.setTrieKeySliceFactory(ExpandedTrieKeySlice.getFactory());

        AccountState a = new AccountState(BigInteger.valueOf(100),
                new Coin(
                        Denomination.satoshisToWeis(10_1000_1000)));

        accountSize = a.getEncoded().length;
        System.out.println("Average account size: "+accountSize);
        // accountSize = 12
        accountSize = 12;

        // A cell address contains 10+20 account bytes, plus 10+32.
        // + 1 domain
        // + 1 intermediate (always fixed byte, we can skip for these tests)
        valueSize = accountSize;
        keySize = 10+20;//+10+32;

        System.out.println("keysize: "+keySize);
        System.out.println("valueSize: "+valueSize);

        ms = ObjectHeap.get();
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
        public void printMemStats(String s) {
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
        System.out.println(":::::::::::::::::::::::::::::::::::::");
        System.out.println(":: Remapping from: "+ms.getUsagePercent()+"%");
        long rstarted = System.currentTimeMillis();
        long rstartMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        System.out.println(":: Remap Used Before MB: " + rstartMbs);
        ms.beginRemap();
        System.out.println(":: Remap removing spaces: "+ ms.getRemovingSpaccesDesc());
        t.compressTree();
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
        if (t==null)
            t = new Trie();

        for (long i = 0; i < max; i++) {

            byte[] key = TestUtils.randomBytes(keySize);
            byte[] value = TestUtils.randomBytes(valueSize);
            t = t.put(key, value);
            if (i % 100000 == 0) {
                dumpProgress(i,max);
            }
            if (ms.heapIsAlmostFull()) {
                garbageCollection(t);
            }
        }
        stop();
        dumpResults();
        countNodes(t);
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

        System.out.println("InMemStore usage[%]: " + ms.getUsagePercent());
        System.out.println("InMemStore usage[Mb]: " + ms.getMemUsed() / 1000 / 1000);
        System.out.println("total remap time[s]: " + remapTime / 1000);
        System.out.println("remap time below 50% [s]: " + remapTimeBelow50 / 1000);
        System.out.println("remap time above 50% [s]: " + remapTimeOver50 / 1000);

        System.out.println("remap time per insertion[msec]: " + (remapTime * 1.0 / max));
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
        int leafBits = keySize*8-intermediateBits;

        // it will be split in 8 subtrees
        int stCount = 64;
        Trie[] nodes = new Trie[stCount];
        int subTreeBits = log2(stCount);
        long stWidthLong = width/stCount;
        if (stWidthLong>Integer.MAX_VALUE)
            throw new RuntimeException("invalid width");

        int stWidth = (int) width/stCount;

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
        t = mergeNodes(nodes);
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
                        new NodeReference(null,nodes[i*2],null,-1),
                        new NodeReference(null,nodes[i*2+1],null,-1),
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
        ObjectHeap ms  = ObjectHeap.get();
        long leftOfs = ms.buildPointer(ms.getCurSpaceNum(),1000);
        long rightOfs = ms.buildPointer(ms.getCurSpaceNum(),2000);
        byte[] s1  = new byte[100];
        long s1o = ms.add(s1,leftOfs,rightOfs);
        ObjectReference s1ref = ms.retrieve(s1o);
        myassert (s1ref.leftOfs==leftOfs);
        myassert (s1ref.rightOfs==rightOfs);
        myassert (s1ref.len==s1.length);
        myassert (Arrays.equals(s1ref.getAsArray(),s1));

        System.out.println("Ok");

    }

    public void smallWorldTest() {
        ObjectHeap.default_spaceMegabytes = 50;
        ObjectHeap.get();
        max = 1L * (1 << 20);
        buildbottomUp();
        //ObjectHeap.get().save("16M",c.t.getEncodedOfs());
        //System.exit(0);
        //ObjectHeap.get().reset();
        //long rootOfs = ObjectHeap.get().load("4M");
        //t = retrieveNode(rootOfs);
        //countNodes(t);
        // now add another 8M items to it!
        max = 1L*(1<<20);
        buildByInsertion();
        countNodes(t);
    }

    public static void main (String args[]) {
        //testInMemStore();
        CompareTries c = new CompareTries();
        c.smallWorldTest();
        System.exit(0);

        // start with 32M items already loaded
        boolean create = true;
        if (create) {
            c.max = 16L * (1 << 20);
            c.buildbottomUp();
            ObjectHeap.get().save("16M",c.t.getEncodedOfs());
            System.exit(0);
            ObjectHeap.get().reset();

        }

        long rootOfs = ObjectHeap.get().load("4M");
        c.t = retrieveNode(rootOfs);
        c.countNodes(c.t);
        System.exit(0);
        // now add another 8M items to it!
        c.max = 8L*(1<<20);
        c.buildByInsertion();

    }

    public static Trie retrieveNode(long encodedOfs) {
        ObjectReference r = ObjectHeap.get().retrieve(encodedOfs);
        Trie node = Trie.fromMessage(r.message, encodedOfs, r.leftOfs, r.rightOfs, null);
        return node;
    }
}
