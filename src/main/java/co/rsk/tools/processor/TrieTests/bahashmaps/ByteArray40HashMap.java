package co.rsk.tools.processor.TrieTests.bahashmaps;

import co.rsk.tools.processor.TrieTests.packedtables.Table;
import co.rsk.tools.processor.TrieTests.packedtables.UInt40Table;
import co.rsk.tools.processor.TrieTests.baheaps.AbstractByteArrayHeap;

import java.util.EnumSet;

public class ByteArray40HashMap extends AbstractByteArrayHashMap {

    public ByteArray40HashMap(int initialCapacity, float loadFactor,
                              co.rsk.tools.processor.TrieTests.bahashmaps.BAKeyValueRelation BAKeyValueRelation,
                              long newBeHeapCapacity,
                              AbstractByteArrayHeap sharedBaHeap,
                              int maxElements,
                              EnumSet<CreationFlag> creationFlags,
                              int dbVersion,int pageSizeInBytes) {
        super(initialCapacity,  loadFactor,
                BAKeyValueRelation,
                newBeHeapCapacity,
                sharedBaHeap,
                maxElements,creationFlags,dbVersion,pageSizeInBytes);
    }

    protected int getElementSize() {
        return UInt40Table.getElementSize();
    }

    protected Table createTable(int cap)
    {
        UInt40Table table;
        table = new UInt40Table(cap);
        return table;
    }

}
