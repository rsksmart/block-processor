package co.rsk.tools.processor.TrieTests;

import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.Index.TrieKeySlice;
import org.ethereum.db.ByteArrayWrapper;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public interface Trie {

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
        public Keccak256 getHash();
        /**
         * get returns the value associated with a key
         *
         * @param key the key associated with the value, a byte array (variable length)
         *
         * @return  the associated value, a byte array, or null if there is no associated value to the key
         */

        public byte[] get(byte[] key);
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
        public Trie put(byte[] key, byte[] value);

        public Trie put(ByteArrayWrapper key, byte[] value) ;
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
        public Trie put(String key, byte[] value);

        /**
         * delete update the key to null value
         *
         * @param key   a byte array
         *
         * @return the new top node of the trie with the association removed
         *
         */
        public Trie delete(byte[] key);

        // This is O(1). The node with exact key "key" MUST exists.
        public Trie deleteRecursive(byte[] key);
        /**
         * delete string key, utility method to be used for testing
         *
         * @param key a string
         *
         * @return the new top node of the trie with the key removed
         */
        public Trie delete(String key);

        // key is the key with exactly collectKeyLen bytes.
        // in non-expanded form (binary)
        // special value Integer.MAX_VALUE means collect them all.
        public  void collectKeys(Set<ByteArrayWrapper> set, TrieKeySlice key, int collectKeyLen) ;

    // Special value Integer.MAX_VALUE means collect them all.
        public Set<ByteArrayWrapper> collectKeys(int byteSize);
        /**
         * trieSize returns the number of nodes in trie
         *
         * @return the number of tries nodes, includes the current one
         */
        public int trieSize();
        /**
         * get retrieves the associated value given the key
         *
         * @param key   full key
         * @return the associated value, null if the key is not found
         *
         */

        public Trie find(byte[] key);
        public Trie find(TrieKeySlice key);
        public List<Trie> findNodes(byte[] key);
        public Trie retrieveNode(byte implicitByte);
        public List<Trie> findNodes(TrieKeySlice key);

        public NodeReference getNodeReference(byte implicitByte);

        public NodeReference getLeft();
        public NodeReference getRight();
        /**
         * put key with associated value, returning a new NewTrie
         *
         * @ param key   key to be updated
         * @ param value     associated value
         *
         * @return the new NewTrie containing the tree with the new key value association
         *
         */
        public Trie put(TrieKeySlice key, byte[] value, boolean isRecursiveDelete);

        //private static Uint24 getDataLength(byte[] value);
        //private Trie internalPut(TrieKeySlice key, byte[] value, boolean isRecursiveDelete) ;
        //private Trie split(TrieKeySlice commonPath);

        public boolean isTerminal() ;

        public boolean isEmptyTrie() ;


        public boolean hasLongValue() ;

        public Uint24 getValueLength() ;
        public Keccak256 getValueHash() ;


        public byte[] getValue();

        /**
         * @return the tree size in bytes as specified in RSKIP107
         *
         * This method will EXPAND internal encoding caches without removing them afterwards.
         * It shouldn't be called from outside. It's still public for NodeReference call
         *
         */
        public long  getChildrenSize() ;


        public TrieKeySlice getSharedPath();
        /*
        public Iterator<co.rsk.tools.processor.TrieTests.ExpandedTrie.IterationElement> getInOrderIterator() {
            return new co.rsk.tools.processor.TrieTests.ExpandedTrie.InOrderIterator(this);
        }

        public Iterator<co.rsk.tools.processor.TrieTests.ExpandedTrie.IterationElement> getPreOrderIterator() {
            return new co.rsk.tools.processor.TrieTests.ExpandedTrie.PreOrderIterator(this);
        }


        public Iterator<co.rsk.tools.processor.TrieTests.ExpandedTrie.IterationElement> getPostOrderIterator() {
            return new co.rsk.tools.processor.TrieTests.ExpandedTrie.PostOrderIterator(this);
        }
        */


}
