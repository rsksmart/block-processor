package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint16;
import co.rsk.core.types.ints.Uint24;
import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.tools.processor.TrieUtils.PathEncoder;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import org.ethereum.crypto.Keccak256Helper;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class TrieBuilder {

    private static final Profiler profiler = ProfilerFactory.getInstance();
    protected static final int ARITY = 2;

    private static final String INVALID_ARITY = "Invalid arity";

    protected static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;


    /**
     * Deserialize a Trie, either using the original format or RSKIP 107 format, based on version flags.
     * The original trie wasted the first byte by encoding the arity, which was always 2. We use this marker to
     * recognize the old serialization format.
     */
    public static Trie fromMessage(TrieFactory trieFactory, byte[] message, TrieStore store) {
        Trie trie;
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BUILD_TRIE_FROM_MSG);
        if (message[0] == ARITY) {

            trie = fromMessageOrchid(trieFactory,message, store);
        } else {
            trie = fromMessageRskip107(trieFactory,ByteBuffer.wrap(message), null,null,null,store);
        }

        profiler.stop(metric);

        return trie;
    }
    public static Trie fromMessage(TrieFactory trieFactory,ByteBuffer message,
                                   EncodedObjectRef encodedOfs,
                                   EncodedObjectRef leftOfs,
                                   EncodedObjectRef rightOfs,
                                   TrieStore store) {
        Trie trie;
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.BUILD_TRIE_FROM_MSG);
        if (message.get(0) == ARITY) {
            throw new RuntimeException("not allowed for this test");
            //System.exit(1); // not allowed in this test
            //trie = fromMessageOrchid(message, store);
            //trie=null;
        } else {
            trie = fromMessageRskip107(trieFactory,message, encodedOfs,leftOfs,rightOfs,store);
        }

        profiler.stop(metric);

        return trie;
    }

    private static Trie fromMessageOrchid(TrieFactory trieFactory,byte[] message, TrieStore store) {
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
        int lshared = Uint16.decodeToInt(message, current); // a bit length
        current += Uint16.BYTES;

        TrieKeySlice sharedPath = TrieKeySliceFactoryInstance.get().empty();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        if (lencoded > 0) {
            if (message.length - current < lencoded) {
                throw new IllegalArgumentException(String.format(
                        "Left message is too short for encoded shared path expected:%d actual:%d total:%d",
                        lencoded, message.length - current, message.length));
            }
            sharedPath = TrieKeySliceFactoryInstance.get().fromEncoded(message, current*8, lshared);
            current += lencoded;
        }

        int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        NodeReference left = NodeReferenceImpl.empty();
        NodeReference right = NodeReferenceImpl.empty();

        int nhashes = 0;
        if ((bhashes & 0b01) != 0) {
            Keccak256 nodeHash = readHash(message, current);
            left = store.getNodeReferenceFactory().newReference(store, null, nodeHash);
            current += keccakSize;
            nhashes++;
        }
        if ((bhashes & 0b10) != 0) {
            Keccak256 nodeHash = readHash(message, current);
            right = store.getNodeReferenceFactory().newReference(store, null, nodeHash);
            current += keccakSize;
            nhashes++;
        }

        int offset = MESSAGE_HEADER_LENGTH + lencoded + nhashes * keccakSize;
        byte[] value;
        Uint24 lvalue;
        Keccak256 valueHash;

        if (hasLongVal) {
            valueHash = readHash(message, current);
            value = store.retrieveValue(valueHash.getBytes());
            lvalue = new Uint24(value.length);
        } else {
            int remaining = message.length - offset;
            if (remaining > 0) {
                if (message.length - current  < remaining) {
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
        Trie trie = store.getTrieFactory().newTrie(store, sharedPath, value, left, right, lvalue, valueHash);

        return trie;
    }

    private static Trie fromMessageRskip107(TrieFactory trieFactory,
                                            ByteBuffer message,
                                            EncodedObjectRef aEncodedOfs,
                                            EncodedObjectRef leftOfs,
                                            EncodedObjectRef rightOfs,
                                            TrieStore store) {

        byte flags = message.get();
        // if we reached here, we don't need to check the version flag
        boolean hasLongVal = (flags & 0b00100000) == 0b00100000;
        boolean sharedPrefixPresent = (flags & 0b00010000) == 0b00010000;
        boolean leftNodePresent = (flags & 0b00001000) == 0b00001000;
        boolean rightNodePresent = (flags & 0b00000100) == 0b00000100;
        boolean leftNodeEmbedded = (flags & 0b00000010) == 0b00000010;
        boolean rightNodeEmbedded = (flags & 0b00000001) == 0b00000001;

        TrieKeySlice sharedPath = SharedPathSerializer.deserialize(message, sharedPrefixPresent);

        NodeReference left = NodeReferenceImpl.empty();
        NodeReference right = NodeReferenceImpl.empty();
        if (leftNodePresent) {
            if (leftNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                Trie node = fromMessageRskip107(trieFactory,ByteBuffer.wrap(serializedNode), null,null,null,store);
                left = store.getNodeReferenceFactory().newReference(store, node, null,leftOfs);
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                left = store.getNodeReferenceFactory().newReference(store, null, nodeHash, leftOfs);
            }
        }

        if (rightNodePresent) {
            if (rightNodeEmbedded) {
                byte[] lengthBytes = new byte[Uint8.BYTES];
                message.get(lengthBytes);
                Uint8 length = Uint8.decode(lengthBytes, 0);

                byte[] serializedNode = new byte[length.intValue()];
                message.get(serializedNode);
                Trie node = fromMessageRskip107(trieFactory,ByteBuffer.wrap(serializedNode),null,null,null, store);
                right = store.getNodeReferenceFactory().newReference(store, node, null,rightOfs);
            } else {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];
                message.get(valueHash);
                Keccak256 nodeHash = new Keccak256(valueHash);
                right = store.getNodeReferenceFactory().newReference(store, null, nodeHash,rightOfs);
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

        Trie trie = store.getTrieFactory().newTrie(store, sharedPath, value, left, right, lvalue, valueHash, childrenSize,aEncodedOfs);
        return trie;
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
}
