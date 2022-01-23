package co.rsk.tools.processor.TrieTests.Unitrie.store;


import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;


public class CAHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {
    private static final long serialVersionUID = 362498820763181265L;
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int MAXIMUM_CAPACITY = 1073741824;
    static final float DEFAULT_LOAD_FACTOR = 0.75F;
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;
    transient Object[] table;
    transient Set<Entry<K, V>> entrySet;
    protected Class<V> valueClass;
    transient int size;
    transient int modCount;
    int threshold;
    float loadFactor;
    public int hashMapCount;

    public interface getHashcode<T> {
        int getHashcode(T var1);
    }

    public int hash(Object key) {
        // It's important that this hash DOES not collude with the HashMap hash, because
        // we're using hashmaps inside each bucket. If both hashes are the same,
        // then all objects in the same bucket will be also in the same bucked of the
        // hashmap stored in the bucket.
        //
        if (key == null)
            return 0;
        if (getHashFunction == null)
            return key.hashCode();
        else
            return getHashFunction.getHashcode((K) key);
    }


    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return n < 0 ? 1 : (n >= 1073741824 ? 1073741824 : n + 1);
    }

    Function<V, K> computeKeyFunction;
    getHashcode<K> getHashFunction;

    public CAHashMap(int initialCapacity, float loadFactor,
                     Function<V, K> computeKeyFunction,
                     getHashcode<K> getHashFunction) {
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
        }
        this.computeKeyFunction = computeKeyFunction;
        this.getHashFunction = getHashFunction;
    }

    public CAHashMap(int initialCapacity, Function<V, K> computeKeyFunction,getHashcode<K> getHashFunction) {
        this(initialCapacity, 0.75F, computeKeyFunction,getHashFunction);
    }

    public CAHashMap() {
        this.loadFactor = 0.75F;
    }

    public CAHashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = 0.75F;
        this.putMapEntries(m, false);
    }

    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
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

            Iterator var8 = m.entrySet().iterator();

            while (var8.hasNext()) {
                Entry<? extends K, ? extends V> e = (Entry) var8.next();
                K key = e.getKey();
                V value = e.getValue();
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

    public V get(Object key) {
        CAHashMap.Node e;
        return (e = this.getNode(hash(key), key)) == null ? null : (V) e.getValue();
    }

    final CAHashMap.Node<K, V> getNode(int hash, Object key) {
        Object[] tab;
        Object firstObject;

        CAHashMap.ValueNode first;
        int n;
        if ((tab = this.table) != null && (n = tab.length) > 0 && (firstObject = tab[n - 1 & hash]) != null) {

            if (firstObject instanceof byte[]) { //valueClass
                K aKey = computeKey(firstObject);
                // Return a temporary node (unlinked)
                if (aKey.equals(key))
                    return newVirtualNode(tab, n - 1 & hash, aKey);
                else
                    return null;
            }
            Map<K, CAHashMap.ValueNode<K, V>> hmap = (HashMap<K, CAHashMap.ValueNode<K, V>>) firstObject;
            return hmap.get(key);
        }

        return null;
    }

    public CAHashMap.VirtualNode<K, V> newVirtualNode(Object[] tab, int tabIndex, K optKey) {
        return new CAHashMap.VirtualNode<K, V>(tab, tabIndex, optKey, computeKeyFunction);
    }

    public boolean containsKey(Object key) {
        return this.getNode(hash(key), key) != null;
    }


    public V put(V value) {
        K key = computeKey(value);
        return this.putVal(hash(key), key, value, false, true);
    }

    public V put(K key, V value) {
        return this.putVal(hash(key), key, value, false, true);
    }

    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        Object[] tab;
        int n;
        tab = this.table;
        if ((tab == null) || (n = tab.length) == 0) {
            n = (tab = this.resize()).length;
        }
        V oldValue = null;
        Object p;
        int i;
        p = tab[i = n - 1 & hash];
        if (p == null) {
            tab[i] = value;
        } else {
            Map<K, CAHashMap.ValueNode<K, V>> hmap;
            ValueNode<K, V> oldNode;
            if (p instanceof byte[]) {
                // convert
                hmap = newHashMap();
                K oldNodeKey = computeKey(p);
                if (onlyIfAbsent && oldNodeKey.equals(key))
                    return (V) p;

                oldNode = (ValueNode<K, V>) newNode(hash(oldNodeKey), oldNodeKey, (V) p);
                hmap.put(oldNodeKey, oldNode);
                tab[i] = hmap;
            } else {
                hmap = (Map<K, CAHashMap.ValueNode<K, V>>) p;
                oldNode = hmap.get(key);
                if (onlyIfAbsent && (oldNode != null))
                    return oldNode.getValue();
            }
            ValueNode<K, V> node = newNode(hash, key, value);

            hmap.put(key, node);
            this.afterNodeAccess((CAHashMap.Node) node);

            if (oldNode != null) {
                oldValue = oldNode.getValue();
            }
        }
        if (++this.size > this.threshold) {
            this.resize();
        }

        this.afterNodeInsertion(evict);
        return oldValue;
    }

    final int subMapInitialCapacity = 6;

    public Map<K, CAHashMap.ValueNode<K, V>> newHashMap() {
        Map<K, CAHashMap.ValueNode<K, V>> ret = new HashMap(subMapInitialCapacity);
        hashMapCount++;
        return ret;
    }

    final Object[] resize() {
        Object[] oldTab = this.table;
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
        Object[] newTab = new Object[newCap];
        this.table = newTab;
        if (oldTab != null) {
            for(int j = 0; j < oldCap; ++j) {
                CAHashMap.Node e;
                Object eObject= oldTab[j];
                if (eObject != null) {
                    oldTab[j] = null;
                    if (eObject instanceof byte[]) {
                        K k = computeKey(eObject);
                        int hc = hash(k);
                        addToTable(newTab,newCap,hc,k,(V) eObject,null);
                    } else {
                        Map<K,CAHashMap.ValueNode<K, V>> hmap = (Map<K,CAHashMap.ValueNode<K, V>>) eObject;
                        // hmapiter
                        for (Map.Entry<K,CAHashMap.ValueNode<K, V>> entry : hmap.entrySet()) {
                            addToTable(newTab,newCap,hash(entry.getKey()),
                                    entry.getKey(),entry.getValue().getValue(),
                                    entry.getValue());
                        }
                    }
                }
            }
        }

        return newTab;
    }


    public void addToTable(Object[] tab,int cap,int hash, K key, V value,CAHashMap.ValueNode<K, V> node) {
        int i = cap - 1 & hash;
        Object p = tab[i];
        if (p == null)
            tab[i] = value;
        else {
            Map<K, CAHashMap.ValueNode<K, V>> hmap;
            if (p instanceof byte[]) {
                byte[] oldValue = (byte[]) p;
                K oldKey = computeKey(p);
                hmap = newHashMap();
                hmap.put(oldKey, newNode(hash(oldKey), oldKey, (V) oldValue));
                tab[i] = hmap;
            } else {
                hmap = (Map<K, CAHashMap.ValueNode<K, V>>) p;
            }
            if (node==null)
                node =newNode(hash,key,value);
            hmap.put(node.getKey(), node);
        }
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        this.putMapEntries(m, true);
    }

    public V remove(Object key) {
        CAHashMap.Node e;
        return (e = this.removeNode(hash(key), key)) == null ? null : (V) e.getValue();
    }

    final CAHashMap.Node<K, V> removeNode(int hash, Object key) {
        Object[] tab;
        CAHashMap.Node node;
        Object pObject;
        int n;
        int index;
        // Same key implies same value, so no point in value matching
        if ((tab = this.table) != null && (n = tab.length) > 0 && (pObject = tab[index = n - 1 & hash]) != null) {
            if (pObject instanceof byte[]) {
                K exKey = computeKey(pObject);
                if (!exKey.equals(key)) {
                    return null;
                }
                table[index] = null;
                node = newNode(hash(exKey),exKey,(V) pObject);
            } else {
                Map<K, CAHashMap.ValueNode<K, V>> hmap = (HashMap<K, CAHashMap.ValueNode<K, V>>) pObject;
                node = hmap.remove(key);
                if (hmap.size()==1) {
                    for (Map.Entry<K,CAHashMap.ValueNode<K, V>> entry : hmap.entrySet()) {
                        table[index] = entry.getValue().getValue();
                    }
                    hashMapCount--;
                }
            }
            --this.size;
            this.afterNodeRemoval((CAHashMap.Node)node);
            return node;
        }

        return null;
    }

    public void clear() {
        ++this.modCount;
        Object[] tab;
        if ((tab = this.table) != null && this.size > 0) {
            this.size = 0;

            for(int i = 0; i < tab.length; ++i) {
                tab[i] = null;
            }
        }

    }

    public boolean containsValue(Object value) {
        Object[] tab;
        if ((tab = this.table) != null && this.size > 0) {
            K key = computeKey(value);
            return containsKey(key);
        }
        return false;
    }

    public Set<K> keySet() {
        /* TO DO
        Set<K> ks = this.keySet;
        if (ks == null) {
            ks = new CAHashMap.KeySet();
            this.keySet = (Set)ks;
        }

        return (Set)ks;
        */
         return null;
    }

    public Collection<V> values() {
        /* TO DO
        Collection<V> vs = this.values;
        if (vs == null) {
            vs = new CAHashMap.Values();
            this.values = (Collection)vs;
        }

        return (Collection)vs;
        */
         return null;
    }

    public Set<Entry<K, V>> entrySet() {
        // TO DO
        return null;
    }

    public V getOrDefault(Object key, V defaultValue) {
        CAHashMap.Node e;
        return (e = this.getNode(hash(key), key)) == null ? defaultValue : (V) e.getValue();
    }

    public V putIfAbsent(K key, V value) {
        return this.putVal(hash(key), key, value, true, true);
    }

    public boolean remove(Object key, Object value) {
        return this.removeNode(hash(key), key) != null;
    }

    public boolean replace(K key, V oldValue, V newValue) {
        CAHashMap.Node e;
        Object v;
        if ((e = this.getNode(hash(key), key)) == null || (v = e.getValue()) != oldValue && (v == null || !v.equals(oldValue))) {
            return false;
        } else {
            e.setValue(newValue);
            this.afterNodeAccess(e);
            return true;
        }
    }

    public V replace(K key, V value) {
        CAHashMap.Node e;
        if ((e = this.getNode(hash(key), key)) != null) {
            V oldValue = (V) e.getValue();
            e.setValue(value);
            this.afterNodeAccess(e);
            return oldValue;
        } else {
            return null;
        }
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
      // TO DO
        return null;
    }

    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null) {
            throw new NullPointerException();
        } else {
            int hash = hash(key);
            CAHashMap.Node e;
            Object oldValue;
            if ((e = this.getNode(hash, key)) != null && (oldValue = e.getValue()) != null) {
                int mc = this.modCount;
                V v = remappingFunction.apply(key, (V) oldValue);
                if (mc != this.modCount) {
                    throw new ConcurrentModificationException();
                }

                if (v != null) {
                    e.setValue(v);
                    this.afterNodeAccess(e);
                    return v;
                }

                this.removeNode(hash, key);
            }

            return null;
        }
    }

    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return null; // TO DO ?
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return null; // TO DO ?
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null) {
            throw new NullPointerException();
        } else {
            Object[] tab;
            if (this.size > 0 && (tab = this.table) != null) {
                int mc = this.modCount;
                int tabLen = tab.length;

                for(int i = 0; i < tabLen; ++i) {
                    if (tab[i] instanceof byte[]) {
                        K aKey = computeKey(tab[i]);
                        action.accept(aKey, (V) tab[i]);
                    } else {
                        Map<K,CAHashMap.ValueNode<K, V>> hmap = (HashMap<K,CAHashMap.ValueNode<K, V>>)  tab[i];
                        for (Map.Entry<K,CAHashMap.ValueNode<K, V>> entry : hmap.entrySet()) {
                            action.accept(entry.getKey(), entry.getValue().getValue());
                        }
                    }
                }

                if (this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null) {
            throw new NullPointerException();
        } else {
            Object[] tab;
            if (this.size > 0 && (tab = this.table) != null) {
                int mc = this.modCount;
                int tabLen = tab.length;

                for(int i = 0; i < tabLen; ++i) {
                    if (tab[i] instanceof byte[]) {
                        Object eObject = tab[i];
                        tab[i] = function.apply(computeKey(eObject),(V) eObject);
                    } else {
                        Map<K,CAHashMap.ValueNode<K, V>> hmap = (HashMap<K,CAHashMap.ValueNode<K, V>>)  tab[i];
                        for (Map.Entry<K,CAHashMap.ValueNode<K, V>> entry : hmap.entrySet()) {
                            CAHashMap.ValueNode<K, V> e = entry.getValue();
                            e.setValue( function.apply((K) e.getKey(), (V) e.getValue()));
                        }
                    }
                }

                if (this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    public K computeKey(Object e) {
        return computeKeyFunction.apply((V) e);
    }

    public Object clone() {
        CAHashMap result;
        try {
            result = (CAHashMap)super.clone();
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
                    CAHashMap.Node<K, V>[] tab = new CAHashMap.Node[cap];
                    this.table = tab;

                    for(int i = 0; i < mappings; ++i) {
                        K key = (K) s.readObject();
                        V value = (V) s.readObject();
                        this.putVal(hash(key), key, value, false, false);
                    }
                }

            }
        } else {
            throw new InvalidObjectException("Illegal load factor: " + this.loadFactor);
        }
    }

    CAHashMap.ValueNode<K, V> newNode(int hash, K key, V value) {
        return new CAHashMap.ValueNode(hash, key, value);
    }

    CAHashMap.Node<K, V> replacementNode(CAHashMap.ValueNode<K, V> p) {
        return new CAHashMap.ValueNode(p.hash, p.key, p.value);
    }


    void reinitialize() {
        this.table = null;
        this.entrySet = null;
        /* TO DO
        this.keySet = null;
        this.values = null;
         */
        this.modCount = 0;
        this.threshold = 0;
        this.size = 0;
    }

    void afterNodeAccess(CAHashMap.Node<K, V> p) {
    }

    void afterNodeInsertion(boolean evict) {
    }

    void afterNodeRemoval(CAHashMap.Node<K, V> p) {
    }

    void internalWriteEntries(ObjectOutputStream s) throws IOException {
        Object[] tab;
        if (this.size > 0 && (tab = this.table) != null) {
            int tabLen = tab.length;

            for(int i = 0; i < tabLen; ++i) {
                if (tab[i] instanceof byte[]) {
                    Object  eObject =tab[i];
                    s.writeObject(computeKey(eObject));
                    s.writeObject(eObject);
                } else {
                    Map<K,CAHashMap.ValueNode<K, V>> hmap = (Map<K,CAHashMap.ValueNode<K, V>>) tab[i];
                    for (Map.Entry<K,CAHashMap.ValueNode<K, V>> entry : hmap.entrySet()) {
                        s.writeObject(entry.getKey());
                        s.writeObject(entry.getValue().getValue());
                    }
                }
            }
        }

    }

    public final void forEach(Consumer<? super Entry<K, V>> action) {
        if (action == null) {
            throw new NullPointerException();
        } else {
            Object[] tab;
            if (CAHashMap.this.size > 0 && (tab = CAHashMap.this.table) != null) {
                int mc = CAHashMap.this.modCount;

                int tabLen = tab.length;
                for (int i = 0; i < tabLen; ++i) {
                    if (tab[i] instanceof byte[]) {
                        CAHashMap.Node<K, V> e = newVirtualNode(tab, i, null);
                        action.accept(e);

                    } else {
                        Map<K, CAHashMap.ValueNode<K, V>> hmap = (Map<K, CAHashMap.ValueNode<K, V>>) tab[i];
                        for (Map.Entry<K, CAHashMap.ValueNode<K, V>> entry : hmap.entrySet()) {
                            action.accept(entry.getValue());
                        }
                    }
                }

                if (CAHashMap.this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    static abstract class  Node<K, V> implements Entry<K, V>  {

        public final String toString() {
            return this.getKey() + "=" + this.getValue();
        }

        public final int hashCode() {
            return Objects.hashCode(this.getKey());
        }


        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            } else {
                if (o instanceof Entry) {
                    Entry<?, ?> e = (Entry)o;
                    if (Objects.equals(this.getKey(), e.getKey()) && Objects.equals(this.getValue(), e.getValue())) {
                        return true;
                    }
                }

                return false;
            }
        }
    }
    static class VirtualNode<K, V> extends Node<K, V>  {
        int tabIndex;
        Object[] tab;
        Function<V,K> computeKeyFunction;
        K optKey;

        VirtualNode(Object[] tab,int tabIndex,K optKey,Function<V,K> computeKeyFunction) {
            this.tab = tab;
            this.tabIndex = tabIndex;
            this.optKey = optKey; // may be nulll
            this.computeKeyFunction = computeKeyFunction;
        }

        public final K getKey() {
            if (optKey==null)
                optKey = computeKeyFunction.apply((V) tab[tabIndex]);
            return optKey;
        }

        public final V getValue() {
            return (V) tab[tabIndex];
        }

        public final V setValue(V newValue) {
            V oldValue = (V) tab[tabIndex];
             tab[tabIndex]= newValue;
            return oldValue;
        }
    }

    static class ValueNode<K, V> extends Node<K, V>  {
        final int hash;
        final K key;
        V value;

        ValueNode(int hash, K key, V value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        public final K getKey() {
            return this.key;
        }

        public final V getValue() {
            return this.value;
        }

        public final V setValue(V newValue) {
            V oldValue = this.value;
            this.value = newValue;
            return oldValue;
        }

    }
}

