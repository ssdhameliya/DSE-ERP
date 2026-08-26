package org.example.documentstudio.service;

import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.TemplateData;
import java.io.IOException;
import java.nio.file.Path;

/** Compatibility entry point for runtime flows. The PDF Studio implementation lives in PdfStudioRenderer. */
public final class PdfTemplateRenderer {
    private PdfTemplateRenderer() {}

    public static Path renderPurchase(DocumentTemplate template, org.example.model.Purchase purchase, Path output) throws IOException {
        return PdfStudioRenderer.renderPurchase(template, purchase, output);
    }

    public static Path renderSample(DocumentTemplate template, Path output) throws IOException {
        return PdfStudioRenderer.renderSample(template, output);
    }

    public static Path render(DocumentTemplate template, TemplateData data, Path output) throws IOException {
        return PdfStudioRenderer.render(template, data, output);
    }
}
