package org.mskcc.kickoff.fast.endtoend;

import org.mskcc.kickoff.upload.jira.FromJiraPmJiraUserRetriever;
import org.mskcc.kickoff.upload.jira.PmJiraUserRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Profile("test")
public class FromJiraPmJiraUserRetrieverTestConfig {
    private static String jiraUsername;
    private static String jiraPassword;

    public static void setJiraUsername(String jiraUsername) {
        FromJiraPmJiraUserRetrieverTestConfig.jiraUsername = jiraUsername;
    }

    public static void setJiraPassword(String jiraPassword) {
        FromJiraPmJiraUserRetrieverTestConfig.jiraPassword = jiraPassword;
    }

    @Bean
    @Order(value = 0)
    static PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() throws IOException {
        final PropertyPlaceholderConfigurer configurer = new PropertyPlaceholderConfigurer();
        configurer.setLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:application-dev" +
                ".properties"));
        return configurer;
    }

    @Bean
    @Qualifier("jiraRestTemplate")
    @Lazy
    public RestTemplate jiraRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        addBasicAuth(restTemplate, jiraUsername, jiraPassword);
        return restTemplate;
    }

    @Bean
    @Lazy
    PmJiraUserRetriever pmJiraUserRetriever() {
        return new FromJiraPmJiraUserRetriever(jiraRestTemplate());
    }

    void addBasicAuth(RestTemplate restTemplate, String username, String password) {
        List<ClientHttpRequestInterceptor> interceptors = Collections.singletonList(new BasicAuthorizationInterceptor
                (username, password));
        restTemplate.setRequestFactory(new InterceptingClientHttpRequestFactory(restTemplate.getRequestFactory(),
                interceptors));
    }
}
