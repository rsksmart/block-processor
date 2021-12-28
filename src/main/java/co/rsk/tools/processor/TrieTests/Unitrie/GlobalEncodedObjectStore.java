package co.rsk.tools.processor.TrieTests.Unitrie;

public class GlobalEncodedObjectStore {
    static EncodedObjectStore encodedObjectStore;

    public static void set(EncodedObjectStore aencodedObjectStore) {
        encodedObjectStore = aencodedObjectStore;
    }

    public static EncodedObjectStore get() {
        if (encodedObjectStore == null) {
            //encodedObjectStore = new SoftRefEncodedObjectStore();
            //encodedObjectStore = new EncodedObjectHeap();
            //encodedObjectStore = new EncodedObjectHashMap();
            //encodedObjectStore = null;
            //encodedObjectStore = new HardEncodedObjectStore();
            //encodedObjectStore = new MultiSoftEncodedObjectStore();
        }
        return encodedObjectStore;
    }
}
