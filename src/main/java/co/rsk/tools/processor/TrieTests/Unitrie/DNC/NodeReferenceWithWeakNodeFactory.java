package co.rsk.tools.processor.TrieTests.Unitrie.DNC;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;

public class NodeReferenceWithWeakNodeFactory implements NodeReferenceFactory {

    static NodeReferenceWithWeakNodeFactory singleton = new NodeReferenceWithWeakNodeFactory();

    static public NodeReferenceFactory get() {
        return singleton;
    }

    public NodeReferenceWithWeakNodeFactory() {

    }

    public NodeReference newReference(TrieStore store, Trie node) {
        // the hash is lazy
        return new NodeReferenceWithWeakNode(store,node,null);
    }

    public NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash) {
        // here the node may be lazy
        return new NodeReferenceWithWeakNode(store,node,nodeHash);
    }

    @Override
    public NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash, EncodedObjectRef ref) {
        // encoded object reference is not used
        return new NodeReferenceWithWeakNode(store,node,nodeHash);
    }

    public NodeReference newReference(TrieStore store, Keccak256 nodeHash) {
        // the node is lazy
        return new NodeReferenceWithWeakNode(store,null,nodeHash);
    }
}
