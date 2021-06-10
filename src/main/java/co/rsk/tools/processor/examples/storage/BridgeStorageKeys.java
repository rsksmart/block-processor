package co.rsk.tools.processor.examples.storage;

import org.ethereum.vm.DataWord;

public class BridgeStorageKeys {
        public static final DataWord NEW_FEDERATION_BTC_UTXOS_KEY = DataWord.fromString("newFederationBtcUTXOs");
        public static final DataWord OLD_FEDERATION_BTC_UTXOS_KEY = DataWord.fromString("oldFederationBtcUTXOs");
        public static final DataWord BTC_TX_HASHES_ALREADY_PROCESSED_KEY = DataWord.fromString("btcTxHashesAP");
        public static final DataWord RELEASE_REQUEST_QUEUE = DataWord.fromString("releaseRequestQueue");
        public static final DataWord RELEASE_TX_SET = DataWord.fromString("releaseTransactionSet");
        public static final DataWord RSK_TXS_WAITING_FOR_SIGNATURES_KEY = DataWord.fromString("rskTxsWaitingFS");
        public static final DataWord NEW_FEDERATION_KEY = DataWord.fromString("newFederation");
        public static final DataWord OLD_FEDERATION_KEY = DataWord.fromString("oldFederation");
        public static final DataWord PENDING_FEDERATION_KEY = DataWord.fromString("pendingFederation");
        public static final DataWord FEDERATION_ELECTION_KEY = DataWord.fromString("federationElection");
        public static final DataWord LOCK_ONE_OFF_WHITELIST_KEY = DataWord.fromString("lockWhitelist");
        public static final DataWord LOCK_UNLIMITED_WHITELIST_KEY = DataWord.fromString("unlimitedLockWhitelist");
        public static final DataWord FEE_PER_KB_KEY = DataWord.fromString("feePerKb");
        public static final DataWord FEE_PER_KB_ELECTION_KEY = DataWord.fromString("feePerKbElection");
        public static final DataWord LOCKING_CAP_KEY = DataWord.fromString("lockingCap");
        public static final DataWord RELEASE_REQUEST_QUEUE_WITH_TXHASH = DataWord.fromString("releaseRequestQueueWithTxHash");
        public static final DataWord RELEASE_TX_SET_WITH_TXHASH = DataWord.fromString("releaseTransactionSetWithTxHash");
        public static final DataWord RECEIVE_HEADERS_TIMESTAMP = DataWord.fromString("receiveHeadersLastTimestamp");

        // Federation creation keys
        public static final DataWord ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY = DataWord.fromString("activeFedCreationBlockHeight");
        public static final DataWord NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY = DataWord.fromString("nextFedCreationBlockHeight");
        public static final DataWord LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY = DataWord.fromString("lastRetiredFedP2SHScript");

        // Version keys and versions
        public static final DataWord NEW_FEDERATION_FORMAT_VERSION = DataWord.fromString("newFederationFormatVersion");
        public static final DataWord OLD_FEDERATION_FORMAT_VERSION = DataWord.fromString("oldFederationFormatVersion");
        public static final DataWord PENDING_FEDERATION_FORMAT_VERSION = DataWord.fromString("pendingFederationFormatVersion");
        public static final Integer FEDERATION_FORMAT_VERSION_MULTIKEY = 1000;

        // Dummy value to use when saved Fast Bridge Derivation Argument Hash
        public static final byte FAST_BRIDGE_FEDERATION_DERIVATION_ARGUMENTS_HASH_TRUE_VALUE = (byte) 1;

    }
