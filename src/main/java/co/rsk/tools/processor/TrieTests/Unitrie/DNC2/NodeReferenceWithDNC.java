package co.rsk.tools.processor.TrieTests.Unitrie.DNC2;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.NodeReferenceImpl;
import co.rsk.tools.processor.TrieTests.Unitrie.Trie;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieStore;

import java.util.Optional;

public class NodeReferenceWithDNC extends NodeReferenceImpl {
    private long decodedRef =-1;

    public NodeReferenceWithDNC(TrieStore store, Trie node, Keccak256 hash,
                                long aDecodedRef) {
        super(store,  node, hash);

        this.decodedRef = aDecodedRef;
    }




    public long getDecodedRef() {
        return decodedRef;
    }


    public void setDecodedRef(long de) {
        this.decodedRef = de;
    }


    public void checkRerefence() {
        //store.getDecodedTrieNodeCache.checkReference(decodedRef);
    }

    public Optional<Trie> getNode(boolean persistent) {
        /*if (decodedRef>=0) {
            Trie lazyNode =store.getDecodedNodeCache().retrieve(decodedRef);
            if (lazyNode==null)
                return Optional.empty();
            return Optional.of(lazyNode);
        }*/
        return super.getNode(persistent);
    }
}
