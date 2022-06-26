package co.rsk.tools.processor.TrieTests.Unitrie.ENC;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieTests.objectstores.ohmap.HashEOR;

import java.util.Optional;

public class NodeReferenceWithENC extends NodeReferenceWithLazyNode {

    private EncodedObjectRef encodedRef;

    public NodeReferenceWithENC(TrieStore store, Trie node, Keccak256 hash,
                                EncodedObjectRef aEndodedOfs) {
        super(store,  node, hash);

        this.encodedRef = aEndodedOfs;
        if (GlobalEncodedObjectStore.get()!=null) {
            GlobalEncodedObjectStore.get().verifyEOR(aEndodedOfs);
        }
    }

    public EncodedObjectRef getEncodedRef() {
        if ((encodedRef == null) && (lazyNode!=null)) {
            // it may have not been computed.
            // This happens when creating tries by hand (and not specifying the
            // encodeOfs argument in the constructor)
            encodedRef = ((TrieWithENC)lazyNode).getEncodedRef();
        }
        return encodedRef;
    }

    public void checkRerefence() {
        GlobalEncodedObjectStore.get().checkDuringRemap(encodedRef);
    }

    public void setEncodedRef(EncodedObjectRef ofs) {
        encodedRef = ofs;
        GlobalEncodedObjectStore.get().verifyEOR(ofs);
    }

    public void recomputeEncodeOfs() {
        encodedRef = ((TrieWithENC)getNode().get()).getEncodedRef();
    }

    public Trie getDynamicLazyNode() {

        if (GlobalEncodedObjectStore.get().accessByHash()) {
            encodedRef = new HashEOR(lazyHash);
        }
        if (encodedRef ==null) return null;
        ObjectReference r  = GlobalEncodedObjectStore.get().retrieve(encodedRef);
        try {
            Trie node = TrieBuilder.fromMessage(store.getTrieFactory(),r.message, encodedRef, r.leftRef, r.rightRef, store);
            if (r.saved)
                node.markAsSaved();
            return node;
        } catch (java.nio.BufferUnderflowException e) {
            //encodeOfs: 3386664381
            //encodeOfs: 2
            System.out.println("fault");
            System.out.println("EOR: "+encodedRef.toString());
            String ri = GlobalEncodedObjectStore.get().getRefInfo(encodedRef);
            System.out.println(ri);

            throw e;
        }

    }

    public Optional<Trie> getNode(boolean persistent) {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }

        if ((encodedRef !=null) ||
                ((GlobalEncodedObjectStore.get()!=null) &&
                        (GlobalEncodedObjectStore.get().accessByHash()) &&
                        (lazyHash !=null))) {
            if (persistent) {
                lazyNode = getDynamicLazyNode();
                this.presentInTrieStore = lazyNode.wasSaved();
                return Optional.of(lazyNode);
            } else
                return Optional.of(getDynamicLazyNode());

        }

        return super.getNode(persistent);
    }

    public Optional<Keccak256> getHash() {
        Optional<Keccak256> hash = super.getHash();

        if (lazyNode == null) {
            if (encodedRef !=null) {
                // This should not happen.
                // If lazyNode is null, then the lazyHash should be set.
                return Optional.of(getDynamicLazyNode().getHash());
            }
            return Optional.empty();
        }

        return hash;
    }

    protected byte[] getMessageFromMem() {
        if (encodedRef ==null)
            return null;
        ObjectReference r = GlobalEncodedObjectStore.get().retrieve(encodedRef);
        return r.getAsArray();
    }


    public void removeLazyNode() {
        // If I remove the node, I keep the offset to retrieve
        if (lazyNode!=null) {
            encodedRef = ((TrieWithENC)lazyNode).getEncodedRef();
            super.removeLazyNode();
        }
    }
}
