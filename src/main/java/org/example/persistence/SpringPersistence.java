package org.example.persistence;

import jakarta.persistence.EntityManagerFactory;
import org.example.config.ConfigManager;
import org.example.database.DatabaseManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/** Owns the Spring/Hibernate persistence context used by the JavaFX application. */
public final class SpringPersistence {
    private static AnnotationConfigApplicationContext context;

    private SpringPersistence() {}

    public static synchronized void initialize() {
        if (context != null) return;
        context = new AnnotationConfigApplicationContext(PersistenceConfiguration.class);
    }

    public static synchronized void close() {
        if (context != null) context.close();
        context = null;
    }

    public static <T> T bean(Class<T> type) {
        if (context == null) throw new IllegalStateException("Spring persistence has not been initialized");
        return context.getBean(type);
    }

    @Configuration
    @EnableJpaRepositories(basePackages = "org.example.persistence.repository")
    static class PersistenceConfiguration {
        @Bean(destroyMethod = "")
        DataSource dataSource() {
            return DatabaseManager.postgresDataSource();
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
            adapter.setGenerateDdl(false);
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(adapter);
            factory.setPackagesToScan("org.example.persistence.entity");
            Map<String, Object> properties = new HashMap<>();
            properties.put("hibernate.hbm2ddl.auto", "validate");
            properties.put("hibernate.jdbc.time_zone", "UTC");
            factory.setJpaPropertyMap(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
