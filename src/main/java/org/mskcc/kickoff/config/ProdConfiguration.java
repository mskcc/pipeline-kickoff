package org.mskcc.kickoff.config;

import org.mskcc.kickoff.util.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

@Profile(Constants.PROD_PROFILE)
@Configuration
public class ProdConfiguration {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/application.properties"));

        AppConfiguration.configureLogger("/log4j.properties");

        propertySourcesPlaceholderConfigurer.setOrder(0);
        propertySourcesPlaceholderConfigurer.setIgnoreUnresolvablePlaceholders(true);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.IGO_PROFILE)
    public static PropertySourcesPlaceholderConfigurer igoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/lims-igo-prod.properties"));
        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.TANGO_PROFILE)
    public static PropertySourcesPlaceholderConfigurer tangoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/lims-tango-prod.properties"));
        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }
}
