package com.openlinklearn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalConfigurationTest {

    @Test
    void actuatorExposesOnlyStatusAndReadinessIncludesDatabase() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        assertNotNull(properties);

        assertEquals("health", properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-details"));
        assertEquals("never", properties.getProperty("management.endpoint.health.show-components"));
        assertEquals("true", properties.getProperty("management.endpoint.health.probes.enabled"));
        assertEquals("readinessState,db",
                properties.getProperty("management.endpoint.health.group.readiness.include"));
        assertEquals("logstash", properties.getProperty("logging.structured.format.console"));
    }

    @Test
    void databaseHealthIsDownWhenPostgresCannotBeReached() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection unavailable"));

        assertEquals(Status.DOWN, new DataSourceHealthIndicator(dataSource).health().getStatus());
    }
}