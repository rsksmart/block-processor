package co.rsk.tools.processor.examples;

import co.rsk.RskContext;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.signature.Secp256k1;

public class CallAnalyzer extends RskBlockProcessor {

    private int numCalls = 0;

    public int getNumCalls() {
        return numCalls;
    }

    @Override
    public boolean processBlock() {
        for (Transaction transaction : this.currentBlock.getTransactionsList()) {
            byte[] code = trie.get(trieKeyMapper.getCodeKey(transaction.getReceiveAddress()));
            byte[] data = transaction.getData();

            if (code != null && data != null) {
                numCalls++;
            }
        }
        return true;
    }
    public static void main (String args[]) {
        Secp256k1.getInstance();
        String[] rskArgs = new String[]{"-Ddatabase.dir=."};
        RskContext ctx = new RskContext(rskArgs);
        ctx.getRskSystemProperties();
    }
    public static void main2 (String args[]) {
        //System.out.println("arg0: "+args[0]);
        RskProvider provider = new RskProvider(args);
        CallAnalyzer analyzer = new CallAnalyzer();

        provider.processBlockchain(analyzer);

        System.out.println("There are " + analyzer.getNumCalls() + " calls.");
    }
}
