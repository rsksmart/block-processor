package co.rsk.tools.processor.TrieTests;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.ohard.HardObjectMapper;
import co.rsk.tools.processor.TrieTests.oheap.ObjectHeap;
import co.rsk.tools.processor.TrieTests.ohmap.ObjectHashMap;

public class ObjectMapper {

    static ObjectMapper objectMapper;

    public static ObjectMapper get() {
        if (objectMapper == null) {
            //objectMapper = new SoftRefObjectMapper();
            //objectMapper = new ObjectHeap();
            //objectMapper = new ObjectHashMap();
            //objectMapper = null;
            objectMapper = new HardObjectMapper();
        }
        return objectMapper;
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
