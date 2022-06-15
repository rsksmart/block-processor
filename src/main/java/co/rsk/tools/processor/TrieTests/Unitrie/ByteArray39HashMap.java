package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.BAKeyValueRelation;

public class ByteArray39HashMap extends AbstractByteArrayHashMap {

    public ByteArray39HashMap(int initialCapacity, float loadFactor,
                              co.rsk.tools.processor.TrieTests.Unitrie.store.BAKeyValueRelation BAKeyValueRelation,
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
        UInt40Table table;
        table = new UInt40Table(cap);
        return table;
    }

}
