package co.rsk.tools.processor.TrieTests.Unitrie.store;

import co.rsk.tools.processor.TrieTests.cahashmaps.CAHashMap;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;

public class TSNodeCACacheRelation implements CAHashMap.KeyValueRelation<ByteArrayWrapper,TSNode> {
    public  int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }
    public int hashCodeFromHashDigest(byte[] bytes) {
        // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
        return intFromBytes(bytes[28], bytes[29], bytes[30], bytes[31]);
    }
    @Override
    public ByteArrayWrapper getKeyFromData(TSNode node) {
        return new ByteArrayWrapper(Keccak256Helper.keccak256(node.data));
    }
    @Override
    public boolean isData(Object obj) {
        return obj instanceof TSNode;
    }

    @Override
    public long getPriority(TSNode data) {
        return data.priority;
    }

    public void afterNodeAccess(TSNode data) {
        if (limit<=0) return;
    }

    public void afterNodeRemoval(long priority) {
        if (priority==minPriority)
            minPriority = minPriority+1;
    }
    long minPriority;

    @Override
    public void afterNodeInsertion(CAHashMap<ByteArrayWrapper,TSNode> map,TSNode data,boolean evict,long latestPriority) {
        data.priority = latestPriority;
        if (!evict)
            return;
        if (map.size()<=limit) return;

        // Remove 20%
        minPriority = minPriority+ (latestPriority-minPriority)/10;
        // With Iterator i = map.iterator(); and i.remove();
        // it should be better, but currently map does not support iterator.
        /* This is too slow because it's recomputing all the keys!
        List<ByteArrayWrapper> removal = new ArrayList<>();
        BiConsumer<ByteArrayWrapper, TSNode> collect = (k, d) -> { if (d.priority<minPriority) removal.add(k); };

        // Remove items that are
        map.forEach(collect);
        for (ByteArrayWrapper b : removal) {
            map.remove(b);
        }
        *
         */
        CAHashMap.ShouldRemove<TSNode>  shouldRemove = (d) -> (d.priority<minPriority);
        map.removeOnCondition(  shouldRemove,false);

    }

    public int getHashcode(ByteArrayWrapper data) {
        return hashCodeFromHashDigest(data.getData());
    }


    int limit;
    public TSNodeCACacheRelation(int limit) {
        this.limit = limit;
    }
}
