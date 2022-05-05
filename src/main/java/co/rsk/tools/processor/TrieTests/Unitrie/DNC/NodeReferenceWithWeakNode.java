package co.rsk.tools.processor.TrieTests.Unitrie.DNC;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.NodeReferenceImpl;
import co.rsk.tools.processor.TrieTests.Unitrie.Trie;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieStore;

import java.lang.ref.SoftReference;
import java.util.Optional;

// If the element has been removed, then we must get it again from the
// store.
// we MUST have the lazyHash if the node was saves into the store.
// Therefore, the lazyHash is not lazy anymore in that case.
// If the node was not saved, then strongNode will have it.
// If it's null, then the node is empty

// Another problem: nodes cannot be removed if they were not stored on
// the store.
public class NodeReferenceWithWeakNode extends NodeReferenceImpl {
    protected SoftReference<Trie> weakNode;
    protected Trie strongNode;


    public NodeReferenceWithWeakNode(TrieStore store, Trie node, Keccak256 hash) {
        super(store,node,hash);

        if (node != null && node.isEmptyTrie()) {
            this.weakNode = null;
        } else {
            if ((node!=null) && (!node.wasSaved())) {
                strongNode = node;
                // Here we keep weakNode == null
            }
            else {

                // we assume the node was saved, because it will be retrieved from
                // store.
                // Here we could have node==null, or lazyHash== null, but not both.
                this.weakNode = new SoftReference<>(node);
                if (lazyHash == null)
                    lazyHash = node.getHash();
            }
        }

    }


    public boolean isEmpty() {
        if (strongNode!=null) return false;
        if (lazyHash != null) return false;
        return true;
    }

    public Optional<Trie> getNode(boolean persistent) {
        if (strongNode!=null) {
            return Optional.of(strongNode);
        }

        if (weakNode != null) {
            Trie node = weakNode.get();
            if (node!=null) {
                node.accessThisNode();
                return Optional.of(node);
            }
        }

        Optional<Trie> node = super.getNode(persistent);

        // Broken database, can't continue
        if (node.isPresent()) {
            Trie trie = node.get();
            if (trie.wasSaved()) {
                weakNode = new SoftReference<>(trie);
                trie.accessThisNode();
            }
        }
        return node;
    }

    public void removeLazyNode() {
        if (weakNode !=null)
            weakNode = new SoftReference<>(null); // bye bye
    }

    public void markAsPresentInTrieStore() {
        presentInTrieStore = true;
        if ((weakNode==null) || (weakNode.get() == null)) {
                if (lazyHash == null)
                    lazyHash = strongNode.getHash();
                weakNode = new SoftReference<>(strongNode);

        }
        strongNode = null; // no more strong references
    }
    public Optional<Keccak256> getHash() {
        if (lazyHash != null) {
            return Optional.of(lazyHash);
        }

        if (strongNode != null) {
            lazyHash = strongNode.getHash();
            return Optional.of(lazyHash);
        }

        return Optional.empty();
    }
}
