package co.rsk.tools.processor.TrieTests;

import java.nio.ByteBuffer;

public class ObjectReference {
    public int len;
    public EncodedObjectRef leftOfs;
    public EncodedObjectRef rightOfs;
    public ByteBuffer message;

    public byte[] getAsArray() {
        byte[] data = new byte[len];
        message.get(data);
        return data;
    }
}
