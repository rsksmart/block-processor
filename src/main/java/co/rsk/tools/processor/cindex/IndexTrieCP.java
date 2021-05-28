package co.rsk.tools.processor.cindex;

public class IndexTrieCP extends IndexTrie {

    protected final byte[] sharedPath;
    protected final IndexTrie left;
    protected final IndexTrie right;

    public IndexTrieCP() {
        this( PackedTrieKeySlice.empty());
    }

    private IndexTrieCP(byte[] sharedPath) {
        this( sharedPath, null, null);
    }

    // full constructor
    protected IndexTrieCP(byte[] sharedPath,
                          IndexTrie left, IndexTrie right) {
        this.sharedPath = sharedPath;
        if (PackedTrieKeySlice.length(sharedPath)==0)
            throw new RuntimeException("should use IndexTrie");
        this.right = right;
        this.left = left;
    }


    public byte[]  getSharedPath() {
        return sharedPath;
    }

    public boolean hasPath() {
        return (PackedTrieKeySlice.length(sharedPath)!=0);
    }

    public IndexTrie getLeft() {
        return left;
    }
    public IndexTrie getRight() {
        return right;
    }


}
