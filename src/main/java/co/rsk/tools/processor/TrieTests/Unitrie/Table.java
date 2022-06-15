package co.rsk.tools.processor.TrieTests.Unitrie;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public interface Table {
    long getPos(int i) ;
    void setPos(int i,long value);
    boolean isNull();
    int length();
    void copyTo(FileChannel file, int ofs) throws IOException;
    void readFrom(DataInputStream din, int count) throws IOException;


    void fill(long value);
}
