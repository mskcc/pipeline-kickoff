package org.mskcc.kickoff.config;

import org.mskcc.kickoff.roslin.util.Utils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.AbstractResource;

@Profile(SpringProfile.DEV)
@Configuration
public class DevConfiguration {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();

        propertySourcesPlaceholderConfigurer.setLocation(Utils.getPropertiesLocation("application-dev.properties"));
        // AppConfiguration.configureLogger("/log4j-dev.properties");
        propertySourcesPlaceholderConfigurer.setOrder(0);
        propertySourcesPlaceholderConfigurer.setIgnoreUnresolvablePlaceholders(true);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(SpringProfile.IGO)
    public static PropertySourcesPlaceholderConfigurer igoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();
        AbstractResource propertiesLocation = Utils.getPropertiesLocation("lims-igo-dev.properties");
        propertySourcesPlaceholderConfigurer.setLocation(propertiesLocation);
        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(SpringProfile.TANGO)
    public static PropertySourcesPlaceholderConfigurer tangoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();
        AbstractResource propertiesLocation = Utils.getPropertiesLocation("lims-tango-dev.properties");
        propertySourcesPlaceholderConfigurer.setLocation(propertiesLocation);

        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }
}
