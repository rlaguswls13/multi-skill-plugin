package com.msp.invindex;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance In-Memory Inverted Index supporting multi-field indexing,
 * Boolean Queries (AND, OR, NOT), and BM25 relevance scoring.
 * Thread-safe.
 */
public final class InvertedIndex<T> {

    private final Tokenizer tokenizer;
    private final BooleanQueryParser queryParser;
    private final Map<String, Double> fieldBoosts = new HashMap<>();

    // Internal storage
    private final Map<String, IndexedDocument<T>> documents = new HashMap<>();
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>(); // term -> (docId -> freq)
    private long totalDocumentLength = 0;

    // BM25 parameters
    private final double k1;
    private final double b;

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public InvertedIndex() {
        this(Tokenizer.standard(), 1.2, 0.75);
    }

    public InvertedIndex(Tokenizer tokenizer, double k1, double b) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer cannot be null");
        this.queryParser = new BooleanQueryParser();
        this.k1 = k1;
        this.b = b;
    }

    /**
     * Sets a relevance boost multiplier for a specific field name.
     */
    public InvertedIndex<T> setFieldBoost(String field, double boost) {
        if (boost <= 0) {
            throw new IllegalArgumentException("Boost must be positive");
        }
        rwLock.writeLock().lock();
        try {
            fieldBoosts.put(field.toLowerCase(Locale.ROOT), boost);
            return this;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Indexes a document with multiple text fields.
     */
    public void addDocument(String docId, T data, Map<String, String> textFields) {
        Objects.requireNonNull(docId, "docId cannot be null");
        Objects.requireNonNull(textFields, "textFields cannot be null");

        rwLock.writeLock().lock();
        try {
            // Remove existing document if re-indexing
            if (documents.containsKey(docId)) {
                removeDocumentInternal(docId);
            }

            Map<String, Integer> termFrequencies = new HashMap<>();
            Map<String, Double> weightedTermFrequencies = new HashMap<>();
            int docLength = 0;

            for (Map.Entry<String, String> entry : textFields.entrySet()) {
                String fieldName = entry.getKey().toLowerCase(Locale.ROOT);
                String fieldValue = entry.getValue();
                if (fieldValue == null || fieldValue.isBlank()) {
                    continue;
                }

                double boost = fieldBoosts.getOrDefault(fieldName, 1.0);
                List<String> tokens = tokenizer.tokenize(fieldValue);
                docLength += tokens.size();

                for (String token : tokens) {
                    termFrequencies.merge(token, 1, Integer::sum);
                    weightedTermFrequencies.merge(token, boost, Double::sum);
                }
            }

            // Update inverted index
            for (Map.Entry<String, Integer> entry : termFrequencies.entrySet()) {
                invertedIndex.computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                        .put(docId, entry.getValue());
            }

            IndexedDocument<T> indexedDoc = new IndexedDocument<>(
                    docId, data, textFields, docLength, termFrequencies, weightedTermFrequencies
            );
            documents.put(docId, indexedDoc);
            totalDocumentLength += docLength;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes a document from the index.
     */
    public boolean removeDocument(String docId) {
        if (docId == null) return false;
        rwLock.writeLock().lock();
        try {
            return removeDocumentInternal(docId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private boolean removeDocumentInternal(String docId) {
        IndexedDocument<T> doc = documents.remove(docId);
        if (doc == null) {
            return false;
        }

        totalDocumentLength -= doc.totalLength();
        for (String term : doc.termFrequencies().keySet()) {
            Map<String, Integer> posting = invertedIndex.get(term);
            if (posting != null) {
                posting.remove(docId);
                if (posting.isEmpty()) {
                    invertedIndex.remove(term);
                }
            }
        }
        return true;
    }

    public int documentCount() {
        rwLock.readLock().lock();
        try {
            return documents.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Optional<T> getDocument(String docId) {
        rwLock.readLock().lock();
        try {
            IndexedDocument<T> doc = documents.get(docId);
            return doc != null ? Optional.ofNullable(doc.data()) : Optional.empty();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Searches the index with a boolean query and returns top-K BM25 scored results.
     */
    public List<SearchResult<T>> search(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return Collections.emptyList();
        }

        QueryNode ast = queryParser.parse(query);

        rwLock.readLock().lock();
        try {
            int numDocs = documents.size();
            if (numDocs == 0) {
                return Collections.emptyList();
            }

            // 1. Evaluate boolean filter to find candidate matching docIds
            Set<String> matchedDocIds = evaluateNode(ast);
            if (matchedDocIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 2. Extract positive search terms for scoring
            Set<String> positiveTerms = new HashSet<>();
            ast.collectPositiveTerms(positiveTerms);

            double avgdl = totalDocumentLength > 0 ? (double) totalDocumentLength / numDocs : 1.0;

            // 3. Compute BM25 relevance score for each matched document
            List<SearchResult<T>> results = new ArrayList<>(matchedDocIds.size());
            for (String docId : matchedDocIds) {
                IndexedDocument<T> doc = documents.get(docId);
                if (doc == null) continue;

                double score = 0.0;
                Map<String, Integer> matchedFrequencies = new HashMap<>();

                for (String term : positiveTerms) {
                    String normTerm = tokenizer.normalizeTerm(term);
                    Map<String, Integer> posting = invertedIndex.get(normTerm);
                    if (posting != null && posting.containsKey(docId)) {
                        int rawFreq = posting.get(docId);
                        matchedFrequencies.put(normTerm, rawFreq);

                        double termFreq = doc.weightedTermFrequencies().getOrDefault(normTerm, (double) rawFreq);
                        int docFreq = posting.size();

                        // IDF calculation with BM25 smoothing
                        double idf = Math.log(1.0 + (numDocs - docFreq + 0.5) / (docFreq + 0.5));
                        double tfNumerator = termFreq * (k1 + 1.0);
                        double tfDenominator = termFreq + k1 * (1.0 - b + b * (doc.totalLength() / avgdl));

                        score += idf * (tfNumerator / Math.max(0.0001, tfDenominator));
                    }
                }

                if (positiveTerms.isEmpty()) {
                    // Query had only NOT or purely structural terms
                    score = 1.0;
                }

                results.add(new SearchResult<>(docId, doc.data(), score, matchedFrequencies));
            }

            // 4. Sort and return topK
            Collections.sort(results);
            if (results.size() > topK) {
                return results.subList(0, topK);
            }
            return results;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private Set<String> evaluateNode(QueryNode node) {
        return switch (node) {
            case QueryNode.TermNode(String term) -> {
                String norm = tokenizer.normalizeTerm(term);
                Map<String, Integer> posting = invertedIndex.get(norm);
                if (posting == null || posting.isEmpty()) {
                    yield Collections.emptySet();
                }
                yield new HashSet<>(posting.keySet());
            }
            case QueryNode.AndNode(List<QueryNode> children) -> {
                if (children.isEmpty()) yield Collections.emptySet();
                Set<String> result = null;
                for (QueryNode child : children) {
                    Set<String> sub = evaluateNode(child);
                    if (result == null) {
                        result = new HashSet<>(sub);
                    } else {
                        result.retainAll(sub);
                    }
                    if (result.isEmpty()) break;
                }
                yield result != null ? result : Collections.emptySet();
            }
            case QueryNode.OrNode(List<QueryNode> children) -> {
                Set<String> result = new HashSet<>();
                for (QueryNode child : children) {
                    result.addAll(evaluateNode(child));
                }
                yield result;
            }
            case QueryNode.NotNode(QueryNode child) -> {
                Set<String> excluded = evaluateNode(child);
                Set<String> result = new HashSet<>(documents.keySet());
                result.removeAll(excluded);
                yield result;
            }
        };
    }
}
