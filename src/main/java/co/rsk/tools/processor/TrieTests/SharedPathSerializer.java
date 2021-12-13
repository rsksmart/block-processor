package co.rsk.tools.processor.TrieTests;


import co.rsk.tools.processor.Index.TrieKeySlice;
import co.rsk.trie.PathEncoder;

import java.nio.ByteBuffer;

public class SharedPathSerializer {
    private final TrieKeySlice sharedPath;
    private final int lshared;

    public SharedPathSerializer(TrieKeySlice sharedPath) {
        this.sharedPath = sharedPath;
        this.lshared = this.sharedPath.length();
    }

    public int serializedLength() {
        if (!isPresent()) {
            return 0;
        }

        return lsharedSize() + PathEncoder.calculateEncodedLength(lshared);
    }

    public boolean isPresent() {
        return lshared > 0;
    }

    public void serializeInto(ByteBuffer buffer) {
        if (!isPresent()) {
            return;
        }

        if (1 <= lshared && lshared <= 32) {
            // first byte in [0..31]
            buffer.put((byte) (lshared - 1));
        } else if (160 <= lshared && lshared <= 382) {
            // first byte in [32..254]
            buffer.put((byte) (lshared - 128));
        } else {
            buffer.put((byte) 255);
            buffer.put(new VarInt(lshared).encode());
        }

        buffer.put(this.sharedPath.encode());
    }

    private int lsharedSize() {
        if (!isPresent()) {
            return 0;
        }

        if (1 <= lshared && lshared <= 32) {
            return 1;
        }

        if (160 <= lshared && lshared <= 382) {
            return 1;
        }

        return 1 + VarInt.sizeOf(lshared);
    }

    public static TrieKeySlice deserialize(ByteBuffer message, boolean sharedPrefixPresent) {
        if (!sharedPrefixPresent) {
            return TrieKeySliceFactoryInstance.get().empty();
        }

        int lshared;
        // upgrade to int so we can compare positive values
        int lsharedFirstByte = Byte.toUnsignedInt(message.get());
        if (0 <= lsharedFirstByte && lsharedFirstByte <= 31) {
            // lshared in [1..32]
            lshared = lsharedFirstByte + 1;
        } else if (32 <= lsharedFirstByte && lsharedFirstByte <= 254) {
            // lshared in [160..382]
            lshared = lsharedFirstByte + 128;
        } else {
            lshared = (int) readVarInt(message).value;
        }

        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        byte[] encodedKey = new byte[lencoded];
        message.get(encodedKey);
        return TrieKeySliceFactoryInstance.get().fromEncoded(encodedKey, 0, lshared, lencoded);
    }
    private static VarInt readVarInt(ByteBuffer message) {
        // read without touching the buffer position so when we read into bytes it contains the header
        int first = Byte.toUnsignedInt(message.get(message.position()));
        byte[] bytes;
        if (first < 253) {
            bytes = new byte[1];
        } else if (first == 253) {
            bytes = new byte[3];
        } else if (first == 254) {
            bytes = new byte[5];
        } else {
            bytes = new byte[9];
        }

        message.get(bytes);
        return new VarInt(bytes, 0);
    }

}

