package co.rsk.tools.processor.TrieTests.Unitrie.DNC;

import co.rsk.tools.crypto.cryptohash.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.DNC2.TrieWithDNC;
import co.rsk.tools.processor.TrieTests.Unitrie.Trie;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Map;

public class DecodedNodeCache {
    static DecodedNodeCache singleton = new DecodedNodeCache();

    static public DecodedNodeCache get() {
        return singleton;
    }


    public static final int maxCacheNodes = 500_000;

    TrieWithChain head;
    TrieWithChain tail;
    int cachedNodes;

    public int getCachedNodes() {
        return cachedNodes;
    }

    public DecodedNodeCache() {
        head = null;
        tail = null;
        cachedNodes = 0;
        rootCache = new MaxSizeHashMap<>(rootCacheSize, true);
    }

    Map<ByteArrayWrapper, Trie> rootCache;
    int rootCacheSize = 100;

    public Trie retrieveRoot(byte[] hash) {
        ByteArrayWrapper bam = new ByteArrayWrapper(hash);
        Trie r = rootCache.get(bam);
        return r;

    }

    public void storeRoot(Trie trie) {
      rootCache.put(new ByteArrayWrapper(trie.getHash().getBytes()),trie);
    }

    public void nodeAccessed(Trie trie) {
        TrieWithChain e = (TrieWithChain) trie;

        // Optimization: if already the top item, do nothing
        if (e == head)
            return;

        // We could also optimize and compare with the top N elements
        // and skip moving them if on the top list
        if ((head!=null) && (e==head.next))
            return;

        // If the element is not linked, then it's a new element
        if ((e.prev == null) && (e.next == null)) {
            // new node
            cachedNodes++;
        }

        // If it's the tail, adjust tail
        if (e == tail)
            tail = e.prev;

        e.unlink();

        // If there is no head, set head
        if (head == null)
            head = e; // this should never happen, because e must be in the list
        else
            head = e.linkNext(head);

        // If there is no tail, set tail
        if (tail == null)
            tail = e;

        removeLastIfFull();
    }

    void removeLastIfFull() {
        if (cachedNodes > maxCacheNodes) {
            if (tail != null) {
                TrieWithChain prev = tail.prev;
                tail.unlink();
                tail = prev;
                if (head == tail)
                    head = prev;
                cachedNodes--;
            }
        }
    }
}
