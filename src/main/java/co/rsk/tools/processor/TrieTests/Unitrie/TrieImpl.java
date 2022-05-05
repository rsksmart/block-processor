package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.tools.processor.TrieUtils.PathEncoder;
import co.rsk.tools.processor.TrieUtils.TrieKeySlice;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class TrieImpl implements Trie {

    public static long trieNodesRetrieved = 0;
    protected static final Profiler profiler = ProfilerFactory.getInstance();
    protected static final int ARITY = 2;
    protected static final String INVALID_VALUE_LENGTH = "Invalid value length";
    // all zeroed, default hash for empty nodes
    private static final Keccak256 EMPTY_HASH = makeEmptyHash();

    // this node associated value, if any
    protected byte[] value;

    protected final NodeReference left;

    protected final NodeReference right;


    // this node hash value
    protected Keccak256 hash;

    // this node hash value as calculated before RSKIP 107
    // we need to cache it, otherwise TrieConverter is prohibitively slow.
    protected Keccak256 hashOrchid;

    // temporary storage of encoding. Removed after save()
    protected byte[] encoded;

    // valueLength enables lazy long value retrieval.
    // The length of the data is now stored. This allows EXTCODESIZE to
    // execute much faster without the need to actually retrieve the data.
    // if valueLength>32 and value==null this means the value has not been retrieved yet.
    // if valueLength==0, then there is no value AND no node.
    // This trie structure does not distinguish between empty arrays
    // and nulls. Storing an empty byte array has the same effect as removing the node.
    //
    protected final Uint24 valueLength;

    // For lazy retrieval and also for cache.
    protected Keccak256 valueHash;

    // the size of this node along with its children (in bytes)
    // note that we use a long because an int would allow only up to 4GB of state to be stored.
    protected VarInt childrenSize;

    // associated store, to store or retrieve nodes in the trie
    protected final TrieStore store;

    // already saved in store flag
    protected volatile boolean saved;

    // already saved in Memory store flag
    //private volatile boolean memSaved;

    // shared Path
    protected final TrieKeySlice sharedPath;



    protected boolean isEmbedded;

    // default constructor, no secure
    public TrieImpl() {
        this(null);
    }

    public TrieImpl(TrieStore store) {
        this(store, TrieKeySliceFactoryInstance.get().empty(), null);
    }

    // constructor
    protected TrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value) {
        this(store, sharedPath, value,
                NodeReferenceImpl.empty(),
                NodeReferenceImpl.empty(), getDataLength(value), null,
                new VarInt(0),
                terminalNodeEmbeddable(
                        new SharedPathSerializer(sharedPath).serializedLength(),
                        value));
    }

    public static boolean isEmbeddable(TrieKeySlice sharedPath,NodeReference left, NodeReference right,Uint24 valueLength) {
        boolean isEmb = false;
        if ((left.isEmpty()) && (right.isEmpty())) {
            isEmb = terminalNodeEmbeddable(
                    new SharedPathSerializer(sharedPath).serializedLength(),
                    valueLength.intValue());
        }
        return isEmb;
    }

    /*public TrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                    NodeReference left, NodeReference right,
                    Uint24 valueLength, Keccak256 valueHash) {

        this(store, sharedPath, value, left, right, valueLength, valueHash, null,
                isEmbeddable(sharedPath, left,  right, valueLength));
    }



    protected TrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                 NodeReference left, NodeReference right,
                 Uint24 valueLength, Keccak256 valueHash,
                 VarInt childrenSize) {
        this(store,  sharedPath, value,
                left,  right,
                valueLength, valueHash,
                childrenSize,
                isEmbeddable(sharedPath, left,  right, valueLength));
    }

     */

    // full constructor
    protected TrieImpl(TrieStore store, TrieKeySlice sharedPath, byte[] value,
                 NodeReference left, NodeReference right,
                 Uint24 valueLength, Keccak256 valueHash,
                 VarInt childrenSize,boolean isEmbedded) {
        this.isEmbedded = isEmbedded;
        this.value = value;
        this.left = left;
        this.right = right;
        this.store = store;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.childrenSize = childrenSize;
        checkValueLength();
        accessThisNode();
    }



    protected void changeSaved() {
    }

    protected void storeNodeInMem() {
    }

    public void accessThisNode() {

    }




    public void checkReference() {
    }


    /**
     * getHash calculates and/or returns the hash associated with this node content
     *
     * the internal variable hash could contains the cached hash.
     *
     * This method is not synchronized because the result of it's execution
     *
     * disregarding the lazy initialization is idempotent. It's better to keep
     *
     * it out of synchronized.
     *
     * @return  a byte array with the node serialized to bytes
     */
    public Keccak256 getHash() {
        if (this.hash != null) {
            return this.hash.copy();
        }

        if (isEmptyTrie()) {
            return EMPTY_HASH.copy();
        }

        byte[] message = this.toMessage();

        this.hash = new Keccak256(Keccak256Helper.keccak256(message));

        return this.hash.copy();
    }

    /**
     * The hash based on pre-RSKIP 107 serialization
     */
    public Keccak256 getHashOrchid(boolean isSecure) {
        if (this.hashOrchid != null) {
            return this.hashOrchid.copy();
        }

        if (isEmptyTrie()) {
            return EMPTY_HASH.copy();
        }

        byte[] message = this.toMessageOrchid(isSecure);

        this.hashOrchid = new Keccak256(Keccak256Helper.keccak256(message));

        return this.hashOrchid.copy();
    }

    /**
     * get returns the value associated with a key
     *
     * @param key the key associated with the value, a byte array (variable length)
     *
     * @return  the associated value, a byte array, or null if there is no associated value to the key
     */

    public byte[] get(byte[] key) {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.TRIE_GET_VALUE_FROM_KEY);
        Trie node = find(key);
        if (node == null) {
            profiler.stop(metric);
            return null;
        }

        byte[] result = node.getValue();
        profiler.stop(metric);
        return result;
    }

    /**
     * get by string, utility method used from test methods
     *
     * @param key   a string, that is converted to a byte array
     * @return a byte array with the associated value
     */
    public byte[] get(String key) {
        return this.get(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * put key value association, returning a new NewTrie
     *
     * @param key   key to be updated or created, a byte array
     * @param value value to associated to the key, a byte array
     *
     * @return a new NewTrie node, the top node of the new tree having the
     * key-value association. The original node is immutable, a new tree
     * is build, adding some new nodes
     */
    public Trie put(byte[] key, byte[] value) {
        TrieKeySlice keySlice = TrieKeySliceFactoryInstance.get().fromKey(key);
        Trie trie = put(keySlice, value, false);

        return trie == null ? store.getTrieFactory().newTrie(this.store) : trie;
    }

    public Trie put(ByteArrayWrapper key, byte[] value) {
        return put(key.getData(), value);
    }
    /**
     * put string key to value, the key is converted to byte array
     * utility method to be used from testing
     *
     * @param key   a string
     * @param value an associated value, a byte array
     *
     * @return  a new NewTrie, the top node of a new trie having the key
     * value association
     */
    public Trie put(String key, byte[] value) {
        return put(key.getBytes(StandardCharsets.UTF_8), value);
    }

    /**
     * delete update the key to null value
     *
     * @param key   a byte array
     *
     * @return the new top node of the trie with the association removed
     *
     */
    public Trie delete(byte[] key) {
        return put(key, null);
    }

    // This is O(1). The node with exact key "key" MUST exists.
    public Trie deleteRecursive(byte[] key) {
        TrieKeySlice keySlice = TrieKeySliceFactoryInstance.get().fromKey(key);
        Trie trie = put(keySlice, null, true);

        return trie == null ? store.getTrieFactory().newTrie(this.store) : trie;
    }

    /**
     * delete string key, utility method to be used for testing
     *
     * @param key a string
     *
     * @return the new top node of the trie with the key removed
     */
    public Trie delete(String key) {
        return delete(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * toMessage serialize the node to bytes. Used to persist the node in a key-value store
     * like levelDB or redis.
     *
     * The serialization includes:
     * - arity: byte
     * - bits with present hashes: two bytes (example: 0x0203 says that the node has
     * hashes at index 0, 1, 9 (the other subnodes are null)
     * - present hashes: 32 bytes each
     * - associated value: remainder bytes (no bytes if null)
     *
     * @return a byte array with the serialized info
     */
    public byte[] toMessage() {
        if (encoded == null) {
            internalToMessage();
        }

        return cloneArray(encoded);
    }

    public int getMessageLength() {
        if (encoded == null) {
            internalToMessage();
        }

        return encoded.length;
    }

    /**
     * Serialize the node to bytes with the pre-RSKIP 107 format
     */
    public byte[] toMessageOrchid(boolean isSecure) {
        Uint24 lvalue = this.valueLength;
        int lshared = this.sharedPath.length();
        int lencoded = PathEncoder.calculateEncodedLength(lshared);
        boolean hasLongVal = this.hasLongValue();
        Optional<Keccak256> leftHashOpt = this.left.getHashOrchid(isSecure);
        Optional<Keccak256> rightHashOpt = this.right.getHashOrchid(isSecure);

        int nnodes = 0;
        int bits = 0;
        if (leftHashOpt.isPresent()) {
            bits |= 0b01;
            nnodes++;
        }

        if (rightHashOpt.isPresent()) {
            bits |= 0b10;
            nnodes++;
        }

        ByteBuffer buffer = ByteBuffer.allocate(TrieBuilder.MESSAGE_HEADER_LENGTH  + (lshared > 0 ? lencoded: 0)
                + nnodes * Keccak256Helper.DEFAULT_SIZE_BYTES
                + (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES : lvalue.intValue()));

        buffer.put((byte) ARITY);

        byte flags = 0;

        if (isSecure) {
            flags |= 1;
        }

        if (hasLongVal) {
            flags |= 2;
        }

        buffer.put(flags);
        buffer.putShort((short) bits);
        buffer.putShort((short) lshared);

        if (lshared > 0) {
            buffer.put(this.sharedPath.encode());
        }

        leftHashOpt.ifPresent(h -> buffer.put(h.getBytes()));

        rightHashOpt.ifPresent(h -> buffer.put(h.getBytes()));

        if (lvalue.compareTo(Uint24.ZERO) > 0) {
            if (hasLongVal) {
                buffer.put(this.getValueHash().getBytes());
            }
            else {
                buffer.put(this.getValue());
            }
        }

        return buffer.array();
    }

    // This method should only be called DURING save(). It should not be called in other places
    // because it will expand the node encoding in a memory cache that is ONLY removed after save()
    public boolean isEmbeddable() {
        return isTerminal() && getMessageLength() <= Trie.MAX_EMBEDDED_NODE_SIZE_IN_BYTES;
    }
    static final int longValThreshold = 32;

    static public boolean terminalNodeEmbeddable(int sharePathLen,byte[] value) {
        int valueLength;
        if (value == null)
            valueLength = 0;
        else
            valueLength = value.length;
        return terminalNodeEmbeddable(sharePathLen, valueLength);
    }

    static public boolean terminalNodeEmbeddable(int sharePathLen,int valueLength) {

        boolean hasLongVal = valueLength > longValThreshold;
        int len =
                1 + // flags
                        sharePathLen +
                        (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES + Uint24.BYTES : valueLength);
        return len <= MAX_EMBEDDED_NODE_SIZE_IN_BYTES;
    }

    // key is the key with exactly collectKeyLen bytes.
    // in non-expanded form (binary)
    // special value Integer.MAX_VALUE means collect them all.
    public void collectKeys(Set<ByteArrayWrapper> set, TrieKeySlice key, int collectKeyLen) {
        if (collectKeyLen != Integer.MAX_VALUE && key.length() > collectKeyLen) {
            return;
        }

        boolean shouldCollect = collectKeyLen == Integer.MAX_VALUE || key.length() == collectKeyLen;
        if (valueLength.compareTo(Uint24.ZERO) > 0 && shouldCollect) {
            // convert bit string into byte[]
            set.add(new ByteArrayWrapper(key.encode()));
        }

        for (byte k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node == null) {
                continue;
            }

            TrieKeySlice nodeKey = key.rebuildSharedPath(k, node.getSharedPath());
            node.collectKeys(set, nodeKey, collectKeyLen);
        }
    }

    // Special value Integer.MAX_VALUE means collect them all.
    public Set<ByteArrayWrapper> collectKeys(int byteSize) {
        Set<ByteArrayWrapper> set = new HashSet<>();

        int bitSize;
        if (byteSize == Integer.MAX_VALUE) {
            bitSize = Integer.MAX_VALUE;
        } else {
            bitSize = byteSize * 8;
        }

        collectKeys(set, getSharedPath(), bitSize);
        return set;
    }

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    public int trieSize() {
        return 1 + this.left.getNode().map(Trie::trieSize).orElse(0)
                + this.right.getNode().map(Trie::trieSize).orElse(0);
    }

    /**
     * get retrieves the associated value given the key
     *
     * @param key   full key
     * @return the associated value, null if the key is not found
     *
     */

    public Trie find(byte[] key) {
        return find(TrieKeySliceFactoryInstance.get().fromKey(key));
    }

    public Trie findReuseSlice(byte[] key) {
        return findReuseSlice(TrieKeySliceFactoryInstance.get().fromKey(key));
    }

    public Trie find(TrieKeySlice key) {
        // 2 keyslice objects created per find() level in the tree
        // one for key.commonPath(sharedPath)
        // another for key.slice(commonPathLength + 1, key.length())
        // I wonder if I can do without them by re-using a slice.
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(sharedPath).length();
        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            return this;
        }

        Trie node = this.retrieveNode(key.get(commonPathLength));
        if (node == null) {
            return null;
        }

        return node.find(key.slice(commonPathLength + 1, key.length()));
    }

    public Trie findReuseSlice(TrieKeySlice key) {
        // 2 keyslice objects created per find() level in the tree
        // one for key.commonPath(sharedPath)
        // another for key.slice(commonPathLength + 1, key.length())
        // I wonder if I can do without them by re-using a slice.
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.getCommonPathLength(sharedPath);
        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            return this;
        }

        Trie node = this.retrieveNode(key.get(commonPathLength));
        if (node == null) {
            return null;
        }
        key.selfSlice(commonPathLength + 1, key.length());
        return node.findReuseSlice(key);
    }

    private void serializeToByteBuffer(ByteBuffer buffer ) {
        Uint24 lvalue = this.valueLength;
        boolean hasLongVal = this.hasLongValue();
        SharedPathSerializer sharedPathSerializer = new SharedPathSerializer(this.sharedPath);

        // current serialization version: 01
        byte flags = 0b01000000;
        if (hasLongVal) {
            flags = (byte) (flags | 0b00100000);
        }

        if (sharedPathSerializer.isPresent()) {
            flags = (byte) (flags | 0b00010000);
        }

        if (!this.left.isEmpty()) {
            flags = (byte) (flags | 0b00001000);
        }

        if (!this.right.isEmpty()) {
            flags = (byte) (flags | 0b00000100);
        }

        if (this.left.isEmbeddable()) {
            flags = (byte) (flags | 0b00000010);
        }

        if (this.right.isEmbeddable()) {
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
    }

    protected void internalToMessage() {
        Uint24 lvalue = this.valueLength;
        boolean hasLongVal = this.hasLongValue();

        SharedPathSerializer sharedPathSerializer = new SharedPathSerializer(this.sharedPath);
        VarInt childrenSize = getChildrenSize();


        ByteBuffer buffer = ByteBuffer.allocate(
                1 + // flags
                        sharedPathSerializer.serializedLength() +
                        this.left.serializedLength() +
                        this.right.serializedLength() +
                        (this.isTerminal() ? 0 : childrenSize.getSizeInBytes()) +
                        (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES + Uint24.BYTES : lvalue.intValue())
        );
        serializeToByteBuffer(buffer);

        encoded = buffer.array();
    }

    public  Trie retrieveNode(byte implicitByte) {
        return retrieveNode(implicitByte,false);
    }

    public  Trie retrieveNode(byte implicitByte,boolean persistent) {
        trieNodesRetrieved++;
        Trie node = getNodeReference(implicitByte).getNode(persistent).orElse(null);
        //if (node!=null)
        //    node.accessThisNode();

        return node;
    }
    public NodeReference getNodeReference(byte implicitByte) {
        return implicitByte == 0 ? this.left : this.right;
    }

    public NodeReference getLeft() {
        return left;
    }

    public NodeReference getRight() {
        return right;
    }

    /**
     * put key with associated value, returning a new NewTrie
     *
     * @param key   key to be updated
     * @param value     associated value
     *
     * @return the new NewTrie containing the tree with the new key value association
     *
     */
    public Trie put(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) {
        // First of all, setting the value as an empty byte array is equivalent
        // to removing the key/value. This is because other parts of the trie make
        // this equivalent. Use always null to mark a node for deletion.
        if (value != null && value.length == 0) {
            value = null;
        }

        Trie trie = this.internalPut(key, value, isRecursiveDelete);

        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || value != null) {
            return trie;
        }

        if (trie.isEmptyTrie()) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.getValueLength().compareTo(Uint24.ZERO) > 0) {
            return trie;
        }

        // both left and right exist (or not) at the same time
        if (trie.getLeft().isEmpty() == trie.getRight().isEmpty()) {
            return trie;
        }

        Trie child;
        byte childImplicitByte;
        if (!trie.getLeft().isEmpty()) {
            child = trie.getLeft().getNode().orElse(null);
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = trie.getRight().getNode().orElse(null);
            childImplicitByte = (byte) 1;
        }

        // could not retrieve from database
        if (child == null) {
            //System.exit(1);
            throw new RuntimeException("not allowed to use the DB");
            //return trie;
        }

        TrieKeySlice newSharedPath = trie.getSharedPath().rebuildSharedPath(childImplicitByte, child.getSharedPath());

        return store.getTrieFactory().cloneTrie(child, newSharedPath);

    }

    private static Uint24 getDataLength(byte[] value) {
        if (value == null) {
            return Uint24.ZERO;
        }

        return new Uint24(value.length);
    }

    private Trie internalPut(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) {
        TrieKeySlice commonPath = key.commonPath(sharedPath);

        // The key to insert is in the middle of our key?
        if (commonPath.length() < sharedPath.length()) {
            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
            if (value == null) {
                return this;
            }

            Trie ret = this.split(commonPath);
            ret = ret.put(key, value, isRecursiveDelete);
            return ret;
        }
        // The key to insert is longer than our key ?
        if (sharedPath.length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            if (this.valueLength.equals(getDataLength(value)) && Arrays.equals(this.getValue(), value)) {
                return this;
            }

            if (isRecursiveDelete) {
                return store.getTrieFactory().newTrie(this.store, this.sharedPath, null);
            }

            if (isEmptyTrie(getDataLength(value), this.left, this.right)) {
                return null;
            }

            // Seems that here we're returning the same node as this.
            // What is changing?
            // We have the same path, but different values
            return store.getTrieFactory().newTrie(
                    this.store,
                    this.sharedPath,
                    cloneArray(value),
                    this.left,
                    this.right,
                    getDataLength(value),
                    null,
                    this.childrenSize,null
            );
        }

        if (isEmptyTrie()) {
            return store.getTrieFactory().newTrie(this.store, key, cloneArray(value));
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        Trie node = retrieveNode(pos);
        if (node == null) {
            node = store.getTrieFactory().newTrie(this.store);
        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        Trie newNode = node.put(subKey, value, isRecursiveDelete);

        // reference equality
        if (newNode == node) {
            return this;
        }

        VarInt newChildrenSize = this.childrenSize;

        NodeReference newNodeReference = store.getNodeReferenceFactory().newReference(
                this.store, newNode);
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newNodeReference;
            newRight = this.right;

            if (newChildrenSize != null) {
                newChildrenSize = new VarInt(childrenSize.value - this.left.referenceSize() + newLeft.referenceSize());
            }
        } else {
            newLeft = this.left;
            newRight = newNodeReference;

            if (childrenSize != null) {
                newChildrenSize = new VarInt(childrenSize.value - this.right.referenceSize() + newRight.referenceSize());
            }
        }

        if (isEmptyTrie(this.valueLength, newLeft, newRight)) {
            return null;
        }

        return store.getTrieFactory().cloneTrie(this, newLeft, newRight, newChildrenSize);
    }

    //Split creates a new node that splits from this node in the point
    // of commonpath
    private Trie split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = sharedPath.slice(commonPathLength + 1, sharedPath.length());
        Trie newChildTrie = store.getTrieFactory().newTrie(this.store, newChildSharedPath, this.value, this.left, this.right, this.valueLength, this.valueHash, this.childrenSize,null);
        NodeReference newChildReference = store.getNodeReferenceFactory().newReference(this.store, newChildTrie);

        // this bit will be implicit and not present in a shared path
        byte pos = sharedPath.get(commonPathLength);

        // Do not compute childrenSize to avoid serializing objects that
        // may be discarded.
        VarInt childrenSize = null; // new VarInt(newChildReference.referenceSize());
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newChildReference;
            newRight = NodeReferenceImpl.empty();
        } else {
            newLeft = NodeReferenceImpl.empty();
            newRight = newChildReference;
        }

        return store.getTrieFactory().newTrie(this.store, commonPath, null, newLeft, newRight, Uint24.ZERO, null, childrenSize,null);
    }

    public boolean isTerminal() {
        return this.left.isEmpty() && this.right.isEmpty();
    }

    public boolean isEmptyTrie() {
        return isEmptyTrie(this.valueLength, this.left, this.right);
    }

    /**
     * isEmptyTrie checks the existence of subnodes, subnodes hashes or value
     *
     * @param valueLength     length of current value
     * @param left      a reference to the left node
     * @param right     a reference to the right node
     *
     * @return true if no data
     */
    private static boolean isEmptyTrie(Uint24 valueLength, NodeReference left, NodeReference right) {
        if (valueLength.compareTo(Uint24.ZERO) > 0) {
            return false;
        }

        return left.isEmpty() && right.isEmpty();
    }


    public boolean hasLongValue() {
        return this.valueLength.compareTo(new Uint24(longValThreshold)) > 0;
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

    public byte[] getValue() {
        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            value = retrieveLongValue();
            checkValueLengthAfterRetrieve();
        }

        return cloneArray(value);
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * It shouldn't be called from outside. It's still public for NodeReference call
     *
     */
    public VarInt getChildrenSize() {
        if (childrenSize == null) {
            if (isTerminal()) {
                childrenSize = new VarInt(0);
            } else {
                childrenSize = new VarInt(this.left.referenceSize() + this.right.referenceSize());
            }
        }

        return childrenSize;
    }

    private byte[] retrieveLongValue() {
        return store.retrieveValue(getValueHash().getBytes());
    }

    private void checkValueLengthAfterRetrieve() {
        // At this time value==null and value.length!=null is really bad.
        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }

        checkValueLength();
    }

    private void checkValueLength() {
        if (value != null && value.length != valueLength.intValue()) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }

        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0 && valueHash == null) {
            // Serious DB inconsistency here
            throw new IllegalArgumentException(INVALID_VALUE_LENGTH);
        }
    }

    public TrieKeySlice getSharedPath() {
        return sharedPath;
    }

    public Iterator<IterationElement> getInOrderIterator() {
        return new InOrderIterator(this);
    }

    public Iterator<IterationElement> getPreOrderIterator() {
        return new PreOrderIterator(this,false);
    }

    /**
     * Returns a Pre-order iterator
     *
     * @param stopAtAccountDepth     if true, only nodes up to the account level will ve traversed
     *                               excluding contract storage and contract code nodes.
     *
     * @return iterator
     */
    public Iterator<IterationElement> getPreOrderIterator(boolean stopAtAccountDepth) {
        return new PreOrderIterator(this,stopAtAccountDepth);
    }

    private static byte[] cloneArray(byte[] array) {
        return array == null ? null : Arrays.copyOf(array, array.length);
    }

    public Iterator<IterationElement> getPostOrderIterator() {
        return new PostOrderIterator(this);
    }

    /**
     * makeEmpyHash creates the hash associated to empty nodes
     *
     * @return a hash with zeroed bytes
     */
    private static Keccak256 makeEmptyHash() {
        return new Keccak256(Keccak256Helper.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }



    @Override
    public int hashCode() {
        return Objects.hashCode(getHash());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        Trie otherTrie = (Trie) other;
        return getHash().equals(otherTrie.getHash());
    }

    @Override
    public String toString() {
        return "no-debug";
        /*
        String s = printParam("TRIE: ", "value", getValue());
        s = printParam(s, "hash0", left.getHash().orElse(null));
        s = printParam(s, "hash1", right.getHash().orElse(null));
        s = printParam(s, "hash", getHash());
        s = printParam(s, "valueHash", getValueHash());
        s = printParam(s, "encodedSharedPath", sharedPath.encode());
        s += "sharedPathLength: " + sharedPath.length() + "\n";
        return s;*/
    }

    private String printParam(String s, String name, byte[] param) {
        if (param != null) {
            s += name + ": " + ByteUtil.toHexString(param) + "\n";
        }
        return s;
    }

    private String printParam(String s, String name, Keccak256 param) {
        if (param != null) {
            s += name + ": " + param.toHexString() + "\n";
        }
        return s;
    }


    // Additional auxiliary methods for Merkle Proof


    public List<Trie> getNodes(byte[] key) {
        return findNodes(key);
    }

    public List<Trie> getNodes(String key) {
        return this.getNodes(key.getBytes(StandardCharsets.UTF_8));
    }


    private List<Trie> findNodes(byte[] key) {
        return findNodes(TrieKeySliceFactoryInstance.get().fromKey(key));
    }


    public List<Trie> findNodes(TrieKeySlice key) {
        if (sharedPath.length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(sharedPath).length();

        if (commonPathLength < sharedPath.length()) {
            return null;
        }

        if (commonPathLength == key.length()) {
            List<Trie> nodes = new ArrayList<>();
            nodes.add(this);
            return nodes;
        }

        Trie node = this.retrieveNode(key.get(commonPathLength));

        if (node == null) {
            return null;
        }

        List<Trie> subnodes = node.findNodes(key.slice(commonPathLength + 1, key.length()));

        if (subnodes == null) {
            return null;
        }

        subnodes.add(this);

        return subnodes;
    }


    public boolean wasSaved() {
        return this.saved;
    }

    public Trie markAsSaved() {
        this.saved = true;
        changeSaved();
        return this;
    }
    final int updaterInterval = 5_000;

    public long countNodes(long limit,int depthLimit,Updater updater) {
        long nodes = 1;

        if (depthLimit<=1)
            return 1;

        depthLimit--;
        limit--;
        if (limit>0)
            for (byte k = 0; k < ARITY; k++) {
                Trie node = this.retrieveNode(k);

                if (node == null) {
                    continue;
                }

                long c =node.countNodes(limit,depthLimit,updater);
                nodes +=c;
                if (updater!=null)  {
                    updater.callCount++;
                    if (updater.callCount % updaterInterval==0)
                        updater.update();
                }

                limit -=c;
                if (limit<=0)
                    break;
            }
        return nodes;
    }

    public long countLeafNodes(long limit,int depthLimit, Updater updater) {
        if (limit<=0)
            return 0;
        if (depthLimit==1)
            return 1;
        depthLimit--;
        long nodes = 0;
        for (byte k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node == null) {
                continue;
            }
            long c = node.countLeafNodes(limit,depthLimit,updater);
            nodes +=c;

            if (updater!=null)  {
                updater.callCount++;
                if (updater.callCount % updaterInterval==0)
                    updater.update();
            }
            limit -=c;
            if (limit<=0)
                break;
        }
        if (nodes==0)
            return 1;
        else
            return nodes;
    }

}
