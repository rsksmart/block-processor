package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;

public class ByteArray39HashMap extends AbstractByteArrayHashMap {

    protected Table createTable(int cap)
    {
        LongTable table;
        table = new LongTable(cap);
        return table;
    }

}
