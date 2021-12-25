package co.rsk.tools.processor.TrieTests.ohmap;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.TrieTests.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.ObjectMapper;
import co.rsk.tools.processor.TrieTests.ObjectReference;
import org.ethereum.db.ByteArrayWrapper;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class ObjectHashMap extends ObjectMapper  {

    HashMap<Keccak256, ByteArrayWrapper> map = new HashMap<>();

    public boolean getByHash() {
        return true;
    }
    public EncodedObjectRef add(byte[] encoded, Keccak256 hash) {
        map.put(hash,new ByteArrayWrapper(encoded));
        return new HashEOR(hash);
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        ObjectReference r = new ObjectReference();
        ByteArrayWrapper b= map.get(((HashEOR) encodedRef).hash);
        r.len = b.getData().length;
        r.message = ByteBuffer.wrap(b.getData());
        return r;
    }

}
