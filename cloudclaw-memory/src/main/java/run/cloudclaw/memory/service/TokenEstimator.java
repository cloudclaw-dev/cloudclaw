package run.cloudclaw.memory.service;

import org.springframework.stereotype.Component;

/**
 * Simple token estimator for memory content.
 * Uses heuristic: Chinese ~1.5 tokens/char, English ~0.75 tokens/word.
 */
@Component
public class TokenEstimator {

    /**
     * Estimate token count for a text string.
     * Handles mixed Chinese/English content.
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int englishWords = 0;
        int inEnglishWord = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                chineseChars++;
                if (inEnglishWord > 0) englishWords++;
                inEnglishWord = 0;
            } else if (Character.isLetterOrDigit(c)) {
                inEnglishWord++;
            } else {
                if (inEnglishWord > 0) englishWords++;
                inEnglishWord = 0;
            }
        }
        if (inEnglishWord > 0) englishWords++;

        return (int) Math.ceil(chineseChars * 1.5 + englishWords * 0.75);
    }

    /**
     * Truncate text to fit within a token budget.
     * Prioritizes keeping complete lines.
     */
    public String truncateToTokens(String text, int maxTokens) {
        if (text == null || estimateTokens(text) <= maxTokens) return text;

        // Rough estimate: average 2 chars per token
        int charBudget = (int) (maxTokens / 1.5);
        if (charBudget >= text.length()) return text;

        // Truncate at last line break within budget
        String truncated = text.substring(0, charBudget);
        int lastBreak = truncated.lastIndexOf('\n');
        if (lastBreak > charBudget * 0.5) {
            truncated = truncated.substring(0, lastBreak);
        }

        return truncated + "\n...[truncated]";
    }

    private boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
}
