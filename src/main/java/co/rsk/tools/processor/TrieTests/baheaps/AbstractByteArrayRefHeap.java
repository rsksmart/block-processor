package co.rsk.tools.processor.TrieTests.baheaps;

public interface AbstractByteArrayRefHeap {

    public boolean heapIsAlmostFull();
    public boolean isRemapping();
    public void beginRemap() ;
    public void endRemap();
    public int getUsagePercent();

    public void remove(int handle);
    public byte[] retrieveData(int handle) ;

    public void setMetadata(int handle, byte[] metadata) ;

    public byte[] retrieveMetadata(int handle) ;

    public int add(byte[] encoded,byte[] metadata);

    public void checkHandle(int handle);
    public void remap(int handle);


}
