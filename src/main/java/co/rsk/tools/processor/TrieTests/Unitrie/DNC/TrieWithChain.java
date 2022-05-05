package co.rsk.tools.processor.TrieTests.Unitrie.DNC;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class TrieWithChain extends TrieImpl {
    // Adds double linking
    public TrieWithChain prev;
    public TrieWithChain next;

    public TrieWithChain() {
        super();
    }

    public TrieWithChain(TrieStore store) {
        super(store);
    }

    protected TrieWithChain(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        super(store, sharedPath, value);
    }

    // full constructor
    protected TrieWithChain(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                            NodeReference left, NodeReference right,
                            Uint24 valueLength, Keccak256 valueHash,
                            VarInt childrenSize,
                            boolean isEmbedded, EncodedObjectRef aEncodedOfs) {
        super(store, sharedPath, value,
                left,  right, valueLength, valueHash,  childrenSize,   isEmbedded);

    }


    public TrieWithChain linkNext(TrieWithChain newNext) {
        this.next = newNext;
        if (newNext!=null)
            newNext.prev = this;
        return this;

    }
    public void unlink() {
        if (prev!=null) {
            prev.next = this.next;
            this.prev =null;
        }

        if (next!=null) {
            next.prev = this.prev;
            this.next =null;
        }


    }

    public Trie cloneNode(TrieKeySlice newSharedPath) {
        return new TrieWithChain(this.store, newSharedPath, this.value, this.left,
                this.right, this.valueLength,
                this.valueHash, this.childrenSize,
                TrieImpl.isEmbeddable(newSharedPath, this.left,  this.right, this.valueLength),
                null);
    }

    public Trie cloneNode(NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        return new TrieWithChain(this.store, this.sharedPath, this.value,
                newLeft, newRight, this.valueLength, this.valueHash, newChildrenSize,
                TrieImpl.isEmbeddable(this.sharedPath, newLeft,  newRight, this.valueLength),
                null);
    }

    public void accessThisNode() {
        if (store!=null) {
            DecodedNodeCache dec = store.getDecodedNodeCache();
            if (dec!=null)
                dec.nodeAccessed(this);
        }
    }
}
