package co.rsk.tools.processor.TrieTests.baheaps;

import java.util.List;

public class ByteArrayHeapRefProxy implements AbstractByteArrayHeap {
    @Override
    public List<String> getStats() {
        return null;
    }

    @Override
    public long addObjectReturnOfs(byte[] encoded, byte[] metadata) {
        return 0;
    }

    @Override
    public byte[] retrieveDataByOfs(long encodedOfs) {
        return new byte[0];
    }

    @Override
    public byte[] retrieveMetadataByOfs(long encodedOfs) {
        return new byte[0];
    }

    @Override
    public void setMetadataByOfs(long encodedOfs, byte[] metadata) {

    }

    @Override
    public void checkObjectByOfs(long encodedOfs) {

    }

    @Override
    public void removeObjectByOfs(long encodedOfs) {

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
    public void remapByOfs(long encodedOfs) {

    }

    @Override
    public int getUsagePercent() {
        return 0;
    }
}
