package org.example.server.runtime;

import org.example.server.persistence.JpaNativeRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentSafetyValidatorTest {
    @Test
    void acceptsUatOnlyWhenTheExpectedDatabaseMatches() {
        JpaNativeRepository repository = mock(JpaNativeRepository.class);
        when(repository.queryForObject("SELECT current_database()", String.class)).thenReturn("dse_erp_uat");
        assertDoesNotThrow(() -> new DeploymentSafetyValidator(repository, "UAT", "dse_erp_uat").run(null));
    }

    @Test
    void rejectsUatOrProdWithoutAnExplicitExpectedDatabase() {
        JpaNativeRepository repository = mock(JpaNativeRepository.class);
        when(repository.queryForObject("SELECT current_database()", String.class)).thenReturn("dse_erp_uat");
        assertThrows(IllegalStateException.class,
                () -> new DeploymentSafetyValidator(repository, "UAT", "").run(null));
    }

    @Test
    void rejectsAProductionServerPointedAtTheUatDatabase() {
        JpaNativeRepository repository = mock(JpaNativeRepository.class);
        when(repository.queryForObject("SELECT current_database()", String.class)).thenReturn("dse_erp_uat");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DeploymentSafetyValidator(repository, "PROD", "dse_erp").run(null));
        assertTrue(failure.getMessage().contains("expected database dse_erp"));
    }
}
