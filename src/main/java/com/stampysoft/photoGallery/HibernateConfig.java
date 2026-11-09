package com.stampysoft.photoGallery;

import com.stampysoft.photoGallery.config.DatabaseChangeTracker;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories
@EnableTransactionManagement
@ComponentScan("com.stampysoft.photoGallery")
public class HibernateConfig {
    @Autowired
    private Environment env;

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.stampysoft.photoGallery");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(hibernateProperties());

        // Has to be registered here rather than through a HibernatePropertiesCustomizer bean: defining this
        // EntityManagerFactory makes Spring Boot's JPA auto-configuration back off, and those customizers back off
        // with it. Set on the property map rather than in hibernateProperties() below because the value is an
        // object, not a string. See DatabaseChangeTracker, which checks at startup that this actually took.
        em.getJpaPropertyMap().put(AvailableSettings.STATEMENT_INSPECTOR, DatabaseChangeTracker.newInspector());

        return em;
    }

    private Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", env.getProperty("spring.jpa.properties.hibernate.dialect"));
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        return properties;
    }
}
