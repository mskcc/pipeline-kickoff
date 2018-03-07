package org.mskcc.kickoff.fast.jirauploadfiles;

import org.mskcc.domain.Pairedness;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.archive.RunPipelineLogger;
import org.mskcc.kickoff.config.AppConfiguration;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.FileManifestGenerator;
import org.mskcc.kickoff.generator.PairingsResolver;
import org.mskcc.kickoff.printer.*;
import org.mskcc.kickoff.printer.observer.FileGenerationStatusManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.upload.FileDeletionException;
import org.mskcc.kickoff.upload.FilesValidator;
import org.mskcc.kickoff.upload.RequiredFilesValidator;
import org.mskcc.kickoff.upload.jira.*;
import org.mskcc.kickoff.upload.jira.state.BadInputsIssueStatus;
import org.mskcc.kickoff.upload.jira.state.HoldIssueStatus;
import org.mskcc.kickoff.upload.jira.state.StatusFactory;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.Set;
import java.util.function.Predicate;

import static org.mockito.Mockito.mock;

@Configuration
@Import(AppConfiguration.class)
public class JiraTestConfiguration {
    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.username}")
    private String jiraUsername;

    @Value("${jira.password}")
    private String jiraPassword;

    @Value("${jira.roslin.project.name}")
    private String jiraRoslinProjectName;

    @Autowired
    private PairingsResolver pairingsResolver;

    @Autowired
    private Predicate<Set<Pairedness>> pairednessValidPredicate;

    @Autowired
    private PairednessResolver pairednessResolver;

    @Autowired
    private StatusFactory statusFactory;

    @Bean
    public FilesValidator filesValidator() {
        return new RequiredFilesValidator();
    }

    @Bean
    public HoldIssueStatus holdJiraIssueState() {
        return new HoldIssueStatus();
    }

    @Bean
    public ToBadInputsTransitioner toBadInputsTransitioner() {
        return new ToBadInputsTransitioner();
    }

    @Bean
    public ToHoldTransitioner dummyToHoldTransitioner() {
        return new DummyTransitioner();
    }

    @Bean
    public BadInputsIssueStatus badInputsJiraIssueState() {
        return new BadInputsIssueStatus();
    }

    @Bean
    public RequestFilePrinter requestFilePrinter() {
        return new RequestFilePrinter(observerManager());
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
    public FileGenerationStatusManifestFileObserver fileGenerationStatusManifestFileObserver() {
        return new FileGenerationStatusManifestFileObserver();
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ObserverManager observerManager() {
        return new ObserverManager();
    }

    @Bean
    public GroupingFilePrinter groupingFilePrinter() {
        return new GroupingFilePrinter(observerManager());
    }

    @Bean
    public MappingFilePrinter mappingFilePrinter() {
        return new MappingFilePrinter(pairednessValidPredicate, pairednessResolver, observerManager());
    }

    @Bean
    public ClinicalFilePrinter clinicalFilePrinter() {
        return new ClinicalFilePrinter(observerManager());
    }

    @Bean
    public PairingFilePrinter pairingFilePrinter() {
        return new PairingFilePrinter(pairingsResolver, observerManager());
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

    class MockJiraFileUploader extends JiraFileUploader {
        private boolean throwExceptionOnDelete;

        public void setThrowExceptionOnDelete(boolean throwException) {
            this.throwExceptionOnDelete = throwException;
        }

        @Override
        public void deleteExistingFiles(KickoffRequest request) throws FileDeletionException {
            if (throwExceptionOnDelete)
                throw new FileDeletionException("");
            super.deleteExistingFiles(request);
        }
    }
}
