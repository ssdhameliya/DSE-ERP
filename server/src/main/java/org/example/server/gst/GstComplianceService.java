package org.example.server.gst;

import org.example.server.persistence.entity.Gstr2bReconciliationEntity;
import org.example.server.persistence.entity.PurchaseHeaderEntity;
import org.example.server.persistence.entity.SalesHeaderEntity;
import org.example.server.persistence.repository.Gstr2bReconciliationRepository;
import org.example.server.persistence.repository.PurchaseHeaderRepository;
import org.example.server.persistence.repository.SalesHeaderRepository;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class GstComplianceService {

    private final Gstr2bReconciliationRepository gstr2bRepository;
    private final PurchaseHeaderRepository purchaseRepository;
    private final SalesHeaderRepository salesRepository;
    private final JpaNativeRepository jdbc;

    public GstComplianceService(Gstr2bReconciliationRepository gstr2bRepository,
                                PurchaseHeaderRepository purchaseRepository,
                                SalesHeaderRepository salesRepository,
                                JpaNativeRepository jdbc) {
        this.gstr2bRepository = gstr2bRepository;
        this.purchaseRepository = purchaseRepository;
        this.salesRepository = salesRepository;
        this.jdbc = jdbc;
    }

    public static String normalizeInvoiceNo(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^a-zA-Z0-9]", "").toUpperCase().replaceFirst("^0+", "");
    }

    @Transactional(readOnly = true)
    public List<GstDtos.Gstr2bRecordDto> listReconciliation(String returnPeriod) {
        return gstr2bRepository.findByReturnPeriodOrderByInvoiceDateDesc(returnPeriod).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public GstDtos.ReconciliationMetricsDto getMetrics(String returnPeriod) {
        List<Gstr2bReconciliationEntity> records = gstr2bRepository.findByReturnPeriodOrderByInvoiceDateDesc(returnPeriod);
        long matched = 0;
        long valueDiff = 0;
        long missing2b = 0;
        long missingBooks = 0;
        BigDecimal eligibleItc = BigDecimal.ZERO;
        BigDecimal itcAtRisk = BigDecimal.ZERO;

        for (Gstr2bReconciliationEntity r : records) {
            BigDecimal totalTax = (r.getIgstAmount() != null ? r.getIgstAmount() : BigDecimal.ZERO)
                    .add(r.getCgstAmount() != null ? r.getCgstAmount() : BigDecimal.ZERO)
                    .add(r.getSgstAmount() != null ? r.getSgstAmount() : BigDecimal.ZERO);

            switch (r.getMatchStatus()) {
                case "MATCHED" -> {
                    matched++;
                    eligibleItc = eligibleItc.add(totalTax);
                }
                case "RATE_VALUE_DIFF", "VALUE_DIFF" -> {
                    valueDiff++;
                    eligibleItc = eligibleItc.add(totalTax);
                }
                case "MISSING_IN_2B" -> {
                    missing2b++;
                    itcAtRisk = itcAtRisk.add(totalTax);
                }
                case "MISSING_IN_BOOKS" -> missingBooks++;
                default -> {}
            }
        }

        return new GstDtos.ReconciliationMetricsDto(
                records.size(), matched, valueDiff, missing2b, missingBooks, eligibleItc, itcAtRisk
        );
    }

    public List<GstDtos.Gstr2bRecordDto> process2bImport(String returnPeriod,
                                                         List<Gstr2bReconciliationEntity> incomingList,
                                                         String username) {
        List<PurchaseHeaderEntity> erpPurchases = purchaseRepository.findTop2500ByOrderByInvoiceDateDescIdDesc();
        Map<String, PurchaseHeaderEntity> erpNormMap = new HashMap<>();
        Map<String, PurchaseHeaderEntity> erpGstinAndNormMap = new HashMap<>();
        Set<Long> matchedPurchaseIds = new HashSet<>();

        for (PurchaseHeaderEntity p : erpPurchases) {
            if (p.getInvoiceNo() != null) {
                String norm = normalizeInvoiceNo(p.getInvoiceNo());
                erpNormMap.put(norm, p);
                String gstin = p.getSupplierGstinSnapshot() != null ? p.getSupplierGstinSnapshot().trim().toUpperCase()
                        : (p.getSupplier() != null && p.getSupplier().getGstin() != null ? p.getSupplier().getGstin().trim().toUpperCase() : "");
                if (!gstin.isBlank()) {
                    erpGstinAndNormMap.put(gstin + "::" + norm, p);
                }
            }
        }

        List<Gstr2bReconciliationEntity> results = new ArrayList<>();

        for (Gstr2bReconciliationEntity item : incomingList) {
            String norm = normalizeInvoiceNo(item.getInvoiceNumber());
            item.setNormalizedInvoiceNo(norm);
            item.setReturnPeriod(returnPeriod);

            String ctin = item.getSupplierGstin() != null ? item.getSupplierGstin().trim().toUpperCase() : "";
            PurchaseHeaderEntity matchedErp = erpGstinAndNormMap.get(ctin + "::" + norm);
            if (matchedErp == null) {
                matchedErp = erpNormMap.get(norm);
            }

            if (matchedErp != null) {
                Long erpId = Long.valueOf(matchedErp.getId());
                item.setErpPurchaseId(erpId);
                matchedPurchaseIds.add(erpId);

                BigDecimal erpTotal = matchedErp.getTotalAmount() != null ?
                        BigDecimal.valueOf(matchedErp.getTotalAmount()) : BigDecimal.ZERO;
                BigDecimal taxableVal = item.getTaxableValue() != null ? item.getTaxableValue() : BigDecimal.ZERO;
                BigDecimal igstVal = item.getIgstAmount() != null ? item.getIgstAmount() : BigDecimal.ZERO;
                BigDecimal cgstVal = item.getCgstAmount() != null ? item.getCgstAmount() : BigDecimal.ZERO;
                BigDecimal sgstVal = item.getSgstAmount() != null ? item.getSgstAmount() : BigDecimal.ZERO;
                BigDecimal portalTotal = taxableVal.add(igstVal).add(cgstVal).add(sgstVal);

                BigDecimal diff = erpTotal.subtract(portalTotal).abs();
                item.setVarianceAmount(diff);

                // Check 4-Point match: GSTIN, Normalized Invoice, Date tolerance (30 days), Tax variance <= 1.00
                boolean dateOk = true;
                if (item.getInvoiceDate() != null && matchedErp.getInvoiceDate() != null) {
                    try {
                        String sDate = matchedErp.getInvoiceDate().length() >= 10 ? matchedErp.getInvoiceDate().substring(0, 10) : matchedErp.getInvoiceDate();
                        LocalDate erpD = LocalDate.parse(sDate);
                        long days = Math.abs(ChronoUnit.DAYS.between(item.getInvoiceDate(), erpD));
                        if (days > 30) dateOk = false;
                    } catch (Exception ignored) {}
                }

                if (!dateOk) {
                    item.setMatchStatus("DATE_MISMATCH");
                    item.setActionTaken("VERIFY_DATE");
                } else if (diff.compareTo(new BigDecimal("1.00")) <= 0) {
                    item.setMatchStatus("MATCHED");
                    item.setActionTaken("CLAIMED");
                } else {
                    item.setMatchStatus("RATE_VALUE_DIFF");
                    item.setActionTaken("NEEDS_ADJUSTMENT");
                }
            } else {
                item.setMatchStatus("MISSING_IN_BOOKS");
                item.setActionTaken("PENDING_BILL_ENTRY");
            }

            item.setReconciledBy(username != null ? username : "AUTO_ENGINE");
            item.setReconciledAt(LocalDateTime.now());
            results.add(gstr2bRepository.save(item));
        }

        // Also detect ERP Purchases in this period with GST that are missing in 2B
        Integer filterMonth = null;
        Integer filterYear = null;
        if (returnPeriod != null && !returnPeriod.isBlank() && !"All Periods".equalsIgnoreCase(returnPeriod)) {
            String clean = returnPeriod.replaceAll("[^0-9]", "");
            if (clean.length() == 6) {
                int firstTwo = Integer.parseInt(clean.substring(0, 2));
                if (firstTwo >= 1 && firstTwo <= 12) {
                    filterMonth = firstTwo;
                    filterYear = Integer.parseInt(clean.substring(2, 6));
                } else {
                    filterYear = Integer.parseInt(clean.substring(0, 4));
                    filterMonth = Integer.parseInt(clean.substring(4, 6));
                }
            }
        }

        if (filterMonth != null && filterYear != null) {
            for (PurchaseHeaderEntity p : erpPurchases) {
                if (matchedPurchaseIds.contains(Long.valueOf(p.getId()))) continue;
                if (p.getInvoiceDate() == null) continue;
                try {
                    String ds = p.getInvoiceDate().length() >= 10 ? p.getInvoiceDate().substring(0, 10) : p.getInvoiceDate();
                    LocalDate d = LocalDate.parse(ds);
                    if (d.getMonthValue() == filterMonth && d.getYear() == filterYear) {
                        double gst = p.getGstAmount() != null ? p.getGstAmount() : 0.0;
                        if (gst > 0.01) {
                            String gstin = p.getSupplierGstinSnapshot() != null ? p.getSupplierGstinSnapshot()
                                    : (p.getSupplier() != null ? p.getSupplier().getGstin() : "UNREGISTERED");
                            String sName = p.getSupplierNameSnapshot() != null ? p.getSupplierNameSnapshot()
                                    : (p.getSupplier() != null ? p.getSupplier().getName() : "Supplier");

                            Gstr2bReconciliationEntity missing = new Gstr2bReconciliationEntity();
                            missing.setReturnPeriod(returnPeriod);
                            missing.setSupplierGstin(gstin);
                            missing.setSupplierTradeName(sName);
                            missing.setInvoiceNumber(p.getInvoiceNo() != null ? p.getInvoiceNo() : "BILL-" + p.getId());
                            missing.setNormalizedInvoiceNo(normalizeInvoiceNo(missing.getInvoiceNumber()));
                            missing.setInvoiceDate(d);
                            missing.setInvoiceType("B2B");
                            double sub = p.getSubtotal() != null ? p.getSubtotal() : (p.getTotalAmount() - gst);
                            missing.setTaxableValue(BigDecimal.valueOf(sub).setScale(2, RoundingMode.HALF_UP));
                            boolean isInter = p.getGstType() != null && p.getGstType().toUpperCase().contains("INTER");
                            if (isInter) {
                                missing.setIgstAmount(BigDecimal.valueOf(gst).setScale(2, RoundingMode.HALF_UP));
                            } else {
                                BigDecimal half = BigDecimal.valueOf(gst / 2.0).setScale(2, RoundingMode.HALF_UP);
                                missing.setCgstAmount(half);
                                missing.setSgstAmount(half);
                            }
                            missing.setMatchStatus("MISSING_IN_2B");
                            missing.setActionTaken("NOTIFY_SUPPLIER");
                            missing.setErpPurchaseId(Long.valueOf(p.getId()));
                            missing.setReconciledBy(username != null ? username : "AUTO_ENGINE");
                            missing.setReconciledAt(LocalDateTime.now());
                            results.add(gstr2bRepository.save(missing));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return results.stream().map(this::toDto).toList();
    }

    public GstDtos.SupplierNoticeDto generateSupplierNotice(Long reconId) {
        Gstr2bReconciliationEntity r = gstr2bRepository.findById(reconId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation record not found: " + reconId));

        String companyName = "Our Company";
        try {
            companyName = jdbc.queryForObject("SELECT setting_value FROM application_setting WHERE setting_key='company.name'", String.class);
            if (companyName == null || companyName.isBlank()) companyName = "DSE ERP";
        } catch (Exception ignored) {}

        String sPhone = "";
        String sEmail = "";
        if (r.getErpPurchaseId() != null) {
            try {
                Map<String, Object> sup = jdbc.queryForMap("""
                    SELECT COALESCE(NULLIF(p.supplier_phone_snapshot, ''), pm.phone, '') as phone,
                           COALESCE(NULLIF(p.supplier_email_snapshot, ''), pm.email, '') as email
                    FROM purchase_header p
                    LEFT JOIN party_master pm ON pm.id = p.supplier_id
                    WHERE p.id = ?
                    """, r.getErpPurchaseId().intValue());
                sPhone = String.valueOf(sup.get("phone"));
                sEmail = String.valueOf(sup.get("email"));
            } catch (Exception ignored) {}
        }

        BigDecimal totalTax = (r.getIgstAmount() != null ? r.getIgstAmount() : BigDecimal.ZERO)
                .add(r.getCgstAmount() != null ? r.getCgstAmount() : BigDecimal.ZERO)
                .add(r.getSgstAmount() != null ? r.getSgstAmount() : BigDecimal.ZERO);

        String message = String.format(
                "Dear %s,\n\n" +
                "Greetings from %s.\n\n" +
                "During our statutory GST reconciliation for the return period [%s], we observed that Tax Invoice No. %s dated %s (Taxable Value: ₹%.2f, GST Amount: ₹%.2f) has NOT appeared in our GSTR-2B statement on the GST Portal.\n\n" +
                "As per Section 16(2)(aa) and Rule 36(4) of the CGST Act, Input Tax Credit (ITC) is strictly inadmissible unless the details of invoice are furnished in GSTR-1 by the supplier and communicated in GSTR-2B.\n\n" +
                "Kindly ensure this invoice is uploaded / amended in your upcoming GSTR-1 return at the earliest so we do not face tax blocking.\n\n" +
                "Invoice Summary:\n" +
                "• Supplier GSTIN: %s\n" +
                "• Invoice No: %s\n" +
                "• Invoice Date: %s\n" +
                "• Taxable Value: ₹%.2f\n" +
                "• Total GST: ₹%.2f\n\n" +
                "Thank you for your prompt attention.\n" +
                "%s Accounts & Tax Team",
                r.getSupplierTradeName() != null ? r.getSupplierTradeName() : "Supplier",
                companyName,
                r.getReturnPeriod(),
                r.getInvoiceNumber(),
                r.getInvoiceDate() != null ? r.getInvoiceDate().toString() : "N/A",
                r.getTaxableValue() != null ? r.getTaxableValue().doubleValue() : 0.0,
                totalTax.doubleValue(),
                r.getSupplierGstin(),
                r.getInvoiceNumber(),
                r.getInvoiceDate() != null ? r.getInvoiceDate().toString() : "N/A",
                r.getTaxableValue() != null ? r.getTaxableValue().doubleValue() : 0.0,
                totalTax.doubleValue(),
                companyName
        );

        String cleanPhone = sPhone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() == 10) cleanPhone = "91" + cleanPhone;
        String waUrl = "https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        String subject = "Urgent: GST Rule 36(4) Reconciliation - Invoice " + r.getInvoiceNumber() + " Missing in GSTR-2B";

        r.setSupplierNoticeSent(Boolean.TRUE);
        r.setSupplierNoticeSentAt(LocalDateTime.now());
        r.setSupplierNoticeText(message);
        gstr2bRepository.save(r);

        return new GstDtos.SupplierNoticeDto(
                r.getId(),
                r.getSupplierGstin(),
                r.getSupplierTradeName(),
                sPhone,
                sEmail,
                r.getInvoiceNumber(),
                r.getInvoiceDate() != null ? r.getInvoiceDate().toString() : "",
                r.getTaxableValue(),
                totalTax,
                r.getReturnPeriod(),
                message,
                waUrl,
                subject,
                message
        );
    }

    @Transactional(readOnly = true)
    public GstDtos.Gstr3bSummaryDto calculateGstr3b(String returnPeriod) {
        Integer filterMonth = null;
        Integer filterYear = null;

        if (returnPeriod != null && !returnPeriod.isBlank() && !"All Periods".equalsIgnoreCase(returnPeriod)) {
            String clean = returnPeriod.replaceAll("[^0-9]", "");
            if (clean.length() == 6) {
                try {
                    int m = Integer.parseInt(clean.substring(0, 2));
                    int y = Integer.parseInt(clean.substring(2, 6));
                    if (m >= 1 && m <= 12) {
                        filterMonth = m;
                        filterYear = y;
                    } else {
                        filterYear = Integer.parseInt(clean.substring(0, 4));
                        filterMonth = Integer.parseInt(clean.substring(4, 6));
                    }
                } catch (Exception ignored) {}
            } else if (returnPeriod.contains("-")) {
                String[] parts = returnPeriod.split("-");
                try {
                    if (parts[0].trim().length() == 4) {
                        filterYear = Integer.parseInt(parts[0].trim());
                        filterMonth = Integer.parseInt(parts[1].trim());
                    } else {
                        filterMonth = Integer.parseInt(parts[0].trim());
                        filterYear = Integer.parseInt(parts[1].trim());
                    }
                } catch (Exception ignored) {}
            }
        }

        List<SalesHeaderEntity> sales = salesRepository.findAllByOrderByInvoiceDateDescIdDesc();
        BigDecimal outwardTaxable = BigDecimal.ZERO;
        BigDecimal outwardIgst = BigDecimal.ZERO;
        BigDecimal outwardCgst = BigDecimal.ZERO;
        BigDecimal outwardSgst = BigDecimal.ZERO;

        for (SalesHeaderEntity s : sales) {
            if (s.getDocumentStatus() != null) {
                String st = s.getDocumentStatus().toUpperCase();
                if (st.contains("CANCEL") || st.contains("DELETE")) continue;
            }

            if (filterMonth != null && filterYear != null && s.getInvoiceDate() != null) {
                try {
                    String ds = s.getInvoiceDate().length() >= 10 ? s.getInvoiceDate().substring(0, 10) : s.getInvoiceDate();
                    LocalDate d = LocalDate.parse(ds);
                    if (d.getMonthValue() != filterMonth || d.getYear() != filterYear) {
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            if (s.getTotalAmount() != null) {
                BigDecimal gross = BigDecimal.valueOf(s.getTotalAmount());
                BigDecimal tax = s.getGstAmount() != null ? BigDecimal.valueOf(s.getGstAmount()) : BigDecimal.ZERO;
                outwardTaxable = outwardTaxable.add(gross.subtract(tax));

                if ("IGST".equalsIgnoreCase(s.getGstType()) || "INTER_STATE".equalsIgnoreCase(s.getGstType())) {
                    outwardIgst = outwardIgst.add(tax);
                } else {
                    BigDecimal split = tax.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
                    outwardCgst = outwardCgst.add(split);
                    outwardSgst = outwardSgst.add(split);
                }
            }
        }

        // Available ITC from matched 2B records
        List<Gstr2bReconciliationEntity> matched2b;
        if (returnPeriod != null && !returnPeriod.isBlank() && !"All Periods".equalsIgnoreCase(returnPeriod)) {
            matched2b = gstr2bRepository.findByReturnPeriodAndMatchStatus(returnPeriod, "MATCHED");
        } else {
            matched2b = gstr2bRepository.findAll().stream()
                    .filter(r -> "MATCHED".equalsIgnoreCase(r.getMatchStatus()))
                    .toList();
        }

        BigDecimal itcIgst = BigDecimal.ZERO;
        BigDecimal itcCgst = BigDecimal.ZERO;
        BigDecimal itcSgst = BigDecimal.ZERO;

        for (Gstr2bReconciliationEntity m : matched2b) {
            itcIgst = itcIgst.add(m.getIgstAmount() != null ? m.getIgstAmount() : BigDecimal.ZERO);
            itcCgst = itcCgst.add(m.getCgstAmount() != null ? m.getCgstAmount() : BigDecimal.ZERO);
            itcSgst = itcSgst.add(m.getSgstAmount() != null ? m.getSgstAmount() : BigDecimal.ZERO);
        }

        // Section 49 / Rule 88A GST Set-off logic
        BigDecimal remOutwardIgst = outwardIgst;
        BigDecimal remItcIgst = itcIgst;
        BigDecimal setOffIgstAgainstIgst = BigDecimal.ZERO;

        if (remItcIgst.compareTo(remOutwardIgst) >= 0) {
            setOffIgstAgainstIgst = remOutwardIgst;
            remItcIgst = remItcIgst.subtract(remOutwardIgst);
            remOutwardIgst = BigDecimal.ZERO;
        } else {
            setOffIgstAgainstIgst = remItcIgst;
            remOutwardIgst = remOutwardIgst.subtract(remItcIgst);
            remItcIgst = BigDecimal.ZERO;
        }

        BigDecimal remOutwardCgst = outwardCgst;
        BigDecimal setOffIgstAgainstCgst = BigDecimal.ZERO;
        if (remItcIgst.compareTo(BigDecimal.ZERO) > 0) {
            if (remItcIgst.compareTo(remOutwardCgst) >= 0) {
                setOffIgstAgainstCgst = remOutwardCgst;
                remItcIgst = remItcIgst.subtract(remOutwardCgst);
                remOutwardCgst = BigDecimal.ZERO;
            } else {
                setOffIgstAgainstCgst = remItcIgst;
                remOutwardCgst = remOutwardCgst.subtract(remItcIgst);
                remItcIgst = BigDecimal.ZERO;
            }
        }

        BigDecimal remOutwardSgst = outwardSgst;
        BigDecimal setOffIgstAgainstSgst = BigDecimal.ZERO;
        if (remItcIgst.compareTo(BigDecimal.ZERO) > 0) {
            if (remItcIgst.compareTo(remOutwardSgst) >= 0) {
                setOffIgstAgainstSgst = remOutwardSgst;
                remItcIgst = remItcIgst.subtract(remOutwardSgst);
                remOutwardSgst = BigDecimal.ZERO;
            } else {
                setOffIgstAgainstSgst = remItcIgst;
                remOutwardSgst = remOutwardSgst.subtract(remItcIgst);
                remItcIgst = BigDecimal.ZERO;
            }
        }

        BigDecimal setOffCgst = itcCgst.min(remOutwardCgst);
        BigDecimal netCgst = remOutwardCgst.subtract(setOffCgst).max(BigDecimal.ZERO);

        BigDecimal setOffSgst = itcSgst.min(remOutwardSgst);
        BigDecimal netSgst = remOutwardSgst.subtract(setOffSgst).max(BigDecimal.ZERO);

        BigDecimal netIgst = remOutwardIgst;
        BigDecimal totalCash = netIgst.add(netCgst).add(netSgst);

        BigDecimal totSetOffIgst = setOffIgstAgainstIgst.add(setOffIgstAgainstCgst).add(setOffIgstAgainstSgst);
        BigDecimal totSetOffCgst = setOffCgst;
        BigDecimal totSetOffSgst = setOffSgst;

        return new GstDtos.Gstr3bSummaryDto(
                returnPeriod, outwardTaxable, outwardIgst, outwardCgst, outwardSgst,
                itcIgst, itcCgst, itcSgst, netIgst, netCgst, netSgst, totalCash,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                totSetOffIgst, totSetOffCgst, totSetOffSgst
        );
    }

    private GstDtos.Gstr2bRecordDto toDto(Gstr2bReconciliationEntity e) {
        return new GstDtos.Gstr2bRecordDto(
                e.getId(), e.getReturnPeriod(), e.getSupplierGstin(), e.getSupplierTradeName(),
                e.getInvoiceNumber(), e.getNormalizedInvoiceNo(), e.getInvoiceDate(),
                e.getInvoiceType(), e.getTaxableValue(), e.getIgstAmount(), e.getCgstAmount(),
                e.getSgstAmount(), e.getCessAmount(), e.getItcEligibility(), e.getMatchStatus(),
                e.getErpPurchaseId(), e.getVarianceAmount(), e.getActionTaken(), e.getRowVersion()
        );
    }
}
