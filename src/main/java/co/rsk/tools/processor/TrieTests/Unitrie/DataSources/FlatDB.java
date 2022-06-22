package co.rsk.tools.processor.TrieTests.Unitrie.DataSources;

import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;
import org.ethereum.db.ByteArrayWrapper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;

public class FlatDB extends DataSourceWithHeap {

    public enum CreationFlag {
        supportNullValues,  // Allow values to be null, and stored as such in the map
        allowRemovals,      // allow remove() to really remove the values from the heap
        supportBigValues;   // support values with lengths higher than 127 bytes to be efficiently handled

        public static final EnumSet<CreationFlag> All = EnumSet.allOf(CreationFlag.class);
        public static final EnumSet<CreationFlag> None = EnumSet.noneOf(CreationFlag.class);
    }

    public FlatDB(int maxNodeCount, long beHeapCapacity, String databaseName,
                  EnumSet<CreationFlag> creationFlags,int dbVersion) throws IOException {
        // single-thread test:
        //  With rwlocks or exclusive locks: 85k/sec.
        //  Without locks: 102K/sec
        super(maxNodeCount, beHeapCapacity,databaseName,LockType.RW,convertFlatDBFlags(creationFlags),dbVersion);
    }

    public static EnumSet<AbstractByteArrayHashMap.CreationFlag> convertFlatDBFlags(EnumSet<CreationFlag> creationFlags) {
        EnumSet<AbstractByteArrayHashMap.CreationFlag> cfs = EnumSet.noneOf(AbstractByteArrayHashMap.CreationFlag.class);
        if (creationFlags.contains(CreationFlag.supportNullValues))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.supportNullValues);
        if (creationFlags.contains(CreationFlag.allowRemovals))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.allowRemovals);
        if (creationFlags.contains(CreationFlag.supportBigValues))
            cfs.add(AbstractByteArrayHashMap.CreationFlag.supportBigValues);

        return cfs;
    }
}
