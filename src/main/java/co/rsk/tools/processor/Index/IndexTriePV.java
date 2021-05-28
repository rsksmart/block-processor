package co.rsk.tools.processor.Index;

public class IndexTriePV  extends IndexTrie {
    protected final CompactTrieKeySlice sharedPath;
    protected final int value;

    public IndexTriePV() {
        this( CompactTrieKeySlice.empty(), nullValue);
    }

    public IndexTriePV(CompactTrieKeySlice sharedPath, int value) {

        this.sharedPath = sharedPath;
        if (sharedPath.length()==0)
            throw new RuntimeException("should use IndexTrie");
        this.value = value;
    }

    public CompactTrieKeySlice getSharedPath() {
        return sharedPath;
    }

    public boolean hasPath() {
        return (sharedPath.length()!=0);
    }

    public int getValue() {
        return value;
    }



}
