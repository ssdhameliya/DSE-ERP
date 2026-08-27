package org.example.server.master;

import org.example.server.audit.AuditService;
import org.example.server.operations.BusinessOperationsService;
import org.example.server.persistence.entity.LookupEntity;
import org.example.server.persistence.entity.MasterCategoryEntity;
import org.example.server.persistence.repository.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MasterDataQuotationSourceTest {

    @Test
    void quotationSourceUsesSameGenericCategoryCodeLookupAsTransporter() {
        MasterCategoryRepository categories = mock(MasterCategoryRepository.class);
        LookupRepository lookups = mock(LookupRepository.class);
        MasterCategoryEntity source = category("QUOTATION_SOURCE", "QUOTATION SOURCE");
        when(categories.findByCategoryCode("QUOTATION_SOURCE")).thenReturn(Optional.of(source));
        when(lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc("QUOTATION SOURCE"))
                .thenReturn(List.of(lookup("QUOTATION SOURCE", "QS001", "Website"), lookup("QUOTATION SOURCE", "QS002", "Referral")));

        MasterDataService service = service(categories, lookups);

        assertEquals(List.of("Website", "Referral"), service.valuesByCategoryCode("QUOTATION_SOURCE"));
        verify(lookups).findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc("QUOTATION SOURCE");
        verify(lookups, never()).findAll();
        verify(lookups, never()).save(any(LookupEntity.class));
    }

    @Test
    void quotationSourceDoesNotFallBackToDifferentSourceCategoriesAtRuntime() {
        MasterCategoryRepository categories = mock(MasterCategoryRepository.class);
        LookupRepository lookups = mock(LookupRepository.class);
        MasterCategoryEntity source = category("QUOTATION_SOURCE", "QUOTATION SOURCE");
        when(categories.findByCategoryCode("QUOTATION_SOURCE")).thenReturn(Optional.of(source));
        when(lookups.findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc("QUOTATION SOURCE"))
                .thenReturn(List.of());

        MasterDataService service = service(categories, lookups);

        assertEquals(List.of(), service.valuesByCategoryCode("QUOTATION_SOURCE"));
        verify(lookups).findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc("QUOTATION SOURCE");
        verifyNoMoreInteractions(lookups);
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

    private static LookupEntity lookup(String type, String code, String value) {
        LookupEntity row = new LookupEntity();
        row.setLookupType(type);
        row.setLookupCode(code);
        row.setLookupValue(value);
        row.setActive(1);
        return row;
    }
}
