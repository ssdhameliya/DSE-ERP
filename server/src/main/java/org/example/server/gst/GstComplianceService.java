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
        List<PurchaseHeaderEntity> erpPurchases = purchaseRepository.findAllByOrderByInvoiceDateDescIdDesc();
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
                BigDecimal portalTotal = item.getTaxableValue()
                        .add(item.getIgstAmount())
                        .add(item.getCgstAmount())
                        .add(item.getSgstAmount());

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
        List<SalesHeaderEntity> sales = salesRepository.findAllByOrderByInvoiceDateDescIdDesc();
        BigDecimal outwardTaxable = BigDecimal.ZERO;
        BigDecimal outwardIgst = BigDecimal.ZERO;
        BigDecimal outwardCgst = BigDecimal.ZERO;
        BigDecimal outwardSgst = BigDecimal.ZERO;

        for (SalesHeaderEntity s : sales) {
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
        List<Gstr2bReconciliationEntity> matched2b = gstr2bRepository.findByReturnPeriodAndMatchStatus(returnPeriod, "MATCHED");
        BigDecimal itcIgst = BigDecimal.ZERO;
        BigDecimal itcCgst = BigDecimal.ZERO;
        BigDecimal itcSgst = BigDecimal.ZERO;

        for (Gstr2bReconciliationEntity m : matched2b) {
            itcIgst = itcIgst.add(m.getIgstAmount() != null ? m.getIgstAmount() : BigDecimal.ZERO);
            itcCgst = itcCgst.add(m.getCgstAmount() != null ? m.getCgstAmount() : BigDecimal.ZERO);
            itcSgst = itcSgst.add(m.getSgstAmount() != null ? m.getSgstAmount() : BigDecimal.ZERO);
        }

        // Section 49 / 49A / 49B Set-off logic
        BigDecimal netIgst = outwardIgst.subtract(itcIgst).max(BigDecimal.ZERO);
        BigDecimal netCgst = outwardCgst.subtract(itcCgst).max(BigDecimal.ZERO);
        BigDecimal netSgst = outwardSgst.subtract(itcSgst).max(BigDecimal.ZERO);
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
