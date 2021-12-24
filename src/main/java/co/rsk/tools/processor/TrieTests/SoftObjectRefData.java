package co.rsk.tools.processor.TrieTests;

public class SoftObjectRefData {
    byte[] encoded;
    EncodedObjectRef leftOfs;
    EncodedObjectRef rightOfs;

    public SoftObjectRefData(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs) {
        this.encoded = encoded;
        this.leftOfs = leftOfs;
        this.rightOfs = rightOfs;

    }
}
