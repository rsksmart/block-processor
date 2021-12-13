package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.Index.CompactTrieKeySlice;
import co.rsk.tools.processor.Index.TrieKeySliceFactory;

public class TrieKeySliceFactoryInstance {
    private static final TrieKeySliceFactory trieKeySliceFactory = CompactTrieKeySlice.getFactory();

    public static TrieKeySliceFactory get() {
        return  trieKeySliceFactory;
    }
}
