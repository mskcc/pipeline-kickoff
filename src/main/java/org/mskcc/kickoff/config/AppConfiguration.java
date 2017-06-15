package org.mskcc.kickoff.config;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.LimsProjectNameValidator;
import org.mskcc.kickoff.validator.ProjectNamePredicate;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;

import java.util.function.Predicate;

@Configuration
public class AppConfiguration {
    @Bean
    @Profile(Constants.PROD_PROFILE)
    public static PropertySourcesPlaceholderConfigurer propertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/application.properties"));

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(Loader.getResource("log4j.properties"));

        propertySourcesPlaceholderConfigurer.setOrder(0);
        propertySourcesPlaceholderConfigurer.setIgnoreUnresolvablePlaceholders(true);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.DEV_PROFILE)
    public static PropertySourcesPlaceholderConfigurer devPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/application-dev.properties"));

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(Loader.getResource("log4j-dev.properties"));
        Logger.getRootLogger().setLevel(Level.OFF);


        propertySourcesPlaceholderConfigurer.setOrder(0);
        propertySourcesPlaceholderConfigurer.setIgnoreUnresolvablePlaceholders(true);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.IGO_PROFILE)
    public static PropertySourcesPlaceholderConfigurer igoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/lims-igo.properties"));
        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.TANGO_PROFILE)
    public static PropertySourcesPlaceholderConfigurer tangoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/lims-tango.properties"));
        propertySourcesPlaceholderConfigurer.setOrder(1);

        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    public ProjectNameValidator projectNameValidator() {
        return new LimsProjectNameValidator(projectNamePredicate());
    }

    @Bean
    public Predicate<String> projectNamePredicate() {
        return new ProjectNamePredicate();
    }

    @Bean
    public LogConfigurator logConfigurer() {
        PmAndDevLogConfigurator pmAndDevLogConfigurator = new PmAndDevLogConfigurator();
        return pmAndDevLogConfigurator;
    }
}
