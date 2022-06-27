package co.rsk.tools.processor.TrieTests.bahashmaps;


import co.rsk.tools.processor.TrieTests.baheaps.*;
import java.util.*;



public class ByteArrayRefHashMap extends ByteArray32HashMap  {


    public ByteArrayRefHashMap(int initialCapacity, float loadFactor,
                               BAKeyValueRelation BAKeyValueRelation,
                               long newBeHeapCapacity,
                               AbstractByteArrayHeap sharedBaHeap,
                               int maxElements,
                               Format format) {
        // Here wwe must check if we need to change the initialCapacity
        // because of the indirection
        super(initialCapacity, loadFactor, BAKeyValueRelation, newBeHeapCapacity,
            sharedBaHeap, maxElements,format);
    }

    AbstractByteArrayHeap createByteArrayHeap(long initialCapacity, long newBeHeapCapacity)  {
        ByteArrayRefHeap baHeap = new ByteArrayRefHeap();
        baHeap.setMaxMemory(newBeHeapCapacity); //730_000_000L); // 500 Mb / 1 GB
        if (maxElements>0) {
            // The current implementation inserts maxElements+1 and then immediately
            // removes elements, so there is a (short) time where there is
            // 1 more handle requested. That's why the "+1".
            // 2* for testing only
            baHeap.setMaxReferences(2*maxElements + 1);
        }
        else {
            int expectedReferences = (int) (initialCapacity*loadFactor+1);
            baHeap.setMaxReferences(expectedReferences);
        }
        baHeap.initialize();
        AbstractByteArrayHeap bah = new ByteArrayHeapRefProxy(baHeap);
        return bah;

    }
    /*
    public ByteArrayRefHashMap(int initialCapacity, BAWrappedKeyValueRelation BAKeyValueRelation) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, BAKeyValueRelation,defaultNewBeHeapCapacity,null,0);
    }
     */


}

