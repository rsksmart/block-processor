package co.rsk.tools.processor.TrieTests.Unitrie.DNC;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.TrieWithENC;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.TrieWithENCFactory;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class TrieWithChainFactory implements TrieFactory  {
    static TrieWithChainFactory singleton = new TrieWithChainFactory();

    static public TrieWithChainFactory get() {
        return singleton;
    }

    public TrieWithChainFactory() {

    }

    @Override
    public Trie newTrie(TrieStore store) {
        return new TrieWithChain(store);
    }

    @Override
    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        return new TrieWithChain(store,sharedPath,value);
    }


    @Override
    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash, VarInt childrenSize, EncodedObjectRef ref) {
        // Ignore ref
        return new TrieWithChain(store,  sharedPath, value,
                left,  right,
                valueLength, valueHash,
                childrenSize,TrieImpl.isEmbeddable(sharedPath, left,  right, valueLength),null);
    }
    @Override
    public Trie cloneTrie(Trie achild,TrieKeySlice newSharedPath) {
        TrieWithChain child = (TrieWithChain) achild;
        return child.cloneNode(newSharedPath);
    }

    @Override
    public Trie cloneTrie(Trie achild,NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        TrieWithChain child = (TrieWithChain) achild;
        return child.cloneNode(newLeft,newRight,newChildrenSize);
    }
}
