package org.mskcc.kickoff.generator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.archive.RunPipelineLogger;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.NotificationFormatter;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.printer.observer.SpyFileUploader;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.InMemoryErrorRepository;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@ComponentScan(basePackages = "org.mskcc.kickoff")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = FileManifestGeneratorTest.TestAppConfiguration.class)
@ActiveProfiles("test")
@PropertySource("classpath:application-dev.properties")
public class FileManifestGeneratorTest {
    private final String projectId = "12345_T";
    private final String outputDir = "";
    @Autowired
    private FileManifestGenerator fileManifestGenerator;
    @Autowired
    private OutputFilesPrinter outputFilesPrinter;
    @Autowired
    private FilesArchiver filesArchiver;
    @Autowired
    private RequestValidator requestValidator;
    @Autowired
    private LogConfigurator logConfigurator;
    @Autowired
    private OutputDirRetriever outputDirRetriever;
    @Autowired
    private RequestProxy requestProxy;
    @Autowired
    private ProjectNameValidator projectNameValidator;
    @Autowired
    private EmailNotificator emailNotificator;
    @Autowired
    @Qualifier("singleSlash")
    private NotificationFormatter notificationFormatter;
    @Autowired
    private SpyFileUploader fileUploader;
    private KickoffRequest request;

    @Before
    public void setUp() throws Exception {
        reset(emailNotificator);
        reset(projectNameValidator);
        reset(outputDirRetriever);
        reset(logConfigurator);
        reset(requestProxy);
        reset(requestValidator);
        reset(outputFilesPrinter);
        reset(filesArchiver);
        request = new KickoffRequest(projectId, mock(ProcessingType.class));
        when(requestProxy.getRequest(projectId)).thenReturn(request);
        when(outputDirRetriever.retrieve(any(), any())).thenReturn(outputDir);
        Arguments.outdir = "outdir";
    }

    @Test
    public void whenInvokeGenerateFiles_shouldValidatePrintArchiveAndUploadFiles() throws Exception {
        //when
        fileManifestGenerator.generate(projectId);

        //then
        verify(projectNameValidator, times(1)).validate(projectId);
        verify(outputDirRetriever, times(1)).retrieve(projectId, Arguments.outdir);
        verify(logConfigurator, times(1)).configureProjectLog(outputDir);
        verify(requestProxy, times(1)).getRequest(projectId);
        verify(requestValidator, times(1)).validate(request);
        verify(outputFilesPrinter, times(1)).print(request);
        verify(filesArchiver, times(1)).archive(request);
    }

    @Test
    public void whenAllRequiredFilesGenerated_shouldNotSendNotification() throws Exception {
        //given
        ManifestFile.setRequiredFiles(Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile
                .REQUEST, ManifestFile.PAIRING));
        ManifestFile.MAPPING.setFileGenerated(true);
        ManifestFile.GROUPING.setFileGenerated(true);
        ManifestFile.REQUEST.setFileGenerated(true);
        ManifestFile.PAIRING.setFileGenerated(true);

        //when
        fileManifestGenerator.generate(projectId);

        //then
        verify(emailNotificator, never()).notifyMessage(eq(projectId), any());
    }

    @Test
    public void whenOneRequiredFileNotGenerated_shouldSendNotification() throws Exception {

        //given
        ManifestFile.setRequiredFiles(Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile
                .REQUEST, ManifestFile.PAIRING));
        ManifestFile.MAPPING.setFileGenerated(false);
        ManifestFile.GROUPING.setFileGenerated(true);
        ManifestFile.REQUEST.setFileGenerated(true);
        ManifestFile.PAIRING.setFileGenerated(true);

        //when
        fileManifestGenerator.generate(projectId);

        //then
        verify(emailNotificator, times(1)).notifyMessage(projectId, notificationFormatter.format());
    }

    @Test
    public void whenAllRequiredFilesNotGenerated_shouldSendNotification() throws Exception {
        //given
        ManifestFile.setRequiredFiles(Arrays.asList(ManifestFile.MAPPING, ManifestFile.REQUEST));
        ManifestFile.MAPPING.setFileGenerated(false);
        ManifestFile.REQUEST.setFileGenerated(false);

        //when
        fileManifestGenerator.generate(projectId);

        //then
        verify(emailNotificator, times(1)).notifyMessage(eq(projectId), any());
    }

    @Configuration
    public static class TestAppConfiguration {
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
        public NotificationFormatter notificationFormatter() {
            return () -> "Errors";
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

        @Bean
        public ErrorRepository errorRepository() {
            return new InMemoryErrorRepository();
        }
    }
}
