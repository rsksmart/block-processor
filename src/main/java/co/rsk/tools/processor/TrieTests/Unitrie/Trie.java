

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
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
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
public interface Trie {

    static final int MAX_EMBEDDED_NODE_SIZE_IN_BYTES = 44;
    static boolean tryToCompress = true;

    public static boolean isEmbeddable(TrieKeySlice sharedPath,NodeReference left, NodeReference right,Uint24 valueLength) {
        boolean isEmb = false;
        if ((left.isEmpty()) && (right.isEmpty())) {
            isEmb = terminalNodeEmbeddable(
                    new SharedPathSerializer(sharedPath).serializedLength(),
                    valueLength.intValue());
        }
        return isEmb;
    }

    abstract public void compressEncodingsRecursivelly() ;

    abstract public void checkTree() ;

    abstract public void checkReference() ;

    abstract void remapEncoding();

    abstract public void compressIfNecessary();



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
    abstract public Keccak256 getHash() ;

    /**
     * The hash based on pre-RSKIP 107 serialization
     */
    abstract public Keccak256 getHashOrchid(boolean isSecure);


    /**
     * get returns the value associated with a key
     *
     * @param key the key associated with the value, a byte array (variable length)
     *
     * @return  the associated value, a byte array, or null if there is no associated value to the key
     */

    abstract public byte[] get(byte[] key);

    /**
     * get by string, utility method used from test methods
     *
     * @param key   a string, that is converted to a byte array
     * @return a byte array with the associated value
     */
    public byte[] get(String key);

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
    abstract public Trie put(byte[] key, byte[] value);


    public Trie put(ByteArrayWrapper key, byte[] value);
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
    public Trie put(String key, byte[] value) ;

    /**
     * delete update the key to null value
     *
     * @param key   a byte array
     *
     * @return the new top node of the trie with the association removed
     *
     */
    public Trie delete(byte[] key) ;



    /**
     * delete string key, utility method to be used for testing
     *
     * @param key a string
     *
     * @return the new top node of the trie with the key removed
     */
    public Trie delete(String key);

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
    abstract  public byte[] toMessage() ;

    abstract public int getMessageLength() ;

    /**
     * Serialize the node to bytes with the pre-RSKIP 107 format
     */
    abstract public byte[] toMessageOrchid(boolean isSecure);

    // This method should only be called DURING save(). It should not be called in other places
    // because it will expand the node encoding in a memory cache that is ONLY removed after save()
    public boolean isEmbeddable() ;
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



    // Special value Integer.MAX_VALUE means collect them all.
    abstract public Set<ByteArrayWrapper> collectKeys(int byteSize);

    abstract void collectKeys(Set<ByteArrayWrapper> set, TrieKeySlice key, int collectKeyLen);

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    abstract public int trieSize();

    /**
     * get retrieves the associated value given the key
     *
     * @param key   full key
     * @return the associated value, null if the key is not found
     *
     */

    public Trie find(byte[] key) ;

    public Trie findReuseSlice(byte[] key);


    abstract public Trie find(TrieKeySlice key);

    abstract public Trie findReuseSlice(TrieKeySlice key);

    abstract public  Trie retrieveNode(byte implicitByte);

    abstract public NodeReference getNodeReference(byte implicitByte);

    abstract public NodeReference getLeft();

    abstract public NodeReference getRight();

    /**
     * put key with associated value, returning a new NewTrie
     *
     * @param key   key to be updated
     * @param value     associated value
     *
     * @return the new NewTrie containing the tree with the new key value association
     *
     */
    abstract public Trie put(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) ;


    abstract public boolean isTerminal();
    abstract public boolean isEmptyTrie();



    abstract public boolean hasLongValue();
    abstract public Uint24 getValueLength();


    abstract public Keccak256 getValueHash();
    abstract public byte[] getValue();

    /**
     * @return the tree size in bytes as specified in RSKIP107
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * It shouldn't be called from outside. It's still public for NodeReference call
     *
     */
    abstract public VarInt getChildrenSize();

    abstract public TrieKeySlice getSharedPath();

    public Iterator<IterationElement> getInOrderIterator() ;

    public Iterator<IterationElement> getPreOrderIterator() ;

     /**
     * Returns a Pre-order iterator
     *
     * @param stopAtAccountDepth     if true, only nodes up to the account level will ve traversed
     *                               excluding contract storage and contract code nodes.
     *
     * @return iterator
     */
    public Iterator<IterationElement> getPreOrderIterator(boolean stopAtAccountDepth) ;

    public Iterator<IterationElement> getPostOrderIterator();

    /**
     * makeEmpyHash creates the hash associated to empty nodes
     *
     * @return a hash with zeroed bytes
     */
    private static Keccak256 makeEmptyHash() {
        return new Keccak256(Keccak256Helper.keccak256(RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }

    abstract public EncodedObjectRef getEncodedRef();

    // Additional auxiliary methods for Merkle Proof


    public List<Trie> getNodes(byte[] key);

    public List<Trie> getNodes(String key);


    private List<Trie> findNodes(byte[] key) {
        return findNodes(TrieKeySliceFactoryInstance.get().fromKey(key));
    }


    abstract public List<Trie> findNodes(TrieKeySlice key);

    abstract public boolean wasSaved();

    abstract public Trie markAsSaved();
    abstract public long countNodes(long limit);
    abstract public long countLeafNodes(long limit);

}
