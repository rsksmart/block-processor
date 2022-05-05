package co.rsk.tools.processor.TrieTests.Unitrie;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileMapUtil {

    static public void mapAndCopyIntArray(FileChannel file, long offset, long size, int[] table) throws IOException {
        int count=0;
        int intCount=0;
        int intLeft = table.length;
        while (intLeft>0) {
            int len = Math.min(intLeft,(1<<24)); // 16M ints = 64 Mbytes Max
            ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset,
                    4L*len );
            for (int i=intCount;i<(intCount+len);i++) {
                buf.putInt(table[i]);
                count++;
                if (count % 1_000_000 == 0)
                    System.out.println("" + (count * 100L / table.length) + "%");
            }
            offset +=4L*len;
            intLeft -=len;
            intCount +=len;
        }
    }

    static public void mapAndCopyLongArray(FileChannel file, long offset, long size, long[] table) throws IOException {
        int count=0;
        int longCount=0;
        int longLeft = table.length;
        while (longLeft>0) {
            int len = Math.min(longLeft,(1<<23)); // 8M longs = 64 Mbytes Max
            ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset,
                    8L*len );
            for (int i=longCount;i<(longCount+len);i++) {
                buf.putLong(table[i]);
                count++;
                if (count % 1_000_000 == 0)
                    System.out.println("" + (count * 100L / table.length) + "%");
            }
            offset +=8L*len;
            longLeft -=len;
            longCount +=len;
        }
    }
}
