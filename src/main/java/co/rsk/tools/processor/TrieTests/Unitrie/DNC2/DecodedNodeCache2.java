package co.rsk.tools.processor.TrieTests.Unitrie.DNC2;

import co.rsk.tools.processor.TrieTests.Unitrie.Trie;

public class DecodedNodeCache2 {

    TrieWithDNC[] cache;

    static DecodedNodeCache2 singleton = new DecodedNodeCache2();

    static public DecodedNodeCache2 get() {
        return singleton;
    }

    final int maxCache = 500_000;

    long base ;
    int count;

    public DecodedNodeCache2() {
        cache = new TrieWithDNC[maxCache];
        base =0;
        count =0;
    }
    // for faster access, null means "not present"
    Trie retrieve(long decodedRef) {
        if (decodedRef<base)
            return null; // Object no longer with us
        Trie e = cache[(int) (decodedRef-base) % cache.length];

        if (count>=maxCache) {
            // we're full
            // remove element at cache[base % length]
            cache[(int) (base % cache.length)]=null;
            base++;
            count--;
        }
        int pos =(int) ((base+count) % cache.length);


    return null;
    }
}
