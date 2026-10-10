package org.example.server.barcode;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/barcode")
public class BarcodeController {

    private final BarcodeService service;

    public BarcodeController(BarcodeService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    public BarcodeDtos.BarcodePreviewDto getPreview(@RequestParam(defaultValue = "ITEM-001") String text) {
        return service.generateBarcodePreview(text);
    }

    @GetMapping("/items")
    public List<BarcodeDtos.DesktopBarcodeItemDto> listItems() {
        return service.listDesktopItems();
    }

    @GetMapping("/search")
    public List<BarcodeDtos.LabelItemDto> searchItems(@RequestParam(defaultValue = "") String q) {
        return service.searchItems(q);
    }

    @PostMapping("/log-print")
    public boolean logPrint(@RequestParam int count, @RequestParam(required = false) String user) {
        return service.logBarcodePrint(count, user);
    }
}
