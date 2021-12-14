package co.rsk.tools.processor.TrieUtils;

public interface TrieKeySlice {

    public TrieKeySlice clone();
    public byte get(int i);
    public byte[] encode();
    public TrieKeySlice slice(int from, int to);
    public TrieKeySlice commonPath(TrieKeySlice other);
    public TrieKeySlice appendBit(byte implicitByte);
    public TrieKeySlice append(TrieKeySlice childSharedPath);
    public TrieKeySlice rebuildSharedPath(byte implicitByte, TrieKeySlice childSharedPath);
    public TrieKeySlice leftPad(int paddingLength);
    public int length();

    // These methods create new objects, same as static methods
    public TrieKeySlice fromKey(byte[] key);
    //public TrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength);
    //public TrieKeySlice empty();

}
