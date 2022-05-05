package co.rsk.tools.processor.TrieTests.Unitrie.DNC2;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.Unitrie.NodeReference;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieImpl;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieStore;
import co.rsk.tools.processor.TrieTests.Unitrie.VarInt;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class TrieWithDNC extends TrieImpl {

    long decodedRef;

    // full constructor
    protected TrieWithDNC(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                          NodeReference left, NodeReference right,
                          Uint24 valueLength, Keccak256 valueHash,
                          VarInt childrenSize,
                          boolean isEmbedded, long aDecodedRef) {
        super(store, sharedPath, value,
                left,  right, valueLength, valueHash,  childrenSize,   isEmbedded);
        this.decodedRef = aDecodedRef;


    }

    public long getDecodedRef() {
        return decodedRef;
    }
}
