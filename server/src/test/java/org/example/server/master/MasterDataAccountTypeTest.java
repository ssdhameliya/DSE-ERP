package org.example.server.master;

import org.example.server.audit.AuditService;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.persistence.entity.LookupEntity;
import org.example.server.persistence.entity.MasterCategoryEntity;
import org.example.server.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MasterDataAccountTypeTest {

    @Test
    void accountTypeValuesResolveThroughStableCategoryCode() {
        MasterCategoryRepository categories = mock(MasterCategoryRepository.class);
        LookupRepository lookups = mock(LookupRepository.class);
        MasterCategoryEntity accountType = category("ACCOUNT_TYPE", "ACCOUNT TYPE");
        when(categories.findByCategoryCode("ACCOUNT_TYPE")).thenReturn(Optional.of(accountType));
        when(lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc("ACCOUNT TYPE"))
                .thenReturn(List.of(lookup("ACT001", "Savings"), lookup("ACT002", "Current"), lookup("ACT003", "Personal")));

        MasterDataService service = service(categories, lookups);

        assertEquals(List.of("Savings", "Current", "Personal"), service.valuesByCategoryCode("ACCOUNT_TYPE"));
    }

    @Test
    void startupSeedsDefaultsAgainstCurrentCategoryNameSoCategoryRenameIsSafe() {
        MasterCategoryRepository categories = mock(MasterCategoryRepository.class);
        LookupRepository lookups = mock(LookupRepository.class);
        MasterCategoryEntity renamed = category("ACCOUNT_TYPE", "BANK ACCOUNT TYPE");

        when(categories.findByCategoryCode("ACCOUNT_TYPE")).thenReturn(Optional.of(renamed));
        when(categories.findAll()).thenReturn(List.of());
        when(categories.save(any(MasterCategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lookups.findByLookupTypeOrderByDisplayOrderAscLookupValueAsc("BANK ACCOUNT TYPE")).thenReturn(List.of());

        MasterDataService service = service(categories, lookups);
        service.ensureFinanceMasterCategories();

        ArgumentCaptor<LookupEntity> rows = ArgumentCaptor.forClass(LookupEntity.class);
        verify(lookups, atLeast(3)).save(rows.capture());
        List<LookupEntity> accountRows = rows.getAllValues().stream()
                .filter(row -> "BANK ACCOUNT TYPE".equals(row.getLookupType()))
                .toList();
        assertEquals(List.of("Savings", "Current", "Personal"), accountRows.stream().map(LookupEntity::getLookupValue).toList());
        assertEquals(List.of("ACT001", "ACT002", "ACT003"), accountRows.stream().map(LookupEntity::getLookupCode).toList());
    }

    private static MasterDataService service(MasterCategoryRepository categories, LookupRepository lookups) {
        return new MasterDataService(
                mock(PartyRepository.class),
                mock(ItemRepository.class),
                lookups,
                categories,
                mock(BusinessOperationsService.class),
                mock(AuditService.class)
        );
    }

    private static MasterCategoryEntity category(String code, String name) {
        MasterCategoryEntity row = new MasterCategoryEntity();
        row.setCategoryCode(code);
        row.setCategoryName(name);
        row.setActive(1);
        return row;
    }

    private static LookupEntity lookup(String code, String value) {
        LookupEntity row = new LookupEntity();
        row.setLookupType("ACCOUNT TYPE");
        row.setLookupCode(code);
        row.setLookupValue(value);
        row.setActive(1);
        return row;
    }
}
