package org.mskcc.kickoff.fast.endtoend;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.FileManifestGenerator;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.DataClinicalFilePrinter;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.printer.FilePrinter;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

@ComponentScan(basePackages = "org.mskcc.kickoff")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ManifestFilesGeneratorTestConfiguration.class)
@ActiveProfiles({"test", "tango"})
@PropertySource("classpath:application-dev.properties")
public class ManifestFilesGeneratorTest {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final String projectId = "04919_G";
    private JiraRestClient restClient;

    @Autowired
    private FileManifestGenerator fileManifestGenerator;

    @Value("${jira.roslin.generated.transition}")
    private String generatedTransition;

    @Value("${jira.roslin.hold.transition}")
    private String holdTransition;

    @Value("${jira.roslin.regenerated.transition}")
    private String regeneratedTransition;

    @Value("${jira.roslin.fastqs.available.status}")
    private String fastqsAvailableStatus;

    @Value("${jira.roslin.input.regeneration.status}")
    private String regenerateStatus;

    @Value("${jira.roslin.input.generated.status}")
    private String filesGeneratedStatus;

    @Value("${jira.roslin.bad.inputs.status}")
    private String badInputsStatus;

    @Autowired
    private MappingFilePrinter mappingFilePrinter;

    @Autowired
    private DataClinicalFilePrinter dataClinicalFilePrinter;

    @Autowired
    private ErrorRepository errorRepository;

    @Autowired
    private ManifestFilesGeneratorTestConfiguration.MockJiraFileUploader fileUploader;
    private String initialTransitionName = "Fastqs Available";
    private String regenerateTransition = "Regenerate Inputs";

    @Rule
    public JiraResource jiraRestConnection = new JiraResource();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        restClient = jiraRestConnection.getJiraRestClient();
        assertNoAttachmentAttached();
        clearFileGeneratedStatus();
        fileUploader.setThrowExceptionOnDelete(false);
        ManifestFile.MAPPING.setFilePrinter(mappingFilePrinter);
        ManifestFile.CLINICAL.setFilePrinter(dataClinicalFilePrinter);
    }

    private void clearFileGeneratedStatus() {
        for (ManifestFile manifestFile : ManifestFile.values()) {
            manifestFile.setFileGenerated(false);
        }
    }

    private void assertNoAttachmentAttached() {
        assertThat(jiraRestConnection.getAllAttachments(projectId).size(), is(0));
    }

    @After
    public void tearDown() throws Exception {
        LOGGER.info("Tearing down....");
        deleteAttachments();
        jiraRestConnection.setJiraStatus(projectId, initialTransitionName);
        for (ManifestFile manifestFile : ManifestFile.values()) {
            manifestFile.getGenerationErrors().clear();
        }
        Utils.setExitLater(false);
    }


    private void deleteAttachments() {
        LOGGER.info("Deleting created attachments.");
        for (JiraIssue.Fields.Attachment attachment : jiraRestConnection.getAllAttachments(projectId)) {
            jiraRestConnection.deleteAttachment(projectId, attachment);
        }
    }

    @Test
    public void whenFilesAreGenerated_shouldUploadAllOfThemToJira() throws Exception {
        //given

        //when
        fileManifestGenerator.generate(projectId);

        //then
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile
                .PAIRING, ManifestFile.REQUEST, ManifestFile.CLINICAL));
        assertJiraStatus(filesGeneratedStatus);
    }

    @Test
    public void
    whenFilesAreRegeneratedWithoutErrors_shouldDeleteOldOnesAndUploadAllNewToJiraAndSetInputsGeneratedStatus() throws
            Exception {
        //given
        List<JiraIssue.Fields.Attachment> allAttachmentsRun1 = uploadFiles();
        //when
        fileManifestGenerator.generate(projectId);
        //then
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.GROUPING, ManifestFile.PAIRING, ManifestFile
                .REQUEST, ManifestFile.MAPPING, ManifestFile.CLINICAL));
        assertJiraStatus(filesGeneratedStatus);
        assertAttachmentsAreDifferent(allAttachmentsRun1);
    }

    @Test
    public void whenFilesAreRegeneratedWithErrors_shouldDeleteOldOnesAndUploadAllNewToJiraAndSetBadInputsStatus()
            throws Exception {
        //given
        List<JiraIssue.Fields.Attachment> allAttachmentsRun1 = uploadFiles();

        //when
        ManifestFile.MAPPING.setFilePrinter(getNotPrintingMappingFilePrinter());
        fileManifestGenerator.generate(projectId);

        //then
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.GROUPING, ManifestFile.PAIRING, ManifestFile.REQUEST, ManifestFile.CLINICAL));
        assertJiraStatus(badInputsStatus);

        assertAttachmentsAreDifferent(allAttachmentsRun1);
    }

    private List<JiraIssue.Fields.Attachment> uploadFiles() throws Exception {
        fileManifestGenerator.generate(projectId);
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile.PAIRING, ManifestFile.REQUEST, ManifestFile.CLINICAL));
        assertJiraStatus(filesGeneratedStatus);

        List<JiraIssue.Fields.Attachment> allAttachmentsRun1 = jiraRestConnection.getAllAttachments(projectId);
        clearFileGeneratedStatus();
        setRegenerateStatus();
        return allAttachmentsRun1;
    }

    private void setRegenerateStatus() {
        jiraRestConnection.setJiraStatus(projectId, holdTransition);
        jiraRestConnection.setJiraStatus(projectId, regenerateTransition);
    }

    @Test
    public void whenFilesFailToDelete_shouldNotUploadAnyNewFiles() throws Exception {

        //given
        List<JiraIssue.Fields.Attachment> allAttachmentsRun1 = uploadFiles();

        //when
        ManifestFile.MAPPING.setFilePrinter(getNotPrintingMappingFilePrinter());
        fileUploader.setThrowExceptionOnDelete(true);
        fileManifestGenerator.generate(projectId);

        //then
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile.PAIRING, ManifestFile.REQUEST, ManifestFile.CLINICAL));

        assertJiraStatus(regenerateStatus);

        assertAttachmentsAreNotChanged(allAttachmentsRun1);
    }

    @Test
    public void whenNoMappingFileIsGenerated_shouldTransitionsToBadInputsStatus() throws Exception {
        //given
        ManifestFile.MAPPING.setFilePrinter(getNotPrintingMappingFilePrinter());

        //when
        fileManifestGenerator.generate(projectId);

        //then
        assertJiraStatus(badInputsStatus);
    }

    @Test
    public void whenNoClinicalFileIsGenerated_shouldTransitionsToBadInputsStatus() throws Exception {
        //given
        ManifestFile.CLINICAL.setFilePrinter(getNotPrintingClinicalFilePrinter());

        //when
        fileManifestGenerator.generate(projectId);

        //then
        assertJiraStatus(badInputsStatus);
    }

    @Test
    public void whenGeneratedFilesContainErrors_shouldTransitionsToBadInputsStatus() throws Exception {
        //given
        ManifestFile.REQUEST.addGenerationError(new GenerationError("terrible errVor", ErrorCode.UNMATCHED_NORMAL));

        //when
        fileManifestGenerator.generate(projectId);

        //then
        assertJiraStatus(badInputsStatus);
    }

    @Test
    public void whenPairingFileHasError_shouldSetStatusBadInputs() throws Exception {
        //given
        ManifestFile.PAIRING.addGenerationError(new GenerationError("some error", ErrorCode.UNMATCHED_NORMAL));

        //when
        fileManifestGenerator.generate(projectId);

        //then
        assertJiraStatus(badInputsStatus);
    }

    @Test
    public void whenRequestRetrievalHasError_shouldSetStatusBadInputs() throws Exception {
        // when
        fileUploader.upload(projectId, null);

        // then
        assertJiraStatus(badInputsStatus);
    }
    
    private void assertAttachmentsAreNotChanged(List<JiraIssue.Fields.Attachment> allAttachmentsRun1) {
        List<JiraIssue.Fields.Attachment> allAttachmentsRun2 = jiraRestConnection.getAllAttachments(projectId);

        for (JiraIssue.Fields.Attachment attachment : allAttachmentsRun2) {
            assertTrue(allAttachmentsRun1.contains(attachment));
        }
    }

    private void assertAttachmentsAreDifferent(List<JiraIssue.Fields.Attachment> allAttachmentsRun1) {
        List<JiraIssue.Fields.Attachment> allAttachmentsRun2 = jiraRestConnection.getAllAttachments(projectId);

        for (JiraIssue.Fields.Attachment attachment : allAttachmentsRun2) {
            assertFalse(allAttachmentsRun1.contains(attachment));
        }
    }

    private FilePrinter getNotPrintingMappingFilePrinter() {
        MappingFilePrinter mappingFilePrinter = mock(MappingFilePrinter.class);
        Mockito.doCallRealMethod().when(mappingFilePrinter).getFilePath(any());
        Mockito.doReturn(false).when(mappingFilePrinter).shouldPrint(any());

        return mappingFilePrinter;
    }

    private FilePrinter getNotPrintingClinicalFilePrinter() {
        DataClinicalFilePrinter dataClinicalFilePrinter = mock(DataClinicalFilePrinter.class);
        Mockito.doCallRealMethod().when(dataClinicalFilePrinter).getFilePath(any());
        Mockito.doReturn(false).when(dataClinicalFilePrinter).shouldPrint(any());

        return dataClinicalFilePrinter;
    }

    private void assertFilesUploadedToJira(String projectId, List<ManifestFile> expectedFiles) throws Exception {
        List<JiraIssue.Fields.Attachment> allAttachments = jiraRestConnection.getAllAttachments(projectId);
        assertThat(allAttachments.size(), is(expectedFiles.size()));
        KickoffRequest request = new KickoffRequest(projectId, mock(ProcessingType.class));
        for (ManifestFile generatedFile : expectedFiles) {
            String generatedFileName = new File(generatedFile.getFilePath(request)).getName();

            assertTrue(allAttachments.stream()
                    .anyMatch(a -> a.getFileName().equals(generatedFileName)));
        }
    }

    private void assertJiraStatus(String status) {
        Issue issue = jiraRestConnection.getIssue(projectId);
        assertThat(issue.getStatus().getName(), is(status));
    }

}
