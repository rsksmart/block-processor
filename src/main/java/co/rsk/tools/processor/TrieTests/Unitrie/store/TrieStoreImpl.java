/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.tools.processor.TrieTests.Unitrie.store;

import co.rsk.tools.processor.TrieTests.Logger;
import co.rsk.tools.processor.TrieTests.LoggerFactory;
import co.rsk.tools.processor.TrieTests.Unitrie.NodeReference;
import co.rsk.tools.processor.TrieTests.Unitrie.Trie;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieStore;
import org.ethereum.datasource.KeyValueDataSource;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TrieStoreImpl store and retrieve Trie node by hash
 *
 * It saves/retrieves the serialized form (byte array) of a Trie node
 *
 * Internally, it uses a key value data source
 *
 * Created by ajlopez on 08/01/2017.
 */
public class TrieStoreImpl implements TrieStore {

    private static  final boolean storeTraceInfo = true;

    private static final Logger logger = LoggerFactory.getLogger("triestore");

    private static final TraceInfo traceInfo  = new TraceInfo();

    private final KeyValueDataSource store;

    public TrieStoreImpl(KeyValueDataSource store) {
        this.store = store;
    }
    public boolean isTraceEnabled() {
        return false;
    }
    /**
     * Recursively saves all unsaved nodes of this trie to the underlying key-value store
     */
    @Override
    public void save(Trie trie) {
        if (storeTraceInfo) {
            logger.trace("Start saving trie root.");
        }

        // save a trie recursively
        save(trie, true, 0);

        if (traceInfo != null) {
            logger.trace("End saving trie root. No. Retrieves: {}. No. Saves: {}. No. No Saves: {}",
                    traceInfo.numOfRetrieves, traceInfo.numOfSaves, traceInfo.numOfNoSaves);
        }
    }

    public List<String> getTraceInfoReport() {
        List<String> result = new ArrayList<>();
        if (storeTraceInfo) {
            result.add("numOfRetrieves: " + traceInfo.numOfRetrieves);
            result.add("numOfSaves: " + traceInfo.numOfSaves);
            result.add("numOfNoSaves: " + traceInfo.numOfNoSaves);
        }
        return result;
    }
    /**
     * @param isRootNode it is the root node of the trie
     */
    private void save(Trie trie, boolean isRootNode, int level) {
        if (trie.wasSaved()) {
            return;
        }

        logger.trace("Start saving trie, level : {}", level);

        byte[] trieKeyBytes = trie.getHash().getBytes();

        if (isRootNode && this.store.get(trieKeyBytes) != null) {
            // the full trie is already saved
            logger.trace("End saving trie, level : {}, already saved.", level);

            if (traceInfo != null) {
                traceInfo.numOfNoSaves++;
            }

            return;
        }

        if (traceInfo != null) {
            traceInfo.numOfSaves++;
        }

        NodeReference leftNodeReference = trie.getLeft();

        if (!leftNodeReference.wasLoaded()) {
            logger.trace("Start left trie. Level: {}", level);
            leftNodeReference.getNode().ifPresent(t -> save(t, false, level + 1));
        }

        NodeReference rightNodeReference = trie.getRight();

        if (!rightNodeReference.wasLoaded()) {
            logger.trace("Start right trie. Level: {}", level);
            rightNodeReference.getNode().ifPresent(t -> save(t, false, level + 1));
        }

        if (trie.hasLongValue()) {
            // Note that there is no distinction in keys between node data and value data. This could bring problems in
            // the future when trying to garbage-collect the data. We could split the key spaces bit a single
            // overwritten MSB of the hash. Also note that when storing a node that has long value it could be the case
            // that the save the value here, but the value is already present in the database because other node shares
            // the value. This is suboptimal, we could check existence here but maybe the database already has
            // provisions to reduce the load in these cases where a key/value is set equal to the previous value.
            // In particular our levelDB driver has not method to test for the existence of a key without retrieving the
            // value also, so manually checking pre-existence here seems it will add overhead on the average case,
            // instead of reducing it.
            logger.trace("Putting in store, hasLongValue. Level: {}", level);
            this.store.put(trie.getValueHash().getBytes(), trie.getValue());
            logger.trace("End Putting in store, hasLongValue. Level: {}", level);
        }

        if (trie.isEmbeddable() && !isRootNode) {
            logger.trace("End Saving. Level: {}", level);
            return;
        }

        logger.trace("Putting in store trie root.");
        this.store.put(trieKeyBytes, trie.toMessage());
        trie.markAsSaved();
        logger.trace("End putting in store trie root.");
        logger.trace("End Saving trie, level: {}.", level);
    }

    @Override
    public void flush(){
        this.store.flush();
    }

    @Override
    public Optional<Trie> retrieve(byte[] hash) {
        byte[] message = this.store.get(hash);

        if (message == null) {
            return Optional.empty();
        }

        if (storeTraceInfo) {
            traceInfo.numOfRetrieves++;

        }

        Trie trie = Trie.fromMessage(message, this).markAsSaved();
        return Optional.of(trie);
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        if (storeTraceInfo) {
            traceInfo.numOfRetrieves++;
        }

        return this.store.get(hash);
    }

    public void clearTraceInfo() {
        traceInfo.clear();
    }
    @Override
    public void dispose() {
        store.close();
    }

    /**
     * This holds tracing information during execution of the {@link #save(Trie)} method.
     * Should not be used when logger tracing is disabled ({@link Logger#isTraceEnabled()} is {@code false}).
     */
    private static final class TraceInfo {
        private int numOfRetrieves;
        private int numOfSaves;
        private int numOfNoSaves;

        public void clear() {
            numOfNoSaves =0;
            numOfRetrieves =0;
            numOfNoSaves =0;
        }

    }
}