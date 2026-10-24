package org.example.server.support;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Automatically suppresses Vaadin Flow auto-configurations when Spring Boot is initialized
 * in non-web mode (e.g. spring.main.web-application-type=none or SpringBootTest.WebEnvironment.NONE).
 *
 * <p>Vaadin Flow's SpringBootAutoConfiguration expects an active WebApplicationContext;
 * suppressing it in headless integration tests preserves pure database/workflow integration tests
 * without requiring a servlet context mock.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VaadinNonWebEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String EXCLUDE_KEY = "spring.autoconfigure.exclude";
    private static final String VAADIN_EXCLUDES =
            "com.vaadin.flow.spring.SpringBootAutoConfiguration," +
            "com.vaadin.flow.spring.SpringSecurityAutoConfiguration," +
            "com.vaadin.flow.spring.VaadinScopesConfig";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String webAppType = environment.getProperty("spring.main.web-application-type", "");
        if ("none".equalsIgnoreCase(webAppType)) {
            String current = environment.getProperty(EXCLUDE_KEY, "");
            String combined = (current == null || current.isBlank())
                    ? VAADIN_EXCLUDES
                    : current + "," + VAADIN_EXCLUDES;
            Map<String, Object> props = new HashMap<>();
            props.put(EXCLUDE_KEY, combined);
            environment.getPropertySources().addFirst(
                    new MapPropertySource("vaadinNonWebExclusionProperties", props));
        }
    }
}
