package co.rsk.tools.processor.TrieTests;

import java.nio.ByteBuffer;

public class SoftRefObjectMapper extends ObjectMapper {

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        return new SoftObjectRef(encoded, leftRef, rightRef);
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        SoftObjectRef sor = (SoftObjectRef) encodedRef;

        ObjectReference r = new ObjectReference();
        // If not present, must load from store.
        SoftObjectRefData data = sor.ref.get();
        r.leftRef = data.leftOfs;
        r.rightRef = data.rightOfs;
        r.len = data.encoded.length;
        r.message = ByteBuffer.wrap(data.encoded);
        return r;
    }
}
