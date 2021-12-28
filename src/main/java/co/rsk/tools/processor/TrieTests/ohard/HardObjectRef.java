package co.rsk.tools.processor.TrieTests.ohard;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;


public class HardObjectRef extends EncodedObjectRef {
    byte[] encoded;
    EncodedObjectRef leftRef;
    EncodedObjectRef rightRef;

    public HardObjectRef(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs) {
        this.encoded = encoded;
        this.leftRef = leftOfs;
        this.rightRef = rightOfs;
    }
}
