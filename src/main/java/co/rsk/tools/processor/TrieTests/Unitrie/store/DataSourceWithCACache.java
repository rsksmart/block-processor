/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import co.rsk.tools.processor.TrieTests.CompareTries;
import co.rsk.tools.processor.TrieTests.Logger;
import co.rsk.tools.processor.TrieTests.LoggerFactory;
import co.rsk.util.FormatUtils;
import co.rsk.util.MaxSizeHashMap;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;


import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataSourceWithCACache implements KeyValueDataSource {

    private static final Logger logger = LoggerFactory.getLogger("datasourcewithcache");

    private final int cacheSize;
    private final KeyValueDataSource base;
    private final Map<ByteArrayWrapper, byte[]> uncommittedCache;
    private final Map<ByteArrayWrapper, byte[]> committedCache;

    private final AtomicInteger numOfPuts = new AtomicInteger();
    private final AtomicInteger numOfGets = new AtomicInteger();
    private final AtomicInteger numOfGetsFromStore = new AtomicInteger();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();


    private final CacheSnapshotHandler cacheSnapshotHandler;

    public DataSourceWithCACache(KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithCACache(KeyValueDataSource base, int cacheSize,
                               CacheSnapshotHandler cacheSnapshotHandler) {
        this.cacheSize = cacheSize;
        this.base = Objects.requireNonNull(base);
        this.uncommittedCache = new LinkedHashMap<>(cacheSize / 8, (float)0.75, false);
        this.committedCache = Collections.synchronizedMap(makeCommittedCache(cacheSize, cacheSnapshotHandler));
        this.cacheSnapshotHandler = cacheSnapshotHandler;
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);

        boolean traceEnabled = logger.isTraceEnabled();
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value;

        this.lock.readLock().lock();

        try {
            if (committedCache.containsKey(wrappedKey)) {
                return committedCache.get(wrappedKey);
            }

            if (uncommittedCache.containsKey(wrappedKey)) {
                return uncommittedCache.get(wrappedKey);
            }

            value = base.get(key);

            if (traceEnabled) {
                numOfGetsFromStore.incrementAndGet();
            }

            //null value, as expected, is allowed here to be stored in committedCache
            committedCache.put(wrappedKey, value);
        }
        finally {
            if (traceEnabled) {
                numOfGets.incrementAndGet();
            }

            this.lock.readLock().unlock();
        }

        return value;
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);

        return put(wrappedKey, value);
    }

    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        Objects.requireNonNull(value);

        this.lock.writeLock().lock();

        try {
            // here I could check for equal data or just move to the uncommittedCache.
            byte[] priorValue = committedCache.get(wrappedKey);

            if (priorValue != null && Arrays.equals(priorValue, value)) {
                return value;
            }

            committedCache.remove(wrappedKey);
            this.putKeyValue(wrappedKey, value);
        }
        finally {
            if (logger.isTraceEnabled()) {
                numOfPuts.incrementAndGet();
            }

            this.lock.writeLock().unlock();
        }

        return value;
    }

    private void putKeyValue(ByteArrayWrapper key, byte[] value) {
        uncommittedCache.put(key, value);

        if (uncommittedCache.size() > cacheSize) {
            this.flush();
        }
    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
        this.lock.writeLock().lock();

        try {
            // always mark for deletion if we don't know the state in the underlying store
            if (!committedCache.containsKey(wrappedKey)) {
                this.putKeyValue(wrappedKey, null);
                return;
            }

            byte[] valueToRemove = committedCache.get(wrappedKey);

            // a null value means we know for a fact that the key doesn't exist in the underlying store, so this is a noop
            if (valueToRemove != null) {
                this.putKeyValue(wrappedKey, null);
                committedCache.remove(wrappedKey);
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public Set<byte[]> keys() {
        Stream<ByteArrayWrapper> baseKeys;
        Stream<ByteArrayWrapper> committedKeys;
        Stream<ByteArrayWrapper> uncommittedKeys;
        Set<ByteArrayWrapper> uncommittedKeysToRemove;

        this.lock.readLock().lock();

        try {
            baseKeys = base.keys().stream().map(ByteArrayWrapper::new);
            committedKeys = committedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeys = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(Map.Entry::getKey);
            uncommittedKeysToRemove = uncommittedCache.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
        finally {
            this.lock.readLock().unlock();
        }

        Set<ByteArrayWrapper> knownKeys = Stream.concat(Stream.concat(baseKeys, committedKeys), uncommittedKeys)
                .collect(Collectors.toSet());
        knownKeys.removeAll(uncommittedKeysToRemove);

        // note that toSet doesn't work with byte[], so we have to do this extra step
        return knownKeys.stream()
                .map(ByteArrayWrapper::getData)
                .collect(Collectors.toSet());
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        if (rows.containsKey(null) || rows.containsValue(null)) {
            throw new IllegalArgumentException("Cannot update null values");
        }

        // remove overlapping entries
        rows.keySet().removeAll(keysToRemove);

        this.lock.writeLock().lock();

        try {
            rows.forEach(this::put);
            keysToRemove.forEach(this::delete);
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void flush() {
        Map<ByteArrayWrapper, byte[]> uncommittedBatch = new LinkedHashMap<>();

        this.lock.writeLock().lock();

        try {
            long saveTime = System.nanoTime();

            this.uncommittedCache.forEach((key, value) -> {
                if (value != null) {
                    uncommittedBatch.put(key, value);
                }
            });

            Set<ByteArrayWrapper> uncommittedKeysToRemove = uncommittedCache.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).collect(Collectors.toSet());
            base.updateBatch(uncommittedBatch, uncommittedKeysToRemove);
            committedCache.putAll(uncommittedCache);
            uncommittedCache.clear();

            long totalTime = System.nanoTime() - saveTime;

            if (logger.isTraceEnabled()) {
                logger.trace("datasource flush: [{}]seconds", FormatUtils.formatNanosecondsToSeconds(totalTime));
            }
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    public String getName() {
        return base.getName() + "-with-uncommittedCache";
    }

    public void init() {
        base.init();
    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        this.lock.writeLock().lock();

        try {
            flush();
            base.close();
            if (cacheSnapshotHandler != null) {
                cacheSnapshotHandler.save(committedCache);
            }
            uncommittedCache.clear();
            committedCache.clear();
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }

    public void emitLogs() {
        if (!logger.isTraceEnabled()) {
            return;
        }

        this.lock.writeLock().lock();

        try {
            logger.trace("Activity: No. Gets: {}. No. Puts: {}. No. Gets from Store: {}",
                    numOfGets.getAndSet(0),
                    numOfPuts.getAndSet(0),
                    numOfGetsFromStore.getAndSet(0));
        }
        finally {
            this.lock.writeLock().unlock();
        }
    }


    // We need to limit the CAHashMap cache.

    private static Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                                     CacheSnapshotHandler cacheSnapshotHandler) {
        TrieCACacheRelation myKeyValueRelation = new TrieCACacheRelation();
        Map<ByteArrayWrapper, byte[]> cache = new CAHashMap<>(cacheSize, 0.3f,myKeyValueRelation);

        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }

        return cache;
    }
}
