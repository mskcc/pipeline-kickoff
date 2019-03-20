package org.mskcc.kickoff.roslin.characterisationTest;

import org.mskcc.kickoff.roslin.archive.FilesArchiver;
import org.mskcc.kickoff.roslin.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.roslin.config.AppConfiguration;
import org.mskcc.kickoff.logger.LogConfigurator;
import org.mskcc.kickoff.roslin.generator.DefaultPathAwareOutputDirRetriever;
import org.mskcc.kickoff.roslin.generator.FileManifestGenerator;
import org.mskcc.kickoff.roslin.generator.OutputDirRetriever;
import org.mskcc.kickoff.roslin.printer.OutputFilesPrinter;
import org.mskcc.kickoff.roslin.upload.FileUploader;
import org.mskcc.kickoff.roslin.upload.JiraFileUploader;
import org.mskcc.kickoff.roslin.validator.RequestValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;

@ActiveProfiles({"test", "tango"})
@ComponentScan(basePackages = "org.mskcc.kickoff.roslin.validator")
@PropertySource("classpath:integrationtest.properties")
@Profile("test")
public class RegressionTestConfig extends AppConfiguration {

    @Value("${test.integration.roslin.manifestOutputFilePath}")
    private String manifestOutputFilePath;
    @Value("${test.integration.roslin.manifestArchivePath}")
    private String manifestArchivePath;

    @Bean
    public FileManifestGenerator fileManifestGenerator() {
        return new FileManifestGenerator();
    }

    @Bean
    public OutputFilesPrinter outputFilesPrinter() {
        return new OutputFilesPrinter();
    }

    @Override
    public OutputDirRetriever outputDirRetriever() {
        return new DefaultPathAwareOutputDirRetriever(manifestOutputFilePath, outputDirPredicate());
    }

    @Override
    public ProjectFilesArchiver projectFilesArchiver() {
        return new ProjectFilesArchiver(manifestArchivePath);
    }

    @Bean
    public FilesArchiver filesArchiver() {
        return mock(FilesArchiver.class);
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
    public FileUploader fileUploader() {
        return mock(JiraFileUploader.class);
    }
}
