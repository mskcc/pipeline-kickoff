package org.mskcc.kickoff.config;

import org.mskcc.kickoff.bic.config.AppConfiguration;
import org.mskcc.kickoff.config.SpringProfile;
import org.mskcc.kickoff.roslin.util.Constants;
import org.mskcc.kickoff.roslin.util.Utils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.AbstractResource;

@Profile(SpringProfile.PROD)
@Configuration
public class ProdConfiguration {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();

        propertySourcesPlaceholderConfigurer.setLocation(Utils.getPropertiesLocation("application.properties"));

        // AppConfiguration.configureLogger("/log4j.properties");
        propertySourcesPlaceholderConfigurer.setOrder(0);
        propertySourcesPlaceholderConfigurer.setIgnoreUnresolvablePlaceholders(true);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(SpringProfile.IGO)
    public static PropertySourcesPlaceholderConfigurer igoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();
        AbstractResource propertiesLocation = Utils.getPropertiesLocation("lims-igo-prod.properties");
        propertySourcesPlaceholderConfigurer.setLocation(propertiesLocation);

        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(SpringProfile.TANGO)
    public static PropertySourcesPlaceholderConfigurer tangoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new
                PropertySourcesPlaceholderConfigurer();
        AbstractResource propertiesLocation = Utils.getPropertiesLocation("lims-tango-prod.properties");
        propertySourcesPlaceholderConfigurer.setLocation(propertiesLocation);

        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }
}
