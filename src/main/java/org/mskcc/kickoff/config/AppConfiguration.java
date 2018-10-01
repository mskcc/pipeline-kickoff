package org.mskcc.kickoff.config;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.mskcc.domain.Pairedness;
import org.mskcc.domain.PassedRunPredicate;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.generator.DefaultPathAwareOutputDirRetriever;
import org.mskcc.kickoff.generator.OutputDirRetriever;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.DoubleSlashNewLineStrategy;
import org.mskcc.kickoff.notify.FilesErrorsNotificationFormatter;
import org.mskcc.kickoff.notify.NotificationFormatter;
import org.mskcc.kickoff.notify.SingleSlashNewLineStrategy;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.retriever.ServiceReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.sampleset.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.sampleset.SampleSetProjectPredicate;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;
import org.mskcc.kickoff.upload.jira.state.*;
import org.mskcc.kickoff.validator.*;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.VeloxConnectionData;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.mskcc.util.Constants;
import org.mskcc.util.email.EmailConfiguration;
import org.mskcc.util.email.EmailSender;
import org.mskcc.util.email.EmailToMimeMessageConverter;
import org.mskcc.util.email.JavaxEmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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

    @Value("${jira.roslin.generated.transition}")
    private String generatedTransition;

    @Value("${jira.roslin.regenerated.transition}")
    private String regeneratedTransition;

    @Value("${jira.roslin.fastqs.available.status}")
    private String fastqsAvailableStatus;

    @Value("${jira.roslin.input.regeneration.status}")
    private String regenerateStatus;

    @Value("${jira.roslin.input.generated.status}")
    private String generatedStatus;

    @Value("${lims.host}")
    private String limsHost;

    @Value("${lims.port}")
    private int limsPort;

    @Value("${lims.username}")
    private String limsUsername;

    @Value("${lims.password}")
    private String limsPassword;

    @Value("${lims.guid}")
    private String limsGuid;

    @Value("${jira.pm.group.name}")
    private String pmGroupName;

    @Value("${jira.igo.formatted.name.property}")
    private String igoFormattedNameProperty;

    @Value("${external.sample.rest.url}")
    private String externalRestUrl;

    @Value("${external.sample.rest.samples.endpoint}")
    private String externalRestEndpoint;

    @Value("${external.sample.rest.username}")
    private String externalSampleRestUsername;

    @Value("${external.sample.rest.password}")
    private String externalSampleRestPassword;

    @Autowired
    private ClientHttpRequestInterceptor loggingClientHttpRequestInterceptor;

    @Autowired
    private HoldIssueStatus holdJiraIssueState;

    @Autowired
    private ErrorRepository errorRepository;

    @Autowired
    private ObserverManager observerManager;

    @Autowired
    private SingleSlashNewLineStrategy singleSlashNewLineStrategy;

    @Autowired
    private DoubleSlashNewLineStrategy doubleSlashNewLineStrategy;

    private String regeneratedStatus = "Files Regenerated";

    public static void configureLogger(String loggerPropertiesName) {
        LogManager.resetConfiguration();

        if (new File(loggerPropertiesName).exists())
            PropertyConfigurator.configure(new FileSystemResource(loggerPropertiesName).getFile().getAbsoluteFile()
                    .toString());
        else {
            try {
                PropertyConfigurator.configure(new ClassPathResource(loggerPropertiesName).getURL());
            } catch (IOException e) {
                PropertyConfigurator.configure(Loader.getResource(loggerPropertiesName));
            }
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
        return new VeloxProjectProxy(veloxConnectionData(), projectFilesArchiver(), requestsRetrieverFactory());
    }

    @Bean
    public VeloxConnectionData veloxConnectionData() {
        return new VeloxConnectionData(limsHost, limsPort, limsUsername, limsPassword, limsGuid);
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
    public SampleSetToRequestConverter sampleSetToRequestConverter() {
        return new SampleSetToRequestConverter(projectInfoConverter());
    }

    @Bean
    public SampleSetProjectInfoConverter projectInfoConverter() {
        return new SampleSetProjectInfoConverter();
    }

    @Bean
    public RequestsRetrieverFactory requestsRetrieverFactory() {
        return new RequestsRetrieverFactory(
                projectInfoRetriever(),
                requestDataPropagator(),
                sampleSetToRequestConverter(),
                readOnlyExternalSamplesRepository());
    }

    @Bean
    public ReadOnlyExternalSamplesRepository readOnlyExternalSamplesRepository() {
        return new ServiceReadOnlyExternalSamplesRepository(externalRestUrl, externalRestEndpoint,
                externalRestTemplate(), observerManager);
    }

    @Bean
    public RequestDataPropagator requestDataPropagator() {
        return new RequestDataPropagator(designFilePath, resultsPathPrefix, errorRepository);
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
    public StatusFactory jiraStateFactory() {
        return new StatusFactory(generateFilesState(), regenerateFilesState(), filesGeneratedState(),
                holdJiraIssueState);
    }

    @Bean
    public GenerateFilesStatus generateFilesState() {
        return new GenerateFilesStatus(fastqsAvailableStatus, generatedTransition, filesGeneratedState());
    }

    @Bean
    public RegenerateFilesStatus regenerateFilesState() {
        return new RegenerateFilesStatus(regenerateStatus, regeneratedTransition, filesRegeneratedState());
    }

    @Bean
    public FilesGeneratedStatus filesGeneratedState() {
        return new FilesGeneratedStatus(generatedStatus);
    }

    @Bean
    public FilesRegeneratedStatus filesRegeneratedState() {
        return new FilesRegeneratedStatus(regeneratedStatus);
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

    @Bean
    @Profile(Constants.PROD_PROFILE)
    @Qualifier("jiraRestTemplate")
    public RestTemplate jiraRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        addBasicAuth(restTemplate, jiraUsername, jiraPassword);

        return restTemplate;
    }

    private void addBasicAuth(RestTemplate restTemplate, String username, String password) {
        List<ClientHttpRequestInterceptor> interceptors = Collections.singletonList(new BasicAuthorizationInterceptor
                (username, password));
        restTemplate.setRequestFactory(new InterceptingClientHttpRequestFactory(restTemplate.getRequestFactory(),
                interceptors));
    }

    @Bean
    @Qualifier("externalSampleRest")
    public RestTemplate externalRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        addBasicAuth(restTemplate, externalSampleRestUsername, externalSampleRestPassword);

        return restTemplate;
    }

    @Bean
    @Profile({Constants.DEV_PROFILE, Constants.TEST_PROFILE})
    @Qualifier("jiraRestTemplate")
    public RestTemplate jiraLoggingRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(loggingClientHttpRequestInterceptor);
        addBasicAuth(restTemplate, jiraUsername, jiraPassword);
        return restTemplate;
    }

    @Bean
    @Qualifier("singleSlash")
    public NotificationFormatter singleSlashNotificationFormatter() {
        return new FilesErrorsNotificationFormatter(errorRepository, singleSlashNewLineStrategy);
    }

    @Bean
    @Qualifier("doubleSlash")
    public NotificationFormatter doubleSlashNotificationFormatter() {
        return new FilesErrorsNotificationFormatter(errorRepository, doubleSlashNewLineStrategy);
    }
}
