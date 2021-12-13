package co.rsk.tools.processor.Index;

public class IndexTrieCPV  extends IndexTrie {
    protected final TrieKeySlice sharedPath;
    protected final int value;
    protected final IndexTrie left;
    protected final IndexTrie right;


    public IndexTrieCPV() {
        this( trieKeySliceFactory.empty(), nullValue);
    }

    private IndexTrieCPV(TrieKeySlice sharedPath, int value) {

        this( sharedPath, value, null, null);
    }

    // full constructor
    protected IndexTrieCPV(TrieKeySlice sharedPath, int value,
                           IndexTrie left, IndexTrie right) {
        this.value = value;
        this.sharedPath = sharedPath;
        this.right = right;
        this.left = left;

        //if (sharedPath.length()==0)
        //    throw new RuntimeException("should use IndexTrie");
    }

    public TrieKeySlice getSharedPath() {
        return sharedPath;
    }
    public boolean hasPath() {
        return (sharedPath.length()!=0);
    }

    public IndexTrie getLeft() {
        return left;
    }

    public IndexTrie getRight() {
        return right;
    }
    public int getValue() {
        return value;
    }
}
