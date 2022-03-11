package co.rsk.tools.processor.TrieUtils;

//import co.rsk.trie.PathEncoder;

import org.ethereum.util.ByteUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class CompactTrieKeySlice implements TrieKeySlice, TrieKeySliceFactory {
    // Always store in maximally expanded format
    private final byte[] compactKey;
    private short offset;
    private short limit;

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

    public byte[] expand() {
        return PathEncoder.decode(compactKey,offset,length());
    }

    public byte[] encode() {
        // TODO(mc) avoid copying by passing the indices to PathEncoder.encode
        return PathEncoder.recode(compactKey, offset, length(),length());
    }

    public void selfSlice(int from, int to) {

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
        this.offset = (short) newOffset;
        this.limit = (short) newLimit;
        //

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

    public TrieKeySlice commonPath1(CompactTrieKeySlice other) {
        short maxCommonLengthPossible = (short) Math.min(length(), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(i) != other.get(i)) {
                return slice(0, i);
            }
        }


        return slice((short) 0, maxCommonLengthPossible);
    }

    public TrieKeySlice commonPath(CompactTrieKeySlice other) {
        int c = getCommonPathLength(other);
        return slice(0, c);
    }

    public int getCommonPathLength(TrieKeySlice other) {
      return getCommonPathLength( (CompactTrieKeySlice) other);
    }

    public int getCommonPathLength(CompactTrieKeySlice other) {
        short maxCommonLengthPossible = (short) Math.min(length(), other.length());
        int ofs =0;


        while (true) {
            int rest = maxCommonLengthPossible-ofs;
            if (rest<2) break; // faster bit-by-bit

            if (rest>57)
                rest = 57;
            long a = getBitSeqAsLong(compactKey,offset +ofs,rest);
            long b = getBitSeqAsLong(other.compactKey,other.offset +ofs,rest);
            if (a!=b)
                break; // go to the bit level
            ofs +=rest;
        }

        for (int i = ofs; i < maxCommonLengthPossible; i++) {

            int ax = get(i);
            int bx = other.get(i);

            if (ax != bx) {
                return i+ofs;
            }
        }


        return maxCommonLengthPossible;
    }

    /*
     * bytes: byte array, with the bits indexed from 0 (MSB) to (bytes.length * 8 - 1) (LSB)
     * offset: index of the MSB of the bit sequence.
     * len: length of bit sequence, must from range [0,16].
     * Not checked for overflow
     */

    static long getBitSeqAsLong(byte[] bytes, int offset, int len){

        int byteIndex = offset / 8;
        int bitIndex = offset % 8;
        long val =0;
        int add =0;
        int count =0;
        int bitsNeeded = len+bitIndex;
        while (bitsNeeded>0) {
            val = (val<<8) | (bytes[byteIndex] & 0xFF);
            add +=8;
            bitsNeeded -=8;
            byteIndex++;
            count++;
        }
        if (count>8)
            throw new RuntimeException("cannot pack more than 8 bytes");

        long mask = (1L<<len)-1;
        int remove = (add-bitIndex-len);
        val = (val  >> remove) & mask;
        return val;
    }


    public TrieKeySlice commonPath(TrieKeySlice other) {
        return commonPath((CompactTrieKeySlice) other);
    }

    public TrieKeySlice commonPath2(TrieKeySlice other) {
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

    public TrieKeySlice fromDecoded(byte[] decodedKey, int bitoffset, int bitLength) {
        byte[] compactKey = PathEncoder.encode(decodedKey,bitoffset,bitLength);
        return new CompactTrieKeySlice(compactKey, 0, bitLength);
    }

    public TrieKeySlice fromKey(byte[] key) {
        byte[] compactKey = PathEncoder.cloneEncoding(key);
        return new CompactTrieKeySlice(compactKey, 0, compactKey.length*8);
    }

    public TrieKeySlice fromEncoded(byte[] encodedKey, int bitOffset, int bitLength) {
        int byteOffset = bitOffset/8;
        int byteLength = (bitOffset+bitLength+7)/8;
        byte[] recodedKey = Arrays.copyOfRange(encodedKey, byteOffset, byteOffset + byteLength);
        int newBitOffset = bitOffset % 8;
        return new CompactTrieKeySlice(recodedKey, newBitOffset, newBitOffset+bitLength);

    }

    /* DEPRECATED
    public TrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength) {
        byte[] encodedKey = Arrays.copyOfRange(src, offset, offset + encodedLength);
        return new CompactTrieKeySlice(encodedKey, 0, encodedKey.length*8);
    }
    */


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
