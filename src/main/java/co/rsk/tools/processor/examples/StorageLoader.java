package co.rsk.tools.processor.examples;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

public class StorageLoader extends RskBlockProcessor {

    private void processReference(NodeReference reference, byte side) {
        if (!reference.isEmpty()) {
            Optional<Trie> node = reference.getNode();

            if (node.isPresent()) {

                if (!reference.isEmbeddable()) {
                    Trie childTrie = node.get();
                    processTrie(childTrie, reference.isEmbeddable());
                }
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
    //TreeMap<Keccak256,byte[]> map = new TreeMap<>();

    private void processTrie(Trie trie, boolean isEmbedded) {

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
        //map.put(trie.getHash(),trie.toMessage());
        try {
            TrieItem ti = new TrieItem(trie.getHash().getBytes(), trie.toMessage());
            ti.writeObject(out);

            if (isEmbedded) {
            }

            if (trie.hasLongValue()) {
                //map.put(trie.getValueHash(),trie.getValue());
                TrieItem ti2 = new TrieItem(trie.getValueHash().getBytes(), trie.getValue());
                ti2.writeObject(out);

            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        NodeReference leftReference = trie.getLeft();
        NodeReference rightReference = trie.getRight();
        processReference(leftReference, LEFT_CHILD_IMPLICIT_KEY);
        processReference(rightReference, RIGHT_CHILD_IMPLICIT_KEY);
    }

    @Override
    public boolean processBlock() {
        try {
            createFile();
            prevNodes = 0;
            prevTime = 0;
            started = System.currentTimeMillis();
            processTrie(getTrieAtCurrentBlock(), false);
            long ended = System.currentTimeMillis();
            System.out.println("Elapsed time [s]: " + (ended - started) / 1000);
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
    }

    static final String fileName;

    final static  int maxBlockchainBlock = 3_210_000; // 3219985
    final static int ioblockNumber =maxBlockchainBlock;

    static {
        fileName = "f" + ioblockNumber + ".txt";
    }

    public void createFile() throws IOException {

        out = new BufferedOutputStream(new FileOutputStream(fileName));

        //out.writeObject(s1);

    }

    public static void readTrie() {

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
            try {
                for (; ; ) {
                    //TrieItem ti = (TrieItem) in.readObject();
                    TrieItem ti = new TrieItem(in);
                    //System.out.println("Count: "+count+" "+count*100/1200000+"%");
                    map.put(new Keccak256(ti.hash), ti.value);
                    count++;
                    if (count%100000==0) {
                        System.out.println("Count: "+count+" "+count*100/1200000+"%");
                    }
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

            //
            in.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //out.writeObject(s1);


    }

    // Avoid th gc to remove it
    public static IndexTrie indexTrie;

    public static void readTrieIndex() {

        // Now load it all again and put it on a an IndexTrie
        // We'll see how much time it takes.
        long started = System.currentTimeMillis();

        InputStream in;

        try {

            FileInputStream fin = new FileInputStream(fileName);
            in = fin;

            System.out.println("Used Before Mb: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 /1024);

            indexTrie = new IndexTrie();
            //AbstractMap<Keccak256,Integer> map = new TreeMap<>();
            int count =0;
            //while (count<100000) {//in.available()>0) {
            try {
                for (; ; ) {
                    TrieItem ti = new TrieItem(in);
                    long pos = ((FileInputStream) in).getChannel().position();
                    indexTrie = indexTrie.put(ti.hash, (int) pos);
                    //map.put(new Keccak256(ti.hash),(int) pos);
                    count++;
                    if (count%100000==0) {
                        System.out.println("Count: "+count+" "+count*100/1200000+"%");
                        System.out.println("triesize: "+indexTrie.trieSize());
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
            System.out.println("emptytriesize: "+indexTrie.emptyTrieSize());
            System.out.println("emptypath: "+indexTrie.emptyPathTrieSize());
            System.out.println("Count: "+count);
            //System.out.println("map.size: "+map.size());
            System.out.println("Used After MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024  /1024);
            System.out.println("triesize: "+indexTrie.trieSize());
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
        //noWithoutPath();
        readTrieIndex();
        //readTrie();
        //writeTrie(args);
    }

    static public void noWithoutPath() {
        byte[] key1 = new byte[] { 0x00};
        byte[] key2 = new byte[] { (byte) 0x80};
        int value1 =100;
        int value2 =101;
        IndexTrie trie = new IndexTrie().put(key1, value1).put(key2,value2);

        System.out.println(trie.getValue());
        System.out.println( trie.getSharedPath().length());

    }
    public static void writeTrie(String args[]) {

        int minBlock = ioblockNumber;///1_600_001; //2_400_001;
        int maxBlock = minBlock+1; // maxBlockchainBlock;
        int step = 800_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        StorageLoader loader = new StorageLoader();
        provider.processBlockchain(loader);

    }
}
