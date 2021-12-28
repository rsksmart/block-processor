package co.rsk.tools.processor.TrieTests.ohmap;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;

public class HashEOR extends EncodedObjectRef {
    Keccak256 hash;

    public HashEOR(Keccak256 hash) {
        this.hash = hash;
    }
}
