package co.rsk.tools.processor.TrieTests.Unitrie.store;


import co.rsk.tools.processor.TrieTests.Unitrie.AbstractByteArrayRefHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.ByteArrayRefHeap;
import co.rsk.tools.processor.TrieTests.Unitrie.FileMapUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;


public class ByteArrayRefHashMap extends AbstractMap<ByteArrayWrapper, byte[]> implements Map<ByteArrayWrapper, byte[]>, Cloneable, Serializable {
    private static final long serialVersionUID = 362498820763181265L;
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int MAXIMUM_CAPACITY = 1073741824;
    static final float DEFAULT_LOAD_FACTOR = 0.5F;
    static final long defaultNewBeHeapCapacity = 750_000_000;
    static final int empty = Integer.MIN_VALUE;
    static final boolean debugCheckHeap = false;
    static final String debugKey = null;

    transient int[] table;
    transient int size;
    transient int modCount;
    int threshold;
    float loadFactor;

    // The average number of slot checks per lookup is tableSlotChecks/tableLookups
    public int tableSlotChecks;
    public int tableLookups;

    int maxElements;
    boolean logEvents = true;

    BAKeyValueRelation BAKeyValueRelation;
    AbstractByteArrayRefHeap baHeap;


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


    public ByteArrayRefHashMap(int initialCapacity, float loadFactor,
                               BAKeyValueRelation BAKeyValueRelation,
                               long newBeHeapCapacity,
                               AbstractByteArrayRefHeap sharedBaHeap,
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
            this.BAKeyValueRelation = BAKeyValueRelation;
            this.maxElements = maxElements;

            if (sharedBaHeap==null)
                this.baHeap = createByteArrayHeap(initialCapacity, newBeHeapCapacity);
            else
                this.baHeap = sharedBaHeap;
        }

    }

    public AbstractByteArrayRefHeap getByteArrayHashMap() {
        return this.baHeap;
    }

    ByteArrayRefHeap createByteArrayHeap(long initialCapacity, long newBeHeapCapacity)  {
        ByteArrayRefHeap baHeap = new ByteArrayRefHeap();
        baHeap.setMaxMemory(newBeHeapCapacity); //730_000_000L); // 500 Mb / 1 GB
        if (maxElements>0) {
            // The current implementation inserts maxElements+1 and then immediately
            // removes elements, so there is a (short) time where there is
            // 1 more handle requested. That's why the "+1".
            // 2* for testing only
            baHeap.setMaxReferences(2*maxElements + 1);
        }
        else {
            int expectedReferences = (int) (initialCapacity*loadFactor+1);
            baHeap.setMaxReferences(expectedReferences);
        }
        baHeap.initialize();
        return baHeap;

    }

    public interface ShouldRemove {
        // Key is only given if data == null.
        // If can be recomputed by user from data.
        boolean remove(byte[] key, byte[] data);
    }

    public ByteArrayRefHashMap(int initialCapacity, BAKeyValueRelation BAKeyValueRelation) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, BAKeyValueRelation,defaultNewBeHeapCapacity,null,0);
    }

    public ByteArrayRefHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    public ByteArrayRefHashMap(Map<? extends ByteArrayWrapper, ? extends byte[]> m) {
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


    //////////////////////////////////////////////////////////////////////////
    // Management of marked handle methods:
    // The bit used is bit 30.
    // All negatives are invalid (except empty constant which is 0x7fffffff)
    // Therefore only 30 bits are available for handles (1 billion handles).

    final static int nullHandleBitMask = 0x40000000;
    final static int nullHandleMask = nullHandleBitMask -1;

    public int unmarkHandle(int markedHandle) {
        return (markedHandle & nullHandleMask);
    }

    public int setNullHandle(int markedHandle) {
        return (markedHandle | nullHandleBitMask);
    }


    public boolean isValueHandle(int markedHandle) {
        return ((markedHandle & nullHandleBitMask)==0);
    }

    public boolean isNullHandle(int markedHandle) {
        return ((markedHandle & nullHandleBitMask)!=0);
    }
    //////////////////////////////////////////////////////////////////
    public void refreshedHandle(int p) {

    }

    public byte[] get(Object key) {
        GetNodeResult result = new GetNodeResult();
        int p = this.getNode(hash(key), key,result);

        if (p!=-1) {
            refreshedHandle(p);
            byte[] data;
            if (!isValueHandle(p))
                data =null;
            else
                data =result.data;
            this.afterNodeAccess(p,data);
            return data;
        } else
            return null;
    }

    class GetNodeResult {
        public byte[] data;
    }

    // Returns a handle or -1 if no node was found (it does not return empty)
    final int getNode(int hash, Object key,GetNodeResult result) {
        tableLookups++;
        if (result!=null) {
            result.data = null;
        }
        if (table==null)
            return -1;
        int n = table.length;
        if (this.table==null) return -1;
        int idx = (n - 1) & hash;
        int markedHandle;
        do {
            markedHandle  = table[idx];
            tableSlotChecks++;
            if (markedHandle == empty)
                return -1;
            if (!isValueHandle(markedHandle)) {
                byte[] keyBytes = baHeap.retrieveData(unmarkHandle(markedHandle));
                if (ByteUtil.fastEquals(keyBytes, ((ByteArrayWrapper)key).getData() )) {
                    return markedHandle;
                }
            } else {
                byte[] data = baHeap.retrieveData(markedHandle);
                ByteArrayWrapper aKey = computeKey(data);
                if (aKey.equals(key)) {
                    if (result!=null) {
                        result.data = data;
                    }
                    return markedHandle;
                }
            }
            idx = (idx+1) & (n-1);
        } while (true);
    }

    public boolean containsKey(Object key) {
        return this.getNode(hash(key), key,null) != -1;
    }


    public byte[] put(byte[] value) {
        ByteArrayWrapper key = computeKey(value);
        return this.putVal(hash(key), key, value, false, true);
    }

    public byte[] put(ByteArrayWrapper key, byte[] value) {
        return this.putVal(hash(key), key, value, false, true);
    }


    final void setItemInTable(int i,int hash,byte[] key,byte[] data,byte[] metadata,boolean evict) {
        int handle ;
        if (table[i]!=empty) {
            baHeap.remove(unmarkHandle(table[i]));
            table[i] = empty;
        } else
            this.size++;

        if (i==8046)
            i = i;
        if (data==null) {
            handle = baHeap.add(key, metadata);
            table[i] = setNullHandle(handle);
        }   else {
            handle = baHeap.add(data, metadata);
            table[i] = handle;
        }
        if (handle==debugHandle)
            handle = handle;
        if (i==debugIndex) {
            i = i;
            int tabp = (table.length - 1) & hash;
            tabp = tabp;
        }

        this.afterNodeInsertion(table[i],key,data,evict);
    }

    byte[] getNewMetadata() {
        return null;
    }

    final byte[] putVal(int hash, ByteArrayWrapper key, byte[] value, boolean onlyIfAbsent, boolean evict) {
        int n;
        if ((debugKey!=null) && (key.toString().equals(debugKey))) {
            key = key;
        }
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
                setItemInTable(i,hash,key.getData(),value, getNewMetadata(),evict);
                break;
            }

            if (!isValueHandle(p)) {
                // The previous value was a null
                oldValue = null;
                byte[] keyBytes = baHeap.retrieveData(unmarkHandle(p));
                if (ByteUtil.fastEquals(keyBytes, ((ByteArrayWrapper)key).getData() )) {
                    // Key matches
                    if (value==null)  {
                        // the existing value is associated with null, and also the new value
                        return null;
                    }
                    if (!evict) {
                        // The value (null) already exists
                        return null;
                    }


                    // replace a null value by a non-null value
                    // Do not increase this.size.
                    setItemInTable(i,hash,key.getData(),value, getNewMetadata(),evict);
                    break;
                }
            } else {
                oldValue =baHeap.retrieveData(p);
                if (value==null) {
                    // replacing a value with null?
                    // I think this case never happens. Why would the value be null?

                    // we must compare keys
                    ByteArrayWrapper oldKey = computeKey(oldValue);

                    if (oldKey.equals(key)) {
                        if (!evict)
                            return oldValue;

                        // replace
                        setItemInTable(i,hash,key.getData(), null, getNewMetadata(),evict);
                        break;
                    }
                } else {
                    // replacing a non-null value with a non-null value
                    // this is the same as comparing the key, because the keys depends on the data.
                    // The advantage of comparing the data is that we don't need to recompute the key
                    // for element oldValue.
                    if (Arrays.equals(oldValue, value)) {
                        if (!evict)
                            return oldValue;

                        // It the keys depend on values, there is no point in replacing
                        // the value by itself (unless to increase the priority
                        // specified in the metadata).
                        // Anyway, do not increase the size.
                        setItemInTable(i, hash,key.getData(), value, getNewMetadata(),evict);
                        break;
                    }
                }
            }
            i = (i + 1) & (n - 1);
        } while (true);

        if (this.size > this.threshold) {
            this.resize();
        }


        return oldValue;
    }


    public void removeOnCondition(ShouldRemove  rem,boolean notifyRemoval) {
        int tabLen = table.length;
        for (int j = 0; j < tabLen; ++j) {
            int p = table[j];
            if (p != empty) {
                byte[] key = null;
                byte[] data = null;
                if (!isValueHandle(p)) {
                    key = baHeap.retrieveData(unmarkHandle(p));
                    } else {
                    data = baHeap.retrieveData(p);
                }
                if (rem.remove(key,data)) {
                    table[j] = empty;
                    size--;

                    if (notifyRemoval)
                        this.afterNodeRemoval(key, data, null);
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

        this.threshold = newThr;
        int[] newTab = new int[newCap];
        Arrays.fill( newTab, empty );
        this.table = newTab;
        if (oldTab != null) {
            for(int j = 0; j < oldCap; ++j) {
                int  p= oldTab[j];
                if (p != empty) {
                    byte[] key;
                    ByteArrayWrapper k;
                    if (!isValueHandle(p)) {
                        key = baHeap.retrieveData(unmarkHandle(p));
                        k = new ByteArrayWrapper(key);
                    } else {
                        byte[] data = baHeap.retrieveData(p);
                        k = computeKey(data);
                    }
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
        e = this.removeNode(hash(key), key);

        if (e==-1) {
            return null;
        }

        if (!isValueHandle(e)) {
            return null;
        }
        return baHeap.retrieveData(e);
    }

    int debugHandle = 954396;
    int debugIndex = 1;

    final int  removeNode(int hash, Object key) {

        int n;
        int p;
        int index;
        if (this.table == null) return -1;
        n = table.length;
        if (n == 0) return -1;
        byte[] exdata = null;
        byte[] exkey = null;
        int counter =0;
        index = (n - 1) & hash;
        do {
            counter++;

            exkey = null;
            exdata = null;

            if (counter % 100_000==0) {
                System.out.println("scanning table index: "+index+" counter "+counter);
            }
            p = table[index];
            if (p == empty) {
                return -1;
            }
            if (!isValueHandle(p)) {
                exkey =baHeap.retrieveData(unmarkHandle(p));
                if (ByteUtil.fastEquals(exkey, ((ByteArrayWrapper)key).getData() )) {
                    table[index] = empty;
                    fillGap(index,n);
                    break;
                }
            } else {
                if (p==debugHandle)
                    p = p;
                exdata = baHeap.retrieveData(p);
                ByteArrayWrapper exKeyBA = computeKey(exdata);
                if (exKeyBA.equals(key)) {
                    exkey = exKeyBA.getData();
                    table[index] = empty;
                    if ((index==1) || (index==0))
                        index = index;
                    baHeap.remove(p);
                    fillGap(index, n);
                    break;
                }
            }
            index = (index + 1) & (n - 1);
        } while (true);

        --this.size;

        this.afterNodeRemoval(exkey,exdata,getOptionalMetadata(p) );
        return p;
    }

    public byte[] getOptionalMetadata(int handle)  {
        // We don't use metadata in this class, child classes may use it
        byte[] metadata = null;
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
        Set<ByteArrayWrapper> ks = new HashSet<ByteArrayWrapper>();
        int tabLen = table.length;

        for (int i = 0; i < tabLen; ++i) {
            int p =table[i];
            if (p== empty)
                continue;
            if (!isValueHandle(p)) {
                byte[] key =    baHeap.retrieveData(unmarkHandle(p));
                ks.add(new ByteArrayWrapper(key));
            } else {
                byte[] data = baHeap.retrieveData(p);
                ByteArrayWrapper aKey = computeKey(data);
                ks.add(aKey);
            }
        }

        return ks;
    }

    public Collection<byte[]> values() {
        // I wonder if a set of byte[] works because byte[] has no
        // hash by value.
        Set<byte[]> vs = new HashSet<byte[]>();

        int tabLen = table.length;

        for (int i = 0; i < tabLen; ++i) {
            int p =table[i];
            if (p== empty)
                continue;
            if (isValueHandle(p)) {
                byte[] data = baHeap.retrieveData(p);
                vs.add(data);
            }
        }

        return vs;
    }

    public Set<Entry<ByteArrayWrapper, byte[]>> entrySet() {
        // TO DO
        return null;
    }

    public byte[] getOrDefault(Object key, byte[] defaultValue) {
        int e;
        GetNodeResult result = new GetNodeResult();
        return (e = this.getNode(hash(key), key,result)) == -1 ? defaultValue : result.data;
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
                    int p =table[i];
                    if (p== empty)
                        continue;
                    if (!isValueHandle(p)) {
                        byte[] key =    baHeap.retrieveData(unmarkHandle(p));
                        action.accept(new ByteArrayWrapper(key), null);
                    } else {
                        byte[] data = baHeap.retrieveData(p);
                        ByteArrayWrapper aKey = computeKey(data);
                        action.accept(aKey, (byte[]) data);
                    }
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
        ByteArrayRefHashMap result;
        try {
            result = (ByteArrayRefHashMap)super.clone();
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

    void afterNodeAccess(int markedHandle, byte[] p) {
    }


    void beforeNodeInsertion() {

    }

    void afterNodeInsertion(int markedHandle,byte[] key, byte[] data, boolean evict) {

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

    public void fillTableItem(TableItem ti) {

    }

    public TableItem getTableItem(int c) {
        TableItem ti = new TableItem();
        ti.handle = table[c];
        if (isValueHandle(ti.handle)) {
            ti.data = baHeap.retrieveData(ti.handle);
            ti.key = computeKey(ti.data);
        } else {
            ti.key =new ByteArrayWrapper(baHeap.retrieveData(unmarkHandle(ti.handle)));
            ti.data = null;
        }
        ti.metadata = baHeap.retrieveMetadata(unmarkHandle(ti.handle));

        fillTableItem(ti);

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

    void afterNodeRemoval(byte[] key,byte[] data,byte[] metadata) {

    }



    boolean removeItem(int j,int boundary) {
        table[j] = empty;
        size--;
        return fillGap(j,boundary);
    }

    // This method will try to fill the empty slot j.
    // Returns true if the element used to fill the empty slot j was at an index equal or higher than
    // the one given by argument boundary.
    boolean fillGap(int j,int boundary) {

        int i = j;
        if (i==8046)
            i = i;
        boundary = boundary % table.length;
        boolean crossedBoundary = false;
        boolean wrapAroundZero = false;
        int n = table.length;
        do {
            i = (i + 1) & (n- 1);
            if (i==boundary)
                crossedBoundary = true;
            if (i==0)
                wrapAroundZero = true;
            int h = table[i];
            if (h == empty)
                return false;
            ByteArrayWrapper key;
            if (!isValueHandle(h)) {
                byte[] keyBytes =  baHeap.retrieveData(unmarkHandle(h));
                key = new ByteArrayWrapper(keyBytes);
            } else {
                byte[] data = baHeap.retrieveData(h);
                key = computeKey(data);
            }
            int keyHash = hash(key) ;
            int index = keyHash & (n - 1);

            boolean move = false;

            if (index==j)
                move = true;
            else
                if (index<j) {
                    if (wrapAroundZero)
                        move = (index>i);
                    else
                        move = true;
                }

            if (move) {
                table[j] = table[i];
                if ((j==1) || (i==1) || (i==0) || (j==0))
                    j = j;
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
                    if (!isValueHandle(p)) {
                        // This is not supported, because we should write the key and flag somehow that
                        // this is not a data entry, but a key entry. TO DO
                        throw new RuntimeException("null entries not supported");
                    }
                    byte[] data = baHeap.retrieveData(p);
                    //s.writeObject(computeKey(data));
                    s.writeObject(data);
                }
            }
        }

    }

    public int countElements() {
        // this should return the same value than size()
        if (this.table == null) return 0;
        int count = 0;
        int mc = this.modCount;
        int tabLen = table.length;
        for (int i = 0; i < tabLen; ++i) {
            int p = table[i];
            if (p != empty) {
                count++;
            }
        }

        if (ByteArrayRefHashMap.this.modCount != mc) {
            throw new ConcurrentModificationException();
        }

        return count;
    }

    public int longestFilledRun() {
        if (this.table == null) return 0;
        int maxRun = 0;
        int run = 0;
        int mc = this.modCount;
        int tabLen = table.length;
        for (int i = 0; i < tabLen; ++i) {
            int p = table[i];
            if (p != empty) {
                run++;
                if (run > maxRun)
                    maxRun = run;
            } else run = 0;
        }

        if (ByteArrayRefHashMap.this.modCount != mc) {
            throw new ConcurrentModificationException();
        }

        return maxRun;
    }

    public double averageFilledRun() {
        if (this.table == null) return 0;

        double acum = 0;
        int count = 0;
        int mc = this.modCount;
        int tabLen = table.length;
        for (int i = 0; i < tabLen; ++i) {
            int p = table[i];
            if (p != empty) {
                count++;
                // I need to recover the slot to see if it is in the
                // right slot.
                ByteArrayWrapper aKey;
                if (!isValueHandle(p)) {
                    byte[] keyBytes = baHeap.retrieveData(unmarkHandle(p));
                    aKey = new ByteArrayWrapper(keyBytes);
                } else {
                    byte[] data = baHeap.retrieveData(p);
                    aKey = computeKey(data);
                }
                int pos = hash(aKey) & (table.length-1);
                if (pos<=i)
                    acum +=(i-pos+1);
                else {
                    acum +=(table.length-pos)+i;
                }
            }
        }
        // wrap-around element not counted

        if (ByteArrayRefHashMap.this.modCount != mc) {
            throw new ConcurrentModificationException();
        }

        return acum/count;
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
                    int p =table[i];
                    if (p!=empty) {
                        if (!isValueHandle(p)) {
                           // Does it make sense to accept a null? Probably not
                        } else {
                            byte[] data = baHeap.retrieveData(table[i]);
                            action.accept(data);
                        }

                    }
                }

                if (ByteArrayRefHashMap.this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    public void readFromFile(String fileName,boolean map) throws IOException {
        File file = new File(fileName);
        FileInputStream fin = new FileInputStream(file);
        BufferedInputStream bin = new BufferedInputStream(fin);
        DataInputStream din = new DataInputStream(bin);

        int count = //(int) ((file.length()-8) / 4);
            din.readInt();
        size = din.readInt();
        threshold = din.readInt();
        table = new int[count];
        System.out.println("reading hash table");
        for (int i = 0; i < count; i++) {
            table[i] = din.readInt();
        }
        System.out.println("done");
        din.close();

    }

    public void saveToFile(String fileName ) throws IOException {
        //FileOutputStream out = new FileOutputStream(fileName);
        RandomAccessFile sc
                = new RandomAccessFile(fileName, "rw");
        FileChannel file = sc.getChannel();

        // Size cannot exceed Integer.MAX_VALUE !! Horrible thing in file.map().
        // However, we can map in parts.
        ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, 0,
                4L * 3);
        buf.putInt(table.length);
        buf.putInt(size);
        buf.putInt(threshold);
        FileMapUtil.mapAndCopyIntArray(file,4*3,table.length,table);
        file.close();
        /* Slower
        DataOutputStream os = new DataOutputStream(
                new FileOutputStream(fileName));

        //write the length first so that you can later know how many ints to read
        os.writeInt(table.length);
        os.writeInt(size);
        os.writeInt(threshold);
        for (int i =0 ; i < table.length; ++i){
            os.writeInt(table[i]);
        }
        os.close();
         */
    }


}

