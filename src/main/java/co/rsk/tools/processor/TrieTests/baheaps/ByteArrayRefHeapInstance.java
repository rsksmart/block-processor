package co.rsk.tools.processor.TrieTests.baheaps;

import co.rsk.tools.processor.TrieTests.baheaps.ByteArrayRefHeap;

public class ByteArrayRefHeapInstance {

    static ByteArrayRefHeap objectHeap;

    public static ByteArrayRefHeap get() {
        if (objectHeap == null)
            objectHeap = new ByteArrayRefHeap();

        return objectHeap;
    }


}
