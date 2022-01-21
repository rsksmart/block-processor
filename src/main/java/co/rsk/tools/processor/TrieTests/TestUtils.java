package co.rsk.tools.processor.TrieTests;


import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.Random;

public final class TestUtils {

    private static PseudoRandom pr = new PseudoRandom();

    private TestUtils() {
    }

    public static PseudoRandom getPseudoRandom() {
        return pr;
    }

}
