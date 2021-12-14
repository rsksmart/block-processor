package co.rsk.tools.processor.TrieTests.sepAttempt;

import co.rsk.core.types.ints.Uint16;
import co.rsk.core.types.ints.Uint24;
import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.Index.TrieKeySlice;
import co.rsk.tools.processor.TrieTests.SharedPathSerializer;
import co.rsk.tools.processor.TrieTests.TrieKeySliceFactoryInstance;
import co.rsk.tools.processor.TrieTests.TrieStore;
import co.rsk.tools.processor.TrieTests.VarInt;
import co.rsk.trie.PathEncoder;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.util.RLP;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class TrieData {
    private static final int ARITY = 2;
    private static final String INVALID_VALUE_LENGTH = "Invalid value length";
    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final String INVALID_ARITY = "Invalid arity";
    // all zeroed, default hash for empty nodes
    private static final Keccak256 EMPTY_HASH = makeEmptyHash();

    static public TrieStore store;

    // this node associated value, if any
    private byte[] value;

    // this node hash value
    private Keccak256 hash;

    private Keccak256 leftHash;



    private Keccak256 rightHash;

    private TrieData right; // only for embedded
    private TrieData left; // only for embedded

    // temporary storage of encoding. Removed after save()
    private byte[] encoded;

    // valueLength enables lazy long value retrieval.
    // The length of the data is now stored. This allows EXTCODESIZE to
    // execute much faster without the need to actually retrieve the data.
    // if valueLength>32 and value==null this means the value has not been retrieved yet.
    // if valueLength==0, then there is no value AND no node.
    // This trie structure does not distinguish between empty arrays
    // and nulls. Storing an empty byte array has the same effect as removing the node.
    //
    private final Uint24 valueLength;

    // For lazy retrieval and also for cache.
    private Keccak256 valueHash;

    // the size of this node along with its children (in bytes)
    // note that we use a long because an int would allow only up to 4GB of state to be stored.
    private long childrenSize;
    // shared Path
    private final TrieKeySlice sharedPath;

    public Keccak256 getLeftHash() {
        return leftHash;
    }

    public Keccak256 getRightHash() {
        return rightHash;
    }
    public TrieKeySlice getSharedPath() {
        return sharedPath;
    }

    private static Keccak256 makeEmptyHash() {
        return new Keccak256(Keccak256Helper.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }

    public TrieData(TrieKeySlice sharedPath, byte[] value,
                    //Trie left, Trie right,
                    Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        this.value = value;
        this.leftHash = null;
        this.rightHash = null;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.childrenSize = childrenSize;
    }

    public TrieData(TrieKeySlice sharedPath, byte[] value,
                    Keccak256 leftHash, Keccak256 rightHash,
                    Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        this.value = value;
        this.leftHash = leftHash;
        this.rightHash = rightHash;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.childrenSize = childrenSize;
    }

    public TrieData(TrieKeySlice sharedPath, byte[] value,
                    TrieData left, Keccak256 leftHash,
                    TrieData right, Keccak256 rightHash,
                    Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        this.value = value;
        this.leftHash = leftHash;
        this.rightHash = rightHash;
        this.left = left;
        this.right = right;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.childrenSize = childrenSize;
    }

    static TrieData fromMessage(byte[] message) {
        if (message[0] == ARITY) {
            return fromMessageOrchid(message);
        } else {
            return fromMessageRskip107(ByteBuffer.wrap(message));
        }
    }


    private static TrieData fromMessageOrchid(byte[] message) {
        int current = 0;
        int arity = message[current];
        current += Byte.BYTES;

        if (arity != ARITY) {
            throw new IllegalArgumentException(INVALID_ARITY);
        }

        int flags = message[current];
        current += Byte.BYTES;

        boolean hasLongVal = (flags & 0x02) == 2;
        int bhashes = Uint16.decodeToInt(message, current);
        current += Uint16.BYTES;
        int lshared = Uint16.decodeToInt(message, current);
        current += Uint16.BYTES;

        TrieKeySlice sharedPath = TrieKeySliceFactoryInstance.get().empty();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        if (lencoded > 0) {
            if (message.length - current < lencoded) {
                throw new IllegalArgumentException(String.format(
                        "Left message is too short for encoded shared path expected:%d actual:%d total:%d",
                        lencoded, message.length - current, message.length));
            }
            sharedPath = TrieKeySliceFactoryInstance.get().fromEncoded(message, current, lshared, lencoded);
            current += lencoded;
        }
        Keccak256 leftHash = null;
        Keccak256 rightHash = null;

        int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        int nhashes = 0;
        if ((bhashes & 0b01) != 0) {
            Keccak256 nodeHash = readHash(message, current);
            leftHash = nodeHash;
            current += keccakSize;
            nhashes++;
        }
        if ((bhashes & 0b10) != 0) {
            Keccak256 nodeHash = readHash(message, current);
            rightHash = nodeHash;
            current += keccakSize;
            nhashes++;
        }

        int offset = MESSAGE_HEADER_LENGTH + lencoded + nhashes * keccakSize;
        byte[] value;
        Uint24 lvalue;
        Keccak256 valueHash;

        if (hasLongVal) {
            valueHash = readHash(message, current);
            // random value
            value = new byte[]{1, 2, 3};//store.retrieveValue(valueHash.getBytes());
            lvalue = new Uint24(value.length);
        } else {
            int remaining = message.length - offset;
            if (remaining > 0) {
                if (message.length - current < remaining) {
                    throw new IllegalArgumentException(String.format(
                            "Left message is too short for value expected:%d actual:%d total:%d",
                            remaining, message.length - current, message.length));
                }

                value = Arrays.copyOfRange(message, current, current + remaining);
                valueHash = null;
                lvalue = new Uint24(remaining);
            } else {
                value = null;
                valueHash = null;
                lvalue = Uint24.ZERO;
            }
        }

        // it doesn't need to clone value since it's retrieved from store or created from message
        TrieData trie = new TrieData(sharedPath, value,
                leftHash, rightHash,
                lvalue, valueHash, -1); // TO DO : children size passed as -1

        return trie;
    }

    private static Keccak256 readHash(byte[] bytes, int position) {
        int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        if (bytes.length - position < keccakSize) {
            throw new IllegalArgumentException(String.format(
                    "Left message is too short for hash expected:%d actual:%d total:%d",
                    keccakSize, bytes.length - position, bytes.length
            ));
        }

        return new Keccak256(Arrays.copyOfRange(bytes, position, position + keccakSize));
    }

    private static TrieData fromMessageRskip107(ByteBuffer message) {
        byte flags = message.get();
        // if we reached here, we don't need to check the version flag
        boolean hasLongVal = (flags & 0b00100000) == 0b00100000;
        boolean sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
        boolean leftNodePresent = (flags & 0b00001000) == 0b00001000;
        boolean rightNodePresent = (flags & 0b00000100) == 0b00000100;
        boolean leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
        boolean rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;

        TrieKeySlice sharedPath = SharedPathSerializer.deserialize(message, sharedPrefixPresent);
        TrieData left = null;
        TrieData right = null;
        Keccak256 leftHash = null;
        Keccak256 rightHash = null;
        if (leftNodePresent) {
            if (leftNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                TrieData node = fromMessageRskip107(ByteBuffer.wrap(serializedNode));
                left = node;
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                leftHash = nodeHash;
            }
        }

        if (rightNodePresent) {
            if (rightNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                TrieData node = fromMessageRskip107(ByteBuffer.wrap(serializedNode));
                right = node;
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                rightHash = nodeHash;
            }
        }

        VarInt childrenSize = new VarInt(0);
        if (leftNodePresent || rightNodePresent) {
            childrenSize = readVarInt(message);
        }

        byte[] value;
        Uint24 lvalue;
        Keccak256 valueHash;

        if (hasLongVal) {
            value = null;
            byte[] valueHashBytes = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
            message.get(valueHashBytes);
            valueHash = new Keccak256(valueHashBytes);
            byte[] lvalueBytes = new byte[Uint24.BYTES];
            message.get(lvalueBytes);
            lvalue = Uint24.decode(lvalueBytes, 0);
        } else {
            int remaining = message.remaining();
            if (remaining != 0) {
                value = new byte[remaining];
                message.get(value);
                valueHash = new Keccak256(Keccak256Helper.keccak256(value));
                lvalue = new Uint24(remaining);
            } else {
                value = null;
                valueHash = null;
                lvalue = Uint24.ZERO;
            }
        }

        if (message.hasRemaining()) {
            throw new IllegalArgumentException("The message had more data than expected");
        }

        TrieData trie = new TrieData(sharedPath, value,
                left, leftHash,
                right, rightHash,
                lvalue, valueHash, childrenSize.value);

        return trie;
    }


    private VarInt getChildrenSize() {
        return new VarInt(0);// TO DO
    }

    private byte[] internalToMessage() {
        Uint24 lvalue = this.valueLength;
        boolean hasLongVal = this.hasLongValue();

        SharedPathSerializer sharedPathSerializer = new SharedPathSerializer(this.sharedPath);
        VarInt childrenSize = getChildrenSize();

        int leftSize;
        if ((this.left == null) && (this.leftHash == null)) {
            leftSize =0;
        } else
            // Is embedded
            leftSize =this.left.getSerializedLength();

        int rightSize;
        if ((this.left == null) && (this.leftHash == null)) {
            rightSize = 0;
        } else
           rightSize= this.right.getSerializedLength();

        ByteBuffer buffer = ByteBuffer.allocate(
                1 + // flags
                        sharedPathSerializer.serializedLength() +
                        leftSize +
                        rightSize +
                        (this.isTerminal() ? 0 : childrenSize.getSizeInBytes()) +
                        (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES + Uint24.BYTES : lvalue.intValue())
        );

        // current serialization version: 01
        byte flags = 0b01000000;
        if (hasLongVal) {
            flags = (byte) (flags | 0b00100000);
        }

        if (sharedPathSerializer.isPresent()) {
            flags = (byte) (flags | 0b00010000);
        }

        if ((this.left == null) && (this.leftHash == null)) { // empty
            flags = (byte) (flags | 0b00001000);
        }

        if ((this.right == null) && (this.rightHash == null)) {// empty
            flags = (byte) (flags | 0b00000100);
        }

        if (this.left != null) {// embedded
            flags = (byte) (flags | 0b00000010);
        }

        if (this.right != null) { // embedded
            flags = (byte) (flags | 0b00000001);
        }

        buffer.put(flags);

        sharedPathSerializer.serializeInto(buffer);

        this.left.serializeInto(buffer);

        this.right.serializeInto(buffer);

        if (!this.isTerminal()) {
            buffer.put(childrenSize.encode());
        }

        if (hasLongVal) {
            buffer.put(this.getValueHash().getBytes());
            buffer.put(lvalue.encode());
        } else if (lvalue.compareTo(Uint24.ZERO) > 0) {
            buffer.put(this.getValue());
        }

        encoded = buffer.array();
        return encoded;
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

    public boolean isTerminal() {
        return (this.leftHash==null) &&
                (this.left==null) &&
                (this.rightHash==null) &&
                (this.right==null);

    }

    public boolean hasLongValue() {
        return this.valueLength.compareTo(new Uint24(32)) > 0;
    }
    public Uint24 getValueLength() {
        return this.valueLength;
    }

    public Keccak256 getValueHash() {
        // For empty values (valueLength==0) we return the null hash because
        // in this trie empty arrays cannot be stored.
        if (valueHash == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            valueHash = new Keccak256(Keccak256Helper.keccak256(getValue()));
        }

        return valueHash;
    }

    // returns a REFERENCE
    public byte[] getValue() {
        return value;
    }
    /*public boolean isEmbeddable() {
        return node.isTerminal() && getMessageLength() <= MAX_EMBEDDED_NODE_SIZE_IN_BYTES;
    }*/

    public void serializeInto(ByteBuffer buffer) {
        byte[] serialized = internalToMessage();
        buffer.put(new Uint8(serialized.length).encode());
        buffer.put(serialized);
    }

    public void serializeInto(ByteBuffer buffer,TrieData node,Keccak256 hash) {
        if (node != null) {
            node.serializeInto(buffer);
        } else {
            buffer.put(hash.getBytes());
        }
    }
    private static boolean isEmptyTrie(Uint24 valueLength, TrieData left, TrieData right) {
        if (valueLength.compareTo(Uint24.ZERO) > 0) {
            return false;
        }

        // TO DO: check if I need to test with null or with hash of []
        return (left==null)  && (right==null);
    }

    public boolean isEmptyTrie() {
        return isEmptyTrie(this.valueLength, this.left, this.right);
    }

    public Keccak256 getHash() {
        if (this.hash != null) {
            return this.hash.copy();
        }

        if (isEmptyTrie()) {
            return EMPTY_HASH.copy();
        }

        // Just return some hash, we won't use them
        this.hash = new Keccak256(Keccak256Helper.keccak256(new byte[]{}));
        return this.hash.copy();
    }

    public int getSerializedLength() {
        return 0; // TO DO
    }
}
