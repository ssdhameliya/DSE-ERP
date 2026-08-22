package org.example.server.runtime;

import org.example.shared.RuntimeContract;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RuntimeControllerTest {
    @Test
    void exposesTheCompleteDesktopReadinessContract() throws Exception {
        RuntimeService service = mock(RuntimeService.class);
        when(service.databaseReady()).thenReturn(true);
        when(service.databaseTimeZone()).thenReturn("UTC");
        MockMvc mvc = standaloneSetup(new RuntimeController(service, RuntimeContract.APP_VERSION, RuntimeContract.API_REVISION)).build();

        mvc.perform(get(RuntimeContract.HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.service").value(RuntimeContract.SERVICE_NAME))
                .andExpect(jsonPath("$.version").value(RuntimeContract.APP_VERSION))
                .andExpect(jsonPath("$.apiRevision").value(RuntimeContract.API_REVISION))
                .andExpect(jsonPath("$.database").value("postgresql"))
                .andExpect(jsonPath("$.databaseTimeZone").value("UTC"))
                .andExpect(jsonPath("$.timePolicy").value("ISO_DATE_UTC_INSTANT"));
    }

    @Test
    void reportsNotReadyWhenTheManagedDatabaseCannotBeReached() throws Exception {
        RuntimeService service = mock(RuntimeService.class);
        when(service.databaseReady()).thenThrow(new IllegalStateException("offline"));
        MockMvc mvc = standaloneSetup(new RuntimeController(service, RuntimeContract.APP_VERSION, RuntimeContract.API_REVISION)).build();

        mvc.perform(get(RuntimeContract.HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.message").value("Database unavailable"));
    }

    @Test
    void sourceContainsExactlyOneRuntimeHealthMapping() throws Exception {
        Path runtimeSource = Path.of("src", "main", "java", "org", "example", "server", "runtime");
        try (var files = Files.list(runtimeSource)) {
            long mappings = files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (Exception exception) { throw new RuntimeException(exception); }
                    })
                    .filter(source -> source.contains("@RequestMapping(\"/api/runtime\")")
                            && source.contains("@GetMapping(\"/health\")"))
                    .count();
            assertEquals(1, mappings, "Exactly one controller may own GET /api/runtime/health");
        }
    }
}
