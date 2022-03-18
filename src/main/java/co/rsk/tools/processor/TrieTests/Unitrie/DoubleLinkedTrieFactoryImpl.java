package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class DoubleLinkedTrieFactoryImpl implements TrieFactory {

    static TrieFactory singleton = new DoubleLinkedTrieFactoryImpl();

    static public TrieFactory get() {
        return singleton;
    }

    public DoubleLinkedTrieFactoryImpl() {

    }

    public Trie newTrie(TrieStore store) {
        return new DoubleLinkedTrieImpl(store);
    }

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        return new DoubleLinkedTrieImpl(store,sharedPath,value);
    }

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash) {
        return new DoubleLinkedTrieImpl(store,  sharedPath, value, left, right, valueLength,  valueHash);
    }

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                           NodeReference left, NodeReference right,
                           Uint24 valueLength, Keccak256 valueHash,
                           VarInt childrenSize,
                           EncodedObjectRef aEncodedOfs) {
        return new DoubleLinkedTrieImpl(store,  sharedPath, value,
                left,  right,
                valueLength, valueHash,
                childrenSize, aEncodedOfs);
    }

    public Trie cloneTrie(Trie achild,TrieKeySlice newSharedPath) {
        TrieImpl child = (TrieImpl) achild;
        return DoubleLinkedTrieImpl.clone(achild,newSharedPath);
    }

    public Trie cloneTrie(Trie achild,NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        TrieImpl child = (TrieImpl) achild;
        return DoubleLinkedTrieImpl.clone(achild,newLeft,newRight,newChildrenSize);
    }
}
