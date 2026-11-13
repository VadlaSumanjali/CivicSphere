package com.civicsphere.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based text normalization, stop-word removal, and Jaccard similarity scoring.
 */
public final class TextSimilarityUtil {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by",
            "can", "could", "did", "do", "does", "for", "from", "had", "has", "have",
            "here", "how", "i", "in", "into", "is", "it", "its", "just", "many",
            "may", "more", "most", "much", "my", "near", "no", "not", "now", "of",
            "on", "or", "our", "out", "over", "please", "same", "should", "since",
            "so", "some", "such", "than", "that", "the", "their", "them", "then",
            "there", "these", "they", "this", "those", "to", "too", "under", "up",
            "very", "was", "we", "were", "what", "when", "where", "which", "who",
            "will", "with", "would", "you", "your",
            "issue", "problem", "complaint", "report", "reported", "causing", "area"
    );

    private TextSimilarityUtil() {
    }

    /**
     * Tokenizes text into normalized meaningful keywords (lowercased, punctuation removed,
     * stop-words filtered, and basic suffix normalization applied).
     */
    public static Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> tokens = new HashSet<>();
        for (String rawToken : normalized.split(" ")) {
            if (rawToken.length() < 2 || STOP_WORDS.contains(rawToken)) {
                continue;
            }
            String stemmed = stemToken(rawToken);
            if (stemmed.length() >= 2 && !STOP_WORDS.contains(stemmed)) {
                tokens.add(stemmed);
            }
        }
        return tokens;
    }

    private static String stemToken(String token) {
        if (token.length() > 5 && token.endsWith("ing")) {
            return token.substring(0, token.length() - 3);
        }
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && token.endsWith("ed")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("es") && !token.endsWith("ses")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    /**
     * Computes Jaccard similarity (|A ∩ B| / |A ∪ B|) over extracted meaningful keywords.
     */
    public static double jaccardSimilarity(String text1, String text2) {
        Set<String> tokens1 = extractKeywords(text1);
        Set<String> tokens2 = extractKeywords(text2);
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        if (intersection.isEmpty()) {
            return 0.0;
        }

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);
        return (double) intersection.size() / (double) union.size();
    }

    /**
     * Converts Jaccard similarity into 0..20 points.
     * Minimum threshold prevents weak single-word overlaps from scoring high.
     */
    public static int scoreDescriptionSimilarity(String text1, String text2, double minThreshold) {
        double similarity = jaccardSimilarity(text1, text2);
        if (similarity < minThreshold) {
            return 0;
        }
        // Full 20 points reached at >= 0.45 Jaccard similarity
        double scaled = Math.min(1.0, similarity / 0.45);
        return (int) Math.round(scaled * 20.0);
    }
}

