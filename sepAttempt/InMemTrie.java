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

import co.rsk.tools.processor.Index.TrieKeySlice;
import co.rsk.tools.processor.TrieTests.*;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;


import java.nio.ByteBuffer;
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
public class InMemTrie implements Trie {

    // Static 10 megabytes
    static public byte[] mem = new byte[1000*1000*10];

    private static final Profiler profiler = ProfilerFactory.getInstance();

    private static final int ARITY = 2;
    private static final String INVALID_VALUE_LENGTH = "Invalid value length";

    // all zeroed, default hash for empty nodes
    private static final Keccak256 EMPTY_HASH = makeEmptyHash();

    private NodeReference left;

    private NodeReference right;
    
    // inital 12 bytes
    private int leftOfs =-1; // 4 bytes, total 16

    private int rightOfs = -1; //4 bytes, total 20

    // permanent  storage of encoding.
    private int encodedOfs = -1; // 4 bytes, total 24

    private TrieData data; // this is temporary information

    // already saved in store flag
    // we'll marc if saved inside the embedded object somehow
    private volatile boolean saved;

    // default constructor, no secure
    public InMemTrie() {
        this( TrieKeySliceFactoryInstance.get().empty(), null);
    }

    private InMemTrie(TrieKeySlice sharedPath, byte[] value) {
        this( sharedPath, value, getDataLength(value), null, (0));
    }

    public InMemTrie( TrieKeySlice sharedPath, byte[] value, InMemTrie left, InMemTrie right, Uint24 valueLength, Keccak256 valueHash) {
        this(sharedPath, value, left, right, valueLength, valueHash, -1);
    }
    public InMemTrie( TrieKeySlice sharedPath, byte[] value,
                      NodeReference left, NodeReference right, Uint24 valueLength, Keccak256 valueHash) {
        this(sharedPath, value, left, right, valueLength, valueHash, -1);
    }

    // full constructor
    private InMemTrie(TrieKeySlice sharedPath, byte[] value,
                      InMemTrie left, InMemTrie right,
                      NodeReference leftRef, NodeReference rightRef,
                      Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        data =new TrieData(sharedPath,value, valueLength, valueHash,  childrenSize);
        this.leftOfs = left.encodedOfs;
        this.rightOfs = right.encodedOfs;
        this.left = leftRef;
        this.right = rightRef;
        storeValue(value);
    }

    private InMemTrie(TrieKeySlice sharedPath, byte[] value,
                      NodeReference leftRef, NodeReference rightRef,
                      Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        data =new TrieData(sharedPath,value, valueLength, valueHash,  childrenSize);
        this.leftOfs = -1;
        this.rightOfs = -1;
        this.left = leftRef;
        this.right = rightRef;
        storeValue(value);
    }

    private InMemTrie(TrieKeySlice sharedPath, byte[] value,
                      InMemTrie left, InMemTrie right,
                      Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        data =new TrieData(sharedPath,value, valueLength, valueHash,  childrenSize);
        this.leftOfs = left.encodedOfs;
        this.rightOfs = right.encodedOfs;
        storeValue(value);
    }

    int memTop = 0;
    private void storeValue(byte[] value) {
        if (value==null)return;

        ByteBuffer buffer = ByteBuffer.wrap(mem,memTop,mem.length-memTop);
        data.serializeInto(buffer);
        // the length is the first byte
        //int len = buffer.get();
        int len = mem[memTop];
        this.encodedOfs = memTop;
        memTop += len;
    }
    private InMemTrie(TrieKeySlice sharedPath, byte[] value,
                      Uint24 valueLength, Keccak256 valueHash, long childrenSize) {
        data =new TrieData(sharedPath,value, valueLength, valueHash,  childrenSize);
        storeValue(value);
    }

    private InMemTrie(TrieKeySlice sharedPath, byte[] value,
                      int  leftOfs,
                      int rightOfs, Uint24 valueLength,
                      Keccak256 valueHash, long childrenSize) {
        data =new TrieData(sharedPath,value, valueLength, valueHash,  childrenSize);
        this.leftOfs = leftOfs;
        this.rightOfs = rightOfs;
        storeValue(value);
    }

    public  int serializedLength() {
        return data.getSerializedLength();
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
        return data.getHash();
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

        return trie == null ? new InMemTrie() : trie;
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

        return trie == null ? new InMemTrie() : trie;
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
        if (data.getValueLength().compareTo(Uint24.ZERO) > 0 && shouldCollect) {
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
        //return 1 + this.left.getNode().map(InMemTrie::trieSize).orElse(0)
        //        + this.right.getNode().map(InMemTrie::trieSize).orElse(0);
        return -1; // TO DO
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

    public Trie find(TrieKeySlice key) {
        if (data.getSharedPath().length() > key.length()) {
            return null;
        }

        int commonPathLength = key.commonPath(getSharedPath()).length();
        if (commonPathLength < getSharedPath().length()) {
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
        if (implicitByte == 0)
            return getLeft();
        else
            return getRight();
    }

    private NodeReference createNodeReference(Keccak256 ahash) {
        return new NodeReference(null,ahash);

    }
    public NodeReference getLeft() {
        if (left!=null) return left;

        left = createNodeReference(this.data.getLeftHash());
        return left;
    }

    public NodeReference getRight() {
        if (right!=null) return right;

        right = createNodeReference(this.data.getRightHash());
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

        InMemTrie trie = this.internalPut(key, value, isRecursiveDelete);

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
        if (trie.left.isEmpty() == trie.right.isEmpty()) {
            return trie;
        }

        InMemTrie child;
        byte childImplicitByte;
        if (!trie.left.isEmpty()) {
            child = (InMemTrie) trie.left.getNode().orElse(null);
            childImplicitByte = (byte) 0;
        } else { // has right node
            child = (InMemTrie) trie.right.getNode().orElse(null);
            childImplicitByte = (byte) 1;
        }

        // could not retrieve from database
        if (child == null) {
            return trie;
        }

        TrieKeySlice newSharedPath = trie.getSharedPath().rebuildSharedPath(childImplicitByte, child.getSharedPath());

        return new InMemTrie(newSharedPath, child.getValue(),
                child.left, child.right, child.getValueLength(), child.getValueHash(), child.getChildrenSize());
    }

    private static Uint24 getDataLength(byte[] value) {
        if (value == null) {
            return Uint24.ZERO;
        }

        return new Uint24(value.length);
    }

    private InMemTrie internalPut(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) {
        TrieKeySlice commonPath = key.commonPath(getSharedPath());
        if (commonPath.length() < getSharedPath().length()) {
            // when we are removing a key we know splitting is not necessary. the key wasn't found at this point.
            if (value == null) {
                return this;
            }

            return (InMemTrie) this.split(commonPath).put(key, value, isRecursiveDelete);
        }

        if (getSharedPath().length() >= key.length()) {
            // To compare values we need to retrieve the previous value
            // if not already done so. We could also compare by hash, to avoid retrieval
            // We do a small optimization here: if sizes are not equal, then values
            // obviously are not.
            if (this.getValueLength().equals(getDataLength(value)) && Arrays.equals(this.getValue(), value)) {
                return this;
            }

            if (isRecursiveDelete) {
                return new InMemTrie( this.getSharedPath(), null);
            }

            if (isEmptyTrie(getDataLength(value), this.left, this.right)) {
                return null;
            }

            return new InMemTrie(
                    this.getSharedPath(),
                    cloneArray(value),
                    this.left,
                    this.right,
                    getDataLength(value),
                    null,
                    this.getChildrenSize()
            );
        }

        if (isEmptyTrie()) {
            return new InMemTrie( key, cloneArray(value));
        }

        // this bit will be implicit and not present in a shared path
        byte pos = key.get(getSharedPath().length());

        Trie node = retrieveNode(pos);
        if (node == null) {
            node = new InMemTrie();
        }

        TrieKeySlice subKey = key.slice(getSharedPath().length() + 1, key.length());
        Trie newNode = node.put(subKey, value, isRecursiveDelete);

        // reference equality
        if (newNode == node) {
            return this;
        }

        long childrenSize = this.getChildrenSize();

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

        if (isEmptyTrie(this.getValueLength(), newLeft, newRight)) {
            return null;
        }

        return new InMemTrie( this.getSharedPath(), this.getValue(),
                newLeft, newRight,
                this.data.getValueLength(),
                this.data.getValueHash(), childrenSize);
    }

    private Trie split(TrieKeySlice commonPath) {
        int commonPathLength = commonPath.length();
        TrieKeySlice newChildSharedPath = getSharedPath().slice(commonPathLength + 1, getSharedPath().length());
        Trie newChildTrie = new InMemTrie( newChildSharedPath, this.data.getValue(),
                this.left, this.right, this.getValueLength(), this.getValueHash(), this.getChildrenSize());
        NodeReference newChildReference = new NodeReference( newChildTrie, null);

        // this bit will be implicit and not present in a shared path
        byte pos = getSharedPath().get(commonPathLength);

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

        return new InMemTrie( commonPath, null, newLeft, newRight, Uint24.ZERO, null, childrenSize);
    }

    public boolean isTerminal() {
        return this.left.isEmpty() && this.right.isEmpty();
    }

    public boolean isEmptyTrie() {
        return isEmptyTrie(this.getValueLength(), this.left, this.right);
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

        // TO DO: see when node references are null
        if ((left==null) && (right==null))
            return true;
        return left.isEmpty() && right.isEmpty();
    }

    public boolean hasLongValue() {
        return this.getValueLength().compareTo(new Uint24(32)) > 0;
    }

    public Uint24 getValueLength() {
        // extract
        return data.getValueLength();
    }

    public Keccak256 getValueHash() {
        return data.getValueHash();    }

    public byte[] getValue() {
        return cloneArray(data.getValue());
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * It shouldn't be called from outside. It's still public for NodeReference call
     *
     */
    public long getReferenceSize() {
        // todo: add self size
        return getChildrenSize();
    }

    public long  getChildrenSize() {
        return -1;
        // TO DO
        /*if (childrenSize ==-1) {
            if (isTerminal()) {
                childrenSize = 0;
            } else {
                childrenSize =this.left.getReferenceSize() + this.right.getReferenceSize();
            }
        }

        return childrenSize;*/
    }

    public TrieKeySlice getSharedPath() {
        // retrieve
        return data.getSharedPath();
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
        String s = data.toString();
        return s;
    }



    /**
     * Returns the leftmost node that has not yet been visited that node is normally on top of the stack
     */





    // Additional auxiliary methods for Merkle Proof

    public List<Trie> getNodes(byte[] key) {
        return findNodes(key);
    }

    public List<Trie> getNodes(String key) {
        return this.getNodes(key.getBytes(StandardCharsets.UTF_8));
    }


    public List<Trie> findNodes(byte[] key) {
        return findNodes(TrieKeySliceFactoryInstance.get().fromKey(key));
    }

    public List<Trie> findNodes(TrieKeySlice key) {
        TrieKeySlice sharedPath = getSharedPath();

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
        return this;
    }
}
