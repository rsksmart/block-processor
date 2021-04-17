package co.rsk.tools.processor.examples;

import org.jetbrains.annotations.Nullable;

public class IndexTrieMid {
    // this node associated value, if any
    protected final int value;

    protected final IndexTrieMid left;

    protected final IndexTrieMid right;

    public IndexTrieMid(int value,IndexTrieMid left,IndexTrieMid right) {
        this.value = value;
        this.right = right;
        this.left = left;
    }

    /**
     * trieSize returns the number of nodes in trie
     *
     * @return the number of tries nodes, includes the current one
     */
    public int trieSize() {
        int r =1;
        if (left!=null)
            r += this.left.trieSize();
        if (right!=null)
            r+= this.right.trieSize();
        return r;
    }

    public boolean hasPath() {
        return false;
    }

    public int noPathCount() {
        int r =0;
        if (!hasPath())
            r ++;
        if (left!=null)
            r += this.left.noPathCount();
        if (right!=null)
            r+= this.right.noPathCount();
        return r;
    }

    public int emptyPathTrieSize() {
        int r =0;
        if (left!=null)
            r += this.left.emptyPathTrieSize();
        if (right!=null)
            r+= this.right.emptyPathTrieSize();
        return r;
    }

    /**
     * get retrieves the associated value given the key
     *
     * @param key   full key
     * @return the associated value, null if the key is not found
     *
     */
    @Nullable
    public IndexTrie find(byte[] key) {
        return find(CompactTrieKeySlice.fromKey(key));
    }

}
