package co.rsk.tools.processor.cindex;

public class IndexTrieCPV  extends IndexTrie {
    protected final byte[] sharedPath;
    protected final int value;
    protected final IndexTrie left;
    protected final IndexTrie right;


    public IndexTrieCPV() {
        this( PackedTrieKeySlice.empty(), nullValue);
    }

    private IndexTrieCPV(byte[] sharedPath, int value) {

        this( sharedPath, value, null, null);
    }

    // full constructor
    protected IndexTrieCPV(byte[] sharedPath, int value,
                           IndexTrie left, IndexTrie right) {
        this.value = value;
        this.sharedPath = sharedPath;
        this.right = right;
        this.left = left;

        //if (sharedPath.length()==0)
        //    throw new RuntimeException("should use IndexTrie");
    }

    public byte[] getSharedPath() {
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
    public int getValue() {
        return value;
    }
}
