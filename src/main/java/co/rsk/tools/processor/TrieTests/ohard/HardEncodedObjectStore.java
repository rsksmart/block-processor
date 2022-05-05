package co.rsk.tools.processor.TrieTests.ohard;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ObjectReference;


import java.nio.ByteBuffer;

public class HardEncodedObjectStore extends EncodedObjectStore {

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef,boolean saved) {
        return new HardObjectRef(encoded, leftRef, rightRef,saved);
    }

    public void setSaved(EncodedObjectRef encodedRef,boolean saved) {
        HardObjectRef hor = (HardObjectRef) encodedRef;
        hor.saved = saved;
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {

        ObjectReference r = new ObjectReference();
        HardObjectRef hor = (HardObjectRef) encodedRef;
        r.leftRef = hor.leftRef;
        r.rightRef = hor.rightRef;
        r.len = hor.encoded.length;
        r.message = ByteBuffer.wrap(hor.encoded);
        r.saved = hor.saved;
        return r;
    }

}
