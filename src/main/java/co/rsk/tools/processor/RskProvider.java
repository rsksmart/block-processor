package co.rsk.tools.processor;

import co.rsk.RskContext;
import co.rsk.config.ConfigLoader;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

public class RskProvider {
    static String foreDatabaseDir = "C:\\Shared\\database\\mainnet";
    public static final String REMASC_BURNED_BALANCE_KEY = "burnedBalance";
    public static final Coin TOTAL_SUPPLY = new Coin(new BigInteger("21000000000000000000000000"));
    public static final BigDecimal WEI = new BigDecimal("1e18");

    private final RskContext ctx;
    private TrieKeyMapper trieKeyMapper;
    private Trie trie;
    private long minBlock;
    private long maxBlock;
    private long step;

    public RskProvider(String rskArgs[]) {
        this.ctx = new RskContext(rskArgs);
        Secp256k1.getInstance();
        ctx.getRskSystemProperties();
        init(ctx.getBlockStore().getMinNumber(), ctx.getBlockStore().getMaxNumber(),1);
    }



    public RskProvider(String rskArgs[], long minBlock, long maxBlock,long step) {
        if (foreDatabaseDir.equals("")) {
            this.ctx = new RskContext(rskArgs);
        } else
        this.ctx  = new RskContext(rskArgs) {
            @Override
            protected RskSystemProperties buildRskSystemProperties() {
                return new RskSystemProperties(new ConfigLoader(this.getCliArgs())) {
                    public String databaseDir() {
                        return foreDatabaseDir;
                    }

                };
            }
        };
        init(minBlock, maxBlock,step);
    }

    public RskProvider(String rskArgs[], long minBlock, long maxBlock) {
        this.ctx = new RskContext(rskArgs);
        init(minBlock, maxBlock,1);
    }

    public static BigDecimal toBTC(Coin wei) {
        return new BigDecimal(wei.asBigInteger()).divide(RskProvider.WEI);
    }

    private void init(long minBlock, long maxBlock, long step) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.minBlock = Long.max(minBlock, 1); // skip the genesis block
        this.maxBlock = Long.min(ctx.getBlockStore().getMaxNumber(), maxBlock);
        this.step = step;
        this.trie = null; // no need to load it in advance
    }

    public void loadTrie() {
        this.trie = getTrie(minBlock);

    }
    public void processBlockchain(RskBlockProcessor blockProcessor) {
        blockProcessor.setContext(ctx);
        blockProcessor.begin();
        long prevPercent =0;
        System.out.println("Processing started");
        for (long blockNumber = minBlock; blockNumber < maxBlock; blockNumber +=step) {
            blockProcessor.setState(blockNumber);
            this.trie =blockProcessor.trie;
            long percent = (blockNumber-minBlock)*100/(maxBlock-minBlock);
            if (percent>prevPercent) {
                System.out.println("Processing: "+blockNumber+" ("+percent+"%)");
                prevPercent = percent;
            }
            if (!blockProcessor.processBlock()) break;
        }
        System.out.println("Processing finished");
        blockProcessor.end();
    }

    public Coin getCirculatingSupply() {
        return TOTAL_SUPPLY.subtract(getBalance(PrecompiledContracts.BRIDGE_ADDR));
    }

    public Coin getBalance(RskAddress addr) {
        byte[] accountData = trie.get(trieKeyMapper.getAccountKey(addr));
        AccountState state = new AccountState(accountData);
        return state.getBalance();
    }

    public byte[] getStoredData(RskAddress addr, String storageKey) {
        return trie.get(trieKeyMapper.getAccountStorageKey(addr, DataWord.fromString(storageKey)));
    }

    private Block getBlock(long blockNumber) {
        return ctx.getBlockStore().getChainBlockByNumber(blockNumber);
    }

    private Trie getTrie(long blockNumber) {
        Block block = getBlock(blockNumber);
        if (block==null)
            throw new RuntimeException("Block not found: " + blockNumber);
        //ctx.getRepositoryLocator().startTrackingAt()
        Keccak256 root =ctx.getStateRootHandler().translate(block.getHeader());
        Optional<Trie> otrie = ctx.getTrieStore().retrieve(root.getBytes());

        if (otrie.isPresent()) {
            return otrie.get();
        } else {
            throw new RuntimeException("Trie not found for block " + blockNumber);
        }
    }
}
