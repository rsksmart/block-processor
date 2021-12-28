package co.rsk.tools.processor.TrieTests.Unitrie;

import java.io.IOException;
import java.io.OutputStream;

public class Utils {

    public static long readUint32(byte[] bytes, int offset) {
        return (long)bytes[offset] & 255L | ((long)bytes[offset + 1] & 255L) << 8 | ((long)bytes[offset + 2] & 255L) << 16 | ((long)bytes[offset + 3] & 255L) << 24;
    }

    public static long readInt64(byte[] bytes, int offset) {
        return (long)bytes[offset] & 255L | ((long)bytes[offset + 1] & 255L) << 8 | ((long)bytes[offset + 2] & 255L) << 16 | ((long)bytes[offset + 3] & 255L) << 24 | ((long)bytes[offset + 4] & 255L) << 32 | ((long)bytes[offset + 5] & 255L) << 40 | ((long)bytes[offset + 6] & 255L) << 48 | ((long)bytes[offset + 7] & 255L) << 56;
    }

    public static long readUint32BE(byte[] bytes, int offset) {
        return ((long)bytes[offset] & 255L) << 24 | ((long)bytes[offset + 1] & 255L) << 16 | ((long)bytes[offset + 2] & 255L) << 8 | (long)bytes[offset + 3] & 255L;
    }

    public static int readUint16BE(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 8 | bytes[offset + 1] & 255;
    }
    public static void uint32ToByteArrayBE(long val, byte[] out, int offset) {
        out[offset] = (byte)((int)(255L & val >> 24));
        out[offset + 1] = (byte)((int)(255L & val >> 16));
        out[offset + 2] = (byte)((int)(255L & val >> 8));
        out[offset + 3] = (byte)((int)(255L & val));
    }

    public static void uint32ToByteArrayLE(long val, byte[] out, int offset) {
        out[offset] = (byte)((int)(255L & val));
        out[offset + 1] = (byte)((int)(255L & val >> 8));
        out[offset + 2] = (byte)((int)(255L & val >> 16));
        out[offset + 3] = (byte)((int)(255L & val >> 24));
    }

    public static void uint64ToByteArrayLE(long val, byte[] out, int offset) {
        out[offset] = (byte)((int)(255L & val));
        out[offset + 1] = (byte)((int)(255L & val >> 8));
        out[offset + 2] = (byte)((int)(255L & val >> 16));
        out[offset + 3] = (byte)((int)(255L & val >> 24));
        out[offset + 4] = (byte)((int)(255L & val >> 32));
        out[offset + 5] = (byte)((int)(255L & val >> 40));
        out[offset + 6] = (byte)((int)(255L & val >> 48));
        out[offset + 7] = (byte)((int)(255L & val >> 56));
    }

    public static void uint32ToByteStreamLE(long val, OutputStream stream) throws IOException {
        stream.write((int)(255L & val));
        stream.write((int)(255L & val >> 8));
        stream.write((int)(255L & val >> 16));
        stream.write((int)(255L & val >> 24));
    }

    public static void int64ToByteStreamLE(long val, OutputStream stream) throws IOException {
        stream.write((int)(255L & val));
        stream.write((int)(255L & val >> 8));
        stream.write((int)(255L & val >> 16));
        stream.write((int)(255L & val >> 24));
        stream.write((int)(255L & val >> 32));
        stream.write((int)(255L & val >> 40));
        stream.write((int)(255L & val >> 48));
        stream.write((int)(255L & val >> 56));
    }


}
