package co.rsk.tools.processor.cindex;

public class IndexTrieV extends IndexTrie {
    protected int value;

    protected IndexTrieV(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }


}
