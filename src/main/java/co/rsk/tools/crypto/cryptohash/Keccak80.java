package co.rsk.tools.crypto.cryptohash;

import org.ethereum.crypto.cryptohash.Digest;
import org.ethereum.crypto.cryptohash.Keccak256;

public class Keccak80 extends org.ethereum.crypto.cryptohash.Keccak256 {


        /**
         * Create the engine.
         */
        public Keccak80()
        {
        }

        public int getBlockLength() {
                return 136;
        }
        /** @see org.ethereum.crypto.cryptohash.Digest */
        public Digest copy()
        {
            return copyState(new co.rsk.tools.crypto.cryptohash.Keccak80());
        }

        /** @see org.ethereum.crypto.cryptohash.Digest */
        public int getDigestLength()
        {
            return 10;
        }
}
