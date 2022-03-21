package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.crypto.Keccak256;

public interface NodeReferenceFactory {

    // If we're using encoded references, the encoded ref must also be copied!
    NodeReference newReference(TrieStore store,  Trie node);
    NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash);

    NodeReference newReference(TrieStore store, Trie node, Keccak256 nodeHash,EncodedObjectRef ref);
     //new NodeReference(null, rootNode, null, null),
}
