package co.rsk.tools.processor.TrieTests.Unitrie.DNC;

import co.rsk.tools.processor.TrieTests.Unitrie.NodeReferenceFactory;
import co.rsk.tools.processor.TrieTests.Unitrie.Trie;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieFactory;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieFactoryImpl;
import co.rsk.tools.processor.TrieTests.Unitrie.store.TrieStoreImpl;
import org.ethereum.datasource.KeyValueDataSource;

import java.util.Optional;

public class TrieWithDNCStore extends TrieStoreImpl  {
    boolean useDecodeNodeCache;
    boolean useNodeChain;


    @Override
    public DecodedNodeCache getDecodedNodeCache() {
        // Only allow other to call accessNode() if the node chain is in use
        if ((useDecodeNodeCache) && (useNodeChain))
            return DecodedNodeCache.get();
        else
            return null;
    }

    public Optional<Trie> retrieveRoot(byte[] hash) {
        if (useDecodeNodeCache) {
            Trie t = DecodedNodeCache.get().retrieveRoot(hash);
            if (t!=null) {
                return Optional.of(t);
            }
        }
        return super.retrieveRoot(hash);
    }

    public void saveRoot(Trie trie) {

        super.saveRoot(trie);
        if (useDecodeNodeCache) {
            // This will attempt to compute all the hashes in the tree
            // if it is a newly created tree
            DecodedNodeCache.get().storeRoot(trie);
        }
    }

    public TrieWithDNCStore(KeyValueDataSource store,boolean useDecodeNodeCache,boolean useNodeChain) {
        super(store);
        this.useDecodeNodeCache = useDecodeNodeCache;
        this.useNodeChain = useNodeChain;
    }


    public TrieFactory getTrieFactory() {
        if (useNodeChain)
            return TrieWithChainFactory.get();
        else
            return TrieFactoryImpl.get();
    }

    public NodeReferenceFactory getNodeReferenceFactory() {
        return NodeReferenceWithWeakNodeFactory.get();
    }


}
