package org.mskcc.kickoff.fast.endtoend;

import org.mskcc.domain.Pairedness;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.archive.RunPipelineLogger;
import org.mskcc.kickoff.config.AppConfiguration;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.FileManifestGenerator;
import org.mskcc.kickoff.notify.DoubleSlashNewLineStrategy;
import org.mskcc.kickoff.notify.SingleSlashNewLineStrategy;
import org.mskcc.kickoff.pairing.PairingInfoRetriever;
import org.mskcc.kickoff.pairing.PairingInfoValidPredicate;
import org.mskcc.kickoff.pairing.PairingsResolver;
import org.mskcc.kickoff.pairing.SmartPairingRetriever;
import org.mskcc.kickoff.poolednormals.PooledNormalPredicate;
import org.mskcc.kickoff.printer.*;
import org.mskcc.kickoff.printer.observer.FileGenerationStatusManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.retriever.FastqPathsRetriever;
import org.mskcc.kickoff.retriever.FileSystemFastqPathsRetriever;
import org.mskcc.kickoff.upload.FileDeletionException;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.mskcc.kickoff.upload.jira.DummyPmJiraUserRetriever;
import org.mskcc.kickoff.upload.jira.LoggingClientHttpRequestInterceptor;
import org.mskcc.kickoff.upload.jira.PmJiraUserRetriever;
import org.mskcc.kickoff.upload.jira.state.BadInputsIssueStatus;
import org.mskcc.kickoff.upload.jira.state.StatusFactory;
import org.mskcc.kickoff.upload.jira.transitioner.ToBadInputsTransitioner;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.InMemoryErrorRepository;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.kickoff.validator.StrandValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.Set;
import java.util.function.Predicate;

import static org.mockito.Mockito.mock;

@Configuration
@PropertySource("classpath:integrationtest.properties")
@Profile("test")
public class ManifestFilesGeneratorTestConfiguration extends AppConfiguration {

    @Value("${test.integration.fastq_path}")
    private String fastqDir;

    @Autowired
    private Predicate<Set<Pairedness>> pairednessValidPredicate;

    @Autowired
    private PairednessResolver pairednessResolver;

    @Autowired
    private StatusFactory statusFactory;

    @Autowired
    private ErrorRepository errorRepository;

    @Bean
    public ToBadInputsTransitioner toBadInputsTransitioner() {
        return new ToBadInputsTransitioner();
    }

    @Bean
    public BadInputsIssueStatus badInputsJiraIssueState() {
        return new BadInputsIssueStatus();
    }

    @Bean
    public RequestFilePrinter requestFilePrinter() {
        return new RequestFilePrinter(observerManager(), errorRepository);
    }

    @Bean
    public JiraFileUploader fileUploader() {
        return new MockJiraFileUploader();
    }

    @Bean
    public FileManifestGenerator fileManifestGenerator() {
        return new FileManifestGenerator();
    }

    @Bean
    public OutputFilesPrinter outputFilesPrinter() {
        return new OutputFilesPrinter();
    }

    @Bean
    public FilesArchiver filesArchiver() {
        return mock(FilesArchiver.class);
    }

    @Bean
    public RunPipelineLogger runPipelineLogger() {
        return new RunPipelineLogger();
    }

    @Bean
    public RequestValidator requestValidator() {
        return mock(RequestValidator.class);
    }

    @Bean
    public LogConfigurator logConfigurator() {
        return mock(LogConfigurator.class);
    }

    @Bean
    public EmailNotificator emailNotificator() {
        return mock(EmailNotificator.class);
    }

    @Bean
    public GroupingFilePrinter groupingFilePrinter() {
        return new GroupingFilePrinter(observerManager());
    }

    @Bean
    public FileGenerationStatusManifestFileObserver fileGenerationStatusManifestFileObserver() {
        return new FileGenerationStatusManifestFileObserver(errorRepository);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ObserverManager observerManager() {
        return new ObserverManager();
    }

    @Bean
    public ErrorRepository errorRepository() {
        return new InMemoryErrorRepository();
    }

    @Bean
    public MappingFilePrinter mappingFilePrinter() {
        return new MappingFilePrinter(pairednessValidPredicate, pairednessResolver, observerManager(),
                fastqPathsRetriever(), new PooledNormalPredicate());
    }

    @Override
    public FastqPathsRetriever fastqPathsRetriever() {
        return new FileSystemFastqPathsRetriever(String.format("%s/hiseq/FASTQ/", fastqDir));
    }

    @Bean
    public PortalConfPrinter portalConfPrinter() {
        return new PortalConfPrinter(observerManager());
    }

    @Bean
    public DataClinicalFilePrinter clinicalFilePrinter() {
        return new DataClinicalFilePrinter(observerManager());
    }

    @Bean
    public PairingFilePrinter pairingFilePrinter() {
        return new PairingFilePrinter(pairingsResolver(), observerManager());
    }

    @Bean
    public SampleKeyPrinter sampleKeyFilePrinter() {
        return new SampleKeyPrinter(observerManager());
    }

    @Bean
    public PatientFilePrinter patientFilePrinter() {
        return new PatientFilePrinter(observerManager());
    }

    @Bean
    public ReadMePrinter readMePrinter() {
        return new ReadMePrinter(observerManager());
    }

    @Bean
    public CidToPidMappingPrinter cidToPidMappingPrinter() {
        return new CidToPidMappingPrinter(observerManager());
    }

    @Bean
    public ManifestFilePrinter manifestFilePrinter() {
        return new ManifestFilePrinter(observerManager());
    }

    @Bean
    public PmJiraUserRetriever pmJiraUserRetriever() {
        return new DummyPmJiraUserRetriever();
    }

    @Bean
    public ClientHttpRequestInterceptor clientHttpRequestInterceptor() {
        return new LoggingClientHttpRequestInterceptor();
    }

    @Bean
    public PairingsResolver pairingsResolver() {
        return new PairingsResolver(pairingInfoRetriever(), smartPairingRetriever());
    }

    @Bean
    public PairingInfoRetriever pairingInfoRetriever() {
        return new PairingInfoRetriever(pairingInfoValidPredicate(), observerManager());
    }

    @Bean
    public SmartPairingRetriever smartPairingRetriever() {
        return new SmartPairingRetriever(pairingInfoValidPredicate());
    }

    @Bean
    public PairingInfoValidPredicate pairingInfoValidPredicate() {
        return new PairingInfoValidPredicate(observerManager());
    }

    @Bean
    public SingleSlashNewLineStrategy singleSlashNewLineStrategy() {
        return new SingleSlashNewLineStrategy();
    }

    @Bean
    public DoubleSlashNewLineStrategy doubleSlashNewLineStrategy() {
        return new DoubleSlashNewLineStrategy();
    }


    @Bean
    public StrandValidator strandValidator() {
        return new StrandValidator(errorRepository);
    }

    public class MockJiraFileUploader extends JiraFileUploader {
        private boolean throwExceptionOnDelete;

        public void setThrowExceptionOnDelete(boolean throwException) {
            this.throwExceptionOnDelete = throwException;
        }

        @Override
        public void deleteExistingFiles(KickoffRequest request, String key) throws FileDeletionException {
            if (throwExceptionOnDelete)
                throw new FileDeletionException("");
            super.deleteExistingFiles(request, key);
        }
    }
}
