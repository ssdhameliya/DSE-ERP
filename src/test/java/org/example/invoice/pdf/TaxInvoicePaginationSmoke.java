package org.example.invoice.pdf;

import org.example.invoice.model.CompanyProfile;
import org.example.invoice.model.InvoiceParty;
import org.example.invoice.model.InvoiceTotals;
import org.example.invoice.model.TaxInvoiceDocument;
import org.example.invoice.model.TaxInvoiceItem;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a deterministic 25-line invoice to verify page continuation and
 * final-page-only totals, bank details, terms, signature, and footer sections.
 */
public final class TaxInvoicePaginationSmoke {
    private TaxInvoicePaginationSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0
                ? Path.of("target", "pdf-verification", "jasvi-25-items.pdf")
                : Path.of(args[0]);

        List<TaxInvoiceItem> items = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            items.add(new TaxInvoiceItem(
                    index,
                    "7306" + String.format("%04d", index),
                    "JASVI verification product " + index,
                    "Verification remark line " + index,
                    index % 4 + 1,
                    "NOS",
                    1250.00 + index * 25.00,
                    index % 3,
                    18.0));
        }

        double basic = items.stream().mapToDouble(TaxInvoiceItem::getGrossAmount).sum();
        double discount = items.stream().mapToDouble(TaxInvoiceItem::getDiscountAmount).sum();
        double taxable = basic - discount;
        double cgst = taxable * 0.09;
        double sgst = taxable * 0.09;
        double grandTotal = taxable + cgst + sgst;

        CompanyProfile company = new CompanyProfile(
                "JASVI INDUSTRIES",
                "Ahmedabad, Gujarat, India",
                "24ABCDE1234F1Z5",
                "jasviindustries1989@gmail.com",
                "marketing@jasviindustries.in",
                "+91 72280 99500",
                "STATE BANK OF INDIA",
                "AHMEDABAD",
                "123456789012",
                "SBIN0001001",
                "CURRENT",
                "AGAINST DELIVERY",
                "Payment is due within the agreed credit period.\nGoods once sold will not be taken back.",
                "");

        InvoiceParty billing = new InvoiceParty(
                "VERIFICATION CUSTOMER",
                "Industrial Estate, Ahmedabad, Gujarat - 380001",
                "24AAAAA1111A1Z5",
                "Accounts Department",
                "+91 98765 43210");

        InvoiceParty delivery = new InvoiceParty(
                "VERIFICATION CUSTOMER - WAREHOUSE",
                "Warehouse Road, Ahmedabad, Gujarat - 380015",
                "24AAAAA1111A1Z5",
                "Store Department",
                "+91 98765 43211");

        TaxInvoiceDocument invoice = new TaxInvoiceDocument(
                company,
                "JASVI-PAGE-TEST-25",
                LocalDate.of(2026, 8, 8),
                "PO-PAGE-TEST",
                LocalDate.of(2026, 8, 7),
                billing,
                delivery,
                "JASVI TRANSPORT",
                "GJ01AB1234",
                "Accounts Department",
                "+91 98765 43210",
                items,
                "CGST/SGST",
                0.0,
                new InvoiceTotals(basic, discount, 0.0, taxable, cgst, sgst, 0.0, 0.0, grandTotal),
                "Verification amount only");

        TaxInvoicePdfGenerator.generate(invoice, output);
        System.out.println(output.toAbsolutePath());
    }
}
