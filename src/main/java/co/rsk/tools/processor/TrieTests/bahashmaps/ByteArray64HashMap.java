package co.rsk.tools.processor.TrieTests.bahashmaps;

import co.rsk.tools.processor.TrieTests.packedtables.LongTable;
import co.rsk.tools.processor.TrieTests.packedtables.Table;
import co.rsk.tools.processor.TrieTests.baheaps.AbstractByteArrayHeap;

import java.util.EnumSet;

public class ByteArray64HashMap extends AbstractByteArrayHashMap {

    public ByteArray64HashMap(int initialCapacity, float loadFactor,
                              BAKeyValueRelation BAKeyValueRelation,
                              long newBeHeapCapacity,
                              AbstractByteArrayHeap sharedBaHeap,
                              int maxElements,
                              Format format) {
        super(initialCapacity,  loadFactor,
        BAKeyValueRelation,
        newBeHeapCapacity,
         sharedBaHeap,
                maxElements,
                format);
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
