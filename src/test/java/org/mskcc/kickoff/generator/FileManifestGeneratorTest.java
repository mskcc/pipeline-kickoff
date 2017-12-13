package org.mskcc.kickoff.generator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.printer.observer.SpyFileUploader;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@ComponentScan(basePackages = "org.mskcc.kickoff")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestAppConfiguration.class)
@ActiveProfiles("test")
@PropertySource("classpath:application-dev.properties")
public class FileManifestGeneratorTest {
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
    private SpyFileUploader fileUploader;

    @Test
    public void when_should() throws Exception {
        String projectId = "12345_T";
        KickoffRequest request = new KickoffRequest(projectId, mock(ProcessingType.class));
        when(requestProxy.getRequest(projectId)).thenReturn(request);
        String outputDir = "";
        when(outputDirRetriever.retrieve(any(), any())).thenReturn(outputDir);
        Arguments.outdir = "outdir";

        fileManifestGenerator.generate(projectId);

        assertThat(fileUploader.getRequestsWithDeletedFiles().size(), is(1));
        verify(projectNameValidator, times(1)).validate(projectId);
        verify(outputDirRetriever, times(1)).retrieve(projectId, Arguments.outdir);
        verify(logConfigurator, times(1)).configureProjectLog(outputDir);
        verify(requestProxy, times(1)).getRequest(projectId);
        verify(requestValidator, times(1)).validate(request);
        verify(outputFilesPrinter, times(1)).print(request);
        verify(filesArchiver, times(1)).archive(request);
    }
}