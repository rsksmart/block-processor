package co.rsk.tools.processor.TrieTests.bahashmaps;

import co.rsk.tools.processor.TrieTests.baheaps.AbstractByteArrayRefHeap;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import org.ethereum.db.ByteArrayWrapper;

public class MaxSizeByteArrayHashMap extends ByteArrayRefHashMap {

    int head =-1;
    int tail=-1;

    class Link {
        int prev;
        int next;
        byte[] metadata;

        public Link(int prev,int next,byte[] metadata) {
            this.prev = prev;
            this.next = next;
            this.metadata = metadata;
        }

        public Link(byte[] metadata) {
            loadFromMetadata(metadata);
        }

        public void loadFromMetadata(byte[] metadata) {
            this.metadata = metadata;
            prev = ObjectIO.getInt(metadata,0);
            next = ObjectIO.getInt(metadata,4);
        }

        public void store() {
            ObjectIO.putInt(metadata,0,prev);
            ObjectIO.putInt(metadata,4,next);
        }
    }

    public MaxSizeByteArrayHashMap(int initialCapacity, float loadFactor,
                                   BAWrappedKeyValueRelation BAKeyValueRelation,
                                       long newBeHeapCapacity,
                                       AbstractByteArrayRefHeap sharedBaHeap,
                                       int maxElements) {
        super(initialCapacity,loadFactor,BAKeyValueRelation,newBeHeapCapacity,sharedBaHeap,maxElements);
    }

    final int metadataSize =8;

    void itemStored(int markedHandle) {
        Link tailLink = null;
        if (tail!=-1) {
            byte[] tailMetadata = baHeap.retrieveMetadataByHandle(unmarkHandle(tail));
            tailLink = new Link(tailMetadata);
        }
        byte[] m = new byte[metadataSize];
        Link newHeadLink = new Link(tail,-1,m);
        newHeadLink.store();
        baHeap.setMetadataByHandle(unmarkHandle(markedHandle),newHeadLink.metadata);

        if (tail!=-1) {
            tailLink.next = markedHandle;
            tailLink.store();
            baHeap.setMetadataByHandle(unmarkHandle(tail), tailLink.metadata);
        } else {
            head = markedHandle;
            tail = markedHandle;
        }

    }

    void afterNodeInsertion(byte[] p, boolean evict) {
        if (!evict) return;
        if (size<maxElements) return;

        // Take one element from the head
        int headHandle = unmarkHandle(head);
        byte[] headMetadata = baHeap.retrieveMetadataByHandle(headHandle);
        Link headLink = new Link(headMetadata);
        byte[] headData = baHeap.retrieveDataByHandle(headHandle);
        ByteArrayWrapper key;
        if (isValueHandle(head)) {
            key = computeKey(headData);
        } else {
            key = new ByteArrayWrapper(headData);
        }
        if (head==tail) {
            tail =-1;
        }
        head = headLink.next;

        removeNode(hash(key),key);
    }

    void afterNodeAccess(int markedHandle, byte[] p) {
        // Unlink and relink at tail
        int handle = unmarkHandle(markedHandle);
        byte[] metadata = baHeap.retrieveMetadataByHandle(handle);
        Link link = new Link(metadata);
        int prev = link.prev;
        int next = link.next;
        if (prev==-1)
            head = next;
        else
            setLink(prev,false,0,true,next);

        if (next==-1)
            tail = prev;
        else
            setLink(next,true,prev,false,0);

    }

    void setLink(int markedHandle,boolean setPrev,int prev,boolean setNext,int next) {
        int handle = unmarkHandle(markedHandle);
        byte[] metadata = baHeap.retrieveMetadataByHandle(handle);
        Link link = new Link(metadata);
        if (setNext)
            link.next = next;
        if (setPrev)
            link.prev = prev;
        link.store();
        baHeap.setMetadataByHandle(handle,link.metadata);
    }
}
