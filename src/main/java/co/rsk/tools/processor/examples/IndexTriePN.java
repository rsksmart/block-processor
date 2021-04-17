package co.rsk.tools.processor.examples;

import org.jetbrains.annotations.Nullable;

public class IndexTriePN extends IndexTrie {

    private CompactTrieKeySlice sharedPath;

    public IndexTriePN() {
        this( CompactTrieKeySlice.empty(), nullValue);
    }

    private IndexTriePN(CompactTrieKeySlice sharedPath, int value) {
        this( sharedPath, value, null, null);
    }

    // full constructor
    protected IndexTriePN(CompactTrieKeySlice sharedPath, int value, IndexTrie left, IndexTrie right) {
        super(value,left,right);

        this.sharedPath = sharedPath;
    }

    public boolean hasPath() {
        return (sharedPath.length()!=0);
    }



}
