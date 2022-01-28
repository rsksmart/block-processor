package co.rsk.tools.processor.TrieTests.Unitrie.store;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import java.io.Serializable;
import java.util.Arrays;
// Overhead per item depending on the number of fields added in Java 11
// 0 fields : 12 bytes of overhead
// 1 int    : 20 bytes of overhead
// 2 objs   : 20 bytes of overhead
// 3 objs   : 26 bytes of overhead

public class TSNode implements Comparable<TSNode>, Serializable {
    public byte[] data;
    //Object o1;
    //Object o2; // Simulates linking (Storing 2 objects is the same as storing one int)
    //Object o3;
    public long priority;

    public TSNode(byte[] data,long priority) {
        this.data = data;
        //this.timeStamp = timeStamp;
    }

    @Override
    public int hashCode() {
        return  Arrays.hashCode(data);
    }

    @Override
    public int compareTo(TSNode o) {
        return FastByteComparisons.compareTo(
                data, 0, data.length,
                o.data, 0, o.data.length);
    }


    public boolean equals(Object other) {
        if (!(other instanceof TSNode)) {
            return false;
        }
        byte[] otherData = ((TSNode) other).data;
        return ByteUtil.fastEquals(data, otherData);
    }
}
