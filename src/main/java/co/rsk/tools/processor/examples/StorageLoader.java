package co.rsk.tools.processor.examples;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.cindex.IndexTrie;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import co.rsk.tools.processor.cindex.PackedTrieKeySlice;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import org.ethereum.db.ByteArrayWrapper;


import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

public class StorageLoader extends RskBlockProcessor {

    private void processReference(NodeReference reference, byte side,FastTrieKeySlice prePath) {
        if (!reference.isEmpty()) {
            Optional<Trie> node = reference.getNode();

            if (node.isPresent()) {

                //if (!reference.isEmbeddable()) {
                    FastTrieKeySlice path = null;
                    if (trackPath) { path = prePath.appendBit(side); }
                    Trie childTrie = node.get();
                    processTrie(childTrie, reference.isEmbeddable(),path);
                // }
            }
        }
    }

    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;

    long prevTime;
    long prevNodes;
    long virtualNodes;
    long started;

    public static class TrieItem  {
        public byte[] hash;
        public byte[] value;

        public TrieItem(byte[] hash, byte[] value) {
            this.hash = hash;
            this.value = value;
        }

        public TrieItem(InputStream in) throws IOException {
            readObject(in);
        }

        private void readObject(InputStream stream)
                throws IOException {

            this.hash =ObjectIO.readNBytes(stream,32);
            int valueLength = ObjectIO.readInt(stream);
            if ((valueLength<0) || (valueLength>10_000_000)) {
                //
                System.out.println(valueLength);
                throw  new RuntimeException("invalid stream");
            }

            this.value = stream.readNBytes(valueLength);

        }
        private void writeObject(OutputStream out)
                throws IOException {
            if (hash.length!=32) {
                throw new RuntimeException("Invalid hash");
            }
            out.write(hash);
            int length = value.length;
            if (length<0)
                throw new RuntimeException("Invalid length");
            ObjectIO.writeInt(out,length);
            out.write(value);
        }
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    //TreeMap<Keccak256,byte[]> map = new TreeMap<>();
    static byte[] akey =hexStringToByteArray("391df4bc67ae9a4c235ee373a183027f60451e19b7ac680a7b5e0d28447194f0");
    static byte[] aPath =hexStringToByteArray("00a1c67f69a80032a500252acc95758f8b5f583470ba265eb685a8f45fc9d580");

    static boolean trackPath = false;
    HashMap<Keccak256,Integer> map ;
    int duplicatedSpace = 0;

    private void processTrie(Trie trie, boolean isEmbedded, FastTrieKeySlice prePath) {
        virtualNodes++;
        if (virtualNodes % 1000 == 0) {
            System.out.println("Nodes processed: " + virtualNodes);
            long currentTime = System.currentTimeMillis();
            long nodesXsec = (virtualNodes) * 1000 / (currentTime - started);
            long nodesXsecNow = (virtualNodes - prevNodes) * 1000 / (currentTime - prevTime);
            System.out.println(" Nodes/sec total: " + nodesXsec);
            System.out.println(" Nodes/sec total: " + nodesXsec + " nodes/sec now: " + nodesXsecNow);
            prevNodes = virtualNodes;
            prevTime = currentTime;
        }
        FastTrieKeySlice path = null;
        if (trackPath) {
            path = prePath.append(trie.getSharedPath());
            if ((path.length() == aPath.length * 8) && (Arrays.compare(path.encode(), aPath) == 0)) {
                System.out.println("Key Found: " + trie.getValueHash().toHexString());
            }
        }

        //map.put(trie.getHash(),trie.toMessage());
        try {
            Keccak256 hash =trie.getHash();

            Integer prevMsgLen =map.get(hash);
            if (prevMsgLen!=null) {
                duplicatedSpace +=prevMsgLen;
                if (trie.hasLongValue()) {
                    Keccak256 hash2 = trie.getValueHash();
                    duplicatedSpace +=map.get(hash2);
                }
                return;
            }

            TrieItem ti = new TrieItem(trie.getHash().getBytes(), trie.toMessage());
            ti.writeObject(out);
            map.put(hash,ti.value.length);

            if (isEmbedded) {
            }

            if (trie.hasLongValue()) {
                //map.put(trie.getValueHash(),trie.getValue());
                byte[] key = trie.getValueHash().getBytes();
                if (trackPath) {
                    if (Arrays.compare(key, akey) == 0) {
                        System.out.println("Found: " + trie.getValueHash().toHexString());
                    }
                }
                Keccak256 hash2 = trie.getValueHash();
                Integer prevLongMsgLen =map.get(hash2);
                if (prevLongMsgLen!=null) {
                    duplicatedSpace +=prevLongMsgLen;
                } else {
                    TrieItem ti2 = new TrieItem(hash2.getBytes(), trie.getValue());
                    ti2.writeObject(out);
                    map.put(hash2,ti.value.length);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (isEmbedded)
            return;
        NodeReference leftReference = trie.getLeft();
        NodeReference rightReference = trie.getRight();
        processReference(leftReference, LEFT_CHILD_IMPLICIT_KEY,path);
        processReference(rightReference, RIGHT_CHILD_IMPLICIT_KEY,path);
    }

    @Override
    public boolean processBlock() {
        try {
            createFile();
            prevNodes = 0;
            prevTime = 0;
            started = System.currentTimeMillis();
            Trie aTrie = getTrieAtCurrentBlock();
            //Trie aTrinode = this.trie.find(aPath);
            //System.out.println(aTrinode.getValueLength());
            map = new HashMap<>();
            duplicatedSpace = 0;
            processTrie(getTrieAtCurrentBlock(), false,FastTrieKeySlice.emptyWithCapacity());
            long ended = System.currentTimeMillis();
            System.out.println("Elapsed time [s]: " + (ended - started) / 1000);
            System.out.println("DuplicatedSpace [Kb]: " + (duplicatedSpace / 1000));
            closeFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

       OutputStream out;

    public void closeFile() throws IOException {
        out.flush();
        //closing the stream
        out.close();
        System.out.println("File "+fileName+" written.");
    }
    static final int bahamas = 3397;
    static final int afterBridgeSync = 370000;
    static final int orchid = 729000; // out of consensus at block: 729000
    static final int orchid060 = 1052700; // out of consensus at block: 1051701
    static final int wasabi100 = 1591000; // out of consensus at block: 1590001
    static final int twoToThree = 2018000;
    static final int papyrus200 = 2392700;

    String fileName ="unset";

    final static  int maxBlockchainBlock = 3_210_000; // 3219985

    // Testnet "difficult" blocks: 1672391 to 167313
    static final int testnetDifficultBlock =  1672391;
    final static int ioblockNumber = testnetDifficultBlock-1;

    public void createFile() throws IOException {

        out = new BufferedOutputStream(new FileOutputStream(fileName));

        //out.writeObject(s1);

    }

    public void readTrie() {

        // Now load it all again and put it on a hashmap.
        // We'll see how much time it takes.
        long started = System.currentTimeMillis();

        InputStream in;

        try {

            //in = new BufferedInputStream(new FileInputStream(fileName));
            in = new FileInputStream(fileName);
            System.out.println("Used Before MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);
            HashMap<Keccak256,byte[]> map = new HashMap<>();
            //AbstractMap<Keccak256,byte[]> map = new TreeMap<>();
            IndexTrie indexTrie = new IndexTrie();
            int count =0;
            //while (count<100000) {//in.available()>0) {
            int duplcatedSpace =0;
            try {
                for (; ; ) {
                    //TrieItem ti = (TrieItem) in.readObject();
                    TrieItem ti = new TrieItem(in);
                    Keccak256 key = new Keccak256(ti.hash);
                    if (!map.containsKey(key)) {
                        //System.out.println("Count: "+count+" "+count*100/1200000+"%");
                        map.put(new Keccak256(ti.hash), ti.value);
                        count++;
                        if (count % 100000 == 0) {
                            System.out.println("Count: " + count + " " + count * 100 / 1200000 + "%");
                        }
                    } else
                        duplcatedSpace +=ti.value.length;
                }
            }
            catch (EOFException exc)
                {
                    // end of stream
                }
            long currentTime = System.currentTimeMillis();
            System.out.println("Time[s]: "+(currentTime-started)/1000);
            gc();
            System.out.println("Count: "+count);
            System.out.println("map.size: "+map.size());
            System.out.println("Used After MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024);
            System.out.println("DuplicatedSpace[Kb]: "+duplcatedSpace/1000);
            //
            in.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //out.writeObject(s1);


    }


    final static int mHashmap =0;
    final static int mTreemap =1;
    final static int mIndexTrie =2;

    public void readTrieIndex(int method,boolean full) {
        // Now load it all again and put it on a an IndexTrie
        // We'll see how much time it takes.
        long started = System.currentTimeMillis();

        InputStream in;

        try {

            FileInputStream fin = new FileInputStream(fileName);
            in = fin;

            System.out.println("Used Before Mb: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 /1024);
            // Avoid th gc to remove it
            IndexTrie indexTrie =null;
            AbstractMap<Keccak256,Integer> map=null;
            AbstractMap<Keccak256, ByteArrayWrapper> fullMap=null;

            if (full) {
                if (method==mTreemap)
                    fullMap = new TreeMap<>();
                else
                    fullMap = new HashMap<>();
            } else
            if (method==mIndexTrie)
                indexTrie = new IndexTrie();
            else
            if (method==mTreemap)
                map = new TreeMap<>();
            else
                map = new HashMap<>();

            int count =0;

            try {
                for (; ; ) {
                    TrieItem ti = new TrieItem(in);
                    long pos = ((FileInputStream) in).getChannel().position();
                    if (full) {
                        fullMap.put(new Keccak256(ti.hash), new ByteArrayWrapper(ti.value));
                    } else {
                        if (method == mIndexTrie)
                            indexTrie = indexTrie.put(ti.hash, (int) pos);
                        else
                            map.put(new Keccak256(ti.hash), (int) pos);
                    }
                    count++;
                    if (count%100000==0) {
                        System.out.println("Count: "+count+" "+count*100/1200000+"%");
                        //System.out.println("triesize: "+indexTrie.trieSize());
                    }
                }
            }
            catch (EOFException exc)
            {
                // end of stream
            }
            long currentTime = System.currentTimeMillis();
            System.out.println("Time[s]: "+(currentTime-started)/1000);
            in.close();
            gc();

            if (method==mIndexTrie) {
                System.out.println("noPathCount: " + indexTrie.noPathCount());
                System.out.println("NPCount: " + indexTrie.NPCount());
                System.out.println("nodeCount: " + indexTrie.nodeCount());
                System.out.println("dataCount: " + indexTrie.dataCount());
                System.out.println("triesize: "+indexTrie.nodeCount());
            }
            System.out.println("Count: "+count);
            //System.out.println("map.size: "+map.size());
            System.out.println("Used After MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024  /1024);

            //


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //out.writeObject(s1);


    }

    /**
     * This method guarantees that garbage collection is
     * done unlike <code>{@link System#gc()}</code>
     */
    public static void gc() {
        Object obj = new Object();
        WeakReference ref = new WeakReference<Object>(obj);
        obj = null;
        while(ref.get() != null) {
            System.gc();
        }
    }
    public static void main (String args[]) {
        /* Treemap: (storing int offsets only)

        ///////////////////////////////////////////
        Hashmap: (storing int offsets only)
        Used Before Mb: 1.281982421875
        Time[s]: 15
        Count: 1155887
        Used After MB: 133.16172790527344
        ///////////////////////////////////////
        IndexTrie: (storing int offsets only)
        Used Before Mb: 1.2819747924804688
        Time[s]: 46
        noPathCount: 856602
        NPCount: 276892
        nodeCount: 2266989
        dataCount: 1133495
        triesize: 2266989
        Count: 1155887
        Used After MB: 118.6827621459961
        ////////////////////////////////////
        Hashmap: FULL DATA contents
        Used Before Mb: 1.281982421875
        Time[s]: 15
        Count: 1155887
        Used After MB: 238.90049743652344
        */
        //noWithoutPath();
        //readTrieIndex_hashmap(false);
        ///readTrieIndex(mHashmap,true);
        //readTrieIndex(mIndexTrie);
        //readTrie();
        StorageLoader sl = new StorageLoader();
        sl.DumpTrie(2_999_999,"3M",args);
        System.exit(0);

        int defaultRange = 100;


        //executeCenter(bahamas,"bahamas",defaultRange, args);
        //executeCenter(afterBridgeSync,"afterBridgeSync",defaultRange, args);
        //executeCenter(orchid,"orchid",defaultRange, args);
        //executeCenter(orchid060,"orchid060",defaultRange , args);
        //executeCenter(wasabi100,"wasabi100",defaultRange , args);
        //executeCenter(twoToThree,"twoToThree",defaultRange , args);
        executeCenter(papyrus200,"papyrus200",defaultRange , args);

    }

    static public void DumpTrie(int bn,String dumpName,String args[]) {
        System.out.println("dumping :"+dumpName);
        StorageLoader storageLoader = new StorageLoader();
        storageLoader.writeTrie(args,bn);
    }

    static public void executeCenter(int bn,String dumpName,int range,String args[]) {

        System.out.println("dumping :"+dumpName);
        StorageLoader storageLoader = new StorageLoader();
        storageLoader.writeTrie(args,bn-range-1);
    }

    static public void noWithoutPath() {
        byte[] key1 = new byte[] { 0x00};
        byte[] key2 = new byte[] { (byte) 0x80};
        byte[] key3 = new byte[] { 0x01};
        byte[] key4 = new byte[] { 0x09}; // Split in the middle, ending with 1.

        int value1 =101;
        int value2 =102;
        int value3 =103;
        int value4 =104;
        IndexTrie trie;

        System.out.println("----");
        trie = new IndexTrie().put(key1, value1).put(key2,value2);
        dump3(trie);
        System.out.println("key1: "+trie.get(key1));
        System.out.println("key2: "+trie.get(key2));

        assertTrue(trie.get(key1)==value1);
        assertTrue(trie.get(key2)==value2);
        assertTrue(PackedTrieKeySlice.length(trie.getSharedPath())==0);

        System.out.println("----");
        trie = new IndexTrie().put(key1, value1).put(key3,value3);
        dump3(trie);
        System.out.println("key1: "+trie.get(key1));
        System.out.println("key3: "+trie.get(key3));

        assertTrue(trie.get(key1)==value1);
        assertTrue(trie.get(key3)==value3);
        assertTrue(PackedTrieKeySlice.length(trie.getSharedPath())==7);

        System.out.println("----");
        trie = new IndexTrie().put(key1, value1).put(key4,value4);
        dump3(trie);
        System.out.println("key1: "+trie.get(key1));
        System.out.println("key4: "+trie.get(key4));
        assertTrue(trie.get(key1)==value1);
        assertTrue(trie.get(key4)==value4);
        assertTrue(PackedTrieKeySlice.length(trie.getSharedPath())==4);

        System.out.println("----");

        trie = new IndexTrie().put(key1, value1).
                put(key2,value2).
                put(key3, value3).
                put(key4,value4);

        System.out.println("key1: "+trie.get(key1));
        System.out.println("key2: "+trie.get(key2));
        System.out.println("key3: "+trie.get(key3));
        System.out.println("key4: "+trie.get(key4));

        assertTrue(trie.get(key1)==value1);
        assertTrue(trie.get(key2)==value2);
        assertTrue(trie.get(key3)==value3);
        assertTrue(trie.get(key4)==value4);

    }

    static void assertTrue(boolean c) {
        if (!c) {
            throw new RuntimeException("Error");
        }
    }

    static public void dump3(IndexTrie trie) {

        System.out.println(trie.nodeCount());
        System.out.println("Root: "+trie.getValue());
        System.out.println("Left: "+trie.getLeft().getValue());
        System.out.println("Right: "+trie.getRight().getValue());

        //System.out.println( trie.getSharedPath().length());
        System.out.println("Root PackedPathLength: "+ trie.getSharedPath().length);
        System.out.println("Root Path length: "+ PackedTrieKeySlice.length(trie.getSharedPath()));
    }

    public void writeTrie(String args[],int aSingleBlockNumber) {
        int minBlock = aSingleBlockNumber;
        int maxBlock = aSingleBlockNumber+1;

        int step = 800_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        fileName = provider.getNetworkName()+"-" + aSingleBlockNumber + ".bin";
        System.out.println("Output filename: "+fileName);
        provider.processBlockchain(this);
        //provider.getContext().getBlockStore().flush();

    }

    public void writeTrie(String args[]) {
        int minBlock = ioblockNumber;///1_600_001; //2_400_001;
        int maxBlock = minBlock+1; // maxBlockchainBlock;
        int step = 800_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        fileName = provider.getNetworkName()+"-" + ioblockNumber + ".bin";
        StorageLoader loader = new StorageLoader();
        provider.processBlockchain(loader);
    }
}
