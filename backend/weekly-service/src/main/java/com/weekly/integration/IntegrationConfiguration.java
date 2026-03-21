package com.weekly.integration;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the external integration adapters.
 *
 * <p>Each adapter is only instantiated if its required configuration is present.
 * Adapters that lack configuration are silently skipped (logged at INFO level).
 *
 * <p>Jira properties:
 * <ul>
 *   <li>{@code integration.jira.base-url}  — Jira instance base URL (e.g. https://org.atlassian.net)</li>
 *   <li>{@code integration.jira.api-token} — API token for authentication</li>
 *   <li>{@code integration.jira.email}     — Email associated with the API token</li>
 * </ul>
 *
 * <p>Linear properties:
 * <ul>
 *   <li>{@code integration.linear.api-key} — Linear workspace or personal API key</li>
 *   <li>{@code integration.linear.team-id} — Optional team ID to scope queries</li>
 * </ul>
 */
@Configuration
public class IntegrationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationConfiguration.class);

    @Bean
    public List<ExternalTicketAdapter> externalTicketAdapters(
            @Value("${integration.jira.base-url:}") String jiraBaseUrl,
            @Value("${integration.jira.api-token:}") String jiraApiToken,
            @Value("${integration.jira.email:}") String jiraEmail,
            @Value("${integration.linear.api-key:}") String linearApiKey,
            @Value("${integration.linear.team-id:}") String linearTeamId
    ) {
        List<ExternalTicketAdapter> adapters = new ArrayList<>();

        if (!jiraBaseUrl.isBlank() && !jiraApiToken.isBlank()) {
            LOG.info("Integration: Jira adapter enabled (baseUrl={})", jiraBaseUrl);
            adapters.add(new JiraAdapter(jiraBaseUrl, jiraApiToken, jiraEmail));
        } else {
            LOG.info("Integration: Jira adapter disabled (missing integration.jira.base-url or api-token)");
        }

        if (!linearApiKey.isBlank()) {
            LOG.info("Integration: Linear adapter enabled");
            adapters.add(new LinearAdapter(linearApiKey, linearTeamId));
        } else {
            LOG.info("Integration: Linear adapter disabled (missing integration.linear.api-key)");
        }

        return adapters;
    }
}
