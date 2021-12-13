package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.Index.TrieKeySlice;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

public class PreOrderIterator implements Iterator<IterationElement> {

    private final Deque<IterationElement> visiting;

    public PreOrderIterator(Trie root) {
        Objects.requireNonNull(root);
        TrieKeySlice traversedPath = root.getSharedPath();
        this.visiting = new LinkedList<>();
        this.visiting.push(new IterationElement(traversedPath, root));
    }

    @Override
    @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
    public IterationElement next() {
        IterationElement visitingElement = visiting.pop();
        Trie node = visitingElement.getNode();
        TrieKeySlice nodeKey = visitingElement.getNodeKey();
        // need to visit the left subtree first, then the right since a stack is a LIFO, push the right subtree first,
        // then the left
        Trie rightNode = node.retrieveNode((byte) 0x01);
        if (rightNode != null) {
            TrieKeySlice rightNodeKey = nodeKey.rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
            visiting.push(new IterationElement(rightNodeKey, rightNode));
        }
        Trie leftNode = node.retrieveNode((byte) 0x00);
        if (leftNode != null) {
            TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
            visiting.push(new IterationElement(leftNodeKey, leftNode));
        }
        // may not have pushed anything.  If so, we are at the end
        return visitingElement;
    }

    @Override
    public boolean hasNext() {
        return !visiting.isEmpty(); // no next node left
    }
}