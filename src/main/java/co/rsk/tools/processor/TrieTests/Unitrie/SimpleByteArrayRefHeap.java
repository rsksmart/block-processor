package co.rsk.tools.processor.TrieTests.Unitrie;

import java.util.Arrays;

public class SimpleByteArrayRefHeap implements AbstractByteArrayRefHeap {

    byte[][] data;
    byte[] metadata;

    int unusedHandles[];
    int unusedHandlesCount;
    int highestHandle;
    int maxReferences;
    int referenceCount;
    int metadataSize;

    public SimpleByteArrayRefHeap(int maxReferences,int metadataSize) {
        this.maxReferences = maxReferences;
        this.metadataSize = metadataSize;
        reset();
    }

    public void reset() {


        if (maxReferences > Integer.MAX_VALUE)
            maxReferences = Integer.MAX_VALUE;

        data = new byte[maxReferences][];
        metadata = new byte[maxReferences*metadataSize];

        unusedHandles = new int[maxReferences];
        for (int i = 0; i < maxReferences; i++)
            unusedHandles[i] = maxReferences - i - 1;

        referenceCount = 0;
        unusedHandlesCount = maxReferences;

    }

    int getNewHandle(byte[] d) {
        int newHandle = unusedHandles[unusedHandlesCount - 1];
        unusedHandlesCount--;
        data[newHandle] = d;
        if (newHandle > highestHandle)
            highestHandle = newHandle;
        referenceCount++;
        return newHandle;
    }

    public void remove(int handle) {
            removeHandle(handle);
    }

    void removeHandle(int i) {
        data[i] = null;
        unusedHandles[unusedHandlesCount] = i;
        unusedHandlesCount++;
    }

    @Override
    public boolean heapIsAlmostFull() {
        return false;
    }

    @Override
    public boolean isRemapping() {
        return false;
    }

    @Override
    public void beginRemap() {

    }

    @Override
    public void endRemap() {

    }

    @Override
    public int getUsagePercent() {
        return 0;
    }

    @Override
    public byte[] retrieveData(int handle) {
        return data[handle];
    }

    @Override
    public void setMetadata(int handle, byte[] ametadata) {
        System.arraycopy(ametadata,0,metadata,handle*metadataSize,metadataSize);
    }

    @Override
    public byte[] retrieveMetadata(int handle) {
        byte[] r = new byte[metadataSize];
        System.arraycopy(metadata,handle*metadataSize,r,0,metadataSize);
        return r;
    }

    @Override
    public int add(byte[] encoded, byte[] metadata) {
        return getNewHandle(encoded);
    }

    @Override
    public void checkHandle(int handle) {
        if (data[handle]==null) {
            throw new RuntimeException("invalid handle");
        }
    }

    @Override
    public void remap(int handle) {

    }
}
