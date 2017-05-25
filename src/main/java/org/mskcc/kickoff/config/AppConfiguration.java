package org.mskcc.kickoff.config;

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
        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.DEV_PROFILE)
    public static PropertySourcesPlaceholderConfigurer devPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/application-dev.properties"));
        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.IGO_PROFILE)
    public static PropertySourcesPlaceholderConfigurer igoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/lims-igo.properties"));
        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    @Profile(Constants.TANGO_PROFILE)
    public static PropertySourcesPlaceholderConfigurer tangoPropertyConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        propertySourcesPlaceholderConfigurer.setLocation(new ClassPathResource("/lims-tango.properties"));
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
    public LogConfigurer logConfigurer() {
        LogConfigurer logConfigurer = new LogConfigurer();
        return logConfigurer;
    }
}
