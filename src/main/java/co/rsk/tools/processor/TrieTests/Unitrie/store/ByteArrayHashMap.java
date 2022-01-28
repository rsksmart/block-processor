package co.rsk.tools.processor.TrieTests.Unitrie.store;


import co.rsk.tools.processor.TrieTests.Unitrie.ByteArrayRefHeap;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;


public class ByteArrayHashMap  extends AbstractMap<ByteArrayWrapper, byte[]> implements Map<ByteArrayWrapper, byte[]>, Cloneable, Serializable {
    private static final long serialVersionUID = 362498820763181265L;
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int MAXIMUM_CAPACITY = 1073741824;
    static final float DEFAULT_LOAD_FACTOR = 0.5F;
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;
    static final long defaultNewBeHeapCapacity = 750_000_000;
    static final int empty = Integer.MIN_VALUE;
    static final boolean debugCheckHeap = false;
    transient int[] table;
    transient int size;
    transient int modCount;
    int threshold;
    float loadFactor;
    public int hashMapCount;
    int maxElements;
    int currentPriority;
    int minPriority;
    public int MaxPriority = Integer.MAX_VALUE;
    public boolean removeInBulk = false;
    int startScanForRemoval =0;
    boolean logEvents = true;

    BAKeyValueRelation BAKeyValueRelation;


    // This data structure is used only for external units tests and debugging.
    // Returns a copy of the data, so you should not use with big tables.
    // DO NOT USE FOR PRODUCTION CODE.
    public class TableItem {
        public int bucket;
        public ByteArrayWrapper key;
        public int handle;
        public byte[] data;
        public byte[] metadata;
        public int priority;
        public int dataHash;
    }

    public interface BAKeyValueRelation {
        int getHashcode(ByteArrayWrapper var1);
        ByteArrayWrapper  getKeyFromData(byte[] data);
        long getPriority(byte[] data);
        void afterNodeAccess(byte[] data);
        void afterNodeInsertion(ByteArrayHashMap map, byte[] data, boolean evict, long latestPriority);
        void afterNodeRemoval(long priority);
    }

    public int hash(Object key) {
        // It's important that this hash DOES not collude with the HashMap hash, because
        // we're using hashmaps inside each bucket. If both hashes are the same,
        // then all objects in the same bucket will be also in the same bucked of the
        // hashmap stored in the bucket.
        //
        if (key == null)
            return 0;
        if (BAKeyValueRelation == null)
            return key.hashCode();
        else
            return BAKeyValueRelation.getHashcode((ByteArrayWrapper) key);
    }


    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return n < 0 ? 1 : (n >= 1073741824 ? 1073741824 : n + 1);
    }


    public ByteArrayHashMap(int initialCapacity, float loadFactor,
                            BAKeyValueRelation BAKeyValueRelation,
                            long newBeHeapCapacity,
                            ByteArrayRefHeap sharedBaHeap,
                            int maxElements) {
        this.loadFactor = 0;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        } else {
            if (initialCapacity > 1073741824) {
                initialCapacity = 1073741824;
            }

            if (!(loadFactor <= 0.0F) && !Float.isNaN(loadFactor)) {
                this.loadFactor = loadFactor;
                this.threshold = tableSizeFor(initialCapacity);
            } else {

                throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
            }
            if (sharedBaHeap==null) {
                baHeap = new ByteArrayRefHeap();
                baHeap.setMaxMemory(newBeHeapCapacity); //730_000_000L); // 500 Mb / 1 GB
                if (maxElements>0) {
                    // The current implementation inserts maxElements+1 and then immediately
                    // removes elements, so there is a (short) time where there is
                    // 1 more handle requested. That's why the "+1".
                    baHeap.setMaxReferences(maxElements + 1);
                }
                else {
                    int expectedReferences = (int) (initialCapacity*loadFactor+1);
                    baHeap.setMaxReferences(expectedReferences);
                }
                baHeap.initialize();
            } else
                baHeap = sharedBaHeap;
        }

        this.BAKeyValueRelation = BAKeyValueRelation;
        this.maxElements = maxElements;
    }

    public interface ShouldRemove {
        boolean remove(byte[] data);
    }

    public ByteArrayHashMap(int initialCapacity, BAKeyValueRelation BAKeyValueRelation) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, BAKeyValueRelation,defaultNewBeHeapCapacity,null,0);
    }

    public ByteArrayHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    public ByteArrayHashMap(Map<? extends ByteArrayWrapper, ? extends byte[]> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.putMapEntries(m, false);
    }

    final void putMapEntries(Map<? extends ByteArrayWrapper, ? extends byte[]> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            if (this.table == null) {
                float ft = (float) s / this.loadFactor + 1.0F;
                int t = ft < 1.07374182E9F ? (int) ft : 1073741824;
                if (t > this.threshold) {
                    this.threshold = tableSizeFor(t);
                }
            } else if (s > this.threshold) {
                this.resize();
            }

            Iterator iter = m.entrySet().iterator();

            while (iter.hasNext()) {
                Entry<? extends ByteArrayWrapper, ? extends byte[]> e = (Entry) iter.next();
                ByteArrayWrapper key = e.getKey();
                byte[] value = e.getValue();
                this.putVal(hash(key), key, value, false, evict);
            }
        }

    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    ByteArrayRefHeap baHeap;

    public int getPriority(Object key) {
        int e = this.getNode(hash(key), key);
        if (e!=-1) {
            byte[] metadata =baHeap.retrieveMetadata(e);
            int priority = ObjectIO.getInt(metadata,0);
            return priority;
        } else
            return -1;
    }

    public byte[] get(Object key) {
        int e = this.getNode(hash(key), key);
        if (e!=-1) {
            byte[] data =baHeap.retrieveData(e);
            this.afterNodeAccess(data);
            return data;
        } else
            return null;
    }

    // Returns a handle or -1 if no node was found (it does not return empty)
    final int getNode(int hash, Object key) {

        int n = table.length;
        if (this.table==null) return -1;
        int idx = (n - 1) & hash;
        int first  = table[idx];
        do {
            if (first == empty) return -1;
            byte[] data = baHeap.retrieveData(first);
            ByteArrayWrapper aKey = computeKey(data);
            if (aKey.equals(key))
                return first;
            idx = (idx+1) & (n-1);
        } while (table[idx]!=empty);
        return -1;
    }

    public boolean containsKey(Object key) {
        return this.getNode(hash(key), key) != -1;
    }


    public byte[] put(byte[] value) {
        ByteArrayWrapper key = computeKey(value);
        return this.putVal(hash(key), key, value, false, true);
    }

    public byte[] put(ByteArrayWrapper key, byte[] value) {
        return this.putVal(hash(key), key, value, false, true);
    }

    byte[] getPriorityAsMetadata() {
        if (maxElements==0)
            return null;
        byte[] m = new byte[4];
        ObjectIO.putInt(m,0,currentPriority++);
        return m;
    }

    final byte[] putVal(int hash, ByteArrayWrapper key, byte[] value, boolean onlyIfAbsent, boolean evict) {
        int n;
        if ((table == null) || (n = table.length) == 0) {
            this.resize();
            n = table.length;
        }
        byte[] oldValue = null;
        int p;
        int i = n - 1 & hash;

        do {
            p = table[i];
            if (p == empty) {
                table[i] = baHeap.add(value,getPriorityAsMetadata());
                break;
            }
            oldValue =baHeap.retrieveData(p);
            if (Arrays.equals(oldValue,value)) {
                return oldValue;
            }
            i = (i + 1) & (n - 1);
        } while (true);

        if (++this.size > this.threshold) {
            this.resize();
        }

        this.afterNodeInsertion(value,evict);
        return oldValue;
    }

    final int subMapInitialCapacity = 6;

    public void removeOnCondition(ShouldRemove  rem,boolean notifyRemoval) {
        int tabLen = table.length;
        for (int j = 0; j < tabLen; ++j) {
            int p = table[j];
            if (p != empty) {
                byte[] data =baHeap.retrieveData(p);
                if (rem.remove(data)) {
                    table[j] = empty;
                    size--;

                    if (notifyRemoval)
                        this.afterNodeRemoval(data,null);
                }
            }
        }
    }


    final int[] resize() {
        int[] oldTab = this.table;
        int oldCap = oldTab == null ? 0 : oldTab.length;
        int oldThr = this.threshold;
        int newThr = 0;
        int newCap;
        if (oldCap > 0) {
            if (oldCap >= 1073741824) {
                this.threshold = 2147483647;
                return oldTab;
            }

            if ((newCap = oldCap << 1) < 1073741824 && oldCap >= 16) {
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            newCap = oldThr;
        } else {
            newCap = 16;
            newThr = 12;
        }

        if (newThr == 0) {
            float ft = (float)newCap * this.loadFactor;
            newThr = newCap < 1073741824 && ft < 1.07374182E9F ? (int)ft : 2147483647;
        }

        hashMapCount =0;
        this.threshold = newThr;
        int[] newTab = new int[newCap];
        Arrays.fill( newTab, empty );
        this.table = newTab;
        if (oldTab != null) {
            for(int j = 0; j < oldCap; ++j) {
                int  p= oldTab[j];
                if (p != empty) {
                    ByteArrayWrapper k = computeKey(baHeap.retrieveData(p));
                    int hc = hash(k);
                    addToTable(newTab, newCap, hc, p);
                }
            }
        }
        return newTab;
    }


    public void addToTable(int[] tab, int cap, int hash,int handle) {
        int i = cap - 1 & hash;
        int  p = tab[i];
        do {
            if (p == empty) {
                tab[i] = handle;
                break;
            }
            i = (i + 1) & (cap - 1);
        } while (true);
    }

    public void putAll(Map<? extends ByteArrayWrapper, ? extends byte[]> m) {
        this.putMapEntries(m, true);
    }

    public byte[] remove(Object key) {
        int e;
        return (e = this.removeNode(hash(key), key)) == -1 ? null : (byte[]) baHeap.retrieveData(e);
    }

    final int  removeNode(int hash, Object key) {

        int n;
        int p;
        int index;
        if (this.table == null) return -1;
        n = table.length;
        if (n == 0) return -1;
        byte[] data = null;
        do {
            index = n - 1 & hash;
            p = table[index];
            if (p == empty) {
                return -1;
            }
            data =baHeap.retrieveData(p);
            ByteArrayWrapper exKey = computeKey(data);
            if (exKey.equals(key)) {
                table[index] = empty;
                fillGap(index,n);
                break;
            }
            index = (index + 1) & (n - 1);
        } while (true);

        --this.size;

        this.afterNodeRemoval(data,getOptionalMetadata(p) );
        return p;
    }

    public byte[] getOptionalMetadata(int handle)  {
        byte[] metadata = null;
        if (maxElements>0)
            metadata = baHeap.retrieveMetadata(handle);
        return metadata;
    }



    public void clear() {
        ++this.modCount;
        if (this.table == null) return;
        this.size = 0;

        for (int i = 0; i < table.length; ++i) {
            table[i] = empty;
        }
    }

    public boolean containsValue(Object value) {

        if ((table != null) && this.size > 0) {
            ByteArrayWrapper key = computeKey( (byte[]) value);
            return containsKey(key);
        }
        return false;
    }

    public Set<ByteArrayWrapper> keySet() {
        /* TO DO
        Set<ByteArrayWrapper> ks = this.keySet;
        if (ks == null) {
            ks = new CAHashMap.KeySet();
            this.keySet = (Set)ks;
        }

        return (Set)ks;
        */
         return null;
    }

    public Collection<byte[]> values() {
        /* TO DO
        Collection<byte[]> vs = this.values;
        if (vs == null) {
            vs = new CAHashMap.Values();
            this.values = (Collection)vs;
        }

        return (Collection)vs;
        */
         return null;
    }

    public Set<Entry<ByteArrayWrapper, byte[]>> entrySet() {
        // TO DO
        return null;
    }

    public byte[] getOrDefault(Object key, byte[] defaultValue) {
        int e;
        return (e = this.getNode(hash(key), key)) == -1 ? defaultValue : (byte[]) baHeap.retrieveData(e);
    }

    public byte[] putIfAbsent(ByteArrayWrapper key, byte[] value) {
        return this.putVal(hash(key), key, value, true, true);
    }

    public boolean remove(Object key, Object value) {
        return this.removeNode(hash(key), key) != -1;
    }


    public byte[] computeIfAbsent(ByteArrayWrapper key, Function<? super ByteArrayWrapper, ? extends byte[]> mappingFunction) {
      // TO DO
        return null;
    }

    public byte[] computeIfPresent(ByteArrayWrapper key, BiFunction<? super ByteArrayWrapper, ? super byte[], ? extends byte[]> remappingFunction) {
        // TO DO
        return null;
    }

    public byte[] compute(ByteArrayWrapper key, BiFunction<? super ByteArrayWrapper, ? super byte[], ? extends byte[]> remappingFunction) {
        return null; // TO DO ?
    }

    public byte[] merge(ByteArrayWrapper key, byte[] value, BiFunction<? super byte[], ? super byte[], ? extends byte[]> remappingFunction) {
        return null; // TO DO ?
    }

    public void forEach(BiConsumer<? super ByteArrayWrapper, ? super byte[]> action) {
        if (action == null) {
            throw new NullPointerException();
        } else {
            if ((this.size > 0) && (this.table != null)) {
                int mc = this.modCount;
                int tabLen = table.length;

                for (int i = 0; i < tabLen; ++i) {
                    if (table[i] == empty)
                        continue;
                    byte[] data = baHeap.retrieveData(table[i]);
                    ByteArrayWrapper aKey = computeKey(data);
                    action.accept(aKey, (byte[]) data);
                }

                if (this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    public ByteArrayWrapper computeKey(byte[] e) {
        return BAKeyValueRelation.getKeyFromData((byte[]) e);
    }

    public Object clone() {
        ByteArrayHashMap result;
        try {
            result = (ByteArrayHashMap)super.clone();
        } catch (CloneNotSupportedException var3) {
            throw new InternalError(var3);
        }

        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    final float loadFactor() {
        return this.loadFactor;
    }

    final int capacity() {
        return this.table != null ? this.table.length : (this.threshold > 0 ? this.threshold : 16);
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        int buckets = this.capacity();
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(this.size);
        this.internalWriteEntries(s);
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        this.reinitialize();
        if (!(this.loadFactor <= 0.0F) && !Float.isNaN(this.loadFactor)) {
            s.readInt();
            int mappings = s.readInt();
            if (mappings < 0) {
                throw new InvalidObjectException("Illegal mappings count: " + mappings);
            } else {
                if (mappings > 0) {
                    float lf = Math.min(Math.max(0.25F, this.loadFactor), 4.0F);
                    float fc = (float)mappings / lf + 1.0F;
                    int cap = fc < 16.0F ? 16 : (fc >= 1.07374182E9F ? 1073741824 : tableSizeFor((int)fc));
                    float ft = (float)cap * lf;
                    this.threshold = cap < 1073741824 && ft < 1.07374182E9F ? (int)ft : 2147483647;
                    //????? TO DO: SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Entry[].class, cap);
                    int[] tab = new int[cap];
                    this.table = tab;

                    for(int i = 0; i < mappings; ++i) {
                        ByteArrayWrapper key = (ByteArrayWrapper) s.readObject();
                        byte[] value = (byte[]) s.readObject();
                        this.putVal(hash(key), key, value, false, false);
                    }
                }

            }
        } else {
            throw new InvalidObjectException("Illegal load factor: " + this.loadFactor);
        }
    }

    void reinitialize() {
        this.table = null;
        //this.entrySet = null;
        /* TO DO
        this.keySet = null;
        this.values = null;
         */
        this.modCount = 0;
        this.threshold = 0;
        this.size = 0;
    }

    void afterNodeAccess(byte[] p) {
    }


    void beforeNodeInsertion() {

    }
    void afterNodeInsertion(byte[] p, boolean evict) {

        if (maxElements==0) return;
        // If priority reaches the maximum integer, we must re-prioritize all elements
        // to make space for higher priorities.
        // maxElements must be lower than Integer.MAX_VALUE to avoid
        // reprioritizing frequently.
        // But it it is one bit lower (less than half) it's enough, since reprioritizations
        // will happen every 2^30 insertions.
        // Here we let the maximum be variable to be able to test the reprizitization code
        // without too many insertions
        if (currentPriority==MaxPriority)
            reprioritize();
        if (!evict) return;


        if (removeInBulk) {
            if (size()<=maxElements) return;
            // if there is no slot left, remove lower priorities
            // because we need always at least one slot free.
            int divisor = 10;
            int increment = (currentPriority - minPriority) / divisor;
            if (increment==0)
                increment = 1;

            minPriority = minPriority + increment;
            if (logEvents)
                System.out.println("Start removing elements below priority "+minPriority+"...");

            checkHeap();
            int priorSize = size;
            baHeap.beginRemap();
            removeLowerPriorityElements(0,table.length,false, true);
            baHeap.endRemap();
            if (logEvents)
                System.out.println("Stop removing elements ("+(priorSize-size)+" elements removed)");
            checkHeap();
        } else {
            int divisor = 10;
            // This is a flexible removal policy. Instead of removing all element with priority below
            // minPriority, we scan only 10% of our table. If we find elements to remove, then we do.
            // When we reach 90% of usage, we start removing 1% for each 1% added.
            int maxReducedElements =maxElements*(divisor-1)/divisor;
            if (size()<=maxReducedElements*(divisor+1)/divisor) return;
            minPriority = minPriority + (size() - maxReducedElements) / divisor;
            int prevSize = size();

            startScanForRemoval = removeLowerPriorityElements(startScanForRemoval,table.length/divisor,false, false);
            //System.out.println("removed "+(prevSize-size) + " = "+(prevSize-size)*100/prevSize+"%");
            if (size()<prevSize)  {
                if (baHeap.heapIsAlmostFull())
                    compressHeap();
            }
        }
    }

    void reprioritize() {
        if (logEvents)
            System.out.println("Reprioritizing");
        int count = table.length;
        for (int c = 0; c < count; ++c) {
            int p = table[c];
            if (p != empty) {
                byte[] metadata = baHeap.retrieveMetadata(p);
                int priority = ObjectIO.getInt(metadata,0);
                if (priority<minPriority)
                    priority =0;
                else
                    priority -=minPriority;
                ObjectIO.putInt(metadata,0,priority);
                baHeap.setMetadata(p,metadata);
            }

        }
        currentPriority -=minPriority;
        minPriority =0;
        if (logEvents) {
            System.out.println("after reprio");
            //dumpTable();
        }
    }


    public List<TableItem> exportTable() {
        List<TableItem> export = new ArrayList<>();
        int count = table.length;
        for (int c = 0; c < count; ++c) {
            int p = table[c];
            if (p!=empty) {
                TableItem ti = getTableItem(c);
                export.add(ti);
            }
        }
        return export;
    }

    public TableItem getTableItem(int c) {
        TableItem ti = new TableItem();
        ti.handle = table[c];
        ti.data = baHeap.retrieveData(ti.handle);
        ti.metadata = baHeap.retrieveMetadata(ti.handle);
        ti.priority = ObjectIO.getInt(ti.metadata,0);
        ti.key = computeKey(ti.data);
        ti.dataHash = hash(ti.key) ;
        ti.bucket = ti.dataHash & (table.length-1);
        return ti;

    }
    public void dumpTable() {
        int count = table.length;
        for (int c = 0; c < count; ++c) {
            int p = table[c];
            if (p!=empty) {
                TableItem ti = getTableItem(c);
                System.out.println("[" + c +"] " +
                        ByteUtil.toHexString(ti.key.getData() ,0,4)+
                        "=" + ByteUtil.toHexString(ti.data,0,4) +
                        " (bucket "+ti.bucket+
                        " prio " + ti.priority + ")");


            }
        }
    }

    void checkHeap() {
        if (!debugCheckHeap) return;

        int count = table.length;
        for (int c = 0; c < count; ++c) {
            int p = table[c];
            if (p != empty) {
                baHeap.checkHandle(p);
            }
        }
    }

    void compressHeap() {
        if (logEvents)
            System.out.println("Usage before: "+ baHeap.getUsagePercent());
        baHeap.beginRemap();
        int count = table.length;
        for (int c = 0; c < count; ++c) {
            int p = table[c];
            if (p != empty) {
                baHeap.remap(p);
            }
        }
        baHeap.endRemap();
        System.out.println("Usage after: "+ baHeap.getUsagePercent());
    }

    void afterNodeRemoval(byte[] p,byte[] metadata) {
        if (maxElements==0) return;
        if (metadata==null) return;

        int priority = ObjectIO.getInt(metadata,0);
        if (priority==minPriority)
            minPriority = minPriority+1;
    }

    // This method performs a scan over all elements and remove all below a certain
    // limit.
    public int removeLowerPriorityElements(int from,int count, boolean notifyRemoval,boolean doremap) {
        int j = from;
        int mask = table.length-1;
        int boundary = (from+count) & mask;
        for (int c = 0; c < count; ++c) {
            int p = table[j];
            if (j==69)
                j =j;
            if (p != empty) {
                byte[] metadata =baHeap.retrieveMetadata(p);
                int priority = ObjectIO.getInt(metadata,0);

                if (priority<minPriority) {
                    // If an item is removed, then it must continue with the same index
                    // because another item may have take its place
                    // It is possible that removeItem brings back into scope a handle
                    // that was previously marked for remap.
                    // Example: element 0 is marked, when element (n-1) is removed, element
                    // 0 is moved to position (n-1) and therefore it is analyzed again
                    boolean movedElementAcrossBoundary = removeItem(j,boundary);
                    if (movedElementAcrossBoundary)
                        j =j;
                    if (notifyRemoval) {
                        byte[] data = baHeap.retrieveData(p);
                        this.afterNodeRemoval(data, metadata);
                    }
                    // If an element was moved to replace the one removed
                    if ((table[j]!=empty) && (!movedElementAcrossBoundary)) {
                        // This wraps-around zero correctly in Java.
                        // The previous value of 0 is mask.
                        j = (j - 1) & mask;
                        c--;
                    }

                } else
                    if (doremap) {
                        // Note: some elements in the wrap-around boundary [(from+count-1)..from]
                        // May be marked two times for remap.
                        baHeap.remap(p);
                    }
            }
            j = (j+1) & mask;
        }
        return j;
    }

    boolean removeItem(int j,int boundary) {
        table[j] = empty;
        size--;
        return fillGap(j,boundary);
    }

    // Returns true if the element used to fill had an index equal or higher than
    // the one given by argument boundary.
    boolean fillGap(int j,int boundary) {
        int i = j;
        boolean crossedBoundary = false;
        int n = table.length;
        do {
            i = (i + 1) & (n- 1);
            if (i==boundary)
                crossedBoundary = true;
            int h = table[i];
            if (h == empty)
                return false;
            byte[] data = baHeap.retrieveData(h);
            ByteArrayWrapper key = computeKey(data);
            int dataHash = hash(key) ;
            int index = dataHash & (n - 1);
            if (index <= j) { // can move
                table[j] = table[i];
                table[i] = empty;
                crossedBoundary |=fillGap(i,boundary);
                return crossedBoundary;
            }
        } while (true);
    }

    void internalWriteEntries(ObjectOutputStream s) throws IOException {

        if (this.size > 0 && (this.table) != null) {
            int tabLen = table.length;

            for(int i = 0; i < tabLen; ++i) {
                if (table[i]!=empty) {
                    int p =table[i];
                    byte[] data = baHeap.retrieveData(p);
                    //s.writeObject(computeKey(data));
                    s.writeObject(data);
                }
            }
        }

    }

    // For each entry in the map. Each entry contains key and value.
    public final void forEach(Consumer<? super byte[]> action) {
        if (action == null) {
            throw new NullPointerException();
        } else {
            if (this.size > 0 && (this.table) != null) {
                int mc = this.modCount;

                int tabLen = table.length;
                for (int i = 0; i < tabLen; ++i) {
                    if (table[i]!=empty) {
                        byte[] data = baHeap.retrieveData(table[i]);
                        action.accept(data);

                    }
                }

                if (ByteArrayHashMap.this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

}

