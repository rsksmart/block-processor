package co.rsk.tools.processor.TrieTests.omsoft;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ObjectReference;

import java.nio.ByteBuffer;

public class MultiSoftEncodedObjectStore extends EncodedObjectStore {

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        return new MultiSoftObjectRef(encoded, leftRef, rightRef);
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {

        ObjectReference r = new ObjectReference();
        MultiSoftObjectRef sor = (MultiSoftObjectRef) encodedRef;
        r.leftRef = sor.leftRef.get();
        r.rightRef = sor.rightRef.get();
        byte[] encoded =sor.encoded.get();
        r.len = encoded.length;
        r.message = ByteBuffer.wrap(encoded);
        return r;
    }

}
