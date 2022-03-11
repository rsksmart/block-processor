/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.tools.crypto;

import co.rsk.tools.crypto.cryptohash.Keccak80;
import co.rsk.tools.crypto.cryptohash.KeccakNative;
import org.ethereum.crypto.cryptohash.Keccak256;

public class Keccak256Helper {

    public static final int DEFAULT_SIZE = 256;
    public static final int DEFAULT_SIZE_BYTES = DEFAULT_SIZE / 8;


    public static byte[] keccak256java(byte[] message, int start, int length) {
        byte[] hash ;//= new byte[32];
        Keccak256 k256 = new Keccak256();
        if (message.length != 0) {
            k256.update(message, start, length);

        }
        hash = k256.digest();
        return hash;
    }
    public static byte[] keccak80(byte[] message, int start, int length) {
        byte[] hash;// = new byte[10];
        Keccak80 k80 = new Keccak80();
        if (message.length != 0) {
            k80.update(message, start, length);

        }
        hash = k80.digest();
        return hash;
    }

    public static byte[] keccak256(byte[] message, int start, int length) {
        byte[] ret = new byte[DEFAULT_SIZE_BYTES];
        KeccakNative.keccak256(message, start, message.length, ret);
        return ret;
    }



    public enum Size {

        S224(224),
        S256(256),
        S384(384),
        S512(512);

        int bits = 0;

        Size(int bits) {
            this.bits = bits;
        }

        public int getValue() {
            return this.bits;
        }
    }

}
