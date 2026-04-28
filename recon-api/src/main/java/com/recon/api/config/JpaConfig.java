package com.recon.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Ensures JPA entity scanning covers all com.recon sub-modules,
 * not just the com.recon.api package of the main class.
 */
@Configuration
public class JpaConfig {

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.recon");
        factory.setPersistenceUnitName("default");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.jdbc.batch_size", 500);
        props.put("hibernate.order_inserts", true);
        props.put("hibernate.order_updates", true);
        factory.setJpaPropertyMap(props);
        return factory;
    }
}
