package org.example.invoice.calculation;

public final class AmountInWordsConverter {
    private static final String[] ONES = {
            "", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE",
            "TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN",
            "SEVENTEEN", "EIGHTEEN", "NINETEEN"
    };
    private static final String[] TENS = {
            "", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY"
    };

    private AmountInWordsConverter() {}

    public static String indianRupees(double value) {
        java.math.BigDecimal normalized = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
        if (normalized.signum() == 0) return "ZERO RUPEES ONLY";
        if (normalized.signum() < 0) return "MINUS " + indianRupees(normalized.abs().doubleValue());
        long rupees = normalized.longValue();
        int paise = normalized.remainder(java.math.BigDecimal.ONE).movePointRight(2).abs().intValueExact();
        StringBuilder out = new StringBuilder();
        if (rupees > 0) out.append(convert(rupees).trim().replaceAll("\\s+", " ")).append(" RUPEES");
        if (paise > 0) {
            if (!out.isEmpty()) out.append(" AND ");
            out.append(twoDigits(paise)).append(" PAISE");
        }
        return out.append(" ONLY").toString();
    }

    private static String convert(long n) {
        StringBuilder out = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;
        long hundred = n / 100;
        n %= 100;

        appendGroup(out, crore, "CRORE");
        appendGroup(out, lakh, "LAKH");
        appendGroup(out, thousand, "THOUSAND");
        if (hundred > 0) out.append(ONES[(int) hundred]).append(" HUNDRED ");
        if (n > 0) out.append(twoDigits((int) n)).append(' ');
        return out.toString();
    }

    private static void appendGroup(StringBuilder out, long value, String label) {
        if (value > 0) out.append(twoDigits((int) value)).append(' ').append(label).append(' ');
    }

    private static String twoDigits(int value) {
        if (value < 20) return ONES[value];
        return TENS[value / 10] + (value % 10 == 0 ? "" : " " + ONES[value % 10]);
    }
}
