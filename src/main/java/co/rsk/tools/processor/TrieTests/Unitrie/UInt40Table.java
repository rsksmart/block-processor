package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.examples.storage.ObjectIO;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class UInt40Table implements Table {

    byte table[];

    public UInt40Table(int cap) {
        table = new byte[cap*5];
    }

    @Override
    public long getPos(int i) {
        return ObjectIO.getLong5(table,i*5);
    }

    @Override
    public void setPos(int i, long value) {
        ObjectIO.putLong5(table,i*5,value);
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
            return table.length/5;
    }

    @Override
    public void copyTo(FileChannel file, int ofs) throws IOException {
        // Child to -do
        FileMapUtil.mapAndCopyByteArray(file,ofs,table.length,table);
    }

    @Override
    public
    void fill(long value) {
        for(int i=0;i<table.length/5;i++){
            setPos(i,value);
        }
    }

    @Override
    public
    void readFrom(DataInputStream din, int count) throws IOException  {
        for (int i = 0; i < count; i++) {
           setPos(i, ObjectIO.readLong5(din));
        }
    }
}
