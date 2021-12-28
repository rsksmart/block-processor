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