package co.rsk.tools.processor.TrieTests.Unitrie.store;

import co.rsk.tools.processor.TrieTests.Unitrie.LinkedByteArrayRefHeap;
import org.ethereum.db.ByteArrayWrapper;
import java.util.BitSet;

public class MaxSizeLinkedByteArrayHashMap extends ByteArrayHashMap {

    LinkedByteArrayRefHeap lba;

    // The isNull bitset is necessary because we need to know if a heap entry
    // corresponds to a key or to a value without knowing the index of the entry
    // on table[].
    // This means that if this class were to be integrated into the ByteArrayHashMap,
    // the marking of the handle would not be necessary.
    BitSet isNull;

    public MaxSizeLinkedByteArrayHashMap(int initialCapacity, float loadFactor,
                                   BAKeyValueRelation BAKeyValueRelation,
                                   long newBeHeapCapacity,
                                         LinkedByteArrayRefHeap sharedBaHeap,
                                   int maxElements) {
        super(initialCapacity,loadFactor,BAKeyValueRelation,newBeHeapCapacity,sharedBaHeap,maxElements);
        lba = sharedBaHeap;
        isNull = new BitSet(maxElements);
    }



    void afterNodeInsertion(int markedHandle,byte[] key, byte[] data, boolean evict) {
        int handle = unmarkHandle(markedHandle);
        // It is automatically added to the tail.
        if (isNullHandle(markedHandle))
            isNull.set(handle);
        else
            isNull.clear(handle);


        if (!evict) return;
        if (size < maxElements) return;
        evictOldest();
    }

    void evictOldest() {
        int oldest = lba.getOldest();
        byte[] pdata = baHeap.retrieveData(oldest);

        ByteArrayWrapper wkey;

        // Now there is a problem: I don't know if it is only a key/null or is a key/data
        if (!isNull.get(oldest)) {
            wkey = computeKey(pdata);
        } else {
            wkey = new ByteArrayWrapper(pdata);
        }
        if (removeNode(hash(wkey),wkey)==-1) {
            removeNode(hash(wkey),wkey);
            throw new RuntimeException("could not remove item");
        }
    }

    void afterNodeAccess(int markedHandle, byte[] p) {
        // Unlink and relink at tail
        lba.setAsNew(unmarkHandle(markedHandle));
    }

}
