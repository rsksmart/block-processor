package co.rsk.tools.processor.TrieTests.Unitrie;

import java.util.Optional;

public class DecodedNodeCache {

    static DecodedNodeCache singleton = new DecodedNodeCache();

    static public DecodedNodeCache get() {
        return singleton;
    }

    public DecodedNodeCache() {

    }
    // for faster access, null means "not present"
    Trie retrieve(long decodedRef) {
        return null;
    }
}
