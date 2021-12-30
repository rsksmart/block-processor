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

public class NodeReference {

    private static final NodeReference EMPTY = new NodeReference(null, null, null, null);

    private final TrieStore store;

    private Trie lazyNode;
    private Keccak256 lazyHash;
    private EncodedObjectRef encodedRef;

    public EncodedObjectRef getEncodedRef() {
        if ((encodedRef == null) && (lazyNode!=null)) {
            // it may have not been computed.
            // This happens when creating tries by hand (and not specifying the
            // encodeOfs argument in the constructor)
            encodedRef = lazyNode.getEncodedRef();
        }
        return encodedRef;
    }

    public void checkRerefence() {
        GlobalEncodedObjectStore.get().checkDuringRemap(encodedRef);
    }

    public void setEncodedRef(EncodedObjectRef ofs) {
        encodedRef = ofs;
        GlobalEncodedObjectStore.get().verifyEOR(ofs);
    }

    public void recomputeEncodeOfs() {
        encodedRef = getNode().get().getEncodedRef();
    }

    public NodeReference(TrieStore store,  Trie node, Keccak256 hash,
                         EncodedObjectRef aEndodedOfs) {
        this.store = store;
        if (node != null && node.isEmptyTrie()) {
            this.lazyNode = null;
            this.lazyHash = null;
        } else {
            this.lazyNode = node;
            this.lazyHash = hash;
            if (hash!=null) {
                this.lazyHash = hash;
            }
        }

     this.encodedRef = aEndodedOfs;

        if (GlobalEncodedObjectStore.get()!=null) {
            GlobalEncodedObjectStore.get().verifyEOR(aEndodedOfs);
        }
    }

    public Trie getDynamicLazyNode() {
        if (GlobalEncodedObjectStore.get().accessByHash()) {
            encodedRef = new HashEOR(lazyHash);
        }
        if (encodedRef ==null) return null;
        ObjectReference r  = GlobalEncodedObjectStore.get().retrieve(encodedRef);
        try {
            //if (encodedOfs==161722718)
            //    encodedOfs=encodedOfs;
            Trie node = Trie.fromMessage(r.message, encodedRef, r.leftRef, r.rightRef, store);
            return node;
        } catch (java.nio.BufferUnderflowException e) {
            //encodeOfs: 3386664381
            //encodeOfs: 2
            /*
            System.out.println("encodeOfs: "+encodedOfs);
            int s = ObjectHeap.get().getSpaceNumOfPointer(encodedOfs);
            System.out.println("space: "+ s);
            System.out.println("internalOfs: "+ObjectHeap.get().getSpaceOfsFromPointer(s,encodedOfs));
            System.out.println("messageLen: "+r.message.array().length);
            r  = ObjectHeap.get().retrieve(encodedOfs);
            */
            throw e;
        }

    }

    public boolean isEmpty() {
        return lazyHash == null && lazyNode == null;
    }

    /**
     * The node or empty if this is an empty reference.
     * If the node is not present but its hash is known, it will be retrieved from the store.
     * If the node could not be retrieved from the store, the Node is stopped using System.exit(1)
     */
    public Optional<Trie> getNode() {
        return getNode(false);
    }
    public Optional<Trie> getNode(boolean persistent) {
        if (lazyNode != null) {
            return Optional.of(lazyNode);
        }
        if ((encodedRef !=null) || (GlobalEncodedObjectStore.get().accessByHash() && (lazyHash !=null))) {
            if (persistent) {
                lazyNode = getDynamicLazyNode();
                return Optional.of(lazyNode);
            } else
                return Optional.of(getDynamicLazyNode());

        }



        if (lazyHash == null) {
            return Optional.empty();
        }
        // retrieve node from mem
        Optional<Trie> node = store.retrieve(lazyHash.getBytes());

        // Broken database, can't continue
        if (!node.isPresent()) {
            //logger.error("Broken database, execution can't continue");
            //System.exit(1);
            throw new RuntimeException("not present");
            //return Optional.empty();
        }

        lazyNode = node.get();

        return node;
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHash() {
        if (lazyHash != null) {
            return Optional.of(lazyHash);
        }

        if (lazyNode == null) {

            if (encodedRef !=null) {
                // This should not happen.
                // If lazyNode is null, then the lazyHash should be set.
                return Optional.of(getDynamicLazyNode().getHash());
            }
            return Optional.empty();
        }

        lazyHash = lazyNode.getHash();
        return Optional.of(lazyHash);
    }

    /**
     * The hash or empty if this is an empty reference.
     * If the hash is not present but its node is known, it will be calculated.
     */
    public Optional<Keccak256> getHashOrchid(boolean isSecure) {
        return getNode().map(trie -> trie.getHashOrchid(isSecure));
    }

    @SuppressWarnings("squid:S2384") // private method knows it can avoid copying the byte[] field
    private byte[] getSerialized() {
        if (lazyNode!=null)
            return lazyNode.toMessage();
        else
            return getMessageFromMem();
    }

    private byte[] getMessageFromMem() {
        if (encodedRef ==null)
            return null;
        ObjectReference r = GlobalEncodedObjectStore.get().retrieve(encodedRef);
        return r.getAsArray();
    }

    public boolean isEmbeddable() {
        // if the node is embeddable then this reference must have a reference in memory
        if (lazyNode == null) {
            return false;
        }
        return lazyNode.isEmbeddable();

    }

    // the referenced node was loaded
    public boolean wasLoaded() {
        return lazyNode != null;
    }

    // This method should only be called from save()
    public int serializedLength() {
        if (!isEmpty()) {
            if (isEmbeddable()) {
                return lazyNode.getMessageLength() + 1;
            }

            return Keccak256Helper.DEFAULT_SIZE_BYTES;
        }

        return 0;
    }

    public void serializeInto(ByteBuffer buffer) {
        if (!isEmpty()) {
            if (isEmbeddable()) {
                byte[] serialized = getSerialized();
                buffer.put(new Uint8(serialized.length).encode());
                buffer.put(serialized);
            } else {
                byte[] hash = getHash().map(Keccak256::getBytes)
                        .orElseThrow(() -> new IllegalStateException("The hash should always exists at this point"));
                buffer.put(hash);
            }
        }
    }

    /**
     * @return the tree size in bytes as specified in RSKIP107 plus the actual serialized size
     *
     * This method will EXPAND internal encoding caches without removing them afterwards.
     * Do not use.
     */
    public long referenceSize() {
        return getNode().map(this::nodeSize).orElse(0L);
    }

    private long nodeSize(Trie trie) {
        long externalValueLength = trie.hasLongValue() ? trie.getValueLength().intValue() : 0L;
        return trie.getChildrenSize().value + externalValueLength + trie.getMessageLength();
    }

    public static NodeReference empty() {
        return EMPTY;
    }

    public void removeLazyNode() {
        // If I remove the node, I keep the offset to retrieve
        if (lazyNode!=null) {
            encodedRef = lazyNode.getEncodedRef();
            lazyNode = null; // bye bye
        }
    }
}