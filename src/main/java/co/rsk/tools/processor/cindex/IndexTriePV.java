package co.rsk.tools.processor.cindex;

public class IndexTriePV  extends IndexTrie {
    protected final byte[] sharedPath;
    protected final int value;

    public IndexTriePV() {
        this( PackedTrieKeySlice.empty(), nullValue);
    }

    public IndexTriePV(byte[] sharedPath, int value) {

        this.sharedPath = sharedPath;
        if (PackedTrieKeySlice.length(sharedPath)==0)
            throw new RuntimeException("should use IndexTrie");
        this.value = value;
    }

    public byte[] getSharedPath() {
        return sharedPath;
    }

    public boolean hasPath() {
        return (PackedTrieKeySlice.length(sharedPath)!=0);
    }

    public int getValue() {
        return value;
    }



}
