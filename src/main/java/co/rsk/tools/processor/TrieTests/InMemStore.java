package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.examples.storage.ObjectIO;

import java.nio.ByteBuffer;

public class InMemStore {
    final static int remapthresHold =95;
    static final int megas = 2140;
    static final int spaceSize = megas*1000*1000;
    static final long MaxPointer = 2L*spaceSize;

    class Space {

        public int memTop = 0;
        public byte[] mem = new byte[spaceSize];
    }
    static InMemStore inMemStore = new InMemStore();
    // Static 1 gigabyte. //100 megabytes

    public Space[] spaces;
    int curSpace;
    boolean remapping;

    public static InMemStore get() {
        return inMemStore;
    }

    public InMemStore() {
        curSpace =0;
        remapping =false;
        spaces = new Space[2];
        spaces[0] = new Space();
    }

    public boolean isRemapping() {
        return remapping;
    }

    public void beginRemap() {
        spaces[1-curSpace] = new Space();
        remapping = true;
    }

    public void endRemap() {
        curSpace = 1-curSpace;
        spaces[1-curSpace] = null;
        remapping = false;
    }

    public boolean verifyOfsChange(long oldo,long newo) {
        if ((oldo==-1) && (newo!=-1)) return false;
        if ((oldo!=-1) && (newo==-1)) return false;
        return true;
    }

    public long remap(long ofs,long leftOfs,long rightOfs) {
        if (getSpaceNumOfPointer(ofs)==(1-curSpace))
            return ofs; // already there
        leftOfs = getSpaceOfsFromPointer(1-curSpace,leftOfs);
        rightOfs = getSpaceOfsFromPointer(1-curSpace,rightOfs);
        ofs = getSpaceOfsFromPointer(curSpace,ofs);
        Space oldSpace = spaces[curSpace];
        Space newSpace = spaces[1-curSpace];
        int len = (int) oldSpace.mem[(int)ofs]+9;
        int retPos = newSpace.memTop;
        System.arraycopy(oldSpace.mem,(int)ofs,newSpace.mem,newSpace.memTop,len);
        // Now check that either both have indexes or none
        int oldLeftOfs =ObjectIO.getInt(oldSpace.mem,(int)ofs+1);
        int oldRightOfs=ObjectIO.getInt(oldSpace.mem,(int)ofs+5);
        if ((!verifyOfsChange(oldRightOfs,rightOfs)) ||
            (!verifyOfsChange(oldLeftOfs,leftOfs)))
            //System.exit(1);
            throw new RuntimeException("invalid ofs");
        // This are the new offsets
        ObjectIO.putInt(newSpace.mem,newSpace.memTop+1,(int)leftOfs);
        ObjectIO.putInt(newSpace.mem,newSpace.memTop+5,(int)rightOfs);
        newSpace.memTop +=len;
        return buildPointer(1-curSpace,retPos);
    }

    public Space getCurSpace() {
        return spaces[curSpace];
    }

    public int getMemUsed() {
        return getCurSpace().memTop;
    }

    public int getMemSize() {
        return getCurSpace().mem.length;
    }


    public boolean almostFull() {
        // To make it faster and test it
        return getUsagePercent()>remapthresHold;
        //return (mem.length-memTop)<1_000_000;
    }

    public int getWriteSpaceNum() {
        int writeSpace;
        if (isRemapping())
            writeSpace =1-curSpace;
        else
            writeSpace = curSpace;
        return writeSpace;
    }
    public long buildPointer(int spaceNum,long ofs) {
        if (ofs==-1)
            return ofs;
        if (ofs>=spaceSize)
            throw new RuntimeException("Invalid space offset given(1)");
        return spaceNum*spaceSize+ofs;
    }

    public long getSpaceOfsFromPointer(int spaceNum, long ofs) {
        if (ofs==-1)
            return ofs;
        long ofsSpace = ofs / spaceSize;
        if (ofsSpace!=spaceNum)
            throw new RuntimeException("Invalid space offset given(2) spaceNum="+spaceNum+" ofs="+ofs);
        return ofs % spaceSize;
    }

    public int getSpaceNumOfPointer(long ofs) {
        if (ofs<0)
            throw new RuntimeException("Invalid space offset given(3)");
        return (int) (ofs / spaceSize);
    }

    // This method may need to be made thread-safe
    public long add(byte[] encoded,long leftOfs,long rightOfs) {
        Space space;
        int writeSpaceNum = getWriteSpaceNum();
        space= spaces[writeSpaceNum];
        // Now check if the value recieved is in the correct space
        leftOfs = getSpaceOfsFromPointer(writeSpaceNum,leftOfs);
        rightOfs = getSpaceOfsFromPointer(writeSpaceNum,rightOfs);

        // We need to store the length because
        // the encoded form does not encode the node length in it.
        int oldMemTop = space.memTop;
        int len = encoded.length;
        if (encoded.length>127)
            throw new RuntimeException("too long");
            //System.exit(1);
        space.mem[space.memTop] =(byte) encoded.length; // max 127 bytes
        ObjectIO.putInt(space.mem,space.memTop+1,(int)leftOfs);
        ObjectIO.putInt(space.mem,space.memTop+5,(int)rightOfs);

        space.memTop +=9;
        System.arraycopy(encoded,0,space.mem,space.memTop,encoded.length);
        space.memTop += len;
        return buildPointer(writeSpaceNum,oldMemTop);
    }

    public InMemReference retrieve(long encodedOfs) {
        Space space;
        space = getCurSpace();
        encodedOfs = getSpaceOfsFromPointer(curSpace,encodedOfs);
        InMemReference r = new InMemReference();
        // Get the max size window
        r.len = space.mem[(int)encodedOfs];
        r.leftOfs=buildPointer(curSpace,ObjectIO.getInt(space.mem,(int) encodedOfs+1));
        r.rightOfs=buildPointer(curSpace,ObjectIO.getInt(space.mem,(int) encodedOfs+5));
        r.message = ByteBuffer.wrap(space.mem,(int) encodedOfs+9,r.len);
        return r;
    }

    public int getUsagePercent() {
        return (int) ((long) spaces[curSpace].memTop*100/spaces[curSpace].mem.length);
    }
}
