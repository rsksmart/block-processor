package co.rsk.tools.processor.TrieTests;

import java.nio.ByteBuffer;

public class SoftRefObjectMapper extends ObjectMapper {

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs) {
        return new SoftObjectRef(encoded, leftOfs, rightOfs);
    }

    public ObjectReference retrieve(EncodedObjectRef encodedOfs) {
        SoftObjectRef sor = (SoftObjectRef) encodedOfs;

        ObjectReference r = new ObjectReference();
        // If not present, must load from store.
        SoftObjectRefData data = sor.ref.get();
        r.leftOfs = data.leftOfs;
        r.rightOfs = data.rightOfs;
        r.len = data.encoded.length;
        r.message = ByteBuffer.wrap(data.encoded);
        return r;
    }
}
