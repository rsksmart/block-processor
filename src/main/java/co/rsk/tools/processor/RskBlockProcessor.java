package co.rsk.tools.processor;

import co.rsk.RskContext;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.Block;
import org.ethereum.db.TrieKeyMapper;

import java.util.Optional;

public abstract class RskBlockProcessor {

    protected RskContext ctx;
    protected Trie trie;
    protected Block currentBlock;
    protected TrieKeyMapper trieKeyMapper = new TrieKeyMapper();
    protected boolean loadTrieForEachBlock = true;

    public abstract boolean processBlock();

    public Trie getTrieAtCurrentBlock() {
        return trie;
    }

    public void setLoadTrieForEachBlock(boolean v) {
        loadTrieForEachBlock = v;
    }
    public void begin() {
    }

    public void end() {

    }
    public void setContext(RskContext ctx) {
        this.ctx = ctx;
    }

    public void setState(long blockNumber) {
        currentBlock = ctx.getBlockStore().getChainBlockByNumber(blockNumber);

        // set to false for if not needed
        if (loadTrieForEachBlock)
            setTrie(blockNumber);
    }

    private void setTrie(long blockNumber) {
        Keccak256 root =ctx.getStateRootHandler().translate(currentBlock.getHeader());
        Optional<Trie> otrie = ctx.getTrieStore().retrieve(root.getBytes());

        if (!otrie.isPresent()) {
            throw new RuntimeException("Trie for block " + blockNumber + " not found.");
        }
        trie = otrie.get();
    }
}
