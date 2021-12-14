package co.rsk.tools.processor.Index;

import co.rsk.tools.processor.TrieUtils.TrieKeySlice;

public class IndexTrieCP extends IndexTrie {

    protected final TrieKeySlice sharedPath;
    protected final IndexTrie left;
    protected final IndexTrie right;

    public IndexTrieCP() {
        this( trieKeySliceFactory.empty());
    }

    private IndexTrieCP(TrieKeySlice sharedPath) {
        this( sharedPath, null, null);
    }

    // full constructor
    protected IndexTrieCP(TrieKeySlice sharedPath,
                          IndexTrie left, IndexTrie right) {
        this.sharedPath = sharedPath;
        if (sharedPath.length()==0)
            throw new RuntimeException("should use IndexTrie");
        this.right = right;
        this.left = left;
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


}
