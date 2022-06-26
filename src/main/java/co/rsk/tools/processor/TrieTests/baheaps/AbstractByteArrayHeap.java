package co.rsk.tools.processor.TrieTests.baheaps;

import java.util.List;

public interface AbstractByteArrayHeap {

    public List<String> getStats();;

    public long addObjectReturnOfs(byte[] encoded, byte[] metadata);

    public byte[] retrieveDataByOfs(long encodedOfs);

    public byte[] retrieveMetadataByOfs(long encodedOfs);

    public void setMetadataByOfs(long encodedOfs,byte [] metadata);

    public void checkObjectByOfs(long encodedOfs);

    public void removeObjectByOfs(long encodedOfs);

    public boolean isRemapping();
    public void beginRemap() ;
    public void endRemap();
    public void remapByOfs(long encodedOfs);
    public int getUsagePercent();

}
