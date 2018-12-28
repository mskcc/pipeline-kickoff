package org.mskcc.kickoff.config;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.mskcc.domain.Pairedness;
import org.mskcc.domain.PassedRunPredicate;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.DefaultPathAwareOutputDirRetriever;
import org.mskcc.kickoff.generator.OutputDirRetriever;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.DoubleSlashNewLineStrategy;
import org.mskcc.kickoff.notify.FilesErrorsNotificationFormatter;
import org.mskcc.kickoff.notify.NotificationFormatter;
import org.mskcc.kickoff.notify.SingleSlashNewLineStrategy;
import org.mskcc.kickoff.pairing.PairingInfoValidPredicate;
import org.mskcc.kickoff.pairing.SampleSetPairingInfoValidPredicate;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.retriever.*;
import org.mskcc.kickoff.sampleset.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.sampleset.SampleSetProjectPredicate;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;
import org.mskcc.kickoff.upload.FilesValidator;
import org.mskcc.kickoff.upload.RequiredFilesValidator;
import org.mskcc.kickoff.upload.jira.state.*;
import org.mskcc.kickoff.validator.*;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.Sample2DataRecordMap;
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
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@Configuration
@Import({ProdConfiguration.class, DevConfiguration.class, TestConfiguration.class})
@ComponentScan(basePackages = "org.mskcc.kickoff")
public class AppConfiguration {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final String PAIR_DELIMITER = ":";
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
    @Value("#{'${pairing.assay.compatibility}'.split(',')}")
    private List<String> baitSetCompatibility;
    @Value("${fastq_path}")
    private String fastqPath;

    @Autowired
    private ClientHttpRequestInterceptor loggingClientHttpRequestInterceptor;
    @Autowired
    private HoldIssueStatus holdIssueStatus;
    @Autowired
    private ErrorRepository errorRepository;
    @Autowired
    private ObserverManager observerManager;
    @Autowired
    private SingleSlashNewLineStrategy singleSlashNewLineStrategy;
    @Autowired
    private DoubleSlashNewLineStrategy doubleSlashNewLineStrategy;
    private String regeneratedStatus = "Files Regenerated";

    @Autowired
    private ProjectInfoRetriever projectInfoRetriever;

    @Autowired
    private PairingInfoValidPredicate singleRequestPairingInfoValidPredicate;

    @Autowired
    private NimblegenResolver nimblegenResolver;

    @Autowired
    private Sample2DataRecordMap sample2DataRecordMap;

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
    public SampleSetToRequestConverter sampleSetToRequestConverter() {
        return new SampleSetToRequestConverter(
                projectInfoConverter(),
                sampleSetBaitSetCompatibilityPredicate(),
                errorRepository);
    }

    @Bean
    public SampleSetProjectInfoConverter projectInfoConverter() {
        return new SampleSetProjectInfoConverter();
    }

    @Bean
    public RequestsRetrieverFactory requestsRetrieverFactory() {
        return new RequestsRetrieverFactory(
                projectInfoRetriever,
                sampleSetRequestDataPropagator(),
                singleRequestRequestDataPropagator(),
                sampleSetToRequestConverter(),
                readOnlyExternalSamplesRepository(),
                sampleSetPairingInfoValidPredicate(),
                singleRequestPairingInfoValidPredicate,
                errorRepository,
                nimblegenResolver,
                sample2DataRecordMap);
    }

    @Bean
    public ReadOnlyExternalSamplesRepository readOnlyExternalSamplesRepository() {
        return new ServiceReadOnlyExternalSamplesRepository(externalRestUrl, externalRestEndpoint,
                externalRestTemplate(), observerManager);
    }

    @Bean
    @Qualifier("sampleSetRequestDataPropagator")
    public RequestDataPropagator sampleSetRequestDataPropagator() {
        return new RequestDataPropagator(designFilePath, resultsPathPrefix, errorRepository,
                sampleSetBaitSetCompatibilityPredicate());
    }

    @Bean
    public BiPredicate<String, String> sampleSetBaitSetCompatibilityPredicate() {
        return new SampleSetBaitSetCompatibilityPredicate(prepareCompatibilityConfig(baitSetCompatibility));
    }

    public List<SampleSetBaitSetCompatibilityPredicate.Pair<String>> prepareCompatibilityConfig(List<String>
                                                                                                        baitSetCompatibility) {
        List<SampleSetBaitSetCompatibilityPredicate.Pair<String>> pairs = new ArrayList<>();

        for (String pair : baitSetCompatibility) {
            String[] baitSets = pair.split(PAIR_DELIMITER);

            if (baitSets.length != 2) {
                LOGGER.warn(String.format("Bait set compatibility '%s' in properties file is in incorrect format. " +
                        "Expected format is BAITSET1:BAITSET2", pair));
                continue;
            }

            SampleSetBaitSetCompatibilityPredicate.Pair<String> compatibilityPair = new
                    SampleSetBaitSetCompatibilityPredicate.Pair<>(baitSets[0], baitSets[1]);
            LOGGER.info(String.format("Adding bait set compatibility pair: %s", compatibilityPair));

            pairs.add(compatibilityPair);
        }

        return pairs;
    }

    @Bean
    public BiPredicate<String, String> singleRequestBaitSetCompatibilityPredicate() {
        return new AllSameBaitSetCompatibilityPredicate();
    }

    @Bean
    @Qualifier("singleRequestRequestDataPropagator")
    public RequestDataPropagator singleRequestRequestDataPropagator() {
        return new RequestDataPropagator(designFilePath, resultsPathPrefix, errorRepository,
                singleRequestBaitSetCompatibilityPredicate());
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
                holdIssueStatus);
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
    public FastqPathsRetriever fastqPathsRetriever() {
        return new FileSystemFastqPathsRetriever(String.format("%s/hiseq/FASTQ/", fastqPath));
    }

    @Bean
    @Profile("prod")
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

    @Bean
    public BiPredicate<Sample, Sample> sampleSetPairingInfoValidPredicate() {
        return new SampleSetPairingInfoValidPredicate(singleRequestPairingInfoValidPredicate,
                sampleSetBaitSetCompatibilityPredicate());
    }

    @Bean
    public FilesValidator filesValidator() {
        return new RequiredFilesValidator(errorRepository);
    }

    @Bean
    public List<Predicate<KickoffRequest>> validators() {
        return Arrays.asList(
                new AutoGenerabilityValidator(errorRepository),
                new BarcodeValidator(),
                new OutputDirValidator(),
                new PoolQcValidator(errorRepository),
                new PostSeqQcValidator(),
                new ReadCountsValidator(),
                new SampleQcValidator(errorRepository),
                new SamplesValidator(errorRepository),
                new SampleUniquenessValidator(errorRepository),
                new SequencingRunsValidator(errorRepository),
                new StrandValidator(errorRepository)
        );
    }
}
