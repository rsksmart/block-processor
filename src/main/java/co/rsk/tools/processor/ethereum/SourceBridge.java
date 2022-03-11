package co.rsk.tools.processor.ethereum;

import org.ethereum.datasource.KeyValueDataSource;

import co.rsk.tools.ethereum.trie.Source;

public class SourceBridge implements Source<byte[], byte[]> {

    KeyValueDataSource ds;

    public SourceBridge(KeyValueDataSource ds) {
        this.ds = ds;
    }
    /**
     * Puts key-value pair into source
     */
    public void put(byte[] key, byte[] val) {
        ds.put(key,val);
    }

    /**
     * Gets a value by its key
     * @return value or <null/> if no such key in the source
     */
    public byte[] get(byte[] key) {
        return ds.get(key);
    }

    /**
     * Deletes the key-value pair from the source
     */
    public void delete(byte[] key) {
        ds.delete(key);
    }

    /**
     * If this source has underlying level source then all
     * changes collected in this source are flushed into the
     * underlying source.
     * The implementation may do 'cascading' flush, i.e. call
     * flush() on the underlying Source
     * @return true if any changes we flushed, false if the underlying
     * Source didn't change
     */
    public boolean flush() {
        ds.flush();
        return true;
    }
}
