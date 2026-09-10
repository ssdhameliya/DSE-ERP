package org.example.server.documents;

import org.example.server.authority.ServerResourceService;
import org.example.server.operations.OperationDtos;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Immutable approved Sales PDF snapshot per business row-version.
 * A later template/company-setting change therefore cannot silently rewrite the same issued Sale.
 */
@Service
public class IssuedSalesPdfStore {
    private static final String TYPE = "ISSUED_SALES_PDF";
    private final ServerResourceService resources;

    public IssuedSalesPdfStore(ServerResourceService resources) { this.resources = resources; }

    public Optional<byte[]> find(OperationDtos.SaleDto sale) {
        try { return Optional.of(resources.get(TYPE, key(sale)).content()); }
        catch (Exception ignored) { return Optional.empty(); }
    }

    public void save(OperationDtos.SaleDto sale, String fileName, byte[] bytes) {
        resources.put(TYPE, key(sale), fileName, "application/pdf", bytes);
    }

    static String key(OperationDtos.SaleDto sale) {
        if (sale == null || sale.id() == null) throw new IllegalArgumentException("Sales id is required for issued PDF storage.");
        return "sale-" + sale.id() + "-r" + Math.max(0, sale.rowVersion());
    }
}
