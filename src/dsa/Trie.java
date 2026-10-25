package dsa;

import model.Center;
import java.util.*;

/**
 * Trie (Prefix Tree) Data Structure
 * Used for instant autocomplete search by Pincode, Center Name, and District.
 * Time Complexity:
 *   - Insertion: O(L) where L is the length of the string
 *   - Search: O(L) to find prefix + O(K) to collect matching centers
 */
public class Trie {

    public static class TrieResult {
        public List<Center> centers;
        public int nodesTraversed;
        public long executionTimeMicros;

        public TrieResult(List<Center> centers, int nodesTraversed, long executionTimeMicros) {
            this.centers = centers;
            this.nodesTraversed = nodesTraversed;
            this.executionTimeMicros = executionTimeMicros;
        }
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord = false;
        Set<Center> associatedCenters = new HashSet<>();
    }

    private final TrieNode root;

    public Trie() {
        this.root = new TrieNode();
    }

    /**
     * Inserts a keyword (pincode, hospital name word, district) linked to a Center.
     */
    public void insert(String key, Center center) {
        if (key == null || key.trim().isEmpty()) return;
        String normalized = key.trim().toLowerCase();
        TrieNode current = root;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
            current.associatedCenters.add(center);
        }
        current.isEndOfWord = true;
    }

    /**
     * Indexes a center across multiple attributes:
     * - Pincode (e.g., "110001")
     * - Full Name & individual words in the center name (e.g., "Apollo", "Clinic")
     * - District / City
     */
    public void indexCenter(Center center) {
        // Index by pincode
        insert(center.getPincode(), center);

        // Index by district
        insert(center.getDistrict(), center);

        // Index full name and each individual word
        insert(center.getName(), center);
        String[] words = center.getName().split("\\s+");
        for (String w : words) {
            if (w.length() > 2) {
                insert(w, center);
            }
        }
    }

    /**
     * Searches for all centers whose pincode, name, or city starts with the given prefix.
     * Records nodes traversed and execution time in microseconds for the UI visualizer.
     */
    public TrieResult searchPrefix(String prefix) {
        long startTime = System.nanoTime();
        int nodesTraversed = 0;

        if (prefix == null || prefix.trim().isEmpty()) {
            return new TrieResult(new ArrayList<>(), 0, 0);
        }

        String normalized = prefix.trim().toLowerCase();
        TrieNode current = root;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            nodesTraversed++;
            if (!current.children.containsKey(c)) {
                long duration = (System.nanoTime() - startTime) / 1000;
                return new TrieResult(new ArrayList<>(), nodesTraversed, Math.max(1, duration));
            }
            current = current.children.get(c);
        }

        // Distinct results
        List<Center> results = new ArrayList<>(current.associatedCenters);
        long duration = (System.nanoTime() - startTime) / 1000;
        return new TrieResult(results, nodesTraversed, Math.max(1, duration));
    }
}
