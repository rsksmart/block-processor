package co.rsk.tools.processor.TrieTests.Unitrie;

import co.rsk.tools.processor.TrieTests.Unitrie.store.AbstractByteArrayHashMap;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ByteArrayHeap extends ByteArrayHeapBase implements AbstractByteArrayHeap {

    public boolean fileExists() {
        Path path = Paths.get(baseFileName + ".desc");
        File f = path.toFile();
        return (f.exists() && !f.isDirectory());
    }

    @Override
    public void removeObject(long encodedOfs) {

    }

    @Override
    public void remap(long encodedOfs) {

    }

    public long load() throws IOException {
        long r = super.load();
        return r;
    }

    public void save(long rootOfs) throws IOException {
        super.save(rootOfs);
    }

}
