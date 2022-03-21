package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.crypto.Keccak256;

public class NodeReferenceWithLazyNodeFactory implements NodeReferenceFactory {

    static NodeReferenceWithLazyNodeFactory singleton = new NodeReferenceWithLazyNodeFactory();

    static public NodeReferenceFactory get() {
        return singleton;
    }

    public NodeReferenceWithLazyNodeFactory() {

    }

    public NodeReference newReference(TrieStore store,  Trie node) {
        // the hash is lazy
        return new NodeReferenceWithLazyNode(store,node,null);
    }

    public NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash) {
        // here the node may be lazy
        return new NodeReferenceWithLazyNode(store,node,nodeHash);
    }

    @Override
    public NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash, EncodedObjectRef ref) {
        // encoded object reference is not used
        return new NodeReferenceWithLazyNode(store,node,nodeHash);
    }

    public NodeReference newReference(TrieStore store, Keccak256 nodeHash) {
        // the node is lazy
        return new NodeReferenceWithLazyNode(store,null,nodeHash);
    }
}
