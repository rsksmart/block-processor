package co.rsk.tools.processor.TrieTests.Unitrie.ENC;

import co.rsk.tools.processor.TrieTests.Unitrie.NodeReferenceFactory;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieFactory;
import co.rsk.tools.processor.TrieTests.Unitrie.store.TrieStoreImpl;
import org.ethereum.datasource.KeyValueDataSource;

public class TrieWithENCStore extends TrieStoreImpl {

    public TrieWithENCStore(KeyValueDataSource store) {
        super(store);
    }


    public TrieFactory getTrieFactory() {
        return TrieWithENCFactory.get();
    }

    public NodeReferenceFactory getNodeReferenceFactory() {
        return NodeReferenceWithENCFactory.get();
    }

}
