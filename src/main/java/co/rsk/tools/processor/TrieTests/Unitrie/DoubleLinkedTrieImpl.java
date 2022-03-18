package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class DoubleLinkedTrieImpl extends TrieImpl {
    // Adds double linking
    DoubleLinkedTrieImpl prev;
    DoubleLinkedTrieImpl next;


    public  DoubleLinkedTrieImpl(TrieStore store) {
        super(store);
    }

    public  DoubleLinkedTrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        super(store,sharedPath,value);
    }

    public  DoubleLinkedTrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash) {
        super(store,  sharedPath, value, left, right, valueLength,  valueHash);
    }

    protected  DoubleLinkedTrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                           NodeReference left, NodeReference right,
                           Uint24 valueLength, Keccak256 valueHash,
                           VarInt childrenSize,
                           EncodedObjectRef aEncodedOfs) {
        super(store,  sharedPath, value,
                left,  right,
                valueLength, valueHash,
                childrenSize, aEncodedOfs);
    }

    static protected  Trie clone(Trie achild,TrieKeySlice newSharedPath) {
        TrieImpl child = (TrieImpl) achild;
        return new DoubleLinkedTrieImpl(child.store, newSharedPath, child.value, child.left, child.right, child.valueLength, child.valueHash, child.childrenSize, null);
    }

    static protected Trie clone(Trie achild,NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        TrieImpl child = (TrieImpl) achild;
        return new DoubleLinkedTrieImpl(child.store, child.sharedPath, child.value, newLeft, newRight, child.valueLength, child.valueHash, newChildrenSize,null);
    }
}
