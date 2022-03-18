package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.LinkedByteArrayRefHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.SimpleByteArrayRefHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.*;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompareHashmaps extends Benchmark {

    DataStructure testDataStructure =
            DataStructure.MaxSizeLinkedByteArrayHashMap;
            //DataStructure.MaxSizeHashMap;

    enum DataStructure {
        CAHashMap,
        HashMap,
        LinkedHashMap,
        LinkedCAHashMap,
        NumberedCAHashMap,
        MaxSizeHashMap,
        MaxSizeCAHashMap,
        ByteArrayHashMap,
        PrioritizedByteArrayHashMap,
        MaxSizeMetadataLinkedByteArrayHashMap,
        MaxSizeLinkedByteArrayHashMap
    }

    StateTrieSimulator stateTrieSim = new StateTrieSimulator();

    boolean limitSize = false;

    public void createLogFile(String basename,String expectedItems) {
        String name = "HashmapsResults/"+basename;
        name = name + "-"+testDataStructure.toString();
        name = name + "-"+expectedItems;

        name = name +"-MemMax_"+ getMillions( Runtime.getRuntime().maxMemory());


        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        String strDate = dateFormat.format(date);
        name = name + "-"+ strDate;

        plainCreateLogFilename(name);
    }

    public void computeAverageAccountSize() {
        stateTrieSim.setSimMode(StateTrieSimulator.SimMode.simEOAs);
        stateTrieSim.computeAverageAccountSize();
        log("Average account size: "+stateTrieSim.accountSize);
    }

    int vmax = 9_000_000;
    int maxSize = vmax;
    int avgAccountPathSize;
    int avgLeafNodeSize;
    int nonEmbeddedNodeSize;

    CAHashMap<ByteArrayWrapper, byte[]> camap =null;
    AbstractMap<ByteArrayWrapper, byte[]> map=null;
    CAHashMap<ByteArrayWrapper, TSNode> tsmap=null;
    TrieCACacheRelation myKeyValueRelation;

    public void prepareHashmap() {
        myKeyValueRelation = new TrieCACacheRelation();
        // For linear-probing hashmaps, the load factor must be low (i.e. 0.3f)
        // For hashmaps with linked-list or tree buckets, it can be much higher (i.e. 0.75f=

        float loadFactorForLinearProbing = 0.3f;
        float loadFactorForMultiBuckets = 0.75f;


        // This is the hashmap that we're going to test. You can
        // find all hashmaps data structures that are being tested
        // in the DataStructure enum.
        DataStructure testDS;
        testDS= testDataStructure;

        float loadFactor = loadFactorForLinearProbing;

        String testClass ="";
        switch (testDS) {
            case ByteArrayHashMap:
            case PrioritizedByteArrayHashMap:
            case MaxSizeMetadataLinkedByteArrayHashMap:
            case MaxSizeLinkedByteArrayHashMap:
                loadFactor = loadFactorForLinearProbing;
                break;
            default:
                loadFactor = loadFactorForMultiBuckets;
        }

        log("loadFactor: "+loadFactor);

        if (limitSize) {
            switch (testDS) {
                case MaxSizeHashMap:
                case MaxSizeCAHashMap:
                case MaxSizeMetadataLinkedByteArrayHashMap:
                case PrioritizedByteArrayHashMap:
                case MaxSizeLinkedByteArrayHashMap:
                    maxSize = 1_000_000; // vmax;//+1; //8_000_000;
            }
            log("maxSize: " + maxSize);
        } else {
            // I need to add 1 more to the maxSize because MaxSizeLinkedByteArrayHashMap
            // will prune nodes 1 put() before  MaxSizeHashMap
            maxSize = vmax+1;
            log("no size limit");
        }
        int initialSize = (int) (maxSize/loadFactor);
        log("initialSize: "+initialSize);

        startMem(true);
        if (testDS== DataStructure.CAHashMap) {
            camap = new CAHashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFactor, myKeyValueRelation);
            map = camap;
        } else
        if (testDS== DataStructure.LinkedHashMap) {
            map =  new LinkedHashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFactor);
        } else
        if (testDS== DataStructure.HashMap) {
            map =  new HashMap<ByteArrayWrapper, byte[]>((int) initialSize, loadFactor);
        } else
        if (testDS== DataStructure.NumberedCAHashMap) {
            TSNodeCACacheRelation myTSKeyValueRelation = new TSNodeCACacheRelation(0);
            tsmap = new CAHashMap<ByteArrayWrapper, TSNode>((int) initialSize, loadFactor, myTSKeyValueRelation);
            map = camap;
        } else
        if (testDS== DataStructure.MaxSizeCAHashMap) {
            TSNodeCACacheRelation myTSKeyValueRelation = new TSNodeCACacheRelation(maxSize);
            tsmap = new MaxSizeCAHashMap<ByteArrayWrapper, TSNode>((int) initialSize, loadFactor, myTSKeyValueRelation);
            map = camap;
        } else
        if (testDS== DataStructure.MaxSizeHashMap) {
            map =  new MaxSizeHashMap<>(maxSize, true);
        } else
        if (testDS== DataStructure.ByteArrayHashMap)  {
            MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
            int avgElementSize =88;
            long beHeapCapacity =(long) vmax*avgElementSize*11/10;
            map =  new ByteArrayHashMap(initialSize,loadFactor,myKR,(long) beHeapCapacity,null,0 );
        }
        else
        if (testDS== DataStructure.MaxSizeMetadataLinkedByteArrayHashMap) {
            MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
            SimpleByteArrayRefHeap sharedBaHeap = new SimpleByteArrayRefHeap(maxSize,8);
            MaxSizeByteArrayHashMap pmap =  new MaxSizeByteArrayHashMap(initialSize,loadFactor,myKR,0,sharedBaHeap,
                    maxSize );
            map = pmap;
        } else
        if (testDS== DataStructure.MaxSizeLinkedByteArrayHashMap) {
            MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
            LinkedByteArrayRefHeap sharedBaHeap = new LinkedByteArrayRefHeap(maxSize,0);
            MaxSizeLinkedByteArrayHashMap pmap =  new MaxSizeLinkedByteArrayHashMap(initialSize,loadFactor,myKR,0,sharedBaHeap,
                    maxSize,true );
            map = pmap;
        } else
        if (testDS== DataStructure.PrioritizedByteArrayHashMap) {
            MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
            int avgElementSize =88;
            long beHeapCapacity;
            boolean removeInBulk = true;
            if (removeInBulk)
                beHeapCapacity =(long) maxSize*avgElementSize*11/10;
            else
                beHeapCapacity =(long) maxSize*avgElementSize*14/10;

            PrioritizedByteArrayHashMap pmap =  new PrioritizedByteArrayHashMap(initialSize,loadFactor,myKR,(long) beHeapCapacity,null,maxSize );
            pmap.removeInBulk = removeInBulk;
            map = pmap;
        }
        if (tsmap!=null)
            testClass = tsmap.getClass().getName();
        else
            testClass = map.getClass().getName();


        computeAverageAccountSize();
        // Having a million accounts (20 bits)
        avgAccountPathSize = (30*8-20)/8;
        avgLeafNodeSize = stateTrieSim.accountSize+avgAccountPathSize+1+1;
        nonEmbeddedNodeSize = (avgLeafNodeSize+1)*2+3+1;
        log("nonEmbeddedNodeSize: "+nonEmbeddedNodeSize);

        log("Class: "+testClass);
        log("Test: "+testDS);
    }

    public void readTest() {
        log("random read test...");
        int maxSize = vmax;
        showPartialMemConsumed = false;
        start(false);
        for (int i=0;i<maxSize;i++) {
            byte[] v1 = new byte[nonEmbeddedNodeSize];

            int x=TestUtils.getPseudoRandom().nextInt(vmax);
            v1[0] = (byte) (x & 0xff);
            v1[1] = (byte) ((x >> 8) & 0xff);
            v1[2] = (byte) ((x >> 16) & 0xff);
            v1[3] = (byte) ((x >> 24) & 0xff);
            ByteArrayWrapper k1 = myKeyValueRelation.getKeyFromData(v1);
            byte[] r;
            if (tsmap!=null) {
                TSNode rn;
                rn = tsmap.get(k1);
                r = rn.data;
            } else {
                if (camap != null)
                    r =camap.get(k1);
                else {
                    r =map.get(k1);
                }
            }

            if ((r==null) && (!limitSize)) {
                log("Element not found index: "+x);
                System.exit(1);
            }
            if (i % 100_0000==0) {
                dumpProgress(i,vmax);
            }
        }
        stop(false);
        dumpSpeedResults(maxSize);
    }

    public void writeTest() {
        start(false);

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
                if ((map!=null) && (map instanceof  ByteArrayHashMap)) {
                    int longest = ((ByteArrayHashMap) map).longestFilledRun();
                    log("Hashmap longest filled run: "+longest);
                }
            }
        }

        stop(true);
        dumpMemResults(vmax);

        long consumedMbs = (endMbs - startMbs);
        long consumedBytes = consumedMbs*1000*1000;
        // When using maxSize, the number of actual elements left may be lower than maxSize, because
        // we use an heuristic to remove elements in bulk.
        int finalSize = 0;

        if (tsmap!=null) {
            log("hashMapCount: " + tsmap.hashMapCount);
            log("tsmap.size : " + tsmap.size());
            finalSize = tsmap.size();
        } else
        if (camap!=null) {
            log("hashMapCount: " + camap.hashMapCount);
            log("camap.size : " + camap.size());
            finalSize = camap.size();
        } else {
            log("HashMap test");
            log("map.size : " + map.size());
            finalSize = map.size();
        }
        log("nonEmbeddedNodeSize: "+nonEmbeddedNodeSize);
        log("entry size : " + consumedBytes/finalSize);
        int overhead = (int) (consumedBytes/finalSize-nonEmbeddedNodeSize);
        log("entry overhead : " + overhead);
        log("entry overhead [%] : " + overhead*100/nonEmbeddedNodeSize+"%");
    }

    public void scanTest() {
        log("Testing scanning and retrieving (key,data) speed");
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
        log("Counter: "+counter[0]);

        dumpSpeedResults(maxSize);

        log("Testing scanning and retrieving (data) only..");
        if ((map!=null) && (map instanceof  ByteArrayHashMap)) {
            start(false);
            int[] counter2 = new int[1];

            Consumer<byte[]> count = (d) -> counter2[0]++;
            ((ByteArrayHashMap) map).forEach(count);

            stop(false);
            log("Counter2: " + counter2[0]);
            dumpSpeedResults(maxSize);
        }

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
        String testName;

        testName ="HashmapTest";
        createLogFile(testName, getMillions( vmax));
        prepareHashmap();

        writeTest();

        readTest();

        scanTest();

        closeLog();
    }

    public static void main (String args[]) {
        CompareHashmaps c = new CompareHashmaps();
        c.testHashmapReplacement();

        System.exit(0);
    }

}
