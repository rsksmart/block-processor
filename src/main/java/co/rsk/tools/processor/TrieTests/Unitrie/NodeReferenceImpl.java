package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.Keccak256Helper;

import java.nio.ByteBuffer;
import java.util.Optional;

public class NodeReferenceImpl implements NodeReference {

    private static final NodeReference EMPTY = new NodeReferenceImpl(null, null, null);

    protected final TrieStore store;

    // This field is called "lazyHash" because inherited classes make this field
    // lazy (it may not be present). However this class will always need lazyHash
    // to be present UNLESS the node referenced is empty.
    protected Keccak256 lazyHash;
    protected boolean presentInTrieStore;
    protected boolean abortOnTraverse;
    protected boolean abortOnRetrieval;

    public NodeReferenceImpl(TrieStore store,  Trie node, Keccak256 hash) {
        this.store = store;
        if (node != null && node.isEmptyTrie()) {
            this.lazyHash = null;
        } else {
            this.lazyHash = hash;
            if (node!=null)
                this.presentInTrieStore = node.wasSaved();
            if (hash!=null) {
                this.lazyHash = hash;
            }
        }

    }


    @Override
    public void checkRerefence() {

    }

    public boolean isEmpty() {
        return lazyHash == null;
    }

    /**
     * The node or empty if this is an empty reference.
     * If the node is not present but its hash is known, it will be retrieved from the store.
     * If the node could not be retrieved from the store, the Node is stopped using System.exit(1)
     */
    public Optional<Trie> getNode() {
        return getNode(false);
    }

    protected void checkAbortOnTraverse() {
        if (abortOnTraverse)
            throw new RuntimeException("Traverse prevented");
    }
    protected void checkAbortOnRetrieval() {
        if (abortOnRetrieval)
            throw new RuntimeException("Retrieval prevented");
    }

    public Optional<Trie> getNode(boolean persistent) {

        if (lazyHash == null) {
            return Optional.empty();
        }
        checkAbortOnTraverse();
        checkAbortOnRetrieval();

        // retrieve node from mem
        Optional<Trie> node = store.retrieve(lazyHash.getBytes());

        // Broken database, can't continue
        if (!node.isPresent()) {
            //logger.error("Broken database, execution can't continue");
            //System.exit(1);
            throw new RuntimeException("not present: "+lazyHash.toHexString());
            //return Optional.empty();
        }

        presentInTrieStore = true;

        return node;
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHash() {
        if (lazyHash != null) {
            return Optional.of(lazyHash);
        }

        return Optional.empty();
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHashOrchid(boolean isSecure) {
        return getNode().map(trie -> trie.getHashOrchid(isSecure));
    }

    @SuppressWarnings("squid:S2384") // private method knows it can avoid copying the byte[] field
    protected byte[] getSerialized() {
        return getMessageFromMem();
    }

    protected byte[] getMessageFromMem() {
        Optional<Trie> node = getNode();
        if (node.isPresent())
            return node.get().toMessage();
        else
            return null;
    }

    public boolean isEmbeddable() {
        Optional<Trie> node = getNode();
        if (node.isPresent())
            return node.get().isEmbeddable();
        else
            return false;

    }

    // the referenced node was loaded from a TrieStore
    public boolean isPresentInTrieStore() {
        return presentInTrieStore;
    }

    public void markAsPresentInTrieStore() {
        presentInTrieStore = true;
    }

    // This method should only be called from save()
    public int serializedLength() {
        if (isEmpty())
            return 0;

        checkNonLazyHash();
        Optional<Trie> node = getNode();
        if (node.isPresent()) {
            if (node.get().isEmbeddable()) {
                return node.get().getMessageLength() + 1;
            }

            return Keccak256Helper.DEFAULT_SIZE_BYTES;

        }
        return 0;
    }

    public void checkNonLazyHash() {
        if (isEmpty())
            throw new RuntimeException("Hash cannot be lazy");
    }

    public void serializeInto(ByteBuffer buffer) {
        if (isEmpty())
            return;

        checkNonLazyHash();
        Optional<Trie> node = getNode();
        if (node.isPresent()) {
            if (node.get().isEmbeddable()) {
                byte[] serialized =  node.get().toMessage();
                buffer.put(new Uint8(serialized.length).encode());
                buffer.put(serialized);
            } else {
                byte[] hash = getHash().map(Keccak256::getBytes)
                        .orElseThrow(() -> new IllegalStateException("The hash should always exists at this point"));
                buffer.put(hash);
            }
        }
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107 plus the actual serialized size
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * Do not use.
     */
    public long referenceSize() {
        return getNode().map(this::nodeSize).orElse(0L);
    }

    private long nodeSize(Trie trie) {
        long externalValueLength = trie.hasLongValue() ? trie.getValueLength().intValue() : 0L;
        return trie.getChildrenSize().value + externalValueLength + trie.getMessageLength();
    }

    public static NodeReference empty() {
        return EMPTY;
    }

    public void removeLazyNode() {
    }

    @Override
    public void shrink() {

    }
    @Override
    public void setAbortOnTraverse(boolean v) {
        this.abortOnTraverse = v;
    }

    @Override
    public void setAbortOnRetrieval(boolean v) {
        this.abortOnRetrieval = v;
    }


    @Override
    public void clear() {
        lazyHash = null; // invalid from now on
    }
}
