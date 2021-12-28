package co.rsk.tools.processor.TrieTests.ohard;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ObjectReference;


import java.nio.ByteBuffer;

public class HardEncodedObjectStore extends EncodedObjectStore {

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        return new HardObjectRef(encoded, leftRef, rightRef);
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {

        ObjectReference r = new ObjectReference();
        HardObjectRef hor = (HardObjectRef) encodedRef;
        r.leftRef = hor.leftRef;
        r.rightRef = hor.rightRef;
        r.len = hor.encoded.length;
        r.message = ByteBuffer.wrap(hor.encoded);
        return r;
    }

}
