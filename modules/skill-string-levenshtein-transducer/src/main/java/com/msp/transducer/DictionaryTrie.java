package com.msp.transducer;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Character-level Prefix Trie storing dictionary words for rapid automaton intersection.
 */
public final class DictionaryTrie {

    public static final class Node {
        private final Map<Character, Node> children = new TreeMap<>();
        private boolean terminal = false;
        private String word = null;

        public Map<Character, Node> getChildren() {
            return children;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public String getWord() {
            return word;
        }

        void setTerminal(String word) {
            this.terminal = true;
            this.word = word;
        }
    }

    private final Node root = new Node();
    private int size = 0;

    public Node getRoot() {
        return root;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void insert(String word) {
        Objects.requireNonNull(word, "word cannot be null");
        Node curr = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            curr = curr.children.computeIfAbsent(ch, c -> new Node());
        }
        if (!curr.terminal) {
            curr.setTerminal(word);
            size++;
        }
    }

    public void insertAll(Collection<String> words) {
        if (words != null) {
            for (String word : words) {
                if (word != null) {
                    insert(word);
                }
            }
        }
    }

    public boolean contains(String word) {
        if (word == null) return false;
        Node curr = root;
        for (int i = 0; i < word.length(); i++) {
            curr = curr.children.get(word.charAt(i));
            if (curr == null) return false;
        }
        return curr.terminal;
    }
}
