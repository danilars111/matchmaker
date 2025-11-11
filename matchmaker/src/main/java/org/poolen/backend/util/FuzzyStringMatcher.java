package org.poolen.backend.util;

public class FuzzyStringMatcher {

    /**
     * Calculates the Levenshtein distance between two strings.
     * This distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change
     * one string into the other.
     *
     * @param s1 The first string.
     * @param s2 The second string.
     * @return The Levenshtein distance between the two strings.
     */
    public static int getLevenshteinDistance(String s1, String s2) {
        // Normalize strings to lower case for case-insensitive comparison
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }
    /**
     * Helper method to quickly check if two strings are "similar"
     * based on a given distance threshold.
     *
     * @param s1 The first string.
     * @param s2 The second string.
     * @param threshold The maximum allowed Levenshtein distance to be considered "similar".
     * @return true if the distance is at or below the threshold, false otherwise.
     */
    public static boolean areStringsSimilar(String s1, String s2, int threshold) {
        if (threshold == 0) {
            return s1.toLowerCase().equals(s2.toLowerCase());
        }
        return getLevenshteinDistance(s1, s2) <= threshold;
    }
}
