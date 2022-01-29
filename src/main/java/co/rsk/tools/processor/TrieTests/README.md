# Trie Benchmarks 

This folder contains two kinds of benchmarks for trie management. Benchmarks start by simple test of different variants of individual component to tests that integrate all components of trie management.

The first benchmark, CompareHashmaps,  targets only a specific class of the rskj trie management classes, whic is the DataSourceWithCache. This test is intended to show how by modifying only that class it is possible to pack many more trie nodes in RAM. 

The second benchmark, CompareTries, targets the creation of fully formed tries in RAM, using different data structures. 
Some variants make use of the different hashmaps tested by the previous test, and some more advanced implementations
use more complex data structures that do not rely on hashmaps.

None of the two benchmarks execute real blocks or real transactions, but only simulate how the trie gets modified for average transactions.

The next benchmarks (not present in this codebase) should execute specially crafted blocks using the new data structures. 

# CompareHashmaps

The DataSourceWithCache is a class that is used by rskj for two purposes. It stores temporarily serialized nodes that will be 
saved in external storage (uncommittedCache), and it also stores in RAM a limited set of nodes that have been either committed or 
read from external storage (committedCache). This last cache serves to speed up future reads of nodes by hash, as nodes in 
the cache are stored in a hashmap indexed each node's hash.

Currently, rskj uses a MaxSizeHashMap<ByteArrayWrapper, byte[]> to store the nodes.
This data structure is suboptimal for at least six reasons. First, the MaxSizeHashMap, compared to a HashMap, adds 
additional overhead to each entry stored by adding two pointers of a linked list that is used
to order entries according to the time it was accessed (the double-links are provided by the ancestor class LinkedHashMap).
The double-linking allows to remove from the map the last element accessed in constant time,
to keep the number of elements in the map below the given limit. While we still need to limit the number of nodes in 
RAM, we don't need that the number of elements is strictly equal to the given limit. We can let it grow until the limit, 
and then perform a slower process that removes a percentage of old nodes (i.e. 10%). To keep the same average memory 
usage, we can let the number of nodes move between 95% and 10% of the given limit.
Second, the HashMap (which the MaxSizeHashMaps inherits from) is suboptimal for storing small object.
For each object it stores a wrapper object called Entry that is part of a linked list of all entries on the map.
This linked list is used to iterate over the elements in the map in time proportional to the number of elements stored.
However, we don't need to iterate over the entries, not with forEach(), not with entrySet(), and not with any 
other key or value iterator. Therefore, we can also remove this double-link overhead.
Third, once we removed all links, the wrapper object is no longer needed, and byte arrays
can be stored directly in the hashtable.
Fourth, the key of our byte arrays is a hash of the contents of the byte array itself. It's not like we're storing 
names indexed by social security numbers. This means that as long as we don't need to compute keys very frequently, we 
do not need to store the keys themselves. Therefore, we don't need to store the key of each entry in our hashmap. 
When the user calls the get() method to retrieve an element, we hash the key to derive the table bucket, and we inspect 
the element in the bucket. If there is an element, we re-compute the key from the element, and we can compare with the 
key we're looking for. If not equal, we can continue with the next element.
where we find the next element depends on the kind of hashtable we're using. Java hashmap uses a linked list when the 
number of elements in the same bucket is below 10, and it uses a balanced tree if greater.
We've implemented two types of hashmaps: one that uses another hashmap for collisions, and a more simple one that uses 
linear probing.
The last and final optimization relates to the storage of the byte arrays. A Java object consumes between 8 and 16 
bytes depending on the JVM used (32 bits vs 64 bits). The average size of a serialized node for a large state trie 
is ~80 bytes, which means that a 16 byte overhead is considerable. Therefore instead of storing the byte arrays as a 
separate objects, we store all of them as consecutive chunks on a byte array. In practice, several arrays (that we 
call "spaces") are used to avoid a Java limitation on the maximum size of arrays. A special heap class manages the 
uses spaces, performing periodic garbage collections when they become full, to keep the data in the array as compact as possible.
Therefore, our hashmap table stores handles (4-byte each). A handle table stores actual int64 offsets in the common array.
The overhead of a reference is 8 bytes for the handle indirection, but we've saved 4 bytes by storing handles instead 
of object pointers in the hashmap table, so this last optimization reduces the overhead by 8 bytes per entry. 
This can be reduced even further by eliminating the handle indirection.
The following are the types of hashmaps compared:

## CAHashMap

A hashmap that does not store the keys, but derive the keys dynamically from values.

## HashMap

The standard Java HashMap.

## LinkedHashMap

Th Java LinkedHashMap class which inherits from HashMap.

## LinkedCAHashMap

A CAHashMap that also links its entries, similar to LinkedHashMap but based on CACacheHashmap.

## NumberedCAHashMap

A CAHashMap that adds a priority to each entry, enabling removing entries with older priorities.

## MaxSizeHashMap

The Java MaxSizeHashMap class, which inherits from LinkedHashMap which inherits from HashMap.

## MaxSizeCAHashMap

A CAHashMap that uses a priority to remove old entries.

## ByteArrayHashMap

A HashMap that is specialized for a ByteArrayWrapper key, and a byte array value. It does not store the key (which is 
recomputed from the value), and it uses linear probing for simplifying all insertion operations.
Values are stored in a ByteArrayRefHeap, which implements its own garbage collection.

## MaxSizeByteArrayHashMap

This is a ByteArrayHashMap what uses priorities to evict old entries in bulk. Priorities are handled using the metadata 
capability of the ByteArrayRefHeap.


# CompareTries

Tool that tests different caches to store Unitrie nodes in memory in encoded form. The objective is to find the storage model that fits the higher number of nodes in memory. The purpose is to reduce I/O access during block processing, by keeping the most used nodes in RAM. The test creates millions of records in the trie simulating external owned accounts. The simulation is done by using keys that are very similar than real account keys, filled with values that have the same average size of accounts. However, these are not real RSK accounts: all the key data and values are fake.

Here we list the different storage models that are being tested.

## HardEncodedObjectStore
This class stores in a POJO the byte encoded representation of the node, plus the two references to child nodes, also in the same byte encoded node format. The end result is that a parallel binary tree is created, where the only Java fields is the encoded byte[] of the node information, and the pointers to child nodes of the same parallel binary tree.

## EncodedObjectHeap
This class stores the nodes into one of different memory spaces. The node is stored in some offset of a byte array, together with serialized versions of the child nodes (which are also references to offsets in one of the memory spaces). Memory spaces can be individually or collectively packed by a new garbage collector (GC). Currently this GC is triggered manually, but  the code is prepared to automatically trigger the GC. To trigger the GC manually, the programmer must indicate he is willing to start the GC (calling `beginRemap()`), and then call a `compressEncodingsRecursivelly()` method on the root node to remap all the node encodings in that tree. After additional root nodes have been indicated, the user tells the GC to stop by calling `endRemap()`. To do it automatically, it's necessary to define a vector of root nodes to explore so that the GC can automatically find the nodes to preserve (this has not been coded).

## EncodedObjectHashMap
This class stores each object in a `HashMap` where the key is the node hash. This is similar to the normal `TrieStore`, but by using the same storage framework, we can easily switch between storage backends.

Obviously this encoding takes more memory, since not only the serialized objects needs to be stored, but also the key (32 bytes).

## MultiSoftEncodedObjectStore
This is similar to the `HardEncodedObjectStore`, but object references are replaced by Java soft references (`SoftReference`). This backend would be used by the node without worrying about garbage collection, since soft references are automatically removed from memory when memory is exhausted.

## SoftRefEncodedObjectStore
This is similar to `MultiSoftEncodedObjectStore`, but instead of the encoding and child pointers having a soft reference each, a single soft reference is used to an object containing the remaining pointers. There are several benefits with this approach. First, soft references take more space, so instead of using 3 soft references we're using only one, without any downside. Also, since there is no benefit of having a node partially removed from memory (i.e. the children removed but not the encoding), it's better that all-or-nothing is removed.