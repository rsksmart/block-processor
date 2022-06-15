package co.rsk.tools.processor.TrieTests.Unitrie;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class LongTable implements Table {

    long table[];

    public LongTable(int cap) {
        table = new long[cap];
    }

    @Override
    public long getPos(int i) {
        return table[i];
    }

    @Override
    public void setPos(int i, long value) {
        table[i] = value;
    }

    @Override
    public boolean isNull() {
        return table==null;
    }

    @Override
    public int length() {
        if (isNull())
            return 0;
        else
            return table.length;
    }

    @Override
    public void copyTo(FileChannel file, int ofs) throws IOException {
        // Child to -do
          FileMapUtil.mapAndCopyLongArray(file,ofs,table.length,table);
    }

    @Override
    public
    void fill(long value) {
        Arrays.fill( table, value );
    }
}
