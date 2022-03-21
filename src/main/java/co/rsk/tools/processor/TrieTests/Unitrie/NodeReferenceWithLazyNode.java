package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.Keccak256Helper;

import java.nio.ByteBuffer;
import java.util.Optional;

public class NodeReferenceWithLazyNode extends NodeReferenceImpl {
    protected Trie lazyNode;

    public NodeReferenceWithLazyNode(TrieStore store,  Trie node, Keccak256 hash) {
        super(store,node,hash);

        if (node != null && node.isEmptyTrie()) {
            this.lazyNode = null;
        } else {
            this.lazyNode = node;
        }
    }

    public boolean isEmpty() {
        return lazyHash == null && lazyNode == null;
    }

    public Optional<Trie> getNode(boolean persistent) {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }

        Optional<Trie> node = super.getNode(persistent);

        // Broken database, can't continue
        if (node.isPresent()) {
            lazyNode = node.get();
        }
        return node;
    }

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

    protected byte[] getSerialized() {
        if (lazyNode!=null)
            return lazyNode.toMessage();
        else
            return super.getSerialized();
    }

    public boolean isEmbeddable() {

        // if the node is embeddable then this reference must have a reference in memory
        if (lazyNode == null) {
            return false;
        }
        return lazyNode.isEmbeddable();
    }

    // This method should only be called from save()
    public int serializedLength() {
        if (!isEmpty()) {
            if (isEmbeddable()) {
                return lazyNode.getMessageLength() + 1;
            }

            return Keccak256Helper.DEFAULT_SIZE_BYTES;
        }

        return 0;
    }

    public void serializeInto(ByteBuffer buffer) {
        if (!isEmpty()) {
            if (isEmbeddable()) {
                byte[] serialized = getSerialized();
                buffer.put(new Uint8(serialized.length).encode());
                buffer.put(serialized);
            } else {
                byte[] hash = getHash().map(Keccak256::getBytes)
                        .orElseThrow(() -> new IllegalStateException("The hash should always exists at this point"));
                buffer.put(hash);
            }
        }
    }

    public void removeLazyNode() {
            lazyNode = null; // bye bye
    }
}
