package co.rsk.tools.processor.TrieTests.objectstores.omsoft;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;

import java.lang.ref.SoftReference;


public class MultiSoftObjectRef extends EncodedObjectRef {
    SoftReference<byte[]> encoded;
    SoftReference<EncodedObjectRef> leftRef;
    SoftReference<EncodedObjectRef> rightRef;

    public MultiSoftObjectRef(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs) {
        this.encoded = new SoftReference<>(encoded);
        this.leftRef = new SoftReference<>(leftOfs);
        this.rightRef = new SoftReference<>(rightOfs);
    }
}
