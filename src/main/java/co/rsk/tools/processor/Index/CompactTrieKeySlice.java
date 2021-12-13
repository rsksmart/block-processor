package co.rsk.tools.processor.Index;

//import co.rsk.trie.PathEncoder;

import java.util.Arrays;

public class CompactTrieKeySlice implements TrieKeySlice, TrieKeySliceFactory {
    // Always store in maximally expanded format
    private final byte[] compactKey;
    private final short offset;
    private final short limit;

    public CompactTrieKeySlice(byte[] compactKey, int offset, int limit) {
        this.compactKey = compactKey;
        this.offset = (short) offset;
        this.limit = (short) limit;
    }

    public TrieKeySlice clone() {
        return new CompactTrieKeySlice(Arrays.copyOfRange(compactKey, offset, limit),0,length());
    }

    public CompactTrieKeySlice cloneGrowBytes() {
        return new CompactTrieKeySlice(Arrays.copyOfRange(compactKey, offset, limit),0,length());
    }

    public int length() {
        return limit - offset;
    }

    public boolean bitSet(int i) {
        // First bit is most significant
        return (compactKey[(offset + i)>>3] & (0x80>>((offset +i) & 0x7)))!=0;
    }

    public byte get(int i) {
        if (bitSet(i))
            return 1;
        else
            return 0;
    }

    public byte[] encode() {
        // TODO(mc) avoid copying by passing the indices to PathEncoder.encode
        return PathEncoder.recode(compactKey, offset, length(),length());
    }

    public TrieKeySlice slice(int from, int to) {
        if (from==to) // SDL performance fix
            return empty();
        if (from < 0) {
            throw new IllegalArgumentException("The start position must not be lower than 0");
        }

        if (from > to) {
            throw new IllegalArgumentException("The start position must not be greater than the end position");
        }

        short newOffset = (short) (offset + from);
        if (newOffset > limit) {
            throw new IllegalArgumentException("The start position must not exceed the key length");
        }

        short newLimit = (short) (offset + to);
        if (newLimit > limit) {
            throw new IllegalArgumentException("The end position must not exceed the key length");
        }

        //
        return new CompactTrieKeySlice(compactKey, newOffset, newLimit);
    }

    public TrieKeySlice commonPath(CompactTrieKeySlice other) {
        short maxCommonLengthPossible = (short) Math.min(length(), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(i) != other.get(i)) {
                return slice(0, i);
            }
        }


        return slice((short) 0, maxCommonLengthPossible);
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

    public TrieKeySlice appendBit(byte implicitByte) {
        int length = length();
        byte[] newCompactKey = PathEncoder.recode(compactKey,0,length,length+1);
        PathEncoder.encodeBit(newCompactKey,limit,implicitByte);
        return new CompactTrieKeySlice(newCompactKey,0,length+1);
    }

    public TrieKeySlice append(TrieKeySlice childSharedPath) {
        int childSharedPathLength = childSharedPath.length();
        if (childSharedPathLength==0) return this;
        int length = length();

        int newLength = length + childSharedPathLength;

        byte[] newcompactKey = new byte[(newLength+7)/8];
        PathEncoder.recodeBinaryPathFromTo(
                this.compactKey,0,this.offset,this.length(),
                newcompactKey,0,0);

        CompactTrieKeySlice childSharedPathCmp = ((CompactTrieKeySlice) childSharedPath);

        PathEncoder.recodeBinaryPathFromTo(
                childSharedPathCmp.compactKey,0,childSharedPathCmp.offset,childSharedPath.length(),
                newcompactKey,0,length);

        return new CompactTrieKeySlice(newcompactKey, 0, newLength);
    }

    /**
     * Rebuild a shared path as [...this, implicitByte, ...childSharedPath]
     */
    public TrieKeySlice rebuildSharedPath(byte implicitByte, TrieKeySlice childSharedPath) {
        int length = length();
        int childSharedPathLength = childSharedPath.length();
        int newLength = length + 1 + childSharedPathLength;
        byte[] newcompactKey = new byte[(newLength+7)/8];
        PathEncoder.recodeBinaryPathFromTo(
                this.compactKey,0,this.offset,this.length(),
                newcompactKey,0,0);

        CompactTrieKeySlice childSharedPathCmp = ((CompactTrieKeySlice) childSharedPath);

        PathEncoder.recodeBinaryPathFromTo(
                childSharedPathCmp.compactKey,0,childSharedPathCmp.offset,childSharedPath.length(),
                newcompactKey,0,length+1);
        PathEncoder.encodeBit(newcompactKey,length,implicitByte);

        return new CompactTrieKeySlice(newcompactKey, 0, newLength);
    }

    public TrieKeySlice leftPad(int paddingLength) {
        if (paddingLength == 0) {
            return this;
        }
        int currentLength = length();
        int newLength= currentLength + paddingLength;
        byte[] paddedcompactKey = new byte[(newLength+7)/8];
        PathEncoder.recodeBinaryPathFromTo(
                this.compactKey,0,this.offset,this.length(),
                paddedcompactKey,0,paddingLength);
        return new CompactTrieKeySlice(paddedcompactKey, 0, newLength);
    }

    public TrieKeySlice fromKey(byte[] key) {
        byte[] compactKey = PathEncoder.cloneEncoding(key);
        return new CompactTrieKeySlice(compactKey, 0, compactKey.length*8);
    }

    public TrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength) {
        byte[] encodedKey = Arrays.copyOfRange(src, offset, offset + encodedLength);
        return new CompactTrieKeySlice(encodedKey, 0, encodedKey.length*8);
    }

    static CompactTrieKeySlice emptyTrie = new CompactTrieKeySlice(new byte[0], 0, 0);

    // This inherits from Factory class
    public TrieKeySlice empty() {
        return emptyTrie;
    }

    static public TrieKeySlice emptyStatic() {
        return emptyTrie;
    }

    // Start aways with a vector with enough capacity to append keys
    public static CompactTrieKeySlice emptyWithCapacity() {
        int maxSize = (1+30+1+42)*8;
        return new CompactTrieKeySlice(new byte[maxSize], 0, 0);
    }

    public static TrieKeySliceFactory getFactory() {
        return emptyTrie;
    }
}
