package org.example.importing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure column-name matching rules for import preflight. */
public final class ImportMappingSupport {
    private ImportMappingSupport() { }

    public static Map<String, String> autoMap(List<String> domainFields, List<String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (domainFields == null || headers == null) return result;
        for (String field : domainFields) {
            String normalizedField = normalize(field);
            for (String header : headers) {
                String normalizedHeader = normalize(header);
                if (normalizedHeader.equals(normalizedField) || areKnownAliases(normalizedField, normalizedHeader)) {
                    result.put(field, header);
                    break;
                }
            }
        }
        return result;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean areKnownAliases(String normalizedField, String normalizedHeader) {
        return switch (normalizedField) {
            case "gst" -> normalizedHeader.equals("gstpercent") || normalizedHeader.equals("gstrate");
            case "gstpercent" -> normalizedHeader.equals("gst") || normalizedHeader.equals("gstrate");
            case "partycode" -> normalizedHeader.equals("customercode") || normalizedHeader.equals("suppliercode") || normalizedHeader.equals("partyid");
            case "description" -> normalizedHeader.equals("itemname") || normalizedHeader.equals("name");
            case "invoiceNo", "invoiceno" -> normalizedHeader.equals("billno") || normalizedHeader.equals("documentno");
            case "invoiceDate", "invoicedate" -> normalizedHeader.equals("billdate") || normalizedHeader.equals("documentdate");
            case "suppliername" -> normalizedHeader.equals("tradelegalname") || normalizedHeader.equals("tradename") || normalizedHeader.equals("legalname") || normalizedHeader.equals("supplier") || normalizedHeader.equals("suppliername");
            case "suppliergstin" -> normalizedHeader.equals("gstinofsupplier") || normalizedHeader.equals("suppliergstin") || normalizedHeader.equals("gstin");
            case "supplierinvoiceno" -> normalizedHeader.equals("invoicenumber") || normalizedHeader.equals("invoiceno") || normalizedHeader.equals("billno") || normalizedHeader.equals("supplierinvoiceno");
            case "taxablevalue" -> normalizedHeader.equals("taxablevalue") || normalizedHeader.equals("taxableamount");
            case "cgst" -> normalizedHeader.equals("centraltax") || normalizedHeader.equals("cgst") || normalizedHeader.equals("cgstamount");
            case "sgst" -> normalizedHeader.equals("stateuttax") || normalizedHeader.equals("statetax") || normalizedHeader.equals("sgst") || normalizedHeader.equals("sgstamount");
            case "igst" -> normalizedHeader.equals("integratedtax") || normalizedHeader.equals("igst") || normalizedHeader.equals("igstamount");
            case "invoicevalue" -> normalizedHeader.equals("invoicevalue") || normalizedHeader.equals("invoicetotal") || normalizedHeader.equals("totalinvoicevalue");
            case "isactive" -> normalizedHeader.equals("active") || normalizedHeader.equals("status");
            case "transactiondate" -> normalizedHeader.equals("transactiondate");
            case "valuedate" -> normalizedHeader.equals("valuedate");
            case "reference" -> normalizedHeader.equals("chqrefno") || normalizedHeader.equals("referenceno");
            case "amount" -> normalizedHeader.equals("amount");
            case "direction" -> normalizedHeader.equals("drcr") || normalizedHeader.equals("debitcredit");
            case "balance" -> normalizedHeader.equals("balance");
            case "charge1type" -> normalizedHeader.equals("charge1") || normalizedHeader.equals("charge1name") || normalizedHeader.equals("additionalcharge1");
            case "charge1amount" -> normalizedHeader.equals("charge1value") || normalizedHeader.equals("additionalcharge1amount");
            case "charge1taxable" -> normalizedHeader.equals("charge1istaxable") || normalizedHeader.equals("charge1tax");
            case "charge1gstpercent" -> normalizedHeader.equals("charge1gst") || normalizedHeader.equals("charge1gstrate");
            case "charge2type" -> normalizedHeader.equals("charge2") || normalizedHeader.equals("charge2name") || normalizedHeader.equals("additionalcharge2");
            case "charge2amount" -> normalizedHeader.equals("charge2value") || normalizedHeader.equals("additionalcharge2amount");
            case "charge2taxable" -> normalizedHeader.equals("charge2istaxable") || normalizedHeader.equals("charge2tax");
            case "charge2gstpercent" -> normalizedHeader.equals("charge2gst") || normalizedHeader.equals("charge2gstrate");
            case "attachmentfile" -> normalizedHeader.equals("attachment") || normalizedHeader.equals("documentfile") || normalizedHeader.equals("attachmentpath");
            default -> false;
        };
    }
}
