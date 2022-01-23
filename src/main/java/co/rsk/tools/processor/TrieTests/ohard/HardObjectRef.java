package co.rsk.tools.processor.TrieTests.ohard;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.GlobalClock;


public class HardObjectRef extends EncodedObjectRef {

    byte[] encoded;
    // we add a timestamp to represent a field that a garbage collector can use to decide what nodes
    // can be disposed. In the future, with storage rent, the timestamp will be inside the encoded node, and
    // this field will be removed. The space consumed will be the same, so all current tests can use this timestamp
    // without problem.
    // We'll set the timestamp with the value of a globalClock that the code can change at will. with storage rent,
    // put operations will have an additional timeStamp parameter. As we don't want to completely change the Trie
    // interface now, we stick with the hack of using this globalClock in the tests.
    // Finally, the timestamp in storage rent will be compressed, and here we can't. This is only a minor difference
    // in sizes.
    int timeStamp;
    boolean saved;
    EncodedObjectRef leftRef;
    EncodedObjectRef rightRef;

    public HardObjectRef(byte[] encoded, EncodedObjectRef leftOfs, EncodedObjectRef rightOfs,boolean saved) {
        this.encoded = encoded;
        this.leftRef = leftOfs;
        this.rightRef = rightOfs;
        this.timeStamp = GlobalClock.getTimestamp();
        this.saved = saved;
    }
}
