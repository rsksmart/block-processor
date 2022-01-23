package co.rsk.tools.processor.TrieTests.Unitrie;

import java.nio.ByteBuffer;

public class ObjectReference {
    public int len;
    public EncodedObjectRef leftRef;
    public EncodedObjectRef rightRef;
    public ByteBuffer message;
    public boolean saved;

    public byte[] getAsArray() {
        byte[] data = new byte[len];
        message.get(data);
        return data;
    }
}
