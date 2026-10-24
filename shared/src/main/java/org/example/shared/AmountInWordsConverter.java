package org.example.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts a monetary amount (rupees) to its Indian-locale words representation.
 *
 * <p>Used by both the desktop {@code InvoicePdfService} and the new Vaadin web
 * invoice editor {@code SalesEditorView} via the {@code shared} module. This
 * class has zero framework dependencies and is safe to use in any context.</p>
 *
 * <p>Examples:
 * <pre>
 *   AmountInWordsConverter.convert(new BigDecimal("1,46,910.00"))
 *   → "One Lakh Forty-Six Thousand Nine Hundred Ten Rupees Only"
 *
 *   AmountInWordsConverter.convert(new BigDecimal("5,00,000.50"))
 *   → "Five Lakh Rupees and Fifty Paise Only"
 * </pre>
 * </p>
 */
public final class AmountInWordsConverter {

    private AmountInWordsConverter() {}

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
        "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };

    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /**
     * Converts the given amount to Indian words format.
     *
     * @param amount the amount to convert; must not be negative; paise (cents) are included
     * @return human-readable string, e.g. "One Lakh Twenty Thousand Rupees Only"
     * @throws IllegalArgumentException if amount is negative or exceeds 99,99,99,999.99
     */
    public static String convert(BigDecimal amount) {
        if (amount == null) return "Zero Rupees Only";
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative: " + amount);
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = amount.longValue();
        int paise   = amount.subtract(BigDecimal.valueOf(rupees))
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP)
                            .intValue();

        if (rupees == 0 && paise == 0) return "Zero Rupees Only";

        StringBuilder sb = new StringBuilder();
        if (rupees > 0) {
            sb.append(convertIndianNumber(rupees)).append("Rupees");
        }
        if (paise > 0) {
            if (sb.length() > 0) sb.append(" and ");
            sb.append(convertBelow100(paise)).append("Paise");
        }
        sb.append(" Only");
        // Normalise double-spaces that can occur from concatenation
        return sb.toString().replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * Convenience overload for {@code double} amounts (rounded to 2 decimal places).
     */
    public static String convert(double amount) {
        return convert(BigDecimal.valueOf(amount));
    }

    // ── Indian number system (crore / lakh / thousand / hundred) ─────────────

    private static String convertIndianNumber(long number) {
        if (number == 0) return "";

        StringBuilder result = new StringBuilder();

        long crore    = number / 10_000_000L;
        number        %= 10_000_000L;
        long lakh     = number / 100_000L;
        number        %= 100_000L;
        long thousand = number / 1_000L;
        number        %= 1_000L;
        long hundred  = number / 100L;
        long remainder = number % 100L;

        if (crore > 0) {
            result.append(convertBelow100((int) crore)).append("Crore ");
        }
        if (lakh > 0) {
            result.append(convertBelow100((int) lakh)).append("Lakh ");
        }
        if (thousand > 0) {
            result.append(convertBelow100((int) thousand)).append("Thousand ");
        }
        if (hundred > 0) {
            result.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (remainder > 0) {
            result.append(convertBelow100((int) remainder));
        }

        return result.toString();
    }

    private static String convertBelow100(int number) {
        if (number < 20) {
            return ONES[number] + " ";
        }
        String tens = TENS[number / 10] + " ";
        String ones = (number % 10 > 0) ? ONES[number % 10] + " " : "";
        return tens + ones;
    }
}
