package org.mskcc.kickoff.generator;

import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.archive.RunPipelineLogger;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.printer.observer.SpyFileUploader;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

@Configuration
public class TestAppConfiguration {
    @Bean
    public OutputFilesPrinter outputFilesPrinter() {
        return mock(OutputFilesPrinter.class);
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
    public OutputDirRetriever outputDirRetriever() {
        return mock(OutputDirRetriever.class);

    }

    @Bean
    public RequestProxy requestProxy() {
        return mock(RequestProxy.class);

    }

    @Bean
    public ProjectNameValidator projectNameValidator() {
        return mock(ProjectNameValidator.class);

    }

    @Bean
    public EmailNotificator emailNotificator() {
        return mock(EmailNotificator.class);
    }

    @Bean
    public SpyFileUploader spyFileUploader() {
        return new SpyFileUploader();
    }

    @Bean
    public RunPipelineLogger runPipelineLogger() {
        return new RunPipelineLogger();
    }

    @Bean
    public FileManifestGenerator fileManifestGenerator() {
        return new FileManifestGenerator();
    }

    @Bean
    public ProjectFilesArchiver projectFilesArchiver() {
        return mock(ProjectFilesArchiver.class);
    }
}
