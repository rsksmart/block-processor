package co.rsk.tools.processor.Index;

public class IndexTriePV  extends IndexTrie {
    protected final TrieKeySlice sharedPath;
    protected final int value;

    public IndexTriePV() {
        this( trieKeySliceFactory.empty(), nullValue);
    }

    public IndexTriePV(TrieKeySlice sharedPath, int value) {

        this.sharedPath = sharedPath;
        if (sharedPath.length()==0)
            throw new RuntimeException("should use IndexTrie");
        this.value = value;
    }

    public TrieKeySlice getSharedPath() {
        return sharedPath;
    }

    public boolean hasPath() {
        return (sharedPath.length()!=0);
    }

    public int getValue() {
        return value;
    }



}
