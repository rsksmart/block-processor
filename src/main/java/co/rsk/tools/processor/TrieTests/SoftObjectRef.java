package co.rsk.tools.processor.TrieTests;

import java.lang.ref.SoftReference;

public class SoftObjectRef extends EncodedObjectRef {
    public SoftReference<SoftObjectRefData> ref;

    public SoftObjectRef(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs) {
        SoftObjectRefData d = new SoftObjectRefData(encoded,leftOfs,rightOfs);
        ref = new SoftReference<SoftObjectRefData>(d);
    }
}
