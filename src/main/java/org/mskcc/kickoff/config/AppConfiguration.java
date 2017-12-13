package org.mskcc.kickoff.config;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.mskcc.domain.Pairedness;
import org.mskcc.domain.PassedRunPredicate;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.converter.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.converter.SampleSetToRequestConverter;
import org.mskcc.kickoff.generator.*;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.mskcc.kickoff.validator.*;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.SampleSetProjectPredicate;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.mskcc.util.Constants;
import org.mskcc.util.email.EmailConfiguration;
import org.mskcc.util.email.EmailSender;
import org.mskcc.util.email.EmailToMimeMessageConverter;
import org.mskcc.util.email.JavaxEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Configuration
@Import({ProdConfiguration.class, DevConfiguration.class, TestConfiguration.class})
public class AppConfiguration {
    @Value("${manifestArchivePath}")
    private String manifestArchivePath;

    @Value("${designFilePath}")
    private String designFilePath;

    @Value("${resultsPathPrefix}")
    private String resultsPathPrefix;

    @Value("${manifestOutputFilePath}")
    private String manifestOutputFilePath;

    @Value("${limsConnectionFilePath}")
    private String limsConnectionFilePath;

    @Value("${file.generation.failure.notification.from}")
    private String from;

    @Value("${file.generation.failure.notification.host}")
    private String host;

    @Value("#{'${file.generation.failure.notification.recipients}'.split(',')}")
    private List<String> recipients;

    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.username}")
    private String jiraUsername;

    @Value("${jira.password}")
    private String jiraPassword;

    @Value("${jira.roslin.project.name}")
    private String jiraRoslinProjectName;

    static void configureLogger(String loggerPropertiesPath) {
        LogManager.resetConfiguration();
        try {
            PropertyConfigurator.configure(new ClassPathResource(loggerPropertiesPath).getURL());
        } catch (IOException e) {
            PropertyConfigurator.configure(Loader.getResource(loggerPropertiesPath));
        }

        Logger.getRootLogger().setLevel(Level.OFF);
    }

    @Bean
    public ProjectNameValidator projectNameValidator() {
        return new LimsProjectNameValidator(projectNamePredicate());
    }

    @Bean
    public Predicate<String> projectNamePredicate() {
        return new ProjectNamePredicate(sampleSetProjectPredicate(), sampleSetNamePredicate(),
                singleRequestNamePredicate());
    }

    @Bean
    public SampleSetProjectPredicate sampleSetProjectPredicate() {
        return new SampleSetProjectPredicate();
    }

    @Bean
    public SampleSetNamePredicate sampleSetNamePredicate() {
        return new SampleSetNamePredicate();
    }

    @Bean
    public SingleRequestNamePredicate singleRequestNamePredicate() {
        return new SingleRequestNamePredicate();
    }

    @Bean
    public RequestProxy requestProxy() {
        return new VeloxProjectProxy(limsConnectionFilePath, projectFilesArchiver(), requestsRetrieverFactory());
    }

    @Bean
    public EmailConfiguration config() {
        return new EmailConfiguration(recipients, from, host);
    }

    @Bean
    @Profile({Constants.PROD_PROFILE, Constants.DEV_PROFILE})
    public EmailSender sender() {
        return new JavaxEmailSender(emailToMimeMessageConverter());
    }

    /**
     * Dummy Email Sender used in tests to avoid overflowing of emails. In TEST profile emails won't be sent.
     *
     * @return
     */
    @Bean
    @Profile(Constants.TEST_PROFILE)
    public EmailSender dummySender() {
        return email -> {
        };
    }

    @Bean
    public EmailToMimeMessageConverter emailToMimeMessageConverter() {
        return new EmailToMimeMessageConverter();
    }

    @Bean
    public ProjectFilesArchiver projectFilesArchiver() {
        return new ProjectFilesArchiver(manifestArchivePath);
    }

    @Bean
    public PassedRunPredicate passedRunPredicate() {
        return new PassedRunPredicate();
    }

    @Bean
    public ProjectInfoRetriever projectInfoRetriever() {
        return new ProjectInfoRetriever();
    }

    @Bean
    public PairingsResolver pairingsResolver() {
        return new PairingsResolver(pairingInfoRetriever(), smartPairingRetriever());
    }

    @Bean
    public PairingInfoRetriever pairingInfoRetriever() {
        return new PairingInfoRetriever();
    }

    @Bean
    public SmartPairingRetriever smartPairingRetriever() {
        return new SmartPairingRetriever();
    }

    @Bean
    public SampleSetToRequestConverter sampleSetToRequestConverter() {
        return new SampleSetToRequestConverter(projectInfoConverter());
    }

    @Bean
    public SampleSetProjectInfoConverter projectInfoConverter() {
        return new SampleSetProjectInfoConverter();
    }

    @Bean
    public RequestsRetrieverFactory requestsRetrieverFactory() {
        return new RequestsRetrieverFactory(projectInfoRetriever(), requestDataPropagator(),
                sampleSetToRequestConverter());
    }

    @Bean
    public RequestDataPropagator requestDataPropagator() {
        return new RequestDataPropagator(designFilePath, resultsPathPrefix);
    }

    @Bean
    public OutputDirRetriever outputDirRetriever() {
        return new DefaultPathAwareOutputDirRetriever(manifestOutputFilePath, outputDirPredicate());
    }

    @Bean
    public Predicate<String> outputDirPredicate() {
        return new FileExistenceOutputDirValidator();
    }

    @Bean
    public FileUploader fileUploader() {
        return new JiraFileUploader(jiraUrl, jiraUsername, jiraPassword, jiraRoslinProjectName);
    }

    @Bean
    public ManifestFile.FilePrinterInjector filePrinterInjector() {
        return new ManifestFile.FilePrinterInjector();
    }

    @Bean
    public Predicate<Set<Pairedness>> pairednessValidPredicate() {
        return new PairednessValidPredicate();
    }

    @Bean
    public PairednessResolver pairednessResolver() {
        return new PairednessResolver();
    }
}
