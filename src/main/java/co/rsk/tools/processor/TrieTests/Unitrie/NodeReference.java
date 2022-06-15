/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.core.types.ints.Uint8;
import co.rsk.crypto.Keccak256;

import co.rsk.tools.processor.TrieTests.ohmap.HashEOR;
import org.ethereum.crypto.Keccak256Helper;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface NodeReference {

    public void checkRerefence();

    public boolean isEmpty();
    /**
     * The node or empty if this is an empty reference.
     * If the node is not present but its hash is known, it will be retrieved from the store.
     * If the node could not be retrieved from the store, the Node is stopped using System.exit(1)
     */
    public Optional<Trie> getNode();
    public Optional<Trie> getNode(boolean persistent);


    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHash();

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHashOrchid(boolean isSecure);


    public boolean isEmbeddable();

    // the referenced node was loaded from a TrieStore
    // or has been saved already in the TrieStore
    public boolean isPresentInTrieStore();

    public void markAsPresentInTrieStore();

    // This method should only be called from save()
    public int serializedLength();

    public void serializeInto(ByteBuffer buffer);
    /**
     * @return the tree size in bytes as specified in RSKIP107 plus the actual serialized size
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * Do not use.
     */
    public long referenceSize();

    // removeLazyNode() will leave all cached data but remove the
    // memory pointer to the child node.
    public void removeLazyNode();

    // shrink() will remove all cached data and only leave the pointer
    // to the child node.
    public void shrink();

    // setAbortOnTraverse() prevents this reference to be traversed
    // while executing any method. If data is not cached, then an exception is risen.
    // This is useful when building large trees to make sure certain computations
    // (such as hashing) are not performed more than once.
    public void setAbortOnTraverse(boolean v);

    // setAbortOnRetrieval() prevents this reference to cause a retrieval from
    // the store. Is has the same effect of clearing the store, but on error
    // it will give a much clearer exception (not just null pointer)
    public void setAbortOnRetrieval(boolean v);

    public void clear();
}
