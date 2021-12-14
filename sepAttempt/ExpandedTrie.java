package co.rsk.tools.processor.TrieTests.sepAttempt;
/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;

import co.rsk.tools.processor.Index.CompactTrieKeySlice;
import co.rsk.tools.processor.Index.TrieKeySlice;
import co.rsk.tools.processor.Index.TrieKeySliceFactory;
import co.rsk.tools.processor.TrieTests.*;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * A binary trie node.
 *
 * Each node has an optional associated value (a byte array)
 *
 * A node is referenced via a key (a byte array)
 *
 * A node can be serialized to/from a message (a byte array)
 *
 * A node has a hash (keccak256 of its serialization)
 *
 * A node is immutable: to add/change a value or key, a new node is created
 *
 * An empty node has no subnodes and a null value
 */
public class ExpandedTrie implements Trie {

    private static final Profiler profiler = ProfilerFactory.getInstance();

    private static final int ARITY = 2;
    private static final String INVALID_VALUE_LENGTH = "Invalid value length";

    // all zeroed, default hash for empty nodes
    private static final Keccak256 EMPTY_HASH = makeEmptyHash();
    private static final TrieKeySliceFactory TrieKeySliceFactory = CompactTrieKeySlice.getFactory();

    // this node associated value, if any
    private byte[] value;

    private final NodeReference left;

    private final NodeReference right;

    // this node hash value
    private Keccak256 hash;

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


    // already saved in store flag
    private volatile boolean saved;

    // shared Path
    private final TrieKeySlice sharedPath;


    // default constructor, no secure
    public ExpandedTrie() {
        this( TrieKeySliceFactory.empty(), null);
    }

    private ExpandedTrie(TrieKeySlice sharedPath, byte[] value) {
        this( sharedPath, value, NodeReference.empty(), NodeReference.empty(), getDataLength(value), null, (0));
    }

    public ExpandedTrie(TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash) {
        this(sharedPath, value, left, right, valueLength, valueHash, -1);
    }


    // full constructor
    private ExpandedTrie(TrieKeySlice sharedPath, byte[] value, NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        this.value = value;
        this.left = left;
        this.right = right;
        this.sharedPath = sharedPath;
        this.valueLength = valueLength;
        this.valueHash = valueHash;
        this.childrenSize = childrenSize;

        checkValueLength();
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

        // Just return some hash, we won't use them
        this.hash = new Keccak256(Keccak256Helper.keccak256(new byte[]{}));
        return this.hash.copy();
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
        TrieKeySlice keySlice = TrieKeySliceFactory.fromKey(key);
        Trie trie = put(keySlice, value, false);

        return trie == null ? new ExpandedTrie() : trie;
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
        TrieKeySlice keySlice = TrieKeySliceFactory.fromKey(key);
        Trie trie = put(keySlice, null, true);

        return trie == null ? new ExpandedTrie() : trie;
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
    public int countLeafNodes() {
        int nodes = 0;
        for (byte k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node == null) {
                continue;
            }

            nodes +=node.countLeafNodes();
        }
        if (nodes==0)
            return 1;
        else
            return nodes;
    }

    public int countNodes() {
        int nodes = 1;
        for (byte k = 0; k < ARITY; k++) {
            Trie node = this.retrieveNode(k);

            if (node == null) {
                continue;
            }

            nodes +=node.countNodes();
        }
        return nodes;
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
        return find(TrieKeySliceFactory.fromKey(key));
    }


    public Trie find(TrieKeySlice key) {
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

    public Trie retrieveNode(byte implicitByte) {
        return getNodeReference(implicitByte).getNode().orElse(null);
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

        ExpandedTrie trie = this.internalPut(key, value, isRecursiveDelete);

        // the following code coalesces nodes if needed for delete operation

        // it's null or it is not a delete operation
        if (trie == null || value != null) {
            return trie;
        }

        if (trie.isEmptyTrie()) {
            return null;
        }

        // only coalesce if node has only one child and no value
        if (trie.valueLength.compareTo(Uint24.ZERO) > 0) {
            return trie;
        }

        // both left and right exist (or not) at the same time
        if (trie.left.isEmpty() == trie.right.isEmpty()) {
            return trie;
        }

        ExpandedTrie child;
        byte childImplicitByte;
        if (!trie.left.isEmpty()) {
            child = (ExpandedTrie) trie.left.getNode().orElse(null);
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = (ExpandedTrie) trie.right.getNode().orElse(null);
            childImplicitByte = (byte) 1;
        }

        // could not retrieve from database
        if (child == null) {
            return trie;
        }

        TrieKeySlice newSharedPath = trie.sharedPath.rebuildSharedPath(childImplicitByte, child.sharedPath);

        return new ExpandedTrie(newSharedPath, child.value, child.left, child.right, child.valueLength, child.valueHash, child.childrenSize);
    }

    private static Uint24 getDataLength(byte[] value) {
        if (value == null) {
            return Uint24.ZERO;
        }

        return new Uint24(value.length);
    }

    private ExpandedTrie internalPut(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) {
        TrieKeySlice commonPath = key.commonPath(sharedPath);
        if (commonPath.length() < sharedPath.length()) {
            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
            if (value == null) {
                return this;
            }

            return (ExpandedTrie) this.split(commonPath).put(key, value, isRecursiveDelete);
        }

        if (sharedPath.length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            if (this.valueLength.equals(getDataLength(value)) && Arrays.equals(this.getValue(), value)) {
                return this;
            }

            if (isRecursiveDelete) {
                return new ExpandedTrie( this.sharedPath, null);
            }

            if (isEmptyTrie(getDataLength(value), this.left, this.right)) {
                return null;
            }

            return new ExpandedTrie(
                    this.sharedPath,
                    cloneArray(value),
                    this.left,
                    this.right,
                    getDataLength(value),
                    null,
                    this.childrenSize
            );
        }

        if (isEmptyTrie()) {
            return new ExpandedTrie( key, cloneArray(value));
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(sharedPath.length());

        Trie node = retrieveNode(pos);
        if (node == null) {
            node = new ExpandedTrie();
        }

        TrieKeySlice subKey = key.slice(sharedPath.length() + 1, key.length());
        Trie newNode = node.put(subKey, value, isRecursiveDelete);

        // reference equality
        if (newNode == node) {
            return this;
        }

        long childrenSize = this.childrenSize;

        NodeReference newNodeReference = new NodeReference(newNode, null);
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newNodeReference;
            newRight = this.right;

            if (childrenSize != -1) {
                childrenSize = childrenSize - this.left.referenceSize() + newLeft.referenceSize();
            }
        } else {
            newLeft = this.left;
            newRight = newNodeReference;

            if (childrenSize != -1) {
                childrenSize = childrenSize - this.right.referenceSize() + newRight.referenceSize();
            }
        }

        if (isEmptyTrie(this.valueLength, newLeft, newRight)) {
            return null;
        }

        return new ExpandedTrie( this.sharedPath, this.value, newLeft, newRight, this.valueLength, this.valueHash, childrenSize);
    }

    private ExpandedTrie split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = sharedPath.slice(commonPathLength + 1, sharedPath.length());
        ExpandedTrie newChildTrie = new ExpandedTrie( newChildSharedPath, this.value, this.left, this.right, this.valueLength, this.valueHash, this.childrenSize);
        NodeReference newChildReference = new NodeReference( newChildTrie, null);

        // this bit will be implicit and not present in a shared path
        byte pos = sharedPath.get(commonPathLength);

        long childrenSize = newChildReference.referenceSize();
        NodeReference newLeft;
        NodeReference newRight;
        if (pos == 0) {
            newLeft = newChildReference;
            newRight = NodeReference.empty();
        } else {
            newLeft = NodeReference.empty();
            newRight = newChildReference;
        }

        return new ExpandedTrie( commonPath, null, newLeft, newRight, Uint24.ZERO, null, childrenSize);
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

    public byte[] getValue() {
        if (value == null && valueLength.compareTo(Uint24.ZERO) > 0) {
            //TO DO: fix this
            // some random data
            value = new byte[]{1,2,3};
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
    public long  getChildrenSize() {
        if (childrenSize ==-1) {
            if (isTerminal()) {
                childrenSize = 0;
            } else {
                childrenSize =this.left.referenceSize() + this.right.referenceSize();
            }
        }

        return childrenSize;
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
        return new PreOrderIterator(this);
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

        ExpandedTrie otherTrie = (ExpandedTrie) other;
        return getHash().equals(otherTrie.getHash());
    }

    @Override
    public String toString() {
        String s = printParam("TRIE: ", "value", getValue());
        s = printParam(s, "hash0", left.getHash().orElse(null));
        s = printParam(s, "hash1", right.getHash().orElse(null));
        s = printParam(s, "hash", getHash());
        s = printParam(s, "valueHash", getValueHash());
        s = printParam(s, "encodedSharedPath", sharedPath.encode());
        s += "sharedPathLength: " + sharedPath.length() + "\n";
        return s;
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


    public List<Trie> findNodes(byte[] key) {
        return findNodes(TrieKeySliceFactory.fromKey(key));
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

    public ExpandedTrie markAsSaved() {
        this.saved = true;
        return this;
    }
}
