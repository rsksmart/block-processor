package co.rsk.tools.processor.TrieTests.Unitrie.store;

import org.ethereum.db.ByteArrayWrapper;

public interface BAWrappedKeyValueRelation {
    int getHashcode(ByteArrayWrapper var1);

    ByteArrayWrapper computeWrappedKey(byte[] data);

    int getKeySize();
}
