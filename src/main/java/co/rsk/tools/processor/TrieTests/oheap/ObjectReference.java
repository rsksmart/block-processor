package co.rsk.tools.processor.TrieTests.oheap;

import java.nio.ByteBuffer;

public class ObjectReference {
    public int len;
    public long leftOfs;
    public long rightOfs;
    public ByteBuffer message;

    public byte[] getAsArray() {
        byte[] data = new byte[len];
        message.get(data);
        return data;
    }
}
