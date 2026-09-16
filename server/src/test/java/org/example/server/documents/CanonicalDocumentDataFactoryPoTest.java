package org.example.server.documents;

import org.example.invoice.model.TaxInvoiceDocument;
import org.example.server.operations.OperationDtos;
import org.example.server.persistence.repository.ItemRepository;
import org.example.server.persistence.repository.PartyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CanonicalDocumentDataFactoryPoTest {

    private final CanonicalDocumentDataFactory factory = new CanonicalDocumentDataFactory(
            mock(ItemRepository.class), mock(PartyRepository.class));

    @Test
    void builtInSalesPdfNeverPromotesReferenceNumberIntoPoNumber() {
        TaxInvoiceDocument missingPo = factory.salesBuiltIn(sale("", "QT-2026-0018"), Map.of(), null, null);
        assertEquals("", missingPo.orderNo(), "Quotation/reference number must remain separate when PO No. is absent");

        TaxInvoiceDocument explicitPo = factory.salesBuiltIn(sale("PO/12565655", "QT-2026-0018"), Map.of(), null, null);
        assertEquals("PO/12565655", explicitPo.orderNo(), "Explicit PO No. must remain authoritative");
    }

    private static OperationDtos.SaleDto sale(String orderNo, String referenceNo) {
        OperationDtos.PartyDto customer = new OperationDtos.PartyDto(
                null, "CUST001", "Real Customer", "customer@example.com", "9999999999",
                "24ABCDE1234F1Z5", "Ahmedabad");
        OperationDtos.LineDto line = new OperationDtos.LineDto(
                "", "MS Pipe", "", "7306", "PCS", "", 1, 100, 0, 0, 18, 118);
        return new OperationDtos.SaleDto(
                1, "JI/25-2026/0109", "2026-09-16", customer,
                100, 0, 18, 118, "", "2026-09-16", false,
                "2026-09-30", 0, "UNPAID", false, "TAX_INVOICE", "", "", "",
                "Ahmedabad", "15 Days", "", referenceNo, "", "Ahmedabad", "GST", "",
                "", "", "", orderNo, "24ABCDE1234F1Z5", "24ABCDE1234F1Z5",
                "24ABCDE1234F1Z5", true, "", "", 0, "", "DRAFT", "", 1,
                List.of(), List.of(line), 0L);
    }
}
