package co.rsk.tools.processor.TrieTests.cahashmaps;

import co.rsk.tools.processor.TrieTests.cahashmaps.CAHashMap;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;

public class TrieCACacheRelation  implements CAHashMap.KeyValueRelation<ByteArrayWrapper,byte[]> {
    public  int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }
    public int hashCodeFromHashDigest(byte[] bytes) {
        // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
        return intFromBytes(bytes[28], bytes[29], bytes[30], bytes[31]);
    }
    @Override
    public ByteArrayWrapper getKeyFromData(byte[] data) {
        return new ByteArrayWrapper(Keccak256Helper.keccak256(data));
    }
    @Override
    public boolean isData(Object obj) {
        return obj instanceof byte[];
    }

    @Override
    public long getPriority(byte[] data) {
        return 0;
    }
    @Override
    public int getHashcode(ByteArrayWrapper  data) {
        return hashCodeFromHashDigest(data.getData());
    }

    @Override
    public void afterNodeAccess(byte[] data) {

    }

    @Override
    public void afterNodeInsertion(CAHashMap<ByteArrayWrapper, byte[]> map, byte[] data, boolean evict, long latestPriority) {

    }

    @Override
    public void afterNodeRemoval(long priority) {

    }

}
