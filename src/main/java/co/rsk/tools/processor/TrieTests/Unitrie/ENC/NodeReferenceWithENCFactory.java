package co.rsk.tools.processor.TrieTests.Unitrie.ENC;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;

public class NodeReferenceWithENCFactory implements NodeReferenceFactory {
    static NodeReferenceWithENCFactory singleton = new NodeReferenceWithENCFactory();

    static public NodeReferenceWithENCFactory get() {
        return singleton;
    }

    public NodeReferenceWithENCFactory() {

    }

    @Override
    public NodeReference newReference(TrieStore store, Trie node) {
        return null;
    }

    @Override
    public NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash) {
        return null;
    }

    @Override
    public NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash, EncodedObjectRef ref) {
        return null;
    }
}
