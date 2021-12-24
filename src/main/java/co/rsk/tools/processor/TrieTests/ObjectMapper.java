package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.oheap.ObjectHeap;

public class ObjectMapper {

    static ObjectMapper objectMapper;

    public static ObjectMapper get() {
        if (objectMapper == null)
            //objectMapper = new SoftRefObjectMapper();
            objectMapper = new ObjectHeap();

        return objectMapper;
    }


    public void verifyEOR(EncodedObjectRef ref) {

    }

    public ObjectReference retrieve(EncodedObjectRef encodedOfs) {
        return null;
    }

    public EncodedObjectRef remap(EncodedObjectRef aofs,EncodedObjectRef aleftOfs,EncodedObjectRef arightOfs) {
      return aofs;
    }


    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs) {
        return null;
    }

    public boolean isRemapping() {
        return false;
    }

    public void checkDuringRemap(EncodedObjectRef encodedOfs) {
    }
}
