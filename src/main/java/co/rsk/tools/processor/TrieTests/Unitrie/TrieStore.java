package co.rsk.tools.processor.TrieTests.Unitrie;



import co.rsk.tools.processor.TrieTests.Unitrie.DNC.DecodedNodeCache;

import java.util.Optional;

public interface TrieStore {
    // This new method is used to notify the TrieStore that a new node has been created
    // or a node has been traversed.
    // This let the trie store decide if the node deserves being cached.
    // Some decisions are made not only based on frequency of access, but also on
    // key depth. Therefore we pass the full key length as argument.
    // void accessNode(Trie trie);

    // Returns a trie factory that is compatible with this store.
    TrieFactory getTrieFactory();

    NodeReferenceFactory getNodeReferenceFactory();

    DecodedNodeCache getDecodedNodeCache();

    // This method should only be used with trie roots.
    void saveRoot(Trie trie);

    // If it's necessary to save other parts of the trie, then a different
    // method must be added.
    void save(Trie trie);

    void flush();

    /**
     * @param hash the root of the {@link Trie} to retrieve
     * @return an optional containing the {@link Trie} with <code>rootHash</code> if found
     */
    Optional<Trie> retrieve(byte[] hash);

    // Optimized for root retrieval
    Optional<Trie> retrieveRoot(byte[] hash);

    //Optional<ByteBuffer> retrieve(byte[] hash);

    byte[] retrieveValue(byte[] hash);

    void dispose();
}
