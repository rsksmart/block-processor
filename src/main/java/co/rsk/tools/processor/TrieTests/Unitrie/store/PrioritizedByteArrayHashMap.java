package co.rsk.tools.processor.TrieTests.Unitrie.store;

import co.rsk.tools.processor.TrieTests.Unitrie.AbstractByteArrayRefHeap;
import co.rsk.tools.processor.examples.storage.ObjectIO;

public class PrioritizedByteArrayHashMap extends ByteArrayRefHashMap {

    int currentPriority;
    int minPriority;
    public int MaxPriority = Integer.MAX_VALUE;
    public boolean removeInBulk = false;
    public boolean moveToTopAccessedItems = true;
    int startScanForRemoval =0;

    public PrioritizedByteArrayHashMap(int initialCapacity, float loadFactor,
                            BAKeyValueRelation BAKeyValueRelation,
                            long newBeHeapCapacity,
                            AbstractByteArrayRefHeap sharedBaHeap,
                            int maxElements) {
        super(initialCapacity,loadFactor,BAKeyValueRelation,newBeHeapCapacity,sharedBaHeap,maxElements);
    }

    public int getPriority(Object key) {
        int e = this.getNode(hash(key), key,null);
        if (e!=-1) {
            e = unmarkHandle(e);
            byte[] metadata =baHeap.retrieveMetadata(e);
            int priority = ObjectIO.getInt(metadata,0);
            return priority;
        } else
            return -1;
    }

    public void refreshedHandle(int p)  {
        // Update priority
        if ((maxElements!=0) && (moveToTopAccessedItems)) {
            p = unmarkHandle(p);
            byte[] metadata = baHeap.retrieveMetadata(p);
            int priority = ObjectIO.getInt(metadata,0);
            priority = currentPriority++;
            ObjectIO.putInt(metadata,0,priority);
            baHeap.setMetadata(p,metadata);
        }

    }
    public byte[] getOptionalMetadata(int handle)  {
        byte[] metadata = null;
        if (maxElements>0)
            metadata = baHeap.retrieveMetadata(unmarkHandle(handle));
        return metadata;
    }

    byte[] getNewMetadata() {
        return getPriorityAsMetadata();
    }

    public void fillTableItem(TableItem ti) {
        ti.priority = ObjectIO.getInt(ti.metadata,0);
    }

    byte[] getPriorityAsMetadata() {
        if (maxElements==0)
            return null;
        byte[] m = new byte[4];
        ObjectIO.putInt(m,0,currentPriority++);
        return m;
    }

    void afterNodeInsertion(byte[] p, boolean evict) {

        if (maxElements == 0) return;
        // If priority reaches the maximum integer, we must re-prioritize all elements
        // to make space for higher priorities.
        // maxElements must be lower than Integer.MAX_VALUE to avoid
        // reprioritizing frequently.
        // But it it is one bit lower (less than half) it's enough, since reprioritizations
        // will happen every 2^30 insertions.
        // Here we let the maximum be variable to be able to test the reprizitization code
        // without too many insertions
        if (currentPriority == MaxPriority)
            reprioritize();
        if (!evict) return;


        removeLowPriorityElements();
    }

    // This method performs a scan over all elements and remove all below a certain
    // limit.
    public int removeLowerPriorityElements(int from,int count, boolean notifyRemoval,boolean doremap) {
        int j = from;
        int mask = table.length-1;
        int boundary = (from+count) & mask;
        for (int c = 0; c < count; ++c) {
            int p = table[j];
            if (p != empty) {
                byte[] metadata =baHeap.retrieveMetadata(unmarkHandle(p));
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
                        if (!isValueHandle(p)) {
                            byte[] key =    baHeap.retrieveData(unmarkHandle(p));
                            this.afterNodeRemoval(key,null, metadata);
                        } else {
                            byte[] data = baHeap.retrieveData(p);
                            this.afterNodeRemoval(null,data, metadata);
                        }
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
                    baHeap.remap(unmarkHandle(p));
                }
            }
            j = (j+1) & mask;
        }
        return j;
    }


    void removeLowPriorityElements_new_attempt() {
        if (removeInBulk) {
            if (size()<=maxElements) return;
            int itemsToRemove = size()-maxElements;

            // now add 10% of additional elements to remove
            // to avoid continuoues scanning
            itemsToRemove += maxElements/10;

            if (logEvents)
                System.out.println("Start removing "+ itemsToRemove+" elements");

            checkHeap();
            int priorSize = size;
            if (baHeap.heapIsAlmostFull()) {
                if (logEvents)
                    System.out.println("Remapping simultaneusly");
                baHeap.beginRemap();
                //removeLowerPriorityElements(0, table.length, false, true);

                baHeap.endRemap();
            }
            else
                removeLowerPriorityElements(0, table.length, false,false);
            if (logEvents)
                System.out.println("Stop removing elements ("+(priorSize-size)+" elements removed)");
            checkHeap();
        }
    }
    // TODO This is NOT the best strategy, because when elements are get()
    // they move to higher priorities, so in the lower segment it's possible
    // to have only few elements.
    // We must remove a certain number of elements, not by priority.
    // Here we try many times until we removed enough elements.
    void removeLowPriorityElements() {
        if (removeInBulk) {
            int pass = 1;

            while (size()>maxElements) {
                System.out.println("Pass "+pass);
                pass++;
                removeLowPriorityElementsInBulk();
            }
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

    void removeLowPriorityElementsInBulk() {
        // if there is no slot left, remove lower priorities
        // because we need always at least one slot free.
        int divisor = 10;
        int increment = (currentPriority - minPriority) / divisor;
        if (increment == 0)
            increment = 1;

        minPriority = minPriority + increment;
        if (logEvents)
            System.out.println("Start removing elements below priority " + minPriority + "...");

        checkHeap();
        int priorSize = size;
        if (baHeap.heapIsAlmostFull()) {
            if (logEvents)
                System.out.println("Remapping simultaneously");
            baHeap.beginRemap();
            removeLowerPriorityElements(0, table.length, false, true);
            baHeap.endRemap();
        } else
            removeLowerPriorityElements(0, table.length, false, false);
        if (logEvents)
            System.out.println("Stop removing elements (" + (priorSize - size) + " elements removed)");
        checkHeap();
    }

    void reprioritize() {
        if (logEvents)
            System.out.println("Reprioritizing");
        int count = table.length;
        for (int c = 0; c < count; ++c) {
            int p = table[c];
            if (p != empty) {
                p = unmarkHandle(p);
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
    // The key is optional. If not given, it must be computed from the data.
    void afterNodeRemoval(byte[] key,byte[] data,byte[] metadata) {
        if (maxElements==0) return;
        if (metadata==null) return;

        int priority = ObjectIO.getInt(metadata,0);
        if (priority==minPriority)
            minPriority = minPriority+1;
    }
}
