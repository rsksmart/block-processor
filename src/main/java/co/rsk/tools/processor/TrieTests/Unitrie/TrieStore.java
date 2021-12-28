package co.rsk.tools.processor.TrieTests.Unitrie;



import java.util.Optional;

public interface TrieStore {
    void save(Trie trie);

    void flush();

    /**
     * @param hash the root of the {@link Trie} to retrieve
     * @return an optional containing the {@link Trie} with <code>rootHash</code> if found
     */
    Optional<Trie> retrieve(byte[] hash);

    //Optional<ByteBuffer> retrieve(byte[] hash);

    byte[] retrieveValue(byte[] hash);

    void dispose();
}
