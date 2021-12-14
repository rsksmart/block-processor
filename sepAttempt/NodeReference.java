package co.rsk.tools.processor.TrieTests.sepAttempt;



import co.rsk.crypto.Keccak256;
import java.util.Optional;

public class NodeReference {


    // This object is a placeholder to grow the size up-to the current RSK
    // Trie object size
    private final Object store=null;

    private Trie lazyNode;
    private Keccak256 lazyHash;

    public NodeReference(Trie node, Keccak256 hash) {
        if (node != null && node.isEmptyTrie()) {
            this.lazyNode = null;
            this.lazyHash = null;
        } else {
            this.lazyNode = node;
            this.lazyHash = hash;
        }
    }

    public boolean isEmpty() {
        return lazyHash == null && lazyNode == null;
    }

    /**
     * The node or empty if this is an empty reference.
     * If the node is not present but its hash is known, it will be retrieved from the store.
     */
    public Optional<Trie> getNode() {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }

        if (lazyHash == null) {
            return Optional.empty();
        }

        throw new RuntimeException("This test is not prepared to store data");
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHash() {
        if (lazyHash != null) {
            return Optional.of(lazyHash);
        }

        if (lazyNode == null) {
            return Optional.empty();
        }

        lazyHash = lazyNode.getHash();
        return Optional.of(lazyHash);
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHashOrchid(boolean isSecure) {
        return getNode().map(trie -> trie.getHash());
    }





    /**
     * @return the tree size in bytes as specified in RSKIP107 plus the actual serialized size
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * Do not use.
     */
    public long referenceSize() {
        // return anything
        return 1234;
    }

    public static NodeReference empty() {
        return new NodeReference(null, null);
    }
}

