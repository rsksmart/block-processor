package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieUtils.ExpandedTrieKeySlice;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import co.rsk.tools.processor.examples.storage.ObjectIO;

public class TrieTestUtils {
    static public void fillWithCounter(byte[] key,int log2Bits,int fixKeySize,long i) {
        // Less significant byte first
        if (log2Bits>0)
            key[fixKeySize+0] = (byte) (i & 0xff);
        if (log2Bits>8)
            key[fixKeySize+1] = (byte) ((i >> 8) & 0xff);
        if (log2Bits>16)
            key[fixKeySize+2] = (byte) ((i >> 16) & 0xff);
        if (log2Bits>24)
            key[fixKeySize+3] = (byte) ((i >> 24) & 0xff);

        rotateLastCounterByte(key,log2Bits,fixKeySize);
    }

    static public void rotateLastCounterByte(byte[] key,int log2Bits,int fixKeySize) {
        // now we need to mask out the MSB to fill them with zeros

        if (log2Bits % 8 != 0) {
            int extraBits = 8 - (log2Bits % 8);
            // first we move the LSBs that were filled by the counter to the MSBs
            int lastByteIdx = log2Bits / 8;
            key[fixKeySize + lastByteIdx] <<= extraBits;
        }
    }

    static public void fillExtraBytesWithRandom(PseudoRandom pseudoRandom,byte[] key,int log2Bits,int fixKeySize,int varKeySize) {
        // now we need to mask out the MSB to fill them with zeros

        if (log2Bits % 8 !=0) {
            int extraBits = 8-(log2Bits % 8);
            // first we move the LSBs that were filled by the counter to the MSBs
            int lastByteIdx = log2Bits/8;

            // Within a byte, to form a trie key, bits are chosen from MSB, therefore
            // fillRandomBits will generally fill the LSBs, not the MSBs.
            pseudoRandom.fillRandomBits(key, fixKeySize*8 + 8-extraBits,extraBits);
        }
        int extraBytes = (log2Bits+7)/8;
        pseudoRandom.fillRandomBytes(key,fixKeySize+extraBytes,varKeySize-extraBytes);
    }

    static public TrieKeySlice getTrieKeySliceWithCounter(long x, int counterBits,int fixKeySize) {
        int treeCounter = (int) x;
        boolean useMSBput = false;

        byte[] counter = new byte[4];

        if (useMSBput) {
            // The counter must fill most significant bits, but not the last byte
            // least significant bits.
            int rotateBits;
            if (counterBits % 8 == 0)
                rotateBits = 0;
            else
                rotateBits = 8 - counterBits % 8;
            // So I will rotate the treeCounter n bits (loosing the MSBs of treeCounter,
            // that are not used
            // Most significant byte first
            ObjectIO.putInt(counter, 0, treeCounter << rotateBits);
        } else {
            // this is another way of doing it, similar to the way used to create
            // the keys.
            ObjectIO.putIntLittleEndian(counter, fixKeySize, treeCounter);
            // Now the most significant bits are in the last byte MSBs.
            // we have to rotate only last byte.
            if (counterBits % 8 !=0) {
                int extraBits = 8 - (counterBits % 8);
                // first we move the LSBs that were filled by the counter to the MSBs
                int lastByteIdx = counterBits / 8;
                counter[fixKeySize+lastByteIdx] <<= extraBits;
            }

        }
        // Now I create an expanded key from those bits
        ExpandedTrieKeySlice slice = (ExpandedTrieKeySlice)
                ExpandedTrieKeySlice.getFactory().
                        fromEncoded(counter, 0, counterBits);
        return slice;
    }

}
