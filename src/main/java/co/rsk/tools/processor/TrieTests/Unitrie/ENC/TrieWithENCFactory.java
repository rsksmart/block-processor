package co.rsk.tools.processor.TrieTests.Unitrie.ENC;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class TrieWithENCFactory implements TrieFactory {

    static TrieWithENCFactory singleton = new TrieWithENCFactory();

    static public TrieWithENCFactory get() {
        return singleton;
    }

    public TrieWithENCFactory() {

    }

    @Override
    public Trie newTrie(TrieStore store) {
        return new TrieWithENC(store);
    }
    @Override
    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        return new TrieWithENC(store,sharedPath,value);
    }


    @Override
    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash, VarInt childrenSize, EncodedObjectRef ref) {
        // Ignore ref
        return new TrieWithENC(store,  sharedPath, value,
                left,  right,
                valueLength, valueHash,
                childrenSize,TrieImpl.isEmbeddable(sharedPath, left,  right, valueLength),null);
    }
    @Override
    public Trie cloneTrie(Trie achild,TrieKeySlice newSharedPath) {
        TrieWithENC child = (TrieWithENC) achild;
        return child.cloneNode(newSharedPath);
    }

    @Override
    public Trie cloneTrie(Trie achild,NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        TrieWithENC child = (TrieWithENC) achild;
        return child.cloneNode(newLeft,newRight,newChildrenSize);
    }
}
