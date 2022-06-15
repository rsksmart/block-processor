package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieTests.oheap.Space;

import java.util.List;

public interface AbstractByteArrayHeap {

    public List<String> getStats();;

    public long addObject(byte[] encoded,byte[] metadata);

    public byte[] retrieveDataByOfs(long encodedOfs);

    public byte[] retrieveMetadataByOfs(long encodedOfs);

    public void setMetadataByOfs(long encodedOfs,byte [] metadata);

    public void checkObject(long encodedOfs);

    public void removeObject(long encodedOfs);

    public boolean isRemapping();
    public void beginRemap() ;
    public void endRemap();
    public void remap(long encodedOfs);
    public int getUsagePercent();

}
