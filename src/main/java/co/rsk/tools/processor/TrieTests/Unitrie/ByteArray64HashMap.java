package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;
import co.rsk.tools.processor.TrieTests.Unitrie.store.BAKeyValueRelation;

import java.util.EnumSet;

public class ByteArray64HashMap extends AbstractByteArrayHashMap {

    public ByteArray64HashMap(int initialCapacity, float loadFactor,
                              BAKeyValueRelation BAKeyValueRelation,
                              long newBeHeapCapacity,
                              AbstractByteArrayHeap sharedBaHeap,
                              int maxElements,
                              EnumSet<CreationFlag> creationFlags,
                              int dbVersion,int pageSizeInBytes) {
        super(initialCapacity,  loadFactor,
        BAKeyValueRelation,
        newBeHeapCapacity,
         sharedBaHeap,
                maxElements,
                creationFlags,dbVersion,pageSizeInBytes);
    }

    protected int getElementSize() {
        return LongTable.getElementSize();
    }
    protected Table createTable(int cap)
    {
        LongTable table;
        table = new LongTable(cap);
        return table;
    }
}
