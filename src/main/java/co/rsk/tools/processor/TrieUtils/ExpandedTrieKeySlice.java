package co.rsk.tools.processor.TrieUtils;

import java.util.Arrays;

/**
 * An immutable slice of a trie key.
 * Sub-slices share array references, so external sources are copied and the internal array is not exposed.
 */
public class ExpandedTrieKeySlice implements TrieKeySlice, TrieKeySliceFactory {
    private final byte[] expandedKey;
    private final int offset;
    private final int limit;

    private ExpandedTrieKeySlice(byte[] expandedKey, int offset, int limit) {
        this.expandedKey = expandedKey;
        this.offset = offset;
        this.limit = limit;
    }

    public TrieKeySlice fromDecoded(byte[] decodedKey, int bitoffset, int bitLength) {
        // create with reference
        TrieKeySlice ret = new ExpandedTrieKeySlice(decodedKey,bitoffset,bitoffset+bitLength);
        // clone to make it unique
        return ret.clone();
    }

    public TrieKeySlice clone() {
        return new ExpandedTrieKeySlice(Arrays.copyOfRange(expandedKey, offset, limit),0,length());
    }

    public int length() {
        return limit - offset;
    }

    public byte get(int i) {
        return expandedKey[offset + i];
    }

    public byte[] expand() {
        return Arrays.copyOfRange(expandedKey, offset, limit);
    }

    public byte[] encode() {
        // TODO(mc) avoid copying by passing the indices to PathEncoder.encode
        return PathEncoder.encode(Arrays.copyOfRange(expandedKey, offset, limit));
    }

    public TrieKeySlice slice(int from, int to) {
        if (from < 0) {
            throw new IllegalArgumentException("The start position must not be lower than 0");
        }

        if (from > to) {
            throw new IllegalArgumentException("The start position must not be greater than the end position");
        }

        int newOffset = offset + from;
        if (newOffset > limit) {
            throw new IllegalArgumentException("The start position must not exceed the key length");
        }

        int newLimit = offset + to;
        if (newLimit > limit) {
            throw new IllegalArgumentException("The end position must not exceed the key length");
        }

        return new ExpandedTrieKeySlice(expandedKey, newOffset, newLimit);
    }

    public TrieKeySlice commonPath(TrieKeySlice other) {
        int maxCommonLengthPossible = Math.min(length(), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(i) != other.get(i)) {
                return slice(0, i);
            }
        }

        return slice(0, maxCommonLengthPossible);
    }

    /**
     * Rebuild a shared path as [...this, implicitByte, ...childSharedPath]
     */
    public TrieKeySlice rebuildSharedPath(byte implicitByte, TrieKeySlice xchildSharedPath) {
        ExpandedTrieKeySlice childSharedPath = (ExpandedTrieKeySlice) xchildSharedPath;
        int length = length();
        int childSharedPathLength = childSharedPath.length();
        int newLength = length + 1 + childSharedPathLength;
        byte[] newExpandedKey = Arrays.copyOfRange(expandedKey, offset, offset + newLength);
        newExpandedKey[length] = implicitByte;
        System.arraycopy(
                childSharedPath.expandedKey, childSharedPath.offset,
                newExpandedKey, length + 1, childSharedPathLength
        );
        return new ExpandedTrieKeySlice(newExpandedKey, 0, newExpandedKey.length);
    }

    public TrieKeySlice append(TrieKeySlice xchildSharedPath) {
        ExpandedTrieKeySlice childSharedPath = (ExpandedTrieKeySlice) xchildSharedPath;
        int length = length();
        int childSharedPathLength = childSharedPath.length();
        int newLength = length + childSharedPathLength;
        byte[] newExpandedKey = Arrays.copyOfRange(expandedKey, offset, offset + newLength);
        System.arraycopy(
                childSharedPath.expandedKey, childSharedPath.offset,
                newExpandedKey, length , childSharedPathLength
        );
        return new ExpandedTrieKeySlice(newExpandedKey, 0, newExpandedKey.length);
    }

    public TrieKeySlice appendBit(byte implicitByte) {
        int length = length();
        int newLength = length + 1;
        byte[] newExpandedKey = Arrays.copyOfRange(expandedKey, offset, offset + newLength);
        newExpandedKey[length] = implicitByte;
        return new ExpandedTrieKeySlice(newExpandedKey,0,newLength);
    }

    public TrieKeySlice leftPad(int paddingLength) {
        if (paddingLength == 0) {
            return this;
        }
        int currentLength = length();
        byte[] paddedExpandedKey = new byte[currentLength + paddingLength];
        System.arraycopy(expandedKey, offset, paddedExpandedKey, paddingLength, currentLength);
        return new ExpandedTrieKeySlice(paddedExpandedKey, 0, paddedExpandedKey.length);
    }

    public  TrieKeySlice fromKey(byte[] key) {
        byte[] expandedKey = PathEncoder.decode(key, key.length * 8);
        return new ExpandedTrieKeySlice(expandedKey, 0, expandedKey.length);
    }

    /*public TrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength) {
        // TODO(mc) avoid copying by passing the indices to PathEncoder.decode
        byte[] encodedKey = Arrays.copyOfRange(src, offset, offset + encodedLength);
        byte[] expandedKey = PathEncoder.decode(encodedKey, keyLength);
        return new ExpandedTrieKeySlice(expandedKey, 0, expandedKey.length);
    }*/

    public TrieKeySlice fromEncoded(byte[] encodedKey, int bitoffset, int bitLength) {
        byte[] expandedKey = PathEncoder.decode(encodedKey, bitoffset,bitLength);
        return new ExpandedTrieKeySlice(expandedKey, 0, expandedKey.length);
    }

    static ExpandedTrieKeySlice emptyTrie = new ExpandedTrieKeySlice(new byte[0], 0, 0);

    public TrieKeySlice empty() {
        return emptyTrie;
    }

    public static TrieKeySliceFactory getFactory() {
        return emptyTrie;
    }

    public String toString() {
        String s ="";
        for (int i = 0; i < length(); i++) {
            if (get(i)==1) {
                s += "1";
            }   else {
                s += "0";
            }
        }
        return s;
    }

    public ExpandedTrieKeySlice revert() {
        int len =length();
        byte[] newKey = new byte[len];

        for (int i = 0; i < length(); i++) {
            newKey[len-i-1] = expandedKey[offset + i];
        }
        ExpandedTrieKeySlice result = new ExpandedTrieKeySlice(
                newKey ,0,len);
        return result;
    }
}
