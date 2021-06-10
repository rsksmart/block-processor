package co.rsk.tools.processor.examples.storage;

import co.rsk.tools.processor.cindex.IndexTrie;
import co.rsk.tools.processor.cindex.PackedTrieKeySlice;

public class IndexTrieTest {
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
}
