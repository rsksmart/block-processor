package co.rsk.tools.processor.TrieTests.orefheap;

import co.rsk.tools.processor.TrieTests.Unitrie.ByteArrayRefHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ObjectReference;
import co.rsk.tools.processor.TrieTests.oheap.HeapFileDesc;
import co.rsk.tools.processor.TrieTests.oheap.Space;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import org.ethereum.util.ByteUtil;

import java.nio.ByteBuffer;
import java.util.*;


public class EncodedObjectRefHeap extends EncodedObjectStore {
    ByteArrayRefHeap baHeap;

    public EncodedObjectRefHeap() {
        baHeap = new ByteArrayRefHeap();

    }

    public void initialize() {
        // creates the memory set by setMaxMemory()
        baHeap.initialize();
    }

    public long getMaxMemory() {
        return baHeap.getMaxMemory();
    }

    public void setMaxMemory(long m) {
        baHeap.setMaxMemory(m);
    }


    public int getCompressionPercent() {
        return baHeap.getCompressionPercent();
    }

    static EncodedObjectRefHeap objectHeap;
    // Static 1 gigabyte. //100 megabytes

    public static EncodedObjectRefHeap get() {
        if (objectHeap == null)
            objectHeap = new EncodedObjectRefHeap();

        return objectHeap;
    }

    public boolean supportsGarbageCollection() {
        return true;
    }

    public boolean isRemapping() {
        return baHeap.isRemapping();
    }

    public void beginRemap() {
        baHeap.beginRemap();
    }

    public void endRemap() {
        baHeap.endRemap();
    }

    public void checkAll() {
        baHeap.checkAll();
    }
    public String getRefInfo(EncodedObjectRef encodedRef) {
        int handle = getHandle(encodedRef);
        return baHeap.getHandleInfo(handle);
    }

    public void checkDuringRemap(EncodedObjectRef encodedRef) {
        baHeap.checkDuringRemap(getHandle(encodedRef));
    }

    public EncodedObjectRef remap(EncodedObjectRef aRef, EncodedObjectRef aleftRef, EncodedObjectRef arightRef) {
        int handle = getHandle(aRef);
        baHeap.remap(handle);
        return aRef;
    }

    public int getMemSize() {
        return baHeap.getMemSize();
    }

    public boolean currentSpaceIsAlmostFull() {
        return baHeap.currentSpaceIsAlmostFull();
    }

    public boolean heapIsAlmostFull() {
        return baHeap.heapIsAlmostFull();
    }

    public List<String> getStats() {
        return baHeap.getStats();
    }


    public int getHandle(EncodedObjectRef ref) {
        if (ref == null)
            return -1;
        return ((IntEOR) ref).handle;
    }

    // This method may need to be made thread-safe
    // encoded: this is the message to store

    // The add() method could be generic, and accept only an encoded argument
    // and let the caller pack the offset within this byte array.
    // By accepting the ofs separately we're able to perform some checks on these,
    // to make sure that the references objects are not in the space that is being
    // removed.
    // We could also accept aa variable number of offsets. However, as we're only
    // using this store for nodes of the trie, we'll never need to store an object
    // having more than 2 offsets.
    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        int aLeftHandle = getHandle(leftRef);
        int aRightHandle = getHandle(rightRef);
        // Build a new encoded data with the handles added at the end
        byte[] mem = new byte[encoded.length+8];
        int newMemTop = encoded.length;
        ObjectIO.putInt(mem, newMemTop + 0, aLeftHandle);
        ObjectIO.putInt(mem, newMemTop + 4, aRightHandle);
        newMemTop += 8;
        System.arraycopy(encoded, 0, mem, 0, encoded.length);
        // The metadata is embedded in the memory
        return (EncodedObjectRef) new IntEOR(baHeap.addGetHandle(mem,null));
    }

    public void verifyEOR(EncodedObjectRef ref) {
        if (ref == null) return;
        int handle = ((IntEOR) ref).handle;
        baHeap.checkHandle(handle);
    }

    public byte[] retrieveData(EncodedObjectRef encodedOfs) {
        int handle = getHandle(encodedOfs);
        if (handle == -1)
            throw new RuntimeException("no data");
        byte[] mem = baHeap.retrieveData(handle);
        // Now remove the last 8 bytes
        byte[] encoded = Arrays.copyOf(mem,mem.length-8);
        return encoded;
    }


    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        int handle = getHandle(encodedRef);
        if (handle == -1)
            throw new RuntimeException("no data");
        byte[] data = baHeap.retrieveData(handle);

        int rLength = data.length-8;

        int leftRef = ObjectIO.getInt(data, rLength);
        int rightRef = ObjectIO.getInt(data, rLength+4);
        ObjectReference or = new ObjectReference();
        or.message = ByteBuffer.wrap(data,0,rLength);
        or.leftRef = new IntEOR(leftRef);
        or.rightRef = new IntEOR(rightRef);
        return or;
    }

    public long getMemUsed() {
        return baHeap.getMemUsed();
    }


    public long getMemAllocated() {
        return baHeap.getMemAllocated();
    }

    public long getMemMax() {
        return baHeap.getMemMax();
    }
    public int getUsagePercent() {
        return baHeap.getUsagePercent();
    }

    public int getFilledSpacesCount() {
        return baHeap.getFilledSpacesCount();
    }

    public String getGarbageCollectionDescription() {
        return baHeap.getRemovingSpacesDesc();
    }


    public String getFilledSpacesDesc() {
        return baHeap.getFilledSpacesDesc();
    }

    public String getPartiallyFilledSpacesDesc() {
        return baHeap.getPartiallyFilledSpacesDesc();
    }

    public int getPartiallyFilledSpacesCount() {
        return baHeap.getPartiallyFilledSpacesCount();
    }
}

