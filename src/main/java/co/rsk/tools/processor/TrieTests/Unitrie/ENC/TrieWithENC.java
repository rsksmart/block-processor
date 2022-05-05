package co.rsk.tools.processor.TrieTests.Unitrie.ENC;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.*;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class TrieWithENC extends TrieImpl {
    static boolean tryToCompress = true;

    EncodedObjectRef encodedRef;

    // default constructor, no secure
    public TrieWithENC() {
        super();
    }

    public TrieWithENC(TrieStore store) {
        super(store);
    }

    protected TrieWithENC(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        super(store, sharedPath, value);
    }

    // full constructor
    protected TrieWithENC(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                          NodeReference left, NodeReference right,
                          Uint24 valueLength, Keccak256 valueHash,
                          VarInt childrenSize,
                          boolean isEmbedded, EncodedObjectRef aEncodedOfs) {
        super(store, sharedPath, value,
        left,  right, valueLength, valueHash,  childrenSize,   isEmbedded);
        this.encodedRef = aEncodedOfs;

        compressIfNecessary();
    }


    public static Trie retrieveNode(TrieFactory trieFactory,EncodedObjectRef encodedOfs) {
        //byte[] data = ObjectHeap.get().retrieveData(encodedOfs);
        ObjectReference r = GlobalEncodedObjectStore.get().retrieve(encodedOfs);
        Trie node = TrieBuilder.fromMessage(trieFactory,r.message, encodedOfs, r.leftRef, r.rightRef, null);
        return node;
    }
    public void compressIfNecessary() {
        if (!tryToCompress) return;
        if (isEmbedded) return;
        if (GlobalEncodedObjectStore.get()==null)
            return;
        if (encodedRef ==null)
            storeNodeInMem();
        if (!this.left.isEmbeddable())
            this.left.removeLazyNode();

        if (!this.right.isEmbeddable())
            this.right.removeLazyNode();

        encoded = null; // remove encoded, it's already in InMemStore
        //value = null; // remove value: it's already in MemStore
    }


    public void compressEncodingsRecursivelly() {
        for (byte k = 0; k < ARITY; k++) {
            TrieWithENC node = (TrieWithENC) this.retrieveNode(k);

            if (node == null) {
                continue;
            }
            node.compressEncodingsRecursivelly();

            ((NodeReferenceWithENC) getNodeReference(k)).setEncodedRef(node.getEncodedRef());
        }
        remapEncoding();
    }

    protected void changeSaved() {
        EncodedObjectStore om = GlobalEncodedObjectStore.get();
        if (this.encoded==null) return;
        if (om==null) return;
        om.setSaved(this.encodedRef,this.saved);
    }

    protected void storeNodeInMem() {
        if (GlobalEncodedObjectStore.get().isRemapping())
            throw new RuntimeException("Should never encode nodes during remapping");
        //ByteBuffer buffer = ByteBuffer.wrap(mem,memTop,mem.length-memTop);
        //serializeToByteBuffer(buffer);
        internalToMessage();
        EncodedObjectStore om = GlobalEncodedObjectStore.get();
        if (om.accessByHash()) {
            this.encodedRef = om.add(encoded,getHash(),this.saved);
        } else
            this.encodedRef = om.add(encoded,
                    ((NodeReferenceWithENC) left).getEncodedRef(),
                    ((NodeReferenceWithENC) right).getEncodedRef(),this.saved);

    }

    public void checkReference() {
        if (!isEmbedded) {
            GlobalEncodedObjectStore.get().checkDuringRemap(encodedRef);// left.getEncodedOfs(), right.getEncodedOfs());
        }
    }

    public void remapEncoding() {
        /*
        long pencodedOfs = encodedOfs;
        if (encodedOfs==161722718)
            encodedOfs=encodedOfs;

         */
        if (!isEmbedded) {
            encodedRef = GlobalEncodedObjectStore.get().remap(encodedRef,
                    ((NodeReferenceWithENC) left).getEncodedRef(),
                    ((NodeReferenceWithENC) right).getEncodedRef());
        }

        //reMapped = true;
    }

    public void checkTree() {
        // I think I could skip moving children if the current offset corresponds to the current space
        // if I reserve space for the parent before creating children, then the parent would always be
        // older than the children. But that requires knowing the size of the parent before serializing the
        // children.
        for (byte k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node == null) {
                continue;
            }
            ((TrieWithENC) node).checkTree();

            getNodeReference(k).checkRerefence();
        }
        checkReference();
    }

    public EncodedObjectRef getEncodedRef() {
        return encodedRef;
    }

    public Trie cloneNode(TrieKeySlice newSharedPath) {
        return new TrieWithENC(this.store, newSharedPath, this.value, this.left,
                this.right, this.valueLength,
                this.valueHash, this.childrenSize,
                TrieImpl.isEmbeddable(newSharedPath, this.left,  this.right, this.valueLength),
                null);
    }

    public Trie cloneNode(NodeReference newLeft, NodeReference newRight,VarInt newChildrenSize) {
        return new TrieWithENC(this.store, this.sharedPath, this.value,
                newLeft, newRight, this.valueLength, this.valueHash, newChildrenSize,
                TrieImpl.isEmbeddable(this.sharedPath, newLeft,  newRight, this.valueLength),
                null);
    }
}
