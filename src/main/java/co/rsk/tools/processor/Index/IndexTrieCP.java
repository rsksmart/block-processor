package co.rsk.tools.processor.Index;

public class IndexTrieCP extends IndexTrie {

    protected final CompactTrieKeySlice sharedPath;
    protected final IndexTrie left;
    protected final IndexTrie right;

    public IndexTrieCP() {
        this( CompactTrieKeySlice.empty());
    }

    private IndexTrieCP(CompactTrieKeySlice sharedPath) {
        this( sharedPath, null, null);
    }

    // full constructor
    protected IndexTrieCP(CompactTrieKeySlice sharedPath,
                          IndexTrie left, IndexTrie right) {
        this.sharedPath = sharedPath;
        if (sharedPath.length()==0)
            throw new RuntimeException("should use IndexTrie");
        this.right = right;
        this.left = left;
    }


    public CompactTrieKeySlice getSharedPath() {
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


}
