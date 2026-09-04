package org.example.util;

import java.text.DecimalFormat;
import java.util.Objects;

/** Pure text/number formatting shared by the professional PDF renderer. */
public final class ProfessionalDocumentFormatSupport {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private ProfessionalDocumentFormatSupport() { }

    public static String present(String value) { return value == null || value.isBlank() ? "Not provided" : value; }
    public static String pdfValue(String value) { return value == null || value.isBlank() ? "NA" : value; }
    public static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : Objects.toString(fallback, "");
    }
    public static String firstNonBlank(String first, String second, String fallback) {
        return firstNonBlank(first, firstNonBlank(second, fallback));
    }
    public static String money(double value) { return MONEY.format(value); }
    public static String quantity(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : new DecimalFormat("0.###").format(value);
    }
    public static String url(String value) { return value.replace(" ", "%20"); }
    public static String amountWords(double amount) {
        return org.example.invoice.calculation.AmountInWordsConverter.indianRupees(amount);
    }
}
