package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.crypto.Keccak256;

import java.util.ArrayList;
import java.util.List;

public class EncodedObjectStore {


    public void initialize() {
        // creates the memory set by setMaxMemory()
    }
    public long getMaxMemory() {
        return 0;
    }

    public void setMaxMemory(long m) {

    }
    public void verifyEOR(EncodedObjectRef ref) {

    }

    public boolean accessByHash() {
        return false;
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        return null;
    }

    public EncodedObjectRef remap(EncodedObjectRef aofs,EncodedObjectRef aleftRef,EncodedObjectRef arightRef) {
      return aofs;
    }


    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        if (accessByHash())
            throw new RuntimeException("invalid access");
        return null;
    }

    public EncodedObjectRef add(byte[] encoded, Keccak256 hash) {
        if (!accessByHash())
            throw new RuntimeException("invalid access");
        return null;
    }

    public boolean isRemapping() {
        return false;
    }

    public void checkDuringRemap(EncodedObjectRef encodedRef) {
    }

    public boolean supportsGarbageCollection() {
        return false;
    }

    public List<String> getStats() {
        List<String> res = new ArrayList<>(0);
        return res;
    }

    // These methods are only for garbage-collected stores
    public boolean heapIsAlmostFull() {
            return false;
    }
    public int getUsagePercent() {
        return 0;
    }
    public void beginRemap() {

    }
    public int getCompressionPercent() {
        return 0;
    }
    public void endRemap() {
    }
    public String getGarbageCollectionDescription() {
        return "";
    }

    public String getRefInfo(EncodedObjectRef encodedRef) {
        return encodedRef.toString();
    }
}
