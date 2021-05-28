package co.rsk.tools.processor.cindex;

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

    public byte[] getSharedPath() {
        return null;
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
