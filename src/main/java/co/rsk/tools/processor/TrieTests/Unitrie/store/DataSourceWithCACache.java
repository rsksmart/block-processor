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


import co.rsk.tools.processor.TrieTests.Logger;
import co.rsk.tools.processor.TrieTests.LoggerFactory;
import co.rsk.util.FormatUtils;

import org.ethereum.datasource.CacheSnapshotHandler;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;

import java.util.*;


public class DataSourceWithCACache extends DataSourceWithCacheAndStats {


    public DataSourceWithCACache(KeyValueDataSource base, int cacheSize) {
        this(base, cacheSize, null);
    }

    public DataSourceWithCACache(KeyValueDataSource base, int cacheSize,
                               CacheSnapshotHandler cacheSnapshotHandler) {
        super(base,cacheSize,cacheSnapshotHandler);

    }

    public String getName() {
        return base.getName() + "-with-DataSourceWithCACache";
    }


    // We need to limit the CAHashMap cache.

    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int cacheSize,
                                                                     CacheSnapshotHandler cacheSnapshotHandler) {
        TrieCACacheRelation myKeyValueRelation = new TrieCACacheRelation();
        Map<ByteArrayWrapper, byte[]> cache = new CAHashMap<>(cacheSize, getDefaultLoadFactor(),myKeyValueRelation);

        if (cacheSnapshotHandler != null) {
            cacheSnapshotHandler.load(cache);
        }

        return cache;
    }

    static public float getDefaultLoadFactor() {
        return 0.3f;
    }

}
