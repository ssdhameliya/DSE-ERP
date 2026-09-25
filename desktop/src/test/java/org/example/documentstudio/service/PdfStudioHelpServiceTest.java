package org.example.documentstudio.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfStudioHelpServiceTest {
    @Test
    void bundledGuideExportsAsValidPdf() throws Exception {
        Path folder = Files.createTempDirectory("pdf-studio-guide-test-");
        Path guide = PdfStudioHelpService.exportGuide(folder);
        assertTrue(Files.isRegularFile(guide));
        assertTrue(Files.size(guide) > 100);
        byte[] signature = Files.readAllBytes(guide);
        assertTrue(signature.length > 4);
        assertEquals((byte)'%', signature[0]);
        assertEquals((byte)'P', signature[1]);
        assertEquals((byte)'D', signature[2]);
        assertEquals((byte)'F', signature[3]);
        assertEquals(PdfStudioHelpService.GUIDE_FILE_NAME, guide.getFileName().toString());
    }
}
