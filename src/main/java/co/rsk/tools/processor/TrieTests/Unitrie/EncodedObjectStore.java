package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.crypto.Keccak256;

public class EncodedObjectStore {


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
}
