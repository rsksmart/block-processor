package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.store.ByteArrayHashMap;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;

public class MyBAKeyValueRelation implements ByteArrayHashMap.BAKeyValueRelation {
    public int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }

    public int hashCodeFromHashDigest(byte[] bytes) {
        // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
        return intFromBytes(bytes[28], bytes[29], bytes[30], bytes[31]);
    }

    @Override
    public int getHashcode(ByteArrayWrapper key) {
        return hashCodeFromHashDigest(key.getData());
    }

    @Override
    public ByteArrayWrapper getKeyFromData(byte[] data) {
        return new ByteArrayWrapper(Keccak256Helper.keccak256(data));
    }

}
