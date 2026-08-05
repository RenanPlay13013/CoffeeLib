package net.loyalnetwork.coffeelib.core.config;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Levenshtein-distance "did you mean X?" matching for {@code @OneOf} validation errors. */
final class Suggestions {

    private Suggestions() {
    }

    static List<String> closest(String input, List<String> candidates, int limit) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(candidate -> distance(input, candidate)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static int distance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
