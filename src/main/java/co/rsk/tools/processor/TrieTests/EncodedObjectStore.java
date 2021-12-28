package co.rsk.tools.processor.TrieTests;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.omsoft.MultiSoftEncodedObjectStore;

public class EncodedObjectStore {

    static EncodedObjectStore encodedObjectStore;

    public static EncodedObjectStore get() {
        if (encodedObjectStore == null) {
            //objectMapper = new SoftRefObjectMapper();
            //objectMapper = new ObjectHeap();
            //objectMapper = new ObjectHashMap();
            //objectMapper = null;
            //objectMapper = new HardObjectMapper();
            encodedObjectStore = new MultiSoftEncodedObjectStore();
        }
        return encodedObjectStore;
    }


    public void verifyEOR(EncodedObjectRef ref) {

    }

    public boolean getByHash() {
        return false;
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        return null;
    }

    public EncodedObjectRef remap(EncodedObjectRef aofs,EncodedObjectRef aleftRef,EncodedObjectRef arightRef) {
      return aofs;
    }


    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        return null;
    }

    public EncodedObjectRef add(byte[] encoded, Keccak256 hash) {
        return null;
    }

    public boolean isRemapping() {
        return false;
    }

    public void checkDuringRemap(EncodedObjectRef encodedRef) {
    }
}
