package co.rsk.tools.processor.TrieTests;


import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.Random;

public final class TestUtils {

    private TestUtils() {
    }

    // Fix the Random object to make tests more deterministic. Each new Random object
    // created gets a seed xores with system nanoTime.
    // Alse it reduces the time to get the random in performance tests
    static Random aRandom;

    static public Random getRandom() {
        if (aRandom==null)
            aRandom = new Random(0); // aways use the same seed
        return aRandom;
    }

    public static byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        getRandom().nextBytes(result);
        return result;
    }

    public static BigInteger randomBigInteger(int maxSizeBytes) {
        return new BigInteger(maxSizeBytes*8,getRandom());
    }

    public static Coin randomCoin(int decimalZeros, int maxValue) {
        return new Coin(BigInteger.TEN.pow(decimalZeros).multiply(
                BigInteger.valueOf(getRandom().nextInt(maxValue))));
    }

    public static DataWord randomDataWord() {
        return DataWord.valueOf(randomBytes(32));
    }

    public static RskAddress randomAddress() {
        return new RskAddress(randomBytes(20));
    }

    public static Keccak256 randomHash() {
        return new Keccak256(randomBytes(32));
    }

}
