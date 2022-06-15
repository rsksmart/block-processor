package co.rsk.tools.processor.TrieTests.Unitrie.DataSources;

import org.ethereum.db.ByteArrayWrapper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class FlatDB extends DataSourceWithHeap {

    public FlatDB(int maxNodeCount, long beHeapCapacity, String databaseName) throws IOException {
        super(maxNodeCount, beHeapCapacity,databaseName);
    }
}
