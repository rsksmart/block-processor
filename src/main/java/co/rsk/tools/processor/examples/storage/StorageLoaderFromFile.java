package co.rsk.tools.processor.examples.storage;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.cindex.IndexTrie;
import org.ethereum.db.ByteArrayWrapper;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.TreeMap;

public class StorageLoaderFromFile {
    String fileName ="unset";
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
                    StorageDumperToFile.TrieItem ti = new StorageDumperToFile.TrieItem(in);
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

    //
    // This are the 3 methods copared to load and store the trie
    //
    final static int mHashmap =0; // Stores the trie in a hashmap
    final static int mTreemap =1; // Stores the trie in a Treemap
    final static int mIndexTrie =2; // Stores the trie in a Trie
    // Full:
    //      true: store also the values in the data structure
    //      false: only store the keys, not the values.

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
                    StorageDumperToFile.TrieItem ti = new StorageDumperToFile.TrieItem(in);
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
    }

    public static void main (String args[]) {
        StorageLoaderFromFile slff = new StorageLoaderFromFile();
        slff.readTrie();
        //slff.readTrieIndex_hashmap(false);
        ///slff.readTrieIndex(mHashmap,true);
        //slff.readTrieIndex(mIndexTrie);
        //readTrie();

    }
    // Results of several runs:
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


    ///////////////////////////////////
    */


}
