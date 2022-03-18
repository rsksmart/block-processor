package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class TrieFactoryImpl implements TrieFactory {

    static TrieFactory singleton = new TrieFactoryImpl();

    static public TrieFactory get() {
        return singleton;
    }

    public TrieFactoryImpl() {

    }

    public Trie newTrie(TrieStore store) {
        return new TrieImpl(store);
    }

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        return new TrieImpl(store,sharedPath,value);
    }

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash) {
        return new TrieImpl(store,  sharedPath, value, left, right, valueLength,  valueHash);
    }

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                           NodeReference left, NodeReference right,
                           Uint24 valueLength, Keccak256 valueHash,
                           VarInt childrenSize,
                           EncodedObjectRef aEncodedOfs) {
        return new TrieImpl(store,  sharedPath, value,
                left,  right,
                valueLength, valueHash,
                childrenSize, aEncodedOfs);
    }

    public Trie cloneTrie(Trie achild,TrieKeySlice newSharedPath) {
        TrieImpl child = (TrieImpl) achild;
        return newTrie(child.store, newSharedPath, child.value, child.left, child.right, child.valueLength, child.valueHash, child.childrenSize, null);
    }

    public Trie cloneTrie(Trie achild,NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        TrieImpl child = (TrieImpl) achild;
        return newTrie(child.store, child.sharedPath, child.value, newLeft, newRight, child.valueLength, child.valueHash, newChildrenSize,null);
    }
}
