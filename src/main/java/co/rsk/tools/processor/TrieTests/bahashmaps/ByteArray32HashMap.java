package co.rsk.tools.processor.TrieTests.bahashmaps;

import co.rsk.tools.processor.TrieTests.baheaps.AbstractByteArrayHeap;
import co.rsk.tools.processor.TrieTests.packedtables.Int32Table;
import co.rsk.tools.processor.TrieTests.packedtables.Table;

import java.util.EnumSet;

public class ByteArray32HashMap extends AbstractByteArrayHashMap {

    public ByteArray32HashMap(int initialCapacity, float loadFactor,
                              co.rsk.tools.processor.TrieTests.bahashmaps.BAKeyValueRelation BAKeyValueRelation,
                              long newBeHeapCapacity,
                              AbstractByteArrayHeap sharedBaHeap,
                              int maxElements,
                              Format format) {
        super(initialCapacity,  loadFactor,
                BAKeyValueRelation,
                newBeHeapCapacity,
                sharedBaHeap,
                maxElements,format);
    }

    protected int getElementSize() {
        return Int32Table.getElementSize();
    }

    protected Table createTable(int cap)
    {
        Int32Table table;
        table = new Int32Table(cap);
        return table;
    }
}
