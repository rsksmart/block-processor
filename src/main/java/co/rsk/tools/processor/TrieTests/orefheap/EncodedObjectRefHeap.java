package co.rsk.tools.processor.TrieTests.orefheap;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ObjectReference;
import co.rsk.tools.processor.TrieTests.oheap.HeapFileDesc;
import co.rsk.tools.processor.TrieTests.oheap.Space;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import org.ethereum.util.ByteUtil;

import java.nio.ByteBuffer;
import java.util.*;


public class EncodedObjectRefHeap extends EncodedObjectStore {
    // The number of maxObjects must be at least 5 times higher than the number of
    // elements that will be inserted in the trie because intermediate nodes
    // consume handles also.
    public static int default_maxObjects = 40 * 1000 * 1000;
    public static int default_spaceMegabytes = 1600;
    public static int default_maxSpaces = 2;
    public static int default_freeSpaces = 1;
    public static int default_compressSpaces = default_maxSpaces;
    public static int default_remapThreshold = 95;

    public static int debugHandle = -2; //104612;
    public static boolean debugCheckAll = false;
    public static boolean debugLogInfo = false;

    public final int remapThreshold;

    public int megas;
    public int spaceSize;
    final int maxSpaces;
    public final long MaxPointer;
    final int freeSpaces;
    final int compressSpaces; // == maxSpaces means compress all

    static final int F0 = 0; // field 0 is 1  bytes in length
    static final int F1 = 1; // field 1 is 4 bytes in length
    static final int F2 = 5; // field 2 is 4 bytes in length
    static final int F3 = 9;

    long references[];
    int unusedHandles[];
    int unusedHandlesCount;
    int highestHandle;

    private HandleComparator handleComparator;

    public EncodedObjectRefHeap() {
        megas = default_spaceMegabytes;
        maxSpaces = default_maxSpaces;
        freeSpaces = default_freeSpaces;
        compressSpaces = default_compressSpaces;
        remapThreshold = default_remapThreshold;
        spaceSize = megas * 1000 * 1000;
        MaxPointer = 1L * maxSpaces * spaceSize;
        // Must call initialize
    }

    public void initialize() {
        // creates the memory set by setMaxMemory()
        reset();
    }

    public long getMaxMemory() {
        return spaceSize * maxSpaces;
    }

    public void setMaxMemory(long m) {
        long q = m / maxSpaces;
        if (q > Integer.MAX_VALUE)
            throw new RuntimeException("Cannot support memory requested");
        spaceSize = (int) q;
        megas = (int) (getMaxMemory() / 1000 / 1000);
    }

    public void save(String fileName, long rootOfs) {

        int head = headOfFilledSpaces.head;
        while (head != -1) {
            spaces[head].saveToFile(fileName + "." + head + ".space");
            head = spaces[head].previousSpaceNum;
        }
        head = headOfPartiallyFilledSpaces.head;
        while (head != -1) {
            spaces[head].saveToFile(fileName + "." + head + ".space");
            head = spaces[head].previousSpaceNum;
        }

        getCurSpace().saveToFile(fileName + "." + curSpaceNum + ".space");
        HeapFileDesc desc = new HeapFileDesc();
        desc.filledSpaces = getSpaces(headOfFilledSpaces);
        desc.emptySpaces = getSpaces(headOfPartiallyFilledSpaces);
        desc.currentSpace = curSpaceNum;
        desc.rootOfs = rootOfs;
        desc.saveToFile(fileName + ".desc");

    }

    public long load(String fileName) {
        HeapFileDesc desc = HeapFileDesc.loadFromFile(fileName + ".desc");
        setHead(headOfFilledSpaces, desc.filledSpaces, true);
        setHead(headOfPartiallyFilledSpaces, desc.emptySpaces, false);

        for (int i = 0; i < desc.filledSpaces.length; i++) {
            int num = desc.filledSpaces[i];
            spaces[num].readFromFile(fileName + "." + num + ".space");
        }
        // This are partially filled spaces
        for (int i = 0; i < desc.emptySpaces.length; i++) {
            int num = desc.emptySpaces[i];
            spaces[num].readFromFile(fileName + "." + num + ".space");
        }
        curSpaceNum = desc.currentSpace;
        spaces[curSpaceNum].readFromFile(fileName + "." + curSpaceNum + ".space");
        return desc.rootOfs;
    }

    public void setHead(SpaceHead sh, int[] vec, boolean filled) {
        sh.count = vec.length;
        sh.head = linkSpaces(vec, filled);
    }

    public int[] getSpaces(SpaceHead sh) {
        int h = sh.head;
        int[] vec = new int[sh.count];
        int i = 0;
        while (h != -1) {
            vec[i] = h;
            i++;
            h = spaces[h].previousSpaceNum;
        }
        return vec;
    }

    public int linkSpaces(int[] vec, boolean filled) {
        int prev = -1;
        for (int i = vec.length - 1; i >= 0; i--) {
            int sn = vec[i];
            spaces[sn].previousSpaceNum = prev;
            spaces[sn].filled = filled;
            prev = sn;
        }
        return prev;
    }

    public int getCurSpaceNum() {
        return curSpaceNum;
    }

    public int getCompressionPercent() {
        return compressionPercent;
    }

    class SpaceHead {
        int head = -1; // inicialize in unlinked
        int count = 0;

        public String getDesc() {
            String s = "";
            int aHead = head;
            while (aHead != -1) {
                s = s + aHead + " ";
                aHead = spaces[aHead].previousSpaceNum;
            }
            return s;
        }

        public int removeLast() {
            //  This is iterative because generally there will be only a few spaces.
            // If many spaces are used, then a double-linked list must be used
            int aHead = head;
            int lastHead = -1;
            int preLastHead = -1;
            while (aHead != -1) {
                preLastHead = lastHead;
                lastHead = aHead;
                aHead = spaces[aHead].previousSpaceNum;
            }
            count--;
            if (preLastHead != -1)
                spaces[preLastHead].previousSpaceNum = -1;
            else
                head = -1;
            return lastHead;
        }

        public boolean empty() {
            return head == -1;
        }

        public void addSpace(int i) {
            int prev = head;
            head = i;
            spaces[head].previousSpaceNum = prev;
            count++;
        }

        public void clear() {
            head = -1;
            count = 0;
        }

        public int peekFirst() {
            return head;
        }

        public int removeFirst() {
            if (head == -1)
                throw new RuntimeException("no space avail");
            int s = head;
            head = spaces[head].previousSpaceNum;
            count--;
            return s;
        }
    }

    static EncodedObjectRefHeap objectHeap;
    // Static 1 gigabyte. //100 megabytes

    public Space[] spaces;
    public BitSet oldSpacesBitmap = new BitSet();
    public BitSet touchedHandles;

    SpaceHead headOfPartiallyFilledSpaces = new SpaceHead();
    SpaceHead headOfFilledSpaces = new SpaceHead();

    int curSpaceNum;
    boolean remapping;
    //int oldSpaceNum;
    long remappedSize;
    int compressionPercent;
    int referenceCount;

    public static EncodedObjectRefHeap get() {
        if (objectHeap == null)
            objectHeap = new EncodedObjectRefHeap();

        return objectHeap;
    }


    public int getNewSpaceNum() {
        int s = headOfPartiallyFilledSpaces.removeFirst();
        if (spaces[s].empty())
            spaces[s].create(spaceSize);
        spaces[s].unlink();
        return s;
    }

    public void reset() {
        int maxReferences = default_maxObjects;
        if (maxReferences > Integer.MAX_VALUE)
            maxReferences = Integer.MAX_VALUE;

        references = new long[maxReferences];
        unusedHandles = new int[maxReferences];
        for (int i = 0; i < maxReferences; i++)
            unusedHandles[i] = maxReferences - i - 1;

        referenceCount = 0;
        unusedHandlesCount = maxReferences;

        headOfPartiallyFilledSpaces.clear();
        spaces = new Space[maxSpaces];

        for (int i = 0; i < maxSpaces; i++) {
            spaces[i] = new Space();
            headOfPartiallyFilledSpaces.addSpace(i);
        }
        curSpaceNum = getNewSpaceNum();
        headOfFilledSpaces.clear();
        clearRemapMode();
        System.out.println("remapThreshold: " + remapThreshold);
        System.out.println("megas = " + megas);
        System.out.println("spaceSize = " + spaceSize / 1000 / 1000 + "M bytes");
        System.out.println("maxSpaces = " + maxSpaces);
        System.out.println("freeSpaces = " + freeSpaces);
        System.out.println("compressSpaces = " + compressSpaces);

    }

    public void clearRemapMode() {
        move = null;
        moveCount = 0;
        touchedHandles = null;
        remapping = false;
        oldSpacesBitmap.clear();
    }

    public boolean supportsGarbageCollection() {
        return true;
    }

    public boolean isRemapping() {
        return remapping;
    }

    public void beginRemap() {
        oldSpacesBitmap.clear();
        touchedHandles = new BitSet();

        remappedSize = 0;

        chooseSpacesToCompress();

        // Create a vector to mark all objects that need to be moved
        //move = new ArrayList<Integer>();
        move = new int[highestHandle + 1];
        remapping = true;
    }

    int[] move; // List of handles
    int moveCount;

    public void chooseSpacesToCompress() {


        // Compress all spaces ?
        if (compressSpaces == maxSpaces)
            chooseToCompressAllSpaces();
        else
            chooseToCompressSomeSpaces();

    }

    public void chooseToCompressAllSpaces() {
        fillCurrentSpace();
        for (int i = 0; i < maxSpaces; i++) {
            oldSpacesBitmap.set(i);
        }
    }

    public void chooseToCompressSomeSpaces() {
        boolean currentMapAdded = false;
        for (int i = 0; i < compressSpaces; i++) {
            // nothing filled, nothing to do
            if (headOfFilledSpaces.empty()) {
                if (currentMapAdded)
                    break;

                currentMapAdded = true;
                // There is no filled space. Then switch the current space
                // to a filled space and compress that.
                fillCurrentSpace();
            }

            int oldSpaceNum = headOfFilledSpaces.removeLast();

            System.out.println(">> add compress space: " + oldSpaceNum);
            if ((oldSpaceNum == -1) || (spaces[oldSpaceNum] == null))
                throw new RuntimeException("Space should exists");

            if (oldSpaceNum == curSpaceNum) {
                // We're trying to compress the space that is currently active.
                // We should either stop all threads to avoid conflicts or advance
                // the curSpaceNum so that writes happen in the next space.
                // We'll do that.
                //curSpaceNum = (curSpaceNum +1) & maxSpaces;
                throw new RuntimeException("never happens");
            }

            if (!spaces[oldSpaceNum].filled) {
                // Warning: trying to compress a Space that is not filled
                // this can happen if compression is manually triggered
            }
            oldSpacesBitmap.set(oldSpaceNum);
        }
    }

    class HandleComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer a, Integer b) {
            long ofsA = references[a];
            long ofsB = references[b];
            return ofsA < ofsB ? -1 : ofsA == ofsB ? 0 : 1;
        }
    }

    public void checkSorted(int[] move, int moveCount) {

        /*for(int i=0;i<move.size()-1;i++) {
            if (references[move.get(i)]>=references[move.get(i+1)]){
                throw new RuntimeException("bad sort");
            }
        }*/
        for (int i = 0; i < moveCount - 1; i++) {
            if (references[move[i]] >= references[move[i + 1]]) {
                throw new RuntimeException("bad sort");
            }
        }
    }

    public void compress(int[] move, int moveCount) { //List<Integer> move) {
        if (debugLogInfo)
            //System.out.println("Handles to move: "+move.size());
            System.out.println("Handles to move: " + moveCount);

        //handleComparator = new HandleComparator();
        // All objects that are in the move list need to be packed in-place
        // We'll sort the move objects based on the offset
        //QuickSort qs = new QuickSort();
        System.out.println("sorting...");
        //qs.quickSort(move,0,moveCount-1);

        List<Integer> moveSort = new ArrayList<>(moveCount);
        for (int i = 0; i < moveCount; i++)
            moveSort.add(move[i]);
        handleComparator = new HandleComparator();
        moveSort.sort(handleComparator);
        for (int i = 0; i < moveCount; i++)
            move[i] = moveSort.get(i);
        moveSort = null;
        System.out.println("sorted!");
        //move.sort(handleComparator);
        if (debugCheckAll)
            checkSorted(move, moveCount);
        long originalSize = 0;
        // Now we can start to compress
        int prevSpace = -1;

        int baseOfs = 0;
        //for(int i=0;i<move.size();i++) {
        //    int h = move.get(i);
        for (int i = 0; i < moveCount; i++) {
            int h = move[i];

            long ofs = references[h];
            if (ofs < 0) debugHandle = h;
            if (h == debugHandle) {
                dumpHandleContent(h);
                System.out.println("Moving from " + ofs);
            }
            int hspace = getSpaceNumOfPointer(ofs);
            int spaceOfs = getSpaceOfsFromPointer(hspace, ofs);
            if (hspace > prevSpace) {
                if (prevSpace != -1) {
                    originalSize += spaces[prevSpace].memTop;
                    spaces[prevSpace].memTop = baseOfs;
                }
                // close this space
                prevSpace = hspace;
                baseOfs = 0;
            }
            // now compress the object in the space
            Space space = spaces[hspace];
            try {
                checkDebugHeader(space, spaceOfs);
            } catch (RuntimeException e) {
                System.out.print("bad handle: " + h);
                //System.out.print(getHandleInfo(h));
                throw e;
            }
            int len = space.mem[spaceOfs];
            // Now check that either both have indexes or none
            int leftHandle = ObjectIO.getInt(space.mem, spaceOfs + F1);
            int rightHandle = ObjectIO.getInt(space.mem, spaceOfs + F2);
            references[h] = buildPointer(hspace, baseOfs); // stores new reference
            // StoreObject receives the offset of the object data, not
            // the object header, therefore we must add F3 to spaceOfs.
            int newTop = storeObject(space, baseOfs, space.mem,
                    spaceOfs + F3, len, leftHandle, rightHandle);
            if (h == debugHandle) {
                dumpHandleContent(h);
                System.out.println("Moving to " + baseOfs);
            }
            baseOfs = newTop;

        }
        if (prevSpace != -1) {
            originalSize += spaces[prevSpace].memTop;
            spaces[prevSpace].memTop = baseOfs;
        }
    }


    public void moveCompressedSpacesToPartiallyFilled() {
        // We clear all queues.
        // move all spaces to partially filled
        headOfFilledSpaces.clear();
        headOfPartiallyFilledSpaces.clear();

        for (int i = 0; i < maxSpaces; i++) {
            spaces[i].filled = false;
            headOfPartiallyFilledSpaces.addSpace(i);
        }
    }

    public void moveFilledSpacesToPartiallyFilled() {
        // I remove all filled spaces and move them to partially filled
        // Even if a space has not been compressed.
        // If not compressed, it will be moved back to the filled list once
        // an object is tried to be added to the space
        do {
            int spaceNum = headOfFilledSpaces.removeFirst();
            if (spaceNum == -1) break;
            spaces[spaceNum].filled = false;
            headOfPartiallyFilledSpaces.addSpace(spaceNum);
        } while (true);
    }

    public void endRemap() {
        compress(move, moveCount);
        markAllUntouchedHandlesAssUnused();
        moveCompressedSpacesToPartiallyFilled();
        clearRemapMode();
        chooseCurrentSpace();
        if (debugCheckAll)
            checkAll();
        remapping = false;
    }

    public void markAllUntouchedHandlesAssUnused() {
        if (debugLogInfo)
            System.out.println("Disposing all unused handles...");
        int disposedHandles = 0;
        for (int i = 0; i < highestHandle; i++) {
            if ((references[i] != -1) && (!touchedHandles.get(i))) {
                references[i] = -1;
                unusedHandles[unusedHandlesCount] = i;
                disposedHandles++;
                unusedHandlesCount++;
            }
        }
        if (debugLogInfo)
            System.out.println("Disposed handles: " + disposedHandles);
    }

    public void checkAll() {
        System.out.println("Checking all...");
        for (int i = 0; i < highestHandle; i++) {
            long ofs = references[i];
            if (ofs == -1) // unassigned
                continue;
            checkObject(ofs);

        }
        System.out.println("Checked!");
    }

    public void emptyOldSpaces() {
        long originalSize = 0;
        for (int i = 0; i < maxSpaces; i++) {
            if (oldSpacesBitmap.get(i)) {
                int oldSpaceNum = i;
                originalSize += spaces[oldSpaceNum].memTop;
                spaces[oldSpaceNum].softDestroy();
                headOfPartiallyFilledSpaces.addSpace(oldSpaceNum);
            }
        }
        compressionPercent = (int) (remappedSize * 100 / originalSize);

    }

    public String getRefInfo(EncodedObjectRef encodedRef) {
        int handle = getHandle(encodedRef);
        return getHandleInfo(handle);
    }

    public String getHandleInfo(int handle) {
        String r = "";
        r += "handle: " + handle + "\n";
        if (handle >= 0) {
            long ofs = references[handle];
            r += "ofs: " + ofs + "\n";
            if (ofs != -1) {
                int s = getSpaceNumOfPointer(ofs);
                r += "space: " + s + "\n";
                int iofs = getSpaceOfsFromPointer(s, ofs);
                r += "internalOfs: " + iofs + "\n";
                r += "messageLen: " + spaces[s].mem[iofs] + "\n";
                byte[] bytes = retrieveData(ofs);
                r += "data: " + ByteUtil.toHexString(bytes) + "\n";
            }
        }
        return r;
    }

    public boolean verifyOfsChange(long oldo, long newo) {
        if ((oldo == -1) && (newo != -1)) return false;
        if ((oldo != -1) && (newo == -1)) return false;
        return true;
    }

    public void checkDuringRemap(EncodedObjectRef encodedRef) {
        checkDuringRemap(getHandle(encodedRef));
    }

    public void dumpHandleContent(int handle) {
        long ofs = references[handle];
        byte[] bytes = retrieveData(ofs);
        System.out.println("dump: " + ByteUtil.toHexString(bytes));
    }

    public void checkHandle(int handle) {
        if (handle == -1) return;
        if (handle == -1)
            return;

        if ((handle < -1) || (handle > highestHandle))
            throw new RuntimeException("Invalid handle=" + handle);
        long ofs = references[handle];
        if ((ofs < -1) || ofs >= MaxPointer) {
            throw new RuntimeException("Invalid ofs arg (2)ofs=" + ofs);
        }
    }

    public void checkDuringRemap(int handle) {
        if (handle == -1) return;
        long ofs = references[handle];
        if (ofs == -1) return;
        //if (getSpaceNumOfPointer(ofs)==oldSpaceNum)
        if (oldSpacesBitmap.get(getSpaceNumOfPointer(ofs)))
            throw new RuntimeException("bad pointer!");
    }

    final int debugHeaderSize = 2;
    final int M1 = 101;
    final int M2 = 74;

    public void writeDebugFooter(Space space, int ofs) {
        if (ofs > space.mem.length - debugHeaderSize) return;
        space.mem[ofs] = M1;
        space.mem[ofs + 1] = M2;
    }

    public void writeDebugHeader(Space space, int ofs) {
        if (ofs < debugHeaderSize) return;
        space.mem[ofs - 2] = M1;
        space.mem[ofs - 1] = M2;
    }

    public void checkDeugMagicWord(Space space, int ofs) {
        if ((space.mem[ofs] != M1) || (space.mem[ofs + 1] != M2))
            throw new RuntimeException("no magic word: ofs=" + ofs + " bytes=" + space.mem[ofs] + "," + space.mem[ofs + 1]);

    }

    public void checkDebugHeader(Space space, int ofs) {
        if (ofs == 0) return;
        if (ofs < debugHeaderSize)
            throw new RuntimeException("invalid ofs");
        ofs -= 2;
        checkDeugMagicWord(space, ofs);
    }

    public void checkDebugFooter(Space space, int ofs) {
        if (ofs > space.mem.length - debugHeaderSize) return;
        checkDeugMagicWord(space, ofs);
    }

    public EncodedObjectRef remap(EncodedObjectRef aRef, EncodedObjectRef aleftRef, EncodedObjectRef arightRef) {
        //int rightHandle  = getHandle(arightRef);
        //int leftHandle = getHandle(aleftRef);
        int handle = getHandle(aRef);
        remap(handle);
        // return the same object: movements will occur later
        //return new IntEOR(remap(handle,leftHandle,rightHandle));
        return aRef;
    }

    public void remap(int handle) {
        // mark if it needs movement
        if (handle != -1)
            touchedHandles.set(handle);
        if (needsToMoveHandle(handle)) {
            //move.add(handle);
            move[moveCount] = handle;
            moveCount++;
        }
    }

    public boolean needsToMoveHandle(int handle) {
        if (handle == -1) return false;
        return needsToMoveOfs(references[handle]);
    }

    public boolean needsToMoveOfs(long ofs) {
        int objectSpaceNum = getSpaceNumOfPointer(ofs);
        boolean objectOffsetNeedsToChange = (oldSpacesBitmap.get(objectSpaceNum));
        return objectOffsetNeedsToChange;
    }

    public Space getCurSpace() {
        return spaces[curSpaceNum];
    }

    public int getMemSize() {

        return getCurSpace().mem.length;

    }

    public boolean currentSpaceIsAlmostFull() {
        // To make it faster and test it
        return getCurSpace().getUsagePercent() > remapThreshold;
        //return (mem.length-memTop)<1_000_000;
    }

    public boolean heapIsAlmostFull() {
        // There must be one empty space in the empty queue, because this may be needed during
        // remap if new elements are added with multi-threading.
        // Since currently we're using single-thread, and we're stopping adding new objects
        // during gargage collection, then we can fill all spaces before doing gc.
        return (getUsagePercent() > 90);
    }

    public boolean heapIsAlmostFull_for_multithreading() {
        return (headOfPartiallyFilledSpaces.count <= freeSpaces) &&
                (getCurSpace().getUsagePercent() > remapThreshold);
    }

    public int getWriteSpaceNum() {
        int writeSpace;
        if (isRemapping())
            writeSpace = 1 - curSpaceNum;
        else
            writeSpace = curSpaceNum;
        return writeSpace;
    }

    public long buildPointer(int spaceNum, long ofs) {
        if (ofs == -1)
            return ofs;
        if (ofs >= spaceSize)
            throw new RuntimeException("Invalid space offset given(1)");
        return (1L * spaceNum * spaceSize) + ofs;
    }

    public int getSpaceOfsFromPointer(int spaceNum, long ofs) {
        if (ofs == -1)
            return -1;
        long ofsSpace = ofs / spaceSize;
        if (ofsSpace != spaceNum)
            throw new RuntimeException("Invalid space offset given(2) spaceNum=" + spaceNum + " ofs=" + ofs);
        return (int) (ofs % spaceSize);
    }

    public int getSpaceNumOfPointer(long ofs) {
        if (ofs < 0)
            throw new RuntimeException("Invalid space offset given(3)");
        return (int) (ofs / spaceSize);
    }

    public boolean spaceAvailFor(int msgLength) {
        msgLength += F3;
        return (getCurSpace().spaceAvailFor(msgLength));

    }

    public void checkInRightSpace(int handle) {
        //if (handle==-1) return;
        //long ofs = references[handle];
        //if (ofs==-1)
        //    return;
        //int spaceNum = getSpaceNumOfPointer(ofs);

    }

    public void fillCurrentSpace() {
        if (curSpaceNum == -1) return;
        getCurSpace().filled = true; // mark as filled
        headOfFilledSpaces.addSpace(curSpaceNum);
        curSpaceNum = -1; // No current space available.
    }

    public void moveToNextCurSpace() {
        int oldCurSpaceNum = curSpaceNum;
        System.out.println(">> Filling space " + oldCurSpaceNum);
        fillCurrentSpace();
        chooseCurrentSpace();
    }

    public void chooseCurrentSpace() {
        curSpaceNum = headOfPartiallyFilledSpaces.removeFirst();
        Space space = getCurSpace();

        System.out.println(">> Switching curspace to " + curSpaceNum);
        if (space.empty())
            space.create(spaceSize);
        else
            System.out.println(">> This is a partially filled space");
        space.unlink();
        System.out.println(">> Switching done");
    }

    public List<String> getStats() {
        List<String> res = new ArrayList<>(20);
        res.add("usage[%]: " + getUsagePercent());
        res.add("usage[Mb]: " + getMemUsed() / 1000 / 1000);
        res.add("alloc[Mb]: " + getMemAllocated() / 1000 / 1000);
        res.add("max[Mb]: " + getMemMax() / 1000 / 1000);
        res.add("PFilled spaces: " + getPartiallyFilledSpacesCount() + " (" + getPartiallyFilledSpacesDesc() + ")");
        res.add("Filled  spaces: " + getFilledSpacesCount() + " (" + getFilledSpacesDesc() + ")");
        res.add("cur space    : " + getCurSpaceNum());
        res.add("cur space usage[%]: " + getCurSpace().getUsagePercent());
        res.add("highest Handle: "+ highestHandle);
        return res;
    }

    // This method may need to be made thread-safe
    // encoded: this is the message to store

    // The add() method could be generic, and accept only an encoded argument
    // and let the caller pack the offset within this byte array.
    // By accepting the ofs separately we're able to perform some checks on these,
    // to make sure that the references objects are not in the space that is being
    // removed.
    // We could also accept aa variable number of offsets. However, as we're only
    // using this store for nodes of the trie, we'll never need to store an object
    // having more than 2 offsets.
    public int getHandle(EncodedObjectRef ref) {
        if (ref == null)
            return -1;
        return ((IntEOR) ref).handle;
    }

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        int aLeftHandle = getHandle(leftRef);
        int aRightHandle = getHandle(rightRef);
        return (EncodedObjectRef) new IntEOR(addGetHandle(encoded, aLeftHandle, aRightHandle));
    }

    public void verifyEOR(EncodedObjectRef ref) {
        if (ref == null) return;
        int handle = ((IntEOR) ref).handle;
        checkHandle(handle);

    }

    public int addGetHandle(byte[] encoded, int leftHandle, int rightHandle) {
        Space space;

        if (!spaceAvailFor(encoded.length + debugHeaderSize)) {
            moveToNextCurSpace();
            if (remapping)
                throw new RuntimeException("Not yet prepared to switch space during remap");
        }
        //if (getUsagePercent()>60)
        //    getUsagePercent();
        space = getCurSpace();

        // Now check if the value recieved is in the correct space
        // they should be in the current space or in filled spaces.
        checkInRightSpace(leftHandle);
        checkInRightSpace(rightHandle);

        // We need to store the length because
        // the encoded form does not encode the node length in it.
        int oldMemTop = space.memTop;
        int newMemTop = storeObject(space, oldMemTop, encoded, 0, encoded.length,
                leftHandle, rightHandle);
        space.memTop = newMemTop;
        long ofs = buildPointer(curSpaceNum, oldMemTop);
        int newHandle = unusedHandles[unusedHandlesCount - 1];
        unusedHandlesCount--;
        references[newHandle] = ofs;
        if (newHandle > highestHandle)
            highestHandle = newHandle;
        referenceCount++;
        if (newHandle == debugHandle) {
            newHandle = newHandle;
            checkHandle(newHandle);
            dumpHandleContent(newHandle);
        }
        return newHandle;
    }

    public int storeObject(Space space, int oldMemTop,
                           byte[] encoded, int encodedOffset, int encodedLength,
                           int leftHandle, int rightHandle) {
        int newMemTop = oldMemTop;

        checkDebugHeader(space, oldMemTop);

        int len = encodedLength;
        if (encodedLength > 127)
            throw new RuntimeException("encoding too long");
        if (encodedOffset + encodedLength > encoded.length)
            throw new RuntimeException("bad pointers");
        //System.exit(1);
        space.mem[newMemTop] = (byte) encodedLength; // max 127 bytes
        ObjectIO.putInt(space.mem, newMemTop + F1, leftHandle);
        ObjectIO.putInt(space.mem, newMemTop + F2, rightHandle);

        newMemTop += F3;
        System.arraycopy(encoded, encodedOffset, space.mem, newMemTop, encodedLength);
        newMemTop += len;
        writeDebugFooter(space, newMemTop);
        newMemTop += debugHeaderSize;
        return newMemTop;
    }

    public byte[] retrieveData(EncodedObjectRef encodedOfs) {
        int handle = getHandle(encodedOfs);
        if (handle == -1)
            throw new RuntimeException("no data");
        return retrieveData(references[handle]);
    }

    public byte[] retrieveData(long encodedOfs) {
        Space space;

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);
        byte[] d = new byte[space.mem[internalOfs]];
        System.arraycopy(space.mem, internalOfs + F3, d, 0, d.length);
        return d;
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        int handle = getHandle(encodedRef);
        if (handle == -1)
            throw new RuntimeException("no data");
        return retrieve(references[handle]);
    }

    public void checkObject(long encodedOfs) {
        Space space;

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);

        checkDebugHeader(space, internalOfs);
        // Get the max size window
        int len = space.mem[internalOfs];
        if ((len > 127) || (len < 0))
            throw new RuntimeException("invalid length");

        checkDebugFooter(space, internalOfs + F3 + len);
        int leftRef = ObjectIO.getInt(space.mem, internalOfs + F1);
        int rightRef = ObjectIO.getInt(space.mem, internalOfs + F2);
        checkHandle(leftRef);
        checkHandle(rightRef);
    }

    public ObjectReference retrieve(long encodedOfs) {
        Space space;

        if (encodedOfs < 0)
            throw new RuntimeException("Disposed reference used");

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);
        ObjectReference r = new ObjectReference();
        checkDebugHeader(space, internalOfs);
        // Get the max size window
        r.len = space.mem[internalOfs];
        checkDebugFooter(space, internalOfs + F3 + r.len);
        r.leftRef = new IntEOR(ObjectIO.getInt(space.mem, internalOfs + F1));
        r.rightRef = new IntEOR(ObjectIO.getInt(space.mem, internalOfs + F2));
        r.message = ByteBuffer.wrap(space.mem, internalOfs + F3, r.len);
        return r;
    }


    public long getMemUsed() {
        long used = 0;

        used += getUsage(headOfFilledSpaces);
        used += getUsage(headOfPartiallyFilledSpaces);

        // While remapping the current space is unusable
        if (curSpaceNum != -1) {
            used += spaces[curSpaceNum].memTop;
        }
        return used;
    }


    public long getMemAllocated() {
        long total = 0;

        total += getSize(headOfFilledSpaces);
        total += getSize(headOfPartiallyFilledSpaces);

        if (curSpaceNum != -1) {
            total += spaces[curSpaceNum].mem.length;
        }
        return total;
    }

    public long getMemMax() {
        // +1 counts the current space
        long spaceCount = headOfFilledSpaces.count + headOfPartiallyFilledSpaces.count;
        if (curSpaceNum != -1)
            spaceCount++;
        return spaceCount * 1L * spaceSize;
    }


    public long getUsage(SpaceHead queue) {
        long used = 0;
        if (!queue.empty()) {
            int head = queue.peekFirst();
            while (head != -1) {
                used += spaces[head].memTop;
                head = spaces[head].previousSpaceNum;
            }
        }
        return used;
    }

    public long getSize(SpaceHead queue) {
        long total = 0;
        if (!queue.empty()) {
            int head = queue.peekFirst();
            while (head != -1) {
                if (spaces[head].mem != null)
                    total += spaces[head].mem.length;
                else
                    total += spaceSize; // it will be created later
                head = spaces[head].previousSpaceNum;
            }
        }
        return total;
    }

    public int getUsagePercent() {
        long used = 0;
        long total = 0;

        used += getUsage(headOfFilledSpaces);
        used += getUsage(headOfPartiallyFilledSpaces);

        total += getSize(headOfFilledSpaces);
        total += getSize(headOfPartiallyFilledSpaces);

        // While remapping the current space is unusable
        if (curSpaceNum != -1) {
            used += spaces[curSpaceNum].memTop;
            total += spaces[curSpaceNum].mem.length;
        }

        if (total == 0)
            return 0;

        return (int) ((long) used * 100 / total);
    }

    public int getFilledSpacesCount() {
        return headOfFilledSpaces.count;
    }

    public String getGarbageCollectionDescription() {
        return getRemovingSpacesDesc();
    }

    public String getRemovingSpacesDesc() {
        String s = "";
        for (int i = 0; i < maxSpaces; i++) {
            if (oldSpacesBitmap.get(i))
                s = s + i + " ";
        }
        return s;
    }

    public String getFilledSpacesDesc() {
        return headOfFilledSpaces.getDesc();
    }

    public String getPartiallyFilledSpacesDesc() {
        return headOfPartiallyFilledSpaces.getDesc();
    }

    public int getPartiallyFilledSpacesCount() {
        return headOfPartiallyFilledSpaces.count;
    }
}

