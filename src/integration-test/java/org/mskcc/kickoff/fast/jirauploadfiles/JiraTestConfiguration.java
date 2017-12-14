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
import org.mskcc.kickoff.printer.observer.FileUploadingManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.upload.FileDeletionException;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

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

    @Bean
    public MockJiraFileUploader fileUploader() {
        return new MockJiraFileUploader(jiraUrl, jiraUsername, jiraPassword, jiraRoslinProjectName);
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
    public FileUploadingManifestFileObserver fileUploadingManifestFileObserver() {
        return new FileUploadingManifestFileObserver(fileUploader());
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
    public PairingFilePrinter pairingFilePrinter() {
        return new PairingFilePrinter(pairingsResolver);
    }

    @Bean
    public RequestFilePrinter requestFilePrinter() {
        return new RequestFilePrinter();
    }

    @Bean
    public SampleKeyPrinter sampleKeyFilePrinter() {
        return new SampleKeyPrinter();
    }

    class MockJiraFileUploader extends JiraFileUploader {
        private boolean throwExceptionOnDelete;

        public MockJiraFileUploader(String jiraUrl, String username, String password, String projectName) {
            super(jiraUrl, username, password, projectName);
        }

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
