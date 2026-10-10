package org.example.server.barcode;

import java.math.BigDecimal;
import java.util.List;

public final class BarcodeDtos {
    private BarcodeDtos() {}

    public record LabelItemDto(
            Long itemId,
            String itemCode,
            String itemName,
            String batchNo,
            String expiryDate,
            BigDecimal mrp,
            Integer printQty
    ) {}

    public record PrintLabelsRequest(
            String labelSize, // "50x25", "38x25", "A4_24UP"
            List<LabelItemDto> items
    ) {}

    public record BarcodePreviewDto(
            String text,
            String format,
            String base64Png
    ) {}

    public record DesktopBarcodeItemDto(
            Long id,
            String itemCode,
            String itemName,
            String category,
            String barcode,
            BigDecimal sellingPrice,
            BigDecimal currentStock
    ) {}
}
