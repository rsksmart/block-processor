package co.rsk.tools.processor.TrieTests;

import co.rsk.core.Coin;
import org.ethereum.core.AccountState;
import org.ethereum.core.Denomination;

import java.math.BigInteger;

public class StateTrieSimulator {

    public enum Blockchain {
        RSK,
        Ethereum
    }

    public enum SimMode {
        simERC20LongBalances,
        simERC20Balances,
        simEOAs,
        simMicroTest
    }

    public int accountSize;
    public int valueSize;
    public int fixKeySize;
    public int varKeySize;

    SimMode simMode;
    Blockchain blockchain;

    public void setBlockchain(Blockchain b) {
        blockchain = b;
    }

    public void setSimMode(SimMode sm) {
        simMode = sm;
    }

    public void computeAverageAccountSize() {

        if (blockchain==Blockchain.Ethereum) {
            co.rsk.tools.ethereum.AccountState a = new co.rsk.tools.ethereum.AccountState(
                    BigInteger.valueOf(100), // nonce
                    Denomination.satoshisToWeis(10_1000_1000));

            accountSize = a.getEncoded().length;
        } else {
            AccountState a = new AccountState(BigInteger.valueOf(100),
                    new Coin(
                            Denomination.satoshisToWeis(10_1000_1000)));

            accountSize = a.getEncoded().length;
        }
    }

    public void computeKeySizes() {
        if (blockchain==Blockchain.Ethereum) {
            if (simMode== SimMode.simEOAs) {
                accountSize = 78;
                fixKeySize = 0;
                // A cell address contains 32 bytes (hash of address)
                valueSize = accountSize;
                varKeySize = 32;
            }
        } else {
            if (simMode == SimMode.simMicroTest) {
                fixKeySize = 1;
                varKeySize = 1;// test
                valueSize = 1;
            } else if (simMode == SimMode.simERC20LongBalances) {

                fixKeySize = 10 + 20 + 1;
                // Solidity key is the hash of something, so it occupies 32 bytes
                varKeySize = 10 + 32; // 10 bytes randomizer + 10 bytes address

                // The number of decimals is 10^18 = 2^60.
                // The number of tokens created is 100 billion = 2^37
                // total: 2^97 (rounded to bytes == 13 bytes)
                valueSize = 13;
            } else if (simMode == SimMode.simERC20Balances) {
                // We assume the contract uses an optimized ERC20 balance
                // Thereare 1 billion tokens (2^30).
                // The minimum unit is one billion. (2^30)
                // This is less than 2^64 (8 bytes)

                // We omit the first byte (domain separation) because it only
                // creates a single node in the trie.
                // Storage addresses are 20-byte in length.
                fixKeySize = 10 + 20 + 1;

                // Here we assume an efficient packing on storage addresses.
                // Solidity will not efficiently pack addresses, as it will
                // turn them 32 bytes long by hashing.
                varKeySize = 10 + 20; // 10 bytes randomizer + 10 bytes address
                valueSize = 8;
            } else {
                // accountSize = 12
                accountSize = 12;
                fixKeySize = 1;
                // A cell address contains 10+20 account bytes, plus 10+32.
                // + 1 domain
                // + 1 intermediate (always fixed byte, we can skip for these tests)
                valueSize = accountSize;
                varKeySize = 10 + 20;//+10+32;
            }
        }
    }

}
