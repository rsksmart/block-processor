package co.rsk.tools.processor.TrieTests.Unitrie.store;

public class MaxSizeCAHashMap<K,V> extends CAHashMap<K,V> {
    long latestPriorty;
    public MaxSizeCAHashMap(int initialCapacity, float loadFactor,
                     KeyValueRelation<K,V> keyValueRelation) {
        super(initialCapacity,loadFactor,keyValueRelation);
        latestPriorty=0;
    }

    void afterNodeAccess(CAHashMap.Node<K, V> p) {
        keyValueRelation.afterNodeAccess(p.getValue());
    }

    void afterNodeInsertion(CAHashMap.Node<K, V> p,boolean evict) {
        keyValueRelation.afterNodeInsertion(this, p.getValue(),evict,latestPriorty++);
    }

}
