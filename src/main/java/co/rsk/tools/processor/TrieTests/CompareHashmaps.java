package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.store.*;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

public class CompareHashmaps extends Benchmark {

    StateTrieSimulator stateTrieSim = new StateTrieSimulator();

    public void computeAverageAccountSize() {
        stateTrieSim.setSimMode(StateTrieSimulator.SimMode.simEOAs);
        stateTrieSim.computeAverageAccountSize();
        log("Average account size: "+stateTrieSim.accountSize);
    }


    ///////////////////////////////////////////////////////////////////////
    // testHashmapReplacement()
    // This is my second approach to optimizing the trie.
    // Instead of keeping the tree structured in memory, the idea is to
    // simply replace the hashmap used for the committed elements in
    // DataSourceWithCache with a more efficient hashmap that provides
    // faster put() and get(), and as a tradeoff, it provides slower
    // operations such as iteration or entrySet() or keySet() which are
    // not needed.
    ///////////////////////////////////////////////////////////////////////
    public void testHashmapReplacement() {
        TrieCACacheRelation myKeyValueRelation = new TrieCACacheRelation();
        float loadFActor = 0.3f;

        // This is the hashmap that we're going to test. You can
        // find all hashmaps data structures that are being tested
        // in the DataStructure enum.
        CompareTries.DataStructure testDS = CompareTries.DataStructure.MaxSizeByteArrayHashMap;

        CAHashMap<ByteArrayWrapper, byte[]> camap =null;
        AbstractMap<ByteArrayWrapper, byte[]> map=null;
        CAHashMap<ByteArrayWrapper, TSNode> tsmap=null;

        int vmax = 9_000_000;
        int maxSize = vmax;

        String testClass ="";

        switch (testDS) {
            case    MaxSizeHashMap:
            case    MaxSizeCACache:
            case    MaxSizeByteArrayHashMap:
                maxSize = vmax;//+1; //8_000_000;
        }

        int initialSize = (int) (maxSize/loadFActor);

        start(true);
        if (testDS== CompareTries.DataStructure.CACache) {
            camap = new CAHashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFActor, myKeyValueRelation);
            map = camap;
        } else
        if (testDS== CompareTries.DataStructure.LinkedHashMap) {
            map =  new LinkedHashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFActor);
        } else
        if (testDS== CompareTries.DataStructure.HashMap) {
            map =  new HashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFActor);
        } else
        if (testDS== CompareTries.DataStructure.NumberedCACache) {
            TSNodeCACacheRelation myTSKeyValueRelation = new TSNodeCACacheRelation(0);
            tsmap = new CAHashMap<ByteArrayWrapper, TSNode>((int) initialSize, loadFActor, myTSKeyValueRelation);
            map = camap;
        } else
        if (testDS== CompareTries.DataStructure.MaxSizeCACache) {
            TSNodeCACacheRelation myTSKeyValueRelation = new TSNodeCACacheRelation(maxSize);
            tsmap = new MaxSizeCAHashMap<ByteArrayWrapper, TSNode>((int) initialSize, loadFActor, myTSKeyValueRelation);
            map = camap;
        } else
        if (testDS== CompareTries.DataStructure.MaxSizeHashMap) {
            map =  new MaxSizeHashMap<>(maxSize, true);
        } else
        if (testDS== CompareTries.DataStructure.ByteArrayHashMap)  {
            MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
            int avgElementSize =88;
            long beHeapCapacity =(long) vmax*avgElementSize*11/10;
            map =  new ByteArrayHashMap(initialSize,loadFActor,myKR,(long) beHeapCapacity,null,0 );
        }
        else
        if (testDS== CompareTries.DataStructure.MaxSizeByteArrayHashMap) {
            MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
            int avgElementSize =88;
            long beHeapCapacity;
            boolean removeInBulk = true;
            if (removeInBulk)
                beHeapCapacity =(long) maxSize*avgElementSize*11/10;
            else
                beHeapCapacity =(long) maxSize*avgElementSize*14/10;

            ByteArrayHashMap bamap =  new ByteArrayHashMap(initialSize,loadFActor,myKR,(long) beHeapCapacity,null,maxSize );
            bamap.removeInBulk = removeInBulk;
            map = bamap;
        }
        if (tsmap!=null)
            testClass = tsmap.getClass().getName();
        else
            testClass = map.getClass().getName();


        computeAverageAccountSize();
        // Having a million accounts (20 bits)
        int avgAccountPathSize = (30*8-20)/8;
        int avgLeafNodeSize = stateTrieSim.accountSize+avgAccountPathSize+1+1;
        int nonEmbeddedNodeSize = (avgLeafNodeSize+1)*2+3+1;
        log("nonEmbeddedNodeSize: "+nonEmbeddedNodeSize);

        for (int i=0;i<vmax;i++) {
            byte[] v1 = new byte[nonEmbeddedNodeSize];
            v1[0]= (byte) (i & 0xff);
            v1[1] =(byte)((i>>8)& 0xff);
            v1[2] =(byte)((i>>16)& 0xff);
            v1[3] =(byte)((i>>24)& 0xff);

            if (tsmap!=null) {
                TSNode node = new TSNode(v1,i);
                tsmap.put(node);
            } else {
                if (camap != null)
                    camap.put(v1);
                else {
                    ByteArrayWrapper k1 = myKeyValueRelation.getKeyFromData(v1);
                    map.put(k1, v1);
                }
            }
            if (i % 100_0000==0) {
                dumpMemProgress(i,vmax);
            }
        }
        stop(true);
        dumpMemResults(vmax);
        System.out.println("Class: "+testClass);
        System.out.println("Test: "+testDS);
        long consumedMbs = (endMbs - startMbs);
        long consumedBytes = consumedMbs*1000*1000;
        // When using maxSize, the number of actual elements left may be lower than maxSize, because
        // we use an heuristic to remove elements in bulk.
        int finalSize = 0;

        if (tsmap!=null) {
            System.out.println("hashMapCount: " + tsmap.hashMapCount);
            System.out.println("tsmap.size : " + tsmap.size());
            finalSize = tsmap.size();
        } else
        if (camap!=null) {
            System.out.println("hashMapCount: " + camap.hashMapCount);
            System.out.println("camap.size : " + camap.size());
            finalSize = camap.size();
        } else {
            System.out.println("HashMap test");
            System.out.println("map.size : " + map.size());
            finalSize = map.size();
        }

        System.out.println("entry size : " + consumedBytes/finalSize);
        int overhead = (int) (consumedBytes/finalSize-nonEmbeddedNodeSize);
        System.out.println("entry overhead : " + overhead);
        System.out.println("entry overhead [%] : " + overhead*100/nonEmbeddedNodeSize+"%");



        System.out.println("Testing scanning speed (for GC)");
        start(false);
        int[] counter  = new int[1];
        if (tsmap!=null) {
            BiConsumer<ByteArrayWrapper, TSNode> count = (k, d) -> counter[0]++;
            tsmap.forEach(count);
        } else {
            BiConsumer<ByteArrayWrapper, byte[]> count = (k, d) -> counter[0]++;
            map.forEach(count);
        }
        stop(false);
        System.out.println("Counter: "+counter[0]);

        dumpSpeedResults(vmax);

    }

    public static void main (String args[]) {
        CompareHashmaps c = new CompareHashmaps();
        c.testHashmapReplacement();

        System.exit(0);
    }
}
