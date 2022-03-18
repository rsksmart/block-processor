package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.DoubleLinkedTrieFactoryImpl;
import co.rsk.tools.processor.TrieTests.Unitrie.Trie;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieFactory;
import co.rsk.tools.processor.TrieTests.Unitrie.TrieStore;

import java.util.Optional;

public class DummyCacheTrieStore implements TrieStore {
    @Override
    public void accessNode(Trie trie) {

    }

    @Override
    public TrieFactory getTrieFactory() {
        return DoubleLinkedTrieFactoryImpl.get();
    }

    @Override
    public void save(Trie trie) {

    }

    @Override
    public void flush() {

    }

    @Override
    public Optional<Trie> retrieve(byte[] hash) {
        return Optional.empty();
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        return new byte[0];
    }

    @Override
    public void dispose() {

    }
}
