package co.rsk.tools.processor.TrieTests.oheap;

import co.rsk.tools.processor.TrieTests.EncodedObjectRef;

public class LongEOR extends EncodedObjectRef {
    public long ofs;
    public LongEOR(long aOfs) {
        ofs = aOfs;
    }
}
