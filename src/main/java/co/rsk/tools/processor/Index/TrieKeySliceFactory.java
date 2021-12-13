package co.rsk.tools.processor.Index;

public interface TrieKeySliceFactory {
    public TrieKeySlice fromKey(byte[] key);
    public TrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength);
    public TrieKeySlice empty();

}
