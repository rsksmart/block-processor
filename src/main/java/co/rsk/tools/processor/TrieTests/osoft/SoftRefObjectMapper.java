package co.rsk.tools.processor.TrieTests.osoft;

import co.rsk.tools.processor.TrieTests.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.ObjectMapper;
import co.rsk.tools.processor.TrieTests.ObjectReference;

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
        r.leftRef = data.leftRef;
        r.rightRef = data.rightRef;
        r.len = data.encoded.length;
        r.message = ByteBuffer.wrap(data.encoded);
        return r;
    }
}
