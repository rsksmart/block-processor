package co.rsk.tools.processor.cindex;

import java.util.Arrays;

/**
 * Created by martin.medina on 5/04/17.
 * improved by sdl
 */
public class PathEncoder {
    private PathEncoder() { }

    public static byte[] encode(byte[] path) {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }

        return encodeBinaryPath(path,0,path.length);
    }

    public static byte[] decode(byte[] encoded, int length) {
        return decode(encoded,0,length);
    }

    public static byte[] decode(byte[] encoded, int offset,int length) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded");
        }

        return decodeBinaryPath(encoded, offset, length);
    }

    public static byte[] recode(byte[] encoded, int sourceOffset,int sourceBitLength,int destBitLength) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded");
        }

        return recodeBinaryPath(encoded, sourceOffset, sourceBitLength,destBitLength);
    }

    // First bit is MOST SIGNIFICANT
    private static byte[] encodeBinaryPath(byte[] path,int soffset,int length) {
        int lpath = path.length;
        int lencoded = calculateEncodedLength(lpath);

        byte[] encoded = new byte[lencoded];
        int nbyte = 0;

        for (int k = 0; k < lpath; k++) {
            int offset = (soffset+k) % 8;
            if (k > 0 && offset == 0) {
                nbyte++;
            }

            if (path[k] == 0) {
                continue;
            }

            encoded[nbyte] |= 0x80 >> offset;
        }

        return encoded;
    }

    private static byte[] decodeBinaryPath(byte[] encoded, int bitlength) {
        return decodeBinaryPath(encoded,0,bitlength);
    }
    private static byte[] recodeBinaryPath(
        byte[] encoded, int soffset,int bitlength,int destBitLength) {
        /*if (soffset %8==0) {
            return Arrays.copyOfRange(encoded, soffset /8, (bitlength+7)/8);
        }*/
        byte[] recoded = new byte[(destBitLength+7)/8];
        recodeBinaryPathFromTo(
         encoded, 0,soffset, bitlength,
         recoded,0,0);
        return recoded;
    }

    public static byte[] cloneEncoding(byte[] encoded) {
        return Arrays.copyOf(encoded,encoded.length);
    }

    public static void encodeBit(byte[] encoded,int soffset, byte bit) {
        int nbyte = (soffset) / 8;
        int offset = (soffset) % 8;
        encoded[nbyte] |=(0x80>>offset);
    }

            // length is the length in bits. For example ({1},8) is fine
    // First bit is MOST SIGNIFICANT
    public static void recodeBinaryPathFromTo(
            byte[] encoded,
            int sByteOffset,
            int soffset,
            int bitlength,
            byte[] recoded,
            int tByteOffset,
            int toffset) {

        for (int k = 0; k < bitlength; k++) {
            int nbyte = sByteOffset+(soffset+k) / 8;
            int offset = (soffset+k) % 8;

            int dnbyte  = tByteOffset+(toffset+k) / 8;
            int doffset = (toffset+k) % 8;
            if (((encoded[nbyte] >> (7 - offset)) & 0x01) != 0) {
                recoded[dnbyte] |= 0x80 >> doffset;
            }
        }
    }

    private static byte[] decodeBinaryPath(byte[] encoded, int soffset,int bitlength) {
        byte[] path = new byte[bitlength];

        for (int k = 0; k < bitlength; k++) {
            int nbyte = (soffset+k) / 8;
            int offset = (soffset+k) % 8;

            if (((encoded[nbyte] >> (7 - offset)) & 0x01) != 0) {
                path[k] = 1;
            }
        }

        return path;
    }

    public static int calculateEncodedLength(int keyLength) {
        return keyLength / 8 + (keyLength % 8 == 0 ? 0 : 1);
    }
}

