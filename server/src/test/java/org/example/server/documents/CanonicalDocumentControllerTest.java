package org.example.server.documents;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalDocumentControllerTest {
    @Test
    void renderEndpointReturnsExactCanonicalBytes() throws Exception {
        CanonicalDocumentService service = mock(CanonicalDocumentService.class);
        byte[] bytes = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'};
        when(service.render("SALES_INVOICE", "SI-95", "PDF"))
                .thenReturn(new CanonicalDocumentService.Rendered("Sales-Tax-Invoice-SI-95.pdf", "application/pdf", bytes));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CanonicalDocumentController(service)).build();

        mvc.perform(get("/api/documents/render")
                        .param("type", "SALES_INVOICE")
                        .param("number", "SI-95")
                        .param("format", "PDF"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Sales-Tax-Invoice-SI-95.pdf")))
                .andExpect(content().bytes(bytes));
        verify(service).render("SALES_INVOICE", "SI-95", "PDF");
    }

    @Test
    void canonicalServiceKeepsPermissionAndServerTemplateAuthority() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/server/documents/CanonicalDocumentService.java"));
        assertTrue(source.contains("CurrentUser.requirePermission(\"SALES.VIEW\""));
        assertTrue(source.contains("CurrentUser.requirePermission(\"PURCHASE.VIEW\""));
        assertTrue(source.contains("templates.pdf(DocumentType.SALES_INVOICE)"));
        assertTrue(source.contains("templates.excel(type)"));
        assertTrue(source.contains("ExcelTemplateRenderer.render"));
    }
}
