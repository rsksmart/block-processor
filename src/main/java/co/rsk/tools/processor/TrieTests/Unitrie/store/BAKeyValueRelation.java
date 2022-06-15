package co.rsk.tools.processor.TrieTests.Unitrie.store;

import org.ethereum.db.ByteArrayWrapper;

public interface BAKeyValueRelation {
    int getHashcode(ByteArrayWrapper var1);

    ByteArrayWrapper getKeyFromData(byte[] data);
}
