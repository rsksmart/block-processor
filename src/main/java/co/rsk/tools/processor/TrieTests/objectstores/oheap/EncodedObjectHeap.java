package co.rsk.tools.processor.TrieTests.objectstores.oheap;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectRef;
import co.rsk.tools.processor.TrieTests.Unitrie.ENC.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.ObjectReference;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;


public class EncodedObjectHeap extends EncodedObjectStore {
    public static  int default_spaceMegabytes = 1600;
    public static  int default_maxSpaces = 4;
    public static  int default_freeSpaces =2;
    public static  int default_removeSpaces = 2;
    public static  int default_remapThreshold =95;

    public final int remapThreshold;

    public int megas;
    final int spaceSize;
    final int maxSpaces;
    public final long MaxPointer;
    final int freeSpaces;
    final int removeSpaces;

    static final int F0 = 0; // field 0 is 1  bytes in length
    static final int F1 = 1; // field 1 is 5 bytes in length
    static final int F2 = 6; // field 2 is 5 bytes in length
    static final int F3 = 11;

    public EncodedObjectHeap() {
        megas = default_spaceMegabytes;
        maxSpaces =  default_maxSpaces;
        freeSpaces = default_freeSpaces;
        removeSpaces = default_removeSpaces;
        remapThreshold  = default_remapThreshold ;
        spaceSize = megas*1000*1000;
        MaxPointer = 1L*maxSpaces*spaceSize;

        if (removeSpaces>freeSpaces)
            throw new RuntimeException("invalid arguments");

        reset();
    }

    public void save(String fileName,long rootOfs) {

        int head = headOfFilledSpaces.head;
        while (head!=-1) {
            spaces[head].saveToFile(fileName+"."+head+".space");
        }
        getCurSpace().saveToFile(fileName+"."+curSpaceNum+".space");
        HeapFileDesc desc = new HeapFileDesc();
        desc.filledSpaces = getSpaces(headOfFilledSpaces);
        desc.emptySpaces = getSpaces(headOfEmptySpaces);
        desc.currentSpace = curSpaceNum;
        desc.rootOfs = rootOfs;
        desc.saveToFile(fileName+".desc");

    }

    public long load(String fileName) {
        HeapFileDesc desc = HeapFileDesc.loadFromFile(fileName+".desc");
        setHead(headOfFilledSpaces,desc.filledSpaces,true);
        setHead(headOfEmptySpaces, desc.emptySpaces,false);
        for(int i=0;i<desc.filledSpaces.length;i++ ) {
            int num = desc.filledSpaces[i];
            spaces[num].readFromFile(fileName+"."+num+".space",false);
        }
        curSpaceNum = desc.currentSpace;
        spaces[curSpaceNum].readFromFile(fileName+"."+curSpaceNum+".space",false);
        return desc.rootOfs;
    }

    public void setHead(SpaceHead sh,int[] vec,boolean filled ) {
        sh.count = vec.length;
        sh.head = linkSpaces(vec,filled);
    }

    public int[] getSpaces(SpaceHead sh) {
        int h = sh.head;
        int[] vec = new int[sh.count];
        int i =0;
        while (h!=-1) {
            vec[i] = h;
            i++;
            h = spaces[h].previousSpaceNum;
        }
        return vec;
    }

    public int linkSpaces(int[] vec,boolean filled) {
        int prev = -1;
        for(int i=vec.length-1;i>=0;i--) {
            int sn =vec[i];
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
        int count =0;

        public String getDesc() {
            String s ="";
            int aHead = head;
            while (aHead!=-1) {
                s = s +aHead+" ";
                aHead = spaces[aHead].previousSpaceNum;
            }
            return s;
        }

        public int removeLast() {
            //  This is iterative because generally there will be only a few spaces.
            // If many spaces are used, then a double-linked list must be used
            int aHead = head;
            int lastHead = -1;
            int preLastHead  =-1;
            while (aHead!=-1) {
                preLastHead = lastHead;
                lastHead= aHead;
                aHead = spaces[aHead].previousSpaceNum;
            }
            count--;
            if (preLastHead!=-1)
                spaces[preLastHead].previousSpaceNum = -1;
            else
                head = -1;
            return lastHead;
        }
        public boolean empty() {
            return head==-1;
        }

        public void addSpace(int i) {
            int prev = head;
            head = i;
            spaces[head].previousSpaceNum = prev;
            count++;
        }

        public void clear() {
            head = -1;
            count =0;
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

    static EncodedObjectHeap objectHeap ;
    // Static 1 gigabyte. //100 megabytes

    public Space[] spaces;
    public BitSet oldSpacesBitmap = new BitSet();

    SpaceHead headOfEmptySpaces = new SpaceHead();
    SpaceHead headOfFilledSpaces= new SpaceHead();

    int curSpaceNum;
    boolean remapping;
    //int oldSpaceNum;
    long remappedSize;
    int compressionPercent;

    public static EncodedObjectHeap get() {
        if (objectHeap==null)
            objectHeap= new EncodedObjectHeap();

        return objectHeap;
    }


    public int getNewSpaceNum() {
        int s = headOfEmptySpaces.removeFirst();
        spaces[s].create(spaceSize);
        spaces[s].unlink();
        return s;
    }

    public Space newSpace() {
        return new DirectAccessSpace();
    }

    public void reset() {
        headOfEmptySpaces.clear();
        spaces = new Space[maxSpaces];

        for(int i=0;i<maxSpaces;i++) {
            spaces[i] = newSpace();
            headOfEmptySpaces.addSpace(i);
        }
        curSpaceNum = getNewSpaceNum();
        headOfFilledSpaces.clear();
        remapping =false;
        System.out.println("remapThreshold: "+remapThreshold);
        System.out.println("megas = "+megas);
        System.out.println("spaceSize = "+spaceSize);
        System.out.println("maxSpaces = "+maxSpaces);
        System.out.println("freeSpaces = "+freeSpaces);
        System.out.println("removeSpaces = "+removeSpaces);

    }



    public boolean isRemapping() {
        return remapping;
    }

    public void beginRemap() {
        oldSpacesBitmap.clear();

        remappedSize = 0;

        boolean currentMapAdded = false;
        for(int i=0;i<removeSpaces;i++) {
            // nothing filled, nothing to do
            if (headOfFilledSpaces.empty()) {
                if (currentMapAdded)
                    break;

                currentMapAdded = true;
                // There is no filled space. Then switch the current space
                // to a filled space and compress that.
                moveToNextCurSpace();
            }

            int oldSpaceNum = headOfFilledSpaces.removeLast();

            System.out.println(">> add remove space: "+oldSpaceNum);
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
        remapping = true;
    }

    public void endRemap() {
        long originalSize =0;
        for(int i=0;i<maxSpaces;i++) {
            if (oldSpacesBitmap.get(i)) {
                int oldSpaceNum = i;
                originalSize += spaces[oldSpaceNum].memTop;
                spaces[oldSpaceNum].softDestroy();
                headOfEmptySpaces.addSpace(oldSpaceNum);
            }
        }
        compressionPercent = (int) (remappedSize * 100 / originalSize);

        remapping = false;
    }

    public boolean verifyOfsChange(long oldo,long newo) {
        if ((oldo==-1) && (newo!=-1)) return false;
        if ((oldo!=-1) && (newo==-1)) return false;
        return true;
    }

    public void checkDuringRemap(EncodedObjectRef encodedRef) {
        checkDuringRemap(getOfs(encodedRef));
    }

    public void checkDuringRemap(long ofs) {
        if (ofs==-1) return;
        //if (getSpaceNumOfPointer(ofs)==oldSpaceNum)
        if (oldSpacesBitmap.get(getSpaceNumOfPointer(ofs)))
            throw new RuntimeException("bad pointer!");
    }

    final int debugHeaderSize = 2;
    final int M1= 101;
    final int M2 = 74;

    public void writeDebugFooter(Space space,int ofs) {
        if(ofs>space.spaceSize()-debugHeaderSize) return;
        space.putByte(ofs,(byte)M1);
        space.putByte(ofs+1,(byte) M2);
    }

    public void writeDebugHeader(Space space,int ofs) {
        if(ofs<debugHeaderSize) return;
        space.putByte(ofs-2, (byte)M1);
        space.putByte(ofs-1, (byte)M2);
    }

    public void checkDeugMagicWord(Space space,int ofs) {
        if ((space.getByte(ofs)!=M1) || (space.getByte(ofs+1)!=M2))
           throw new RuntimeException("no magic word: ofs="+ofs+" bytes="+space.getByte(ofs)+","+space.getByte(ofs+1));

    }

    public void checkDebugHeader(Space space,int ofs) {
        if(ofs==0) return;
        if (ofs<debugHeaderSize)
            throw new RuntimeException("invalid ofs");
        ofs-=2;
        checkDeugMagicWord(space,ofs);
    }

    public void checkDebugFooter(Space space,int ofs) {
        if(ofs>space.spaceSize()-debugHeaderSize) return;
        checkDeugMagicWord(space,ofs);
    }

    public EncodedObjectRef remap(EncodedObjectRef aofs, EncodedObjectRef aleftRef, EncodedObjectRef arightRef) {
     long rightOfs = getOfs(arightRef);
     long leftOfs = getOfs(aleftRef);
     long ofs = getOfs( aofs);
     return new LongEOR(remap(ofs,leftOfs,rightOfs));
    }


    public long remap(long ofs,long leftOfs,long rightOfs) {
        if (ofs==161722791)
        {
            System.out.println("this is the object colluding");
        }
        if (ofs==47958557) {
            System.out.println("Moving problematic object");
        }
        int objectSpaceNum = getSpaceNumOfPointer(ofs);
        int internalOfs  = getSpaceOfsFromPointer(objectSpaceNum,ofs);

        Space objectSpace = spaces[objectSpaceNum];

        checkDebugHeader(objectSpace,internalOfs);
        // Now check that either both have indexes or none
        long oldLeftOfs =objectSpace.getLong5(internalOfs+F1);
        long oldRightOfs=objectSpace.getLong5(internalOfs+F2);
        if ((!verifyOfsChange(oldRightOfs,rightOfs)) ||
                (!verifyOfsChange(oldLeftOfs,leftOfs)))
            //System.exit(1);
            throw new RuntimeException("invalid ofs");

        // While ofs is in the right map, some of the previous references
        // stored in the offset ofs may be invalid.

        boolean childReferencesChanged =((leftOfs!=oldLeftOfs) || (rightOfs!=oldRightOfs));
        //boolean objectOffsetNeedsToChange =  (objectSpaceNum==oldSpaceNum);
        boolean objectOffsetNeedsToChange =  (oldSpacesBitmap.get(objectSpaceNum));
        if ((!objectOffsetNeedsToChange) && (!childReferencesChanged)) {
            return ofs; // already there or in a space that is not being compressed
        }

        if (objectOffsetNeedsToChange) {
            // objectSpaceNum == oldSpaceNum
            int oldSpaceNum  = objectSpaceNum;
            Space oldSpace = spaces[oldSpaceNum];

            int len = (int) oldSpace.getByte(internalOfs);

            Space newSpace = spaces[curSpaceNum];
            int consumedSpace = debugHeaderSize+len + F3;
            if (!newSpace.spaceAvailFor(consumedSpace)) {
                // we must enable a new space!
                moveToNextCurSpace();
                newSpace = getCurSpace();
            }
            if (!newSpace.spaceAvailFor(consumedSpace)) {
                throw new RuntimeException("never should this happen");
            }

            // The debug header is shared with the footer, so we only need to write
            // the debug footer
            int retPos = newSpace.memTop;
            checkDebugHeader(newSpace,retPos);

            // Here we copy the encoded message plus the length (1 byte)
            oldSpace.copyBytes(internalOfs, newSpace, retPos, len + F1);
            checkDebugHeader(newSpace,retPos);
            // This are the new offsets
            newSpace.putLong5( newSpace.memTop + F1, leftOfs);
            newSpace.putLong5( newSpace.memTop + F2, rightOfs);
            checkbug();

            writeDebugFooter(newSpace,retPos+len+F3);
            newSpace.memTop += len + F3+debugHeaderSize;
            remappedSize +=(len + F3);
            return buildPointer(curSpaceNum, retPos);
        } else {
            // only child references changed
            objectSpace.putLong5( internalOfs+F1, leftOfs);
            objectSpace.putLong5(internalOfs + F2, rightOfs);
            checkDebugHeader(objectSpace,internalOfs);
            int len = objectSpace.getByte(internalOfs);
            checkDebugFooter(objectSpace,internalOfs+len);
            checkbug();
            return ofs;
        }
    }

    public boolean bug = false;
    public void checkbug() {
        if (bug) return;
        long ofs = 161722718; // 47958557;
        int objectSpaceNum = getSpaceNumOfPointer(ofs);
        int internalOfs  = getSpaceOfsFromPointer(objectSpaceNum,ofs);
        if (spaces[objectSpaceNum].empty()) return;
        byte v =spaces[objectSpaceNum].getByte(internalOfs+11+70);
        if (v==(byte)-1) {
            System.out.println("problem!");
            bug = true;
        }
    }

    public Space getCurSpace() {
        return spaces[curSpaceNum];
    }

    /*public Space getOldSpace() {
        return spaces[oldSpaceNum];
    }*/



    public int getMemSize() {
        return getCurSpace().spaceSize();

    }


    public boolean currentSpaceIsAlmostFull() {
        // To make it faster and test it
        return getCurSpace().getUsagePercent()>remapThreshold;
        //return (mem.length-memTop)<1_000_000;
    }

    public boolean heapIsAlmostFull() {
        // There must be one empty space in the empty queue, because this may be needed during
        // remap. For example, the current space is half full, and there is a filled space that
        // cannot be compressed, the filled space will be poured into the current space, and will fill it
        // forcing the switch to a new empty space.
        //
        return (headOfEmptySpaces.count<=freeSpaces) &&
                (getCurSpace().getUsagePercent()>remapThreshold);
    }

    public int getWriteSpaceNum() {
        int writeSpace;
        if (isRemapping())
            writeSpace =1- curSpaceNum;
        else
            writeSpace = curSpaceNum;
        return writeSpace;
    }
    public long buildPointer(int spaceNum,long ofs) {
        if (ofs==-1)
            return ofs;
        if (ofs>=spaceSize)
            throw new RuntimeException("Invalid space offset given(1)");
        return (1L*spaceNum*spaceSize)+ofs;
    }

    public int getSpaceOfsFromPointer(int spaceNum, long ofs) {
        if (ofs==-1)
            return -1;
        long ofsSpace = ofs / spaceSize;
        if (ofsSpace!=spaceNum)
            throw new RuntimeException("Invalid space offset given(2) spaceNum="+spaceNum+" ofs="+ofs);
        return (int) (ofs % spaceSize);
    }

    public int getSpaceNumOfPointer(long ofs) {
        if (ofs<0)
            throw new RuntimeException("Invalid space offset given(3)");
        return (int) (ofs / spaceSize);
    }

    public boolean spaceAvailFor(int msgLength) {
        msgLength +=F3;
        return (getCurSpace().spaceAvailFor(msgLength));

    }

    public void checkInRightSpace(long ofs) {
        if (ofs==-1)
            return;
        int spaceNum = getSpaceNumOfPointer(ofs);
        if ((!spaces[spaceNum].filled) && (spaceNum!=curSpaceNum)) {
            throw new RuntimeException("Newer space for a subnode: invalid ptrSpaceNum="+spaceNum+" curSpaceNum="+curSpaceNum); //+" oldSpaceNum="+oldSpaceNum);
        }
    }

    public void moveToNextCurSpace() {
        int oldCurSpaceNum = curSpaceNum;
        getCurSpace().filled = true; // mark as filled
        headOfFilledSpaces.addSpace(curSpaceNum);
        curSpaceNum = headOfEmptySpaces.removeFirst();
        Space space= getCurSpace();
        if (!space.empty())
            throw new RuntimeException("Next space should be empty.");
        System.out.println(">> Switching curspace from "+oldCurSpaceNum+" to "+curSpaceNum);
        space.create(spaceSize);
        space.unlink();
        System.out.println(">> Switching done");

    }
    // This method may need to be made thread-safe
    // encoded: this is the message to store
    // leftOfs and rightOfs are two pointers to two other objects in the heap
    // The add() method could be generic, and accept only an ecoded argument
    // and let the caller pack the offset within this byte array.
    // By accepting the ofs separately we're able to perform some checks on these,
    // to make sure that the references objects are not in the space that is being
    // removed.
    // We could also accept aa variable number of offsets. However, as we're only
    // using this store for nodes of the trie, we'll never need to store an object
    // having more than 2 offsets.
    public long getOfs(EncodedObjectRef ref) {
        if (ref==null)
            return -1;
      return ((LongEOR) ref).ofs;
    }

    public EncodedObjectRef add(byte[] encoded, EncodedObjectRef leftRef, EncodedObjectRef rightRef) {
        long aLeftOfs = getOfs(leftRef);
        long aRightOfs = getOfs(rightRef);
        return (EncodedObjectRef) new LongEOR(add(encoded,aLeftOfs,aRightOfs));
    }

    public List<String> getStats() {
        List<String> res = new ArrayList<>(20);
        res.add("usage[%]: " + getUsagePercent());
        res.add("usage[Mb]: " + getMemUsed() / 1000 / 1000);
        res.add("alloc[Mb]: " + getMemAllocated() / 1000 / 1000);
        res.add("max[Mb]: " + getMemMax() / 1000 / 1000);
        res.add("Empty   spaces: " + getEmptySpacesCount() + " (" + getEmptySpacesDesc() + ")");
        res.add("Filled  spaces: " + getFilledSpacesCount() + " (" + getFilledSpacesDesc() + ")");
        res.add("cur space    : " + getCurSpaceNum());
        res.add("cur space usage[%]: " + getCurSpace().getUsagePercent());
        return res;
    }

    public void verifyEOR(EncodedObjectRef ref) {
        if (ref==null) return;
        long ofs =((LongEOR) ref).ofs;
        if ((ofs<-1) || ofs>= MaxPointer) {
            throw new RuntimeException("Invalid ofs arg (2)ofs="+ofs);
        }

    }
    public long add(byte[] encoded,long leftOfs,long rightOfs) {
        Space space;
        // The invariant is that leftOfs and rightOfs can never be on a higher space
        // meanining that are newer than where this encoded object will be stored
        if (!spaceAvailFor(encoded.length+debugHeaderSize)) {
            moveToNextCurSpace();
            if (remapping)
                throw new RuntimeException("Not yet prepared to switch space during remap");
        }
        space= getCurSpace();

        // Now check if the value recieved is in the correct space
        // they should be in the current space or in filled spaces.
        checkInRightSpace(leftOfs);
        checkInRightSpace(rightOfs);

        // We need to store the length because
        // the encoded form does not encode the node length in it.
        int oldMemTop = space.memTop;
        checkDebugHeader(space,space.memTop);

        int len = encoded.length;
        if (encoded.length>127)
            throw new RuntimeException("too long");
            //System.exit(1);
        space.putByte(space.memTop,(byte) encoded.length); // max 127 bytes
        space.putLong5(space.memTop+F1,leftOfs);
        space.putLong5(space.memTop+F2,rightOfs);

        /*long rleftOfs=ObjectIO.getLong5(space.mem, space.memTop+F1);
        long rrightOfs=ObjectIO.getLong5(space.mem, space.memTop+F2);
        if ((rleftOfs!=leftOfs) || (rrightOfs!=rightOfs))
            throw new RuntimeException("bad encoding");
         */
        space.memTop +=F3;
        space.setBytes(space.memTop,encoded,0,encoded.length);
        space.memTop += len;
        writeDebugFooter(space,space.memTop);
        space.memTop +=debugHeaderSize;
        checkbug();
        return buildPointer(curSpaceNum,oldMemTop);
    }

    public byte[] retrieveData(EncodedObjectRef encodedOfs) {
        return retrieveData(getOfs(encodedOfs));
    }

    public byte[] retrieveData(long encodedOfs) {
        Space space;

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum,encodedOfs);
        byte[] d = new byte[space.getByte(internalOfs)];
        space.getBytes(internalOfs+F3,d,0,d.length);
        return d;
    }

    public ObjectReference retrieve(EncodedObjectRef encodedRef) {
        return retrieve(getOfs(encodedRef));
    }

    public ObjectReference retrieve(long encodedOfs) {
        Space space;

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum,encodedOfs);
        ObjectReference r = new ObjectReference();
        checkDebugHeader(space,internalOfs);
        // Get the max size window
        r.len = space.getByte(internalOfs);
        checkDebugFooter(space,internalOfs+F3+r.len);
        r.leftRef = new LongEOR(space.getLong5( internalOfs+F1));
        r.rightRef =new LongEOR(space.getLong5(internalOfs+F2));
        r.message = space.getByteBuffer(internalOfs+F3,r.len);
        return r;
    }

/*    public void dumpMem(int space,int ofs) {
        org.bouncycastle.util.encoders.Hex.toHexString(spaces[space].mem,ofs,
                (int) spaces[space].mem[ofs]);
    }

 */
    public long getMemUsed() {
        long used = 0;

        if (!headOfFilledSpaces.empty()) {
            int head = headOfFilledSpaces.peekFirst();
            while (head != -1) {
                used += spaces[head].memTop;
                head = spaces[head].previousSpaceNum;
            }
        }
        used+=spaces[curSpaceNum].memTop;
        return used;
    }


    public long getMemAllocated() {
        long total = 0;

        if (!headOfFilledSpaces.empty()) {
            int head = headOfFilledSpaces.peekFirst();
            while (head != -1) {
                total += spaces[head].spaceSize();
                head = spaces[head].previousSpaceNum;

            }
        }
        total +=spaces[curSpaceNum].spaceSize();
        return total;
    }

    public long getMemMax() {
        // +1 counts the current space
         return (headOfFilledSpaces.count+headOfEmptySpaces.count+1)*1L*spaceSize;
    }


    public int getUsagePercent() {
        long used = 0;
        long total = 0;

        if (!headOfFilledSpaces.empty()) {
            int head = headOfFilledSpaces.peekFirst();
            while (head != -1) {
                used += spaces[head].memTop;
                total += spaces[head].spaceSize();
                head = spaces[head].previousSpaceNum;
            }
        }
        used+=spaces[curSpaceNum].memTop;
        total +=spaces[curSpaceNum].spaceSize();

        total +=getEmptySpacesCount()*1L*spaceSize; // This have not yet been created but may be created later.

        return (int) ((long) used*100/total);
    }

    public int getFilledSpacesCount() {
        return headOfFilledSpaces.count;
    }

    public String getGarbageCollectionDescription() {
        return getRemovingSpacesDesc();
    }
    public String getRemovingSpacesDesc() {
        String s="";
        for(int i=0;i<maxSpaces;i++) {
            if (oldSpacesBitmap.get(i))
                s = s + i+" ";
        }
        return s;
    }

    public String getFilledSpacesDesc() {
        return headOfFilledSpaces.getDesc();
    }

    public String getEmptySpacesDesc() {
        return headOfEmptySpaces.getDesc();
    }
    public int getEmptySpacesCount() {
        return headOfEmptySpaces.count;
    }
}
