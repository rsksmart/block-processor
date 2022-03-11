package co.rsk.tools.processor.TrieUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CompactTrieKeySliceTest {
    public static void test_getBitSeqAsInt() {
        test_getBitSeqAsInt1();
        test_getBitSeqAsInt2();
        test_getBitSeqAsLong1();
        test_getBitSeqAsLong2();
        test_getBitSeqAsLong3();
    }

    public static void test_getBitSeqAsInt1() {

        byte[] a = binToArray(
                "00000011"+"11111111"+"11111111"+"11111100"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        byte[] b = binToArray(
                "01111111"+"11111111"+"11111111"+"10000000"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        int ax = getBitSeqAsInt(a,5,25);
        int bx = getBitSeqAsInt(b,0,25);
        System.out.println(ax);
        System.out.println(bx);
        if (ax!=bx) throw new RuntimeException("bad");
    }

    public static void test_getBitSeqAsInt2() {

        byte[] a = binToArray(
                "00000011"+"11111111"+"11111111"+"11111100"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        byte[] b = binToArray(
                "01111111"+"11111111"+"11111111"+"10000000"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        int ax = getBitSeqAsInt(a,5,16);
        int bx = getBitSeqAsInt(b,0,16);
        System.out.println(ax);
        System.out.println(bx);
        if (ax!=bx) throw new RuntimeException("bad");
    }

    static long getBitSeqAsLong(byte[] bytes, int offset, int len) {
        return CompactTrieKeySlice.getBitSeqAsLong(bytes,offset,len);
    }

    public static void test_getBitSeqAsLong1() {

        byte[] a = binToArray(
                "00000011"+"11111111"+"11111111"+"11111100"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        byte[] b = binToArray(
                "01111111"+"11111111"+"11111111"+"10000000"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        long ax = getBitSeqAsLong(a,5,25);
        long bx = getBitSeqAsLong(b,0,25);
        System.out.println(ax);
        System.out.println(bx);
        if (ax!=bx) throw new RuntimeException("bad");
    }

    public static void test_getBitSeqAsLong2() {

        byte[] a = binToArray(
                "00000011"+"11111111"+"11111111"+"11111100"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        byte[] b = binToArray(
                "01111111"+"11111111"+"11111111"+"10000000"+
                        "00000000"+"00000000"+"00000000"+"00000000");
        long ax = getBitSeqAsLong(a,5,16);
        long bx = getBitSeqAsLong(b,0,16);
        System.out.println(ax);
        System.out.println(bx);
        if (ax!=bx) throw new RuntimeException("bad");
    }
    public static void test_getBitSeqAsLong3() {

        byte[] a = binToArray(
                "00000011"+"11111111"+"11111111"+"11111100"+
                        "00000000"+"00000000"+"00000100"+"00000000");
        byte[] b = binToArray(
                "01111111"+"11111111"+"11111111"+"10000000"+
                        "00000000"+"00000000"+"10000000"+"00000000");
        long ax = getBitSeqAsLong(a,5,50);
        long bx = getBitSeqAsLong(b,0,50);
        System.out.println(ax);
        System.out.println(bx);
        if (ax!=bx) throw new RuntimeException("bad");
    }

    public static byte[] binToArray(String b) {
        long a = Long.parseLong(b, 2);
        ByteBuffer bytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(a);

        byte[] array = bytes.array();
        return array;
    }
    static int getBitSeqAsInt(byte[] bytes, int offset, int len){

        int byteIndex = offset / 8;
        int bitIndex = offset % 8;
        int val =0;
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
        if (count>4)
            throw new RuntimeException("cannot pack more than 4 bytes");

        int mask = (1<<len)-1;
        int remove = (add-bitIndex-len);
        val = (val  >> remove) & mask;
        return val;
    }

}
