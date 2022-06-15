package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.BAKeyValueRelation;

public class ByteArray63HashMap  extends AbstractByteArrayHashMap {

    public ByteArray63HashMap(int initialCapacity, float loadFactor,
                                    BAKeyValueRelation BAKeyValueRelation,
                                    long newBeHeapCapacity,
                                    AbstractByteArrayHeap sharedBaHeap,
                                    int maxElements) {
        super(initialCapacity,  loadFactor,
        BAKeyValueRelation,
        newBeHeapCapacity,
         sharedBaHeap,
         maxElements);
    }

    protected Table createTable(int cap)
    {
        LongTable table;
        table = new LongTable(cap);
        return table;
    }
}
