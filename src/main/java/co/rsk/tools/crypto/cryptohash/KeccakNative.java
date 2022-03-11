package co.rsk.tools.crypto.cryptohash;

/**
 * Created by bakaking on 25/10/2019.
 */
public class KeccakNative {

    public static native void keccak256(byte[] input, int start, int len, byte[] output);

    static {
        System.loadLibrary("keccak");
    }

    public byte[] digest(byte[] inbuf) {
        byte[] output = new byte[32];
        keccak256(inbuf, 0, inbuf.length, output);
        return output;
    }

    public byte[] digest(byte[] inbuf, int start, int len) {
        if (start < 0 || start + len > inbuf.length) {
            throw new IllegalArgumentException("invalid start and len");
        }
        byte[] output = new byte[32];
        keccak256(inbuf, start, len, output);
        return output;
    }
}