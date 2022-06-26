package co.rsk.tools.processor.TrieTests.objectstores.oheap;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;

public class LongEOR extends EncodedObjectRef {
    public long ofs;
    public LongEOR(long aOfs) {
        ofs = aOfs;
    }

    public String toString() {
        return "ofs: "+ofs;
    }
}
