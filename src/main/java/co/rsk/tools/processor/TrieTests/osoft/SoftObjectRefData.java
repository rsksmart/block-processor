package co.rsk.tools.processor.TrieTests.osoft;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;

public class SoftObjectRefData {
    byte[] encoded;
    EncodedObjectRef leftRef;
    EncodedObjectRef rightRef;

    public SoftObjectRefData(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        this.encoded = encoded;
        this.leftRef = leftRef;
        this.rightRef = rightRef;

    }
}
