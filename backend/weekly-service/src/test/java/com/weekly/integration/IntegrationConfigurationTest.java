package com.weekly.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Verifies that adapter beans are composed from environment-backed properties and
 * injected through the named {@code externalTicketAdapters} list.
 */
class IntegrationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void exposesEmptyAdapterListByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("externalTicketAdapters");
            assertThat(context.getBean(AdapterConsumer.class).adapters()).isEmpty();
        });
    }

    @Test
    void enablesJiraAdapterWhenRequiredPropertiesPresent() {
        contextRunner
                .withPropertyValues(
                        "integration.jira.base-url=https://org.atlassian.net",
                        "integration.jira.api-token=test-token",
                        "integration.jira.email=user@example.com"
                )
                .run(context -> {
                    assertThat(context.getBean(AdapterConsumer.class).adapters())
                            .hasSize(1)
                            .extracting(ExternalTicketAdapter::providerName)
                            .containsExactly("JIRA");
                });
    }

    @Test
    void enablesLinearAdapterWhenApiKeyPresent() {
        contextRunner
                .withPropertyValues(
                        "integration.linear.api-key=test-key",
                        "integration.linear.team-id=team-123"
                )
                .run(context -> {
                    assertThat(context.getBean(AdapterConsumer.class).adapters())
                            .hasSize(1)
                            .extracting(ExternalTicketAdapter::providerName)
                            .containsExactly("LINEAR");
                });
    }

    @Test
    void enablesBothAdaptersWhenBothConfigurationsPresent() {
        contextRunner
                .withPropertyValues(
                        "integration.jira.base-url=https://org.atlassian.net",
                        "integration.jira.api-token=test-token",
                        "integration.linear.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context.getBean(AdapterConsumer.class).adapters())
                            .hasSize(2)
                            .extracting(ExternalTicketAdapter::providerName)
                            .containsExactly("JIRA", "LINEAR");
                });
    }

    record AdapterConsumer(List<ExternalTicketAdapter> adapters) {
        AdapterConsumer(@Qualifier("externalTicketAdapters") List<ExternalTicketAdapter> adapters) {
            this.adapters = List.copyOf(adapters);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IntegrationConfiguration.class)
    static class TestConfig {
        @Bean
        AdapterConsumer adapterConsumer(
                @Qualifier("externalTicketAdapters") List<ExternalTicketAdapter> adapters
        ) {
            return new AdapterConsumer(adapters);
        }
    }
}
