package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

import java.nio.ByteBuffer;

public interface TrieFactory {


    public Trie newTrie(TrieStore store) ;

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value) ;
    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right,
                        Uint24 valueLength, Keccak256 valueHash) ;

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                     NodeReference left, NodeReference right,
                     Uint24 valueLength, Keccak256 valueHash,
                     VarInt childrenSize) ;

    public Trie newTrie(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                        NodeReference left, NodeReference right,
                        Uint24 valueLength, Keccak256 valueHash,
                        VarInt childrenSize,EncodedObjectRef ref) ;

    public Trie cloneTrie(Trie achild,TrieKeySlice newSharedPath);

    public Trie cloneTrie(Trie achild,NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize);

}
