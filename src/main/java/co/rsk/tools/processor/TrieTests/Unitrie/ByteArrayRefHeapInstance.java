package co.rsk.tools.processor.TrieTests.Unitrie;

public class ByteArrayRefHeapInstance {

    static ByteArrayRefHeap objectHeap;

    public static ByteArrayRefHeap get() {
        if (objectHeap == null)
            objectHeap = new ByteArrayRefHeap();

        return objectHeap;
    }


}
