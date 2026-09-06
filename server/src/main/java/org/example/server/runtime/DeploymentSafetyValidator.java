package org.example.server.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Fails startup before serving traffic when UAT/PROD is wired to the wrong database. */
@Component
public final class DeploymentSafetyValidator implements ApplicationRunner {
    private static final Set<String> ALLOWED = Set.of("LOCAL", "UAT", "PROD");
    private final JpaNativeRepository repository;
    private final String environment;
    private final String expectedDatabase;

    public DeploymentSafetyValidator(JpaNativeRepository repository,
            @Value("${dse.deployment.environment:LOCAL}") String environment,
            @Value("${dse.expected.database:}") String expectedDatabase) {
        this.repository = repository;
        this.environment = normalize(environment);
        this.expectedDatabase = expectedDatabase == null ? "" : expectedDatabase.trim();
    }

    @Override public void run(ApplicationArguments args) {
        if (!ALLOWED.contains(environment))
            throw new IllegalStateException("DSE deployment environment must be LOCAL, UAT or PROD; found " + environment);
        String actual = repository.queryForObject("SELECT current_database()", String.class);
        if (!expectedDatabase.isBlank() && (actual == null || !expectedDatabase.equalsIgnoreCase(actual.trim())))
            throw new IllegalStateException("Deployment safety check failed: " + environment + " expected database "
                    + expectedDatabase + " but connected to " + actual);
        if (("UAT".equals(environment) || "PROD".equals(environment)) && expectedDatabase.isBlank())
            throw new IllegalStateException("DSE_EXPECTED_DATABASE is required for " + environment + " deployments.");
    }

    private static String normalize(String value) {
        return value == null ? "LOCAL" : value.trim().toUpperCase(Locale.ROOT);
    }
}
