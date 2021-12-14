package co.rsk.tools.processor.Index;

import co.rsk.tools.processor.TrieUtils.CompactTrieKeySlice;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Set;

public class AbstractIndexTrie {

    public int noPathCount() {
        return 0;
    }
    public int dataCount() {
        return 0;
    }
    public int NPCount() {
        return 0;
    }

    public static CompactTrieKeySlice getSharedPath() {
        return (CompactTrieKeySlice) CompactTrieKeySlice.emptyStatic();
        //return (CompactTrieKeySlice) empty();
    }

    public Set<ByteArrayWrapper> collectKeys(int byteSize) {
        return null;
    }
    public AbstractIndexTrie getLeft() {
        return null;
    }

    public AbstractIndexTrie getRight() {
        return null;
    }

    public int nodeCount() {
        return 1;
    }
}
