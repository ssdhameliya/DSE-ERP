package org.example.server.automation;

import org.example.server.persistence.entity.AutomationRuleEntity;
import org.example.server.persistence.entity.ItemEntity;
import org.example.server.persistence.entity.PartyEntity;
import org.example.server.persistence.entity.ReorderSuggestionEntity;
import org.example.server.persistence.entity.ThreeWayMatchLogEntity;
import org.example.server.persistence.repository.AutomationRuleRepository;
import org.example.server.persistence.repository.ItemRepository;
import org.example.server.persistence.repository.PartyRepository;
import org.example.server.persistence.repository.ReorderSuggestionRepository;
import org.example.server.persistence.repository.ThreeWayMatchLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class SmartAutomationService {

    private final AutomationRuleRepository ruleRepository;
    private final ThreeWayMatchLogRepository matchLogRepository;
    private final ReorderSuggestionRepository reorderRepository;
    private final ItemRepository itemRepository;
    private final PartyRepository partyRepository;

    public SmartAutomationService(AutomationRuleRepository ruleRepository,
                                  ThreeWayMatchLogRepository matchLogRepository,
                                  ReorderSuggestionRepository reorderRepository,
                                  ItemRepository itemRepository,
                                  PartyRepository partyRepository) {
        this.ruleRepository = ruleRepository;
        this.matchLogRepository = matchLogRepository;
        this.reorderRepository = reorderRepository;
        this.itemRepository = itemRepository;
        this.partyRepository = partyRepository;
    }

    @Transactional(readOnly = true)
    public List<AutomationDtos.RuleDto> listRules() {
        return ruleRepository.findAllByOrderByCategoryAscRuleNameAsc().stream()
                .map(this::toRuleDto)
                .toList();
    }

    public AutomationDtos.RuleDto toggleRule(String ruleCode, boolean enabled) {
        AutomationRuleEntity rule = ruleRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("Automation rule not found: " + ruleCode));
        rule.setIsEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now());
        return toRuleDto(ruleRepository.save(rule));
    }

    public AutomationDtos.RuleDto updateRuleConfig(String ruleCode, String payload) {
        AutomationRuleEntity rule = ruleRepository.findByRuleCode(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("Automation rule not found: " + ruleCode));
        rule.setConfigPayload(payload);
        rule.setUpdatedAt(LocalDateTime.now());
        return toRuleDto(ruleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<AutomationDtos.ThreeWayMatchDto> listMatchLogs() {
        return matchLogRepository.findTop100ByOrderByMatchedAtDesc().stream()
                .map(this::toMatchDto)
                .toList();
    }

    public AutomationDtos.ThreeWayMatchDto runThreeWayMatch(Long poId, Long grnId, Long billId,
                                                           String poNo, String billNo, String supplier,
                                                           BigDecimal poAmt, BigDecimal grnAmt, BigDecimal billAmt,
                                                           String user) {
        BigDecimal variance = billAmt.subtract(grnAmt).abs();
        BigDecimal variancePct = BigDecimal.ZERO;
        if (grnAmt.compareTo(BigDecimal.ZERO) > 0) {
            variancePct = variance.divide(grnAmt, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }

        String status;
        if (variancePct.compareTo(new BigDecimal("0.50")) <= 0) {
            status = "MATCHED";
        } else {
            status = "DISCREPANT";
        }

        ThreeWayMatchLogEntity log = new ThreeWayMatchLogEntity();
        log.setPoId(poId);
        log.setGrnId(grnId);
        log.setBillId(billId);
        log.setPoNumber(poNo);
        log.setBillNumber(billNo);
        log.setSupplierName(supplier);
        log.setPoAmount(poAmt);
        log.setGrnReceivedAmount(grnAmt);
        log.setBillAmount(billAmt);
        log.setVarianceAmount(variance);
        log.setVariancePercentage(variancePct);
        log.setMatchStatus(status);
        log.setMatchedBy(user != null ? user : "AUTO_MATCH_ENGINE");
        log.setMatchedAt(LocalDateTime.now());

        ThreeWayMatchLogEntity saved = matchLogRepository.save(log);
        return toMatchDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AutomationDtos.ReorderSuggestionDto> listReorderSuggestions() {
        return reorderRepository.findByStatusOrderByGeneratedAtDesc("OPEN").stream()
                .map(this::toReorderDto)
                .toList();
    }

    public List<AutomationDtos.ReorderSuggestionDto> scanAndGenerateReorderSuggestions() {
        List<ItemEntity> items = itemRepository.findAllByOrderByItemCodeAsc();
        List<ReorderSuggestionEntity> suggestions = new ArrayList<>();

        for (ItemEntity item : items) {
            BigDecimal stock = item.getOpeningStock() != null ? BigDecimal.valueOf(item.getOpeningStock()) : BigDecimal.ZERO;
            // Standard safety buffer calculation: Reorder level at minimumStock or 10 units if not configured
            BigDecimal rol = item.getMinimumStock() != null ? BigDecimal.valueOf(item.getMinimumStock()) : new BigDecimal("10.0000");

            if (stock.compareTo(rol) < 0) {
                Optional<ReorderSuggestionEntity> existing = reorderRepository.findByItemIdAndStatus(item.getId(), "OPEN");
                ReorderSuggestionEntity entity = existing.orElseGet(ReorderSuggestionEntity::new);
                entity.setItemId(item.getId());
                entity.setCurrentStock(stock);
                entity.setReorderLevel(rol);
                entity.setSuggestedQty(new BigDecimal("50.0000")); // Replenishment batch

                BigDecimal rate = item.getPurchasePrice() != null ? BigDecimal.valueOf(item.getPurchasePrice()) : (item.getSellingPrice() != null ? BigDecimal.valueOf(item.getSellingPrice()) : BigDecimal.ZERO);
                entity.setEstimatedCost(entity.getSuggestedQty().multiply(rate));
                entity.setStatus("OPEN");
                entity.setGeneratedAt(LocalDateTime.now());

                suggestions.add(reorderRepository.save(entity));
            }
        }

        return suggestions.stream().map(this::toReorderDto).toList();
    }

    private AutomationDtos.RuleDto toRuleDto(AutomationRuleEntity e) {
        return new AutomationDtos.RuleDto(
                e.getId(), e.getRuleCode(), e.getRuleName(), e.getCategory(),
                e.getIsEnabled(), e.getConfigPayload(), e.getLastExecutedAt(),
                e.getExecutionCount(), e.getRowVersion()
        );
    }

    private AutomationDtos.ThreeWayMatchDto toMatchDto(ThreeWayMatchLogEntity e) {
        return new AutomationDtos.ThreeWayMatchDto(
                e.getId(), e.getPoId(), e.getGrnId(), e.getBillId(),
                e.getPoNumber(), e.getBillNumber(), e.getSupplierName(),
                e.getPoAmount(), e.getGrnReceivedAmount(), e.getBillAmount(),
                e.getVarianceAmount(), e.getVariancePercentage(), e.getMatchStatus(),
                e.getDebitNoteId(), e.getResolutionNotes(), e.getMatchedAt()
        );
    }

    private AutomationDtos.ReorderSuggestionDto toReorderDto(ReorderSuggestionEntity e) {
        Optional<ItemEntity> item = itemRepository.findById(e.getItemId());
        String code = item.map(ItemEntity::getItemCode).orElse("");
        String name = item.map(ItemEntity::getDescription).orElse("");

        String supplierName = "";
        if (e.getPrimarySupplierId() != null) {
            supplierName = partyRepository.findById(e.getPrimarySupplierId())
                    .map(PartyEntity::getName).orElse("");
        }

        return new AutomationDtos.ReorderSuggestionDto(
                e.getId(), e.getItemId(), code, name, e.getCurrentStock(),
                e.getReorderLevel(), e.getSuggestedQty(), e.getPrimarySupplierId(),
                supplierName, e.getEstimatedCost(), e.getStatus(), e.getGeneratedAt()
        );
    }
}
