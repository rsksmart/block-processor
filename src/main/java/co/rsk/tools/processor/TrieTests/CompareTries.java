package co.rsk.tools.processor.TrieTests;

//import co.rsk.tools.processor.TrieTests.sepAttempt.InMemTrie;

import co.rsk.core.Coin;
import co.rsk.core.types.ints.Uint24;
import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySliceFactory;
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.core.Denomination;
import org.w3c.dom.Node;

import java.math.BigInteger;

public class CompareTries {
    int accountSize;
    int valueSize;
    int keySize;
    int max = 8*(1<<20);// 8 Million nodes // 1_000_000;
    InMemStore ms;
    long remapTime =0;
    long remapTimeBelow50 =0;
    long remapTimeOver50 =0;

    long startMbs;
    long started;
    long ended;
    long endMbs;

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

        ms =InMemStore.get();
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

    public void dumpProgress(int i) {
        System.out.println("item " + i + " (" + (i * 100 / max) + "%)");
        System.out.println("--InMemStore usage[%]: "+ms.getUsagePercent());
        System.out.println("--InMemStore usage[Mb]: "+ms.getMemUsed()/1000/1000);
        long ended = System.currentTimeMillis();
        long  elapsedTime = (ended - started) / 1000;
        System.out.println("--Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            System.out.println("--Added nodes/sec: "+(i/elapsedTime)); // 18K
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        //System.gc();
        System.out.println("--Jave Mem Used[MB]: " + endMbs);
        System.out.println("--Jave Mem Comsumed [MB]: " + (endMbs - startMbs));
    }

    public void garbageCollection(Trie t) {
        System.out.println(":::::::::::::::::::::::::::::::::::::");
        System.out.println("::Remapping from: "+ms.getUsagePercent()+"%");
        long rstarted = System.currentTimeMillis();
        long rstartMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        System.out.println("::Remap Used Before MB: " + rstartMbs);
        ms.beginRemap();
        t.compressTree();
        long rended = System.currentTimeMillis();

        long  relapsedTime = (rended - rstarted) ;

        if (ms.getUsagePercent()<50)
            remapTimeBelow50 +=relapsedTime;
        else
            remapTimeOver50 +=relapsedTime;
        ms.endRemap();

        System.out.println("::Remapping   to: "+ms.getUsagePercent()+"%");
        remapTime +=relapsedTime;
        System.out.println("::Remap Elapsed time [msec]: " + relapsedTime);
        System.gc();
        long rendMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        System.out.println("::Remap Used After MB: " + rendMbs );
        System.out.println("::Remap Freed MBs: " + (rstartMbs-rendMbs));
        System.out.println(":::::::::::::::::::::::::::::::::::::");

    }

    public void buildByInsertion() {
        prepare();

        start();
        Trie t = new Trie();

        for (int i = 0; i < max; i++) {

            byte[] key = TestUtils.randomBytes(keySize);
            byte[] value = TestUtils.randomBytes(valueSize);
            t = t.put(key, value);
            if (i % 100000 == 0) {
                dumpProgress(i);
            }
            if (ms.almostFull()) {
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

    public static int log2(int n){
        if(n <= 0) throw new IllegalArgumentException();
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    public void buildbottomUp() {
        prepare();
        // The idea is that we create the terminal nodes, then we pair them up
        // and keep doing this.
        // We don't store the leaf nodes, they are generated dynamically
        int width = max;
        int log2w = log2(width);
        if((1<<log2w)!=width)
            throw new RuntimeException("only for powers of 2");

        Trie[] nodes = new Trie[width];
        int intermediateBits =log2w;
        int leafBits = keySize*8-intermediateBits;

        int step = 300000;
        start();
        for (int i=0;i<width;i++) {
            byte[] key = TestUtils.randomBytes(leafBits);
            byte[] value = TestUtils.randomBytes(valueSize);
            nodes[i] = new Trie(null,
                    TrieKeySliceFactoryInstance.get().fromEncoded(key,
                            0,leafBits),
                    value,
                    NodeReference.empty(),
                    NodeReference.empty(),
                    new Uint24(value.length),
                    null);

            if (i % step == 0) {
                dumpProgress(i);
            }


        }
        System.out.println("Creating intermediate levels...");
        TrieKeySlice emptySharedPath =TrieKeySliceFactoryInstance.get().empty();
        int level =0;
        while (width>1) {
            width = width/2;
            level++;
            Trie[] newNodes = new Trie[width];
            for (int i=0;i<width;i++) {

                if (i % 50000 == 0) {
                    System.out.println("Level "+level+" of "+intermediateBits+" width: "+width);
                    dumpProgress(i);
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
        stop();
        dumpResults();
        countNodes(nodes[0]);
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

    public static void main (String args[]) {
      //new CompareTries().buildByInsertion();
        new CompareTries().buildbottomUp();
    }
}
