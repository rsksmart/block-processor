package co.rsk.tools.processor.TrieTests.objectstores.orefheap;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;

public class IntEOR extends EncodedObjectRef {
    public int handle;
    public IntEOR(int aHndle) {
        handle = aHndle;
    }
    public String toString() {
        return "handle: "+handle;
    }
}
