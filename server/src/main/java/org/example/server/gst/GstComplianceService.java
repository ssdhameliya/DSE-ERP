package org.example.server.gst;

import org.example.server.persistence.entity.Gstr2bReconciliationEntity;
import org.example.server.persistence.entity.PurchaseHeaderEntity;
import org.example.server.persistence.entity.SalesHeaderEntity;
import org.example.server.persistence.repository.Gstr2bReconciliationRepository;
import org.example.server.persistence.repository.PurchaseHeaderRepository;
import org.example.server.persistence.repository.SalesHeaderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class GstComplianceService {

    private final Gstr2bReconciliationRepository gstr2bRepository;
    private final PurchaseHeaderRepository purchaseRepository;
    private final SalesHeaderRepository salesRepository;

    public GstComplianceService(Gstr2bReconciliationRepository gstr2bRepository,
                                PurchaseHeaderRepository purchaseRepository,
                                SalesHeaderRepository salesRepository) {
        this.gstr2bRepository = gstr2bRepository;
        this.purchaseRepository = purchaseRepository;
        this.salesRepository = salesRepository;
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
                case "RATE_VALUE_DIFF" -> {
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
        for (PurchaseHeaderEntity p : erpPurchases) {
            if (p.getInvoiceNo() != null) {
                erpNormMap.put(normalizeInvoiceNo(p.getInvoiceNo()), p);
            }
        }

        List<Gstr2bReconciliationEntity> results = new ArrayList<>();

        for (Gstr2bReconciliationEntity item : incomingList) {
            String norm = normalizeInvoiceNo(item.getInvoiceNumber());
            item.setNormalizedInvoiceNo(norm);
            item.setReturnPeriod(returnPeriod);

            PurchaseHeaderEntity matchedErp = erpNormMap.get(norm);
            if (matchedErp != null) {
                item.setErpPurchaseId(Long.valueOf(matchedErp.getId()));
                BigDecimal erpTotal = matchedErp.getTotalAmount() != null ?
                        BigDecimal.valueOf(matchedErp.getTotalAmount()) : BigDecimal.ZERO;
                BigDecimal taxableVal = item.getTaxableValue() != null ? item.getTaxableValue() : BigDecimal.ZERO;
                BigDecimal igstVal = item.getIgstAmount() != null ? item.getIgstAmount() : BigDecimal.ZERO;
                BigDecimal cgstVal = item.getCgstAmount() != null ? item.getCgstAmount() : BigDecimal.ZERO;
                BigDecimal sgstVal = item.getSgstAmount() != null ? item.getSgstAmount() : BigDecimal.ZERO;
                BigDecimal portalTotal = taxableVal.add(igstVal).add(cgstVal).add(sgstVal);

                BigDecimal diff = erpTotal.subtract(portalTotal).abs();
                item.setVarianceAmount(diff);

                if (diff.compareTo(new BigDecimal("1.00")) <= 0) {
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

        return results.stream().map(this::toDto).toList();
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
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
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
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
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
                } catch (Exception ignored) { java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored); }
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
        // 1. IGST ITC offsets IGST liability first
        BigDecimal remOutwardIgst = outwardIgst;
        BigDecimal remItcIgst = itcIgst;
        if (remItcIgst.compareTo(remOutwardIgst) >= 0) {
            remItcIgst = remItcIgst.subtract(remOutwardIgst);
            remOutwardIgst = BigDecimal.ZERO;
        } else {
            remOutwardIgst = remOutwardIgst.subtract(remItcIgst);
            remItcIgst = BigDecimal.ZERO;
        }

        // 2. Excess IGST ITC offsets CGST, then SGST liability
        BigDecimal remOutwardCgst = outwardCgst;
        if (remItcIgst.compareTo(BigDecimal.ZERO) > 0) {
            if (remItcIgst.compareTo(remOutwardCgst) >= 0) {
                remItcIgst = remItcIgst.subtract(remOutwardCgst);
                remOutwardCgst = BigDecimal.ZERO;
            } else {
                remOutwardCgst = remOutwardCgst.subtract(remItcIgst);
                remItcIgst = BigDecimal.ZERO;
            }
        }

        BigDecimal remOutwardSgst = outwardSgst;
        if (remItcIgst.compareTo(BigDecimal.ZERO) > 0) {
            if (remItcIgst.compareTo(remOutwardSgst) >= 0) {
                remItcIgst = remItcIgst.subtract(remOutwardSgst);
                remOutwardSgst = BigDecimal.ZERO;
            } else {
                remOutwardSgst = remOutwardSgst.subtract(remItcIgst);
                remItcIgst = BigDecimal.ZERO;
            }
        }

        // 3. CGST ITC offsets remaining CGST liability
        BigDecimal netCgst = remOutwardCgst.subtract(itcCgst).max(BigDecimal.ZERO);

        // 4. SGST ITC offsets remaining SGST liability
        BigDecimal netSgst = remOutwardSgst.subtract(itcSgst).max(BigDecimal.ZERO);

        BigDecimal netIgst = remOutwardIgst;
        BigDecimal totalCash = netIgst.add(netCgst).add(netSgst);

        return new GstDtos.Gstr3bSummaryDto(
                returnPeriod, outwardTaxable, outwardIgst, outwardCgst, outwardSgst,
                itcIgst, itcCgst, itcSgst, netIgst, netCgst, netSgst, totalCash
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
