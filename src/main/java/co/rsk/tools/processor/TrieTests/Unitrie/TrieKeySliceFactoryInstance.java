package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieUtils.CompactTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySliceFactory;

public class TrieKeySliceFactoryInstance {
    private static TrieKeySliceFactory trieKeySliceFactory = CompactTrieKeySlice.getFactory();

    public static void setTrieKeySliceFactory(TrieKeySliceFactory factory) {
        trieKeySliceFactory = factory;
    }
    public static TrieKeySliceFactory get() {
        return  trieKeySliceFactory;
    }
}
