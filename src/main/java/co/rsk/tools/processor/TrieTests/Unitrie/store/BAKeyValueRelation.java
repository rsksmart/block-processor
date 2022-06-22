package co.rsk.tools.processor.TrieTests.Unitrie.store;


public interface BAKeyValueRelation {
    int getHashcode(byte[] var1);

    byte[] computeKey(byte[] data);

    int getKeySize();
}
