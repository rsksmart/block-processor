package co.rsk.tools.processor.TrieUtils;

public interface TrieKeySliceFactory {
    // fromKey() receives the key in bit-packed format.
    // key length will always be a multiple of 8.
    public TrieKeySlice fromKey(byte[] key);

    // creates a KeySlice from an bit-packed array src, starting at a
    // byte-offset byteOffset offset.
    // The resulting key length need not be a mulitple of 8, because
    // encodedLength represents the size of the src array (in bytes)
    // THIS METHOD IS DEPRECATED, because it has an argument that is not
    // necessary.
    //public TrieKeySlice fromEncoded(byte[] src, int byteOffset, int keyLength, int encodedLength);

    public TrieKeySlice fromEncoded(byte[] encodedKey, int bitoffset, int bitLength);


        public TrieKeySlice empty();

}
