package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.ByteArray39HashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArray63HashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.*;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import java.io.IOException;
import java.util.List;

public class UnitrieUnitTests {

    public byte[] getByteArrayFromInt(int i) {
        byte[] v1 = new byte[4];
        v1[0] = (byte) (i & 0xff);
        v1[1] = (byte) ((i >> 8) & 0xff);
        v1[2] = (byte) ((i >> 16) & 0xff);
        v1[3] = (byte) ((i >> 24) & 0xff);
        return v1;
    }

    public void unittest_ByteArrayHashMap() {
        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();
        // Size : 4 from byte[], 1 for data size, 2 for metadata, 2 for debug header
        int avgElementSize =4+1+2+2;
        long beHeapCapacity;
        boolean removeInBulk = true;
        int initialSize=100;
        int maxSize = 20;
        float loadFActor = 0.3f;
        beHeapCapacity =(long) maxSize*avgElementSize*14/10;

        PrioritizedByteArrayHashMap bamap =  new PrioritizedByteArrayHashMap(initialSize,loadFActor,myKR,(long) beHeapCapacity,null,maxSize );
        bamap.removeInBulk = removeInBulk;
        bamap.MaxPriority = 30;
        boolean dumpTables = false;

        int vmax = 30;
        // When the 30th element is inserted, all priorities will be
        // reduced by 10.
        for (int i=0;i<vmax;i++) {
            byte[] v1 = getByteArrayFromInt(i);

            System.out.println("adding "+i+" value "+ ByteUtil.toHexString(v1));

            boolean dump =dumpTables && ((i==18) || (i==19) || (i>=29));
            if (dump) {
                dumpElements(bamap);
                System.out.println("-------------------------");
            }
            bamap.put(v1);
            if (i==18) {
                // Check that there are 19 elements, and none have been removed
                checkEqual(bamap.size(),19);
            }
            if (i==20) {
                // After the 21st element has been inserted, there should be no
                // element with priority zero. Also there should be no more than
                // 20 elements (there can be less)
                if (bamap.size()>=21)
                    throw new RuntimeException("Should have removed at leat one");

                checkNoPriorityBelow(bamap,1);
            }
            if (i==29) {
                // Now priorities have been reassigned. There should be no priority
                // 20 or higher.
                checkNoPriorityAbove(bamap,29);
            }
            if (dump) {
                dumpElements(bamap);
                System.out.println("-------------------------");
            }
        }

    }
    public void checkNoPriorityAbove(ByteArrayRefHashMap bamap, int maxPriority) {
        List<ByteArrayRefHashMap.TableItem> etab = bamap.exportTable();
        for(ByteArrayRefHashMap.TableItem ti: etab) {
            if (ti.priority>maxPriority)
                throw new RuntimeException("wrong priority");

        }
    }
    public void checkNoPriorityBelow(ByteArrayRefHashMap bamap, int minPriority) {
        List<ByteArrayRefHashMap.TableItem> etab = bamap.exportTable();
        for(ByteArrayRefHashMap.TableItem ti: etab) {
            if (ti.priority<minPriority)
                throw new RuntimeException("wrong priority");

        }
    }

    public void dumpElements(ByteArrayRefHashMap bamap) {
        bamap.dumpTable();
        /*
        bamap.forEach((k, v) -> {
            int priority = bamap.getPriority(k);
            System.out.println("" + ByteUtil.toHexString(k.getData() ,0,4)+
                    "=" + ByteUtil.toHexString(v,0,4) + " (" + priority + ")");
        });

         */
    }


    public void testCACache() {
        TrieCACacheRelation myKeyValueRelation = new TrieCACacheRelation();
        CAHashMap<ByteArrayWrapper, byte[]> map =
                new CAHashMap<ByteArrayWrapper, byte[]>(10,0.3f,myKeyValueRelation );
        int max = 10000;
        ByteArrayWrapper[] k = new ByteArrayWrapper[max];
        byte[][] v = new byte[max][];
        for (int i=0;i<max;i++) {
            byte[] v1 = new byte[]{ (byte) (i & 0xff), (byte)((i>>8)& 0xff), (byte)((i>>16)& 0xff)};
            ByteArrayWrapper k1 = myKeyValueRelation.getKeyFromData(v1);
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

    public void checkEqual(int a, int b) {
        if (a!=b)
            throw new RuntimeException("mismatch");
    }

    public void checkEqual(boolean a, boolean b) {
        if (a!=b)
            throw new RuntimeException("mismatch");
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


    private static boolean IS_64_BIT_JVM;

    public static void printOverhead() {
        String arch = System.getProperty("sun.arch.data.model");
        IS_64_BIT_JVM = (arch == null) || arch.contains("32");

    }

    public void testByteArrayRefHashMap() {
        MyBAKeyValueRelation myBAKeyValueRelation  = new MyBAKeyValueRelation();
        // First, create  a map without maximums
        ByteArrayRefHashMap ba = new ByteArrayRefHashMap(100,0.3f,myBAKeyValueRelation,
                100*100,null,0);
        int max = 10;
        ByteArrayWrapper[] k = new ByteArrayWrapper[max];
        byte[][] v = new byte[max][];
        for(int i=0;i<max;i++) {
            v[i] = getByteArrayFromInt(i);
            k[i] = myBAKeyValueRelation.getKeyFromData(v[i]);
        }

        ba.put(v[1]);
        checkEqual(ba.containsKey(k[1]),true);
        checkEqual(ba.get(k[1]),v[1]);

        ba.put(k[2],null); // put a key with null
        checkEqual(ba.containsKey(k[2]),true);
        checkEqual(ba.get(k[1]),null);

        // Add a second null, it should do nothing
        ba.put(k[2],null); // put a key with null
        checkEqual(ba.containsKey(k[2]),true);
        checkEqual(ba.get(k[1]),null);


        // Now store a real value, removing the previous null
        ba.put(k[2],v[2]);
        checkEqual(ba.containsKey(k[2]),true);
        checkEqual(ba.get(k[2]),v[2]);

        // Now remove it completely
        ba.remove(k[2]);
        checkEqual(ba.containsKey(k[2]),false);
        checkEqual(ba.get(k[2]),null);


    }

    class KeyValues {
        public MyBAKeyValueRelation myBAKeyValueRelation  = new MyBAKeyValueRelation();
        public int max = 10;
        public ByteArrayWrapper[] k = new ByteArrayWrapper[max];
        public byte[][] v = new byte[max][];

        void create() {
            for(int i=0;i<max;i++) {
                v[i] = getByteArrayFromInt(i);
                k[i] = myBAKeyValueRelation.getKeyFromData(v[i]);
            }
        }
    }

    public void testByteArrayHashMap() {
        KeyValues kvs = new KeyValues();
        kvs.create();

        // First, create  a map without maximums
        AbstractByteArrayHashMap ba1 = new ByteArray39HashMap(100, 0.3f, kvs.myBAKeyValueRelation,
                100 * 100, null, 0);
        testByteArrayHashMap(kvs,ba1,"test1");

        AbstractByteArrayHashMap ba2 = new ByteArray63HashMap(100, 0.3f, kvs.myBAKeyValueRelation,
                100 * 100, null, 0);
        testByteArrayHashMap(kvs,ba2,"test2");

    }
    public void testByteArrayHashMap(
                KeyValues kvs,AbstractByteArrayHashMap ba,String testName ) {


        putKvs(kvs, ba);
        checkKvs(kvs, ba);
        try {
            ba.saveToFile(testName);
            ba.readFromFile(testName, true); // now read, mapped
        } catch (IOException e) {
            e.printStackTrace();
        }
        checkKvs(kvs, ba);

    }

    void  putKvs(KeyValues kvs , AbstractByteArrayHashMap ba) {
        ba.put(kvs.v[1]);
        ba.put(kvs.v[2]);
    }
    void checkKvs(KeyValues kvs , AbstractByteArrayHashMap ba) {
        checkEqual(ba.containsKey(kvs.k[1]),true);
        checkEqual(ba.get(kvs.k[1]),kvs.v[1]);

        checkEqual(ba.containsKey(kvs.k[2]),true);
        checkEqual(ba.get(kvs.k[2]),kvs.v[2]);
    }

    void putAndCheckKvs(KeyValues kvs , AbstractByteArrayHashMap ba) {
        ba.put(kvs.v[1]);
        checkEqual(ba.containsKey(kvs.k[1]),true);
        checkEqual(ba.get(kvs.k[1]),kvs.v[1]);

        ba.put(kvs.k[2],null); // put a key with null
        checkEqual(ba.containsKey(kvs.k[2]),true);
        checkEqual(ba.get(kvs.k[1]),null);

        // Add a second null, it should do nothing
        ba.put(kvs.k[2],null); // put a key with null
        checkEqual(ba.containsKey(kvs.k[2]),true);
        checkEqual(ba.get(kvs.k[1]),null);


        // Now store a real value, removing the previous null
        ba.put(kvs.k[2],kvs.v[2]);
        checkEqual(ba.containsKey(kvs.k[2]),true);
        checkEqual(ba.get(kvs.k[2]),kvs.v[2]);

        // Now remove it completely
        ba.remove(kvs.k[2]);
        checkEqual(ba.containsKey(kvs.k[2]),false);
        checkEqual(ba.get(kvs.k[2]),null);




    }
    public static void main (String args[]) {
        UnitrieUnitTests u = new UnitrieUnitTests();
        u.testByteArrayHashMap();
        System.exit(0);
        u.printOverhead();
        u.testCACache();
        u.unittest_ByteArrayHashMap();
    }
}
