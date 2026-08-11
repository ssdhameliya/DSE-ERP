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
        long amount = Math.round(value);
        if (amount == 0) return "ZERO ONLY";
        if (amount < 0) return "MINUS " + indianRupees(-amount);
        return convert(amount).trim().replaceAll("\\s+", " ") + " ONLY";
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
