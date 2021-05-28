package co.rsk.tools.processor.cindex;


//import co.rsk.trie.PathEncoder;

import co.rsk.tools.processor.examples.ObjectIO;

import java.util.Arrays;

public class PackedTrieKeySlice  {
    // Always store in maximally expanded format
    private static final int pkIndex = 2;
    private static final int lenIndex =0;

    public PackedTrieKeySlice () {
    }

    static void putShort(byte[] b, int off, short val) {
        b[off + 1] = (byte) (val      );
        b[off    ] = (byte) (val >>> 8);
    }
    public static void setLength(byte[] self,int len) {
        putShort(self,lenIndex, (short) len);
    }

    public static byte[] newPk(byte[] self,int offset,int limit) {
        byte[] ret  = new byte[pkIndex+(limit-offset+7)/8];
        PathEncoder.recodeBinaryPathFromTo(self,pkIndex,offset,limit-offset,
                ret,pkIndex,0);
        setLength(ret,limit-offset);
        return ret;
    }
    public static byte[] clone(byte[] packedKey) {
        return packedKey.clone();
    }
    
    static short getShort(byte[] b, int off) {
        return (short) ((b[off + 1] & 0xFF) +
                (b[off] << 8));
    }

    public static int length(byte[] self) {
        return getShort(self,lenIndex);
    }


    public static boolean bitSet(byte[] self,int i) {
        // First bit is most significant
        int bitofs =  i;
        return (self[(bitofs>>3)+pkIndex] & (0x80>>(bitofs & 0x7)))!=0;
    }

    public static byte get(byte[] self,int i) {
        if (bitSet(self,i))
            return 1;
        else
            return 0;
    }

    public static int packedByteLength(byte[] self) {
        return pkIndex+(length(self)+7)/8;
    }

    public static int encodedByteLength(byte[] self) {
        return pkIndex+(length(self)+7)/8;
    }
    public static byte[] encode(byte[] self) {
        byte[] ret = new byte[encodedByteLength(self)];
        PathEncoder.recodeBinaryPathFromTo(self, 0,0, length(self),
                ret, 0,0);
        return ret;
    }

    public static /*pk*/ byte[]  slice(byte[] self,int from, int to) {
        if (from==to) // SDL performance fix
            return empty();
        if (from < 0) {
            throw new IllegalArgumentException("The start position must not be lower than 0");
        }

        if (from > to) {
            throw new IllegalArgumentException("The start position must not be greater than the end position");
        }
        if (from > length(self)) {
            throw new IllegalArgumentException("The start position must not exceed the key length");
        }

        if (to > length((self))) {
            throw new IllegalArgumentException("The end position must not exceed the key length");
        }

        //
        return newPk(self,from,to);
    }

    public static /*pk*/ byte[]  commonPath(byte[] self,/*pk*/ byte[]  other) {
        short maxCommonLengthPossible = (short) Math.min(length(self), length(other));
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(self,i) != get(other,i)) {
                return slice(self,0, i);
            }
        }


        return slice(self,(short) 0, maxCommonLengthPossible);
    }
    public static /*pk*/ byte[]  commonPath(byte[] self,co.rsk.trie.TrieKeySlice other) {
        int maxCommonLengthPossible = Math.min(length(self), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(self,i) != other.get(i)) {
                return slice(self,0, i);
            }
        }


        return slice(self,0, maxCommonLengthPossible);
    }

    public static /*pk*/ byte[]  appendBit(byte[] self,byte implicitByte) {
        int length = length(self);
        /* todo
        byte[] newCompactKey = PathEncoder.recode(compactKey,0,length,length+1);
        PathEncoder.encodeBit(newCompactKey,limit,implicitByte);
        return new  byte[] (newCompactKey,0,length+1);
        */
        return null;
    }

    public static /*pk*/ byte[]  append(byte[] self,/*pk*/ byte[]  childSharedPath) {
        int childSharedPathLength = length(childSharedPath);
        if (childSharedPathLength==0) return self;
        int length = length(self);

        int newLength = length + childSharedPathLength;

        byte[] newcompactKey = new byte[pkIndex+(newLength+7)/8];
        PathEncoder.recodeBinaryPathFromTo(
                self,pkIndex,0,length,
                newcompactKey,pkIndex,0);
        PathEncoder.recodeBinaryPathFromTo(
                childSharedPath,pkIndex,0,length(childSharedPath),
                newcompactKey,pkIndex,length);

        setLength(self,newLength);
        return newcompactKey;
    }

    /**
     * Rebuild a shared path as [...this, implicitByte, ...childSharedPath]
     */
    public static /*pk*/ byte[]  rebuildSharedPath(byte[] self,byte implicitByte, /*pk*/ byte[]  childSharedPath) {
        int length = length(self);
        int childSharedPathLength = length(childSharedPath);
        int newLength = length + 1 + childSharedPathLength;
        byte[] newcompactKey = new byte[pkIndex+(newLength+7)/8];
        PathEncoder.recodeBinaryPathFromTo(
                self,pkIndex,0,length,
                newcompactKey,pkIndex,0);
        PathEncoder.recodeBinaryPathFromTo(
                childSharedPath,pkIndex,0,childSharedPathLength,
                newcompactKey,pkIndex,length+1);
        PathEncoder.encodeBit(newcompactKey,length,implicitByte);
        setLength(newcompactKey,newLength);
        return newcompactKey;
    }

    public /*pk*/ byte[]  leftPad(byte[] self,int paddingLength) {
        if (paddingLength == 0) {
            return self;
        }
        int currentLength = length(self);
        int newLength= currentLength + paddingLength;
        byte[] paddedcompactKey = new byte[pkIndex+(newLength+7)/8];
        PathEncoder.recodeBinaryPathFromTo(
                self,pkIndex,0,currentLength,
                paddedcompactKey,pkIndex,paddingLength);
        setLength(paddedcompactKey,newLength);
        return paddedcompactKey;
    }

    public static /*pk*/ byte[] fromKey(byte[] key) {
        byte[] compactKey = new byte[pkIndex+key.length];
        setLength(compactKey,key.length*8);
        System.arraycopy(key,0,compactKey,pkIndex,key.length);
        return compactKey;
    }
/*
    public static byte[]  fromEncoded(byte[] src, int offset, int keyLength, int encodedLength) {
        byte[] encodedKey = Arrays.copyOfRange(src, offset, offset + encodedLength);
        return new  byte[] (encodedKey, 0, encodedKey.length*8);
    }
*/
    static /*pk*/ byte[]  emptyTrie = new byte[pkIndex];

    public static /*pk*/ byte[]  empty() {
        return emptyTrie;
    }

    // Start aways with a vector with enough capacity to append keys
    public static /*pk*/ byte[]  emptyWithCapacity() {
        int maxSize = (1+30+1+42)*8;
        return new byte[pkIndex+maxSize];
    }
}
