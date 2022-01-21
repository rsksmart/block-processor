package co.rsk.tools.processor.TrieTests.Unitrie;

public class GlobalClock {
    // This timestamp is used to mark trie nodes depending on their age and simulate a gabrgabe collection
    // based on age. In the future, the Trie will support put operations that pass a timestamp as an
    // additional argument.

    static int timestamp;

    static public int getTimestamp() {
        return timestamp;
    }

    static public void setTimestamp(int aTimestamp) {
        timestamp = aTimestamp;
    }
}
