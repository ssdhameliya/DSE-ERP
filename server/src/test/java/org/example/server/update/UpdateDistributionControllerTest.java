package org.example.server.update;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class UpdateDistributionControllerTest {
    @Test
    void exposesServerOwnedReleaseMetadataWithoutRepositoryCredentials() throws Exception {
        UpdateDistributionService service = mock(UpdateDistributionService.class);
        when(service.latest(false)).thenReturn(new UpdateDistributionService.ReleaseView(
                "v10.9.8", "DSE ERP", "Notes", "2026-09-14T17:57:36Z", false,
                List.of(new UpdateDistributionService.AssetView(42, "installer.exe", 1234,
                        "/api/updates/assets/42", "application/octet-stream")),
                ""));
        MockMvc mvc = standaloneSetup(new UpdateDistributionController(service)).build();

        mvc.perform(get("/api/updates/releases/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagName").value("v10.9.8"))
                .andExpect(jsonPath("$.assets[0].downloadUrl").value("/api/updates/assets/42"))
                .andExpect(jsonPath("$.htmlUrl").value(""));
    }
}
