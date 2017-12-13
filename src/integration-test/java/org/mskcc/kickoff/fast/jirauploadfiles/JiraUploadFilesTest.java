package org.mskcc.kickoff.fast.jirauploadfiles;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import com.google.common.collect.Iterables;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.FileManifestGenerator;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.FilePrinter;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.upload.Attachment;
import org.mskcc.kickoff.upload.JiraIssue;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;

@ComponentScan(basePackages = "org.mskcc.kickoff")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = JiraTestConfiguration.class)
@ActiveProfiles({"test", "igo"})
@PropertySource("classpath:application-dev.properties")
public class JiraUploadFilesTest {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final String projectId = "04919_G";
    private JiraRestClient restClient;
    @Autowired
    private FileManifestGenerator fileManifestGenerator;
    @Value("${jira.url}")
    private String jiraUrl;
    @Value("${jira.username}")
    private String jiraUsername;
    @Value("${jira.password}")
    private String jiraPassword;
    @Value("${jira.roslin.project.name}")
    private String jiraRoslinProjectName;
    private Issue issue;

    @Autowired
    private MappingFilePrinter mappingFilePrinter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        restClient = getJiraRestClient();
        issue = getIssue(projectId, restClient);

        assertNoAttachmentAttached();
    }

    private void assertNoAttachmentAttached() {
        assertThat(getAllAttachments().size(), is(0));
    }

    @After
    public void tearDown() throws Exception {
        deleteAttachments();
        closeJiraConnection(restClient);
    }

    private Issue getIssue(String summary, JiraRestClient restClient) {
        Iterable<Issue> issues = getIssues(summary, restClient);
        return Iterables.getFirst(issues, null);
    }

    private Iterable<Issue> getIssues(String summary, JiraRestClient restClient) {
        SearchRestClient searchClient = restClient.getSearchClient();
        String jql = String.format("project" +
                " = \"%s\" AND summary ~ %s", jiraRoslinProjectName, summary);
        Promise<SearchResult> searchResultPromise = searchClient.searchJql(jql);

        SearchResult searchResult = searchResultPromise.claim();

        Iterable<Issue> issues = searchResult.getIssues();

        return issues;
    }

    private void deleteAttachments() {
        LOGGER.info("Deleting created attachments.");

        for (Attachment attachment : getAllAttachments()) {
            deleteAttachment(attachment);
        }
    }

    private void deleteAttachment(Attachment existingAttachment) {
        LOGGER.info(String.format("Deleting attachment: %s", existingAttachment.getUri()));

        HttpEntity<String> request = getHttpHeaders();
        new RestTemplate().exchange(existingAttachment.getUri(), HttpMethod.DELETE, request, String.class);
    }

    @Test
    public void whenFilesAreGenerated_shouldUploadAllOfThemToJira() throws Exception {
        //given

        //when
        fileManifestGenerator.generate(projectId);

        //then
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile
                .PAIRING, ManifestFile.REQUEST));
    }

    @Test
    public void whenFilesAreRegenerated_shouldDeleteOldOnesAndUploadAllNewToJira() throws Exception {
        //given
        fileManifestGenerator.generate(projectId);
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.MAPPING, ManifestFile.GROUPING, ManifestFile
                .PAIRING, ManifestFile.REQUEST));

        //when
        ManifestFile.MAPPING.setFilePrinter(getNotPrintingMappingFilePrinter());
        fileManifestGenerator.generate(projectId);

        //then
        assertFilesUploadedToJira(projectId, Arrays.asList(ManifestFile.GROUPING, ManifestFile.PAIRING, ManifestFile
                .REQUEST));
    }

    private FilePrinter getNotPrintingMappingFilePrinter() {
        MappingFilePrinter mappingFilePrinter = mock(MappingFilePrinter.class);
        Mockito.doCallRealMethod().when(mappingFilePrinter).getFilePath(any());
        Mockito.doReturn(false).when(mappingFilePrinter).shouldPrint(any());

        return mappingFilePrinter;
    }

    private void assertFilesUploadedToJira(String projectId, List<ManifestFile> expectedFiles) throws Exception {
        List<Attachment> allAttachments = getAllAttachments();
        assertThat(allAttachments.size(), is(expectedFiles.size()));
        KickoffRequest request = new KickoffRequest(projectId, mock(ProcessingType.class));
        for (ManifestFile generatedFile : expectedFiles) {
            String generatedFileName = new File(generatedFile.getFilePath(request)).getName();

            assertTrue(allAttachments.stream()
                    .anyMatch(a -> a.getFileName().equals(generatedFileName)));
        }
    }

    private JiraRestClient getJiraRestClient() throws URISyntaxException {
        URI jiraServerUri = new URI(jiraUrl);

        return new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication
                (jiraServerUri, jiraUsername, jiraPassword);
    }

    private List<Attachment> getAllAttachments() {
        JiraIssue jiraIssue = getJiraIssue(issue);
        return jiraIssue.getFields().getAttachments();
    }

    private JiraIssue getJiraIssue(Issue issue) {
        String getAttachmentsUrl = String.format("%s/rest/api/2/issue/%s?fields=attachment", jiraUrl, issue
                .getKey());

        HttpEntity<String> request = getHttpHeaders();
        ResponseEntity<JiraIssue> response = new RestTemplate().exchange(getAttachmentsUrl, HttpMethod.GET, request,
                JiraIssue.class);
        return response.getBody();
    }

    private HttpEntity<String> getHttpHeaders() {
        String plainCreds = String.format("%s:%s", jiraUsername, jiraPassword);
        byte[] plainCredsBytes = plainCreds.getBytes();
        byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        String base64Creds = new String(base64CredsBytes);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);

        return new HttpEntity<>(headers);
    }

    private void closeJiraConnection(JiraRestClient restClient) throws IOException {
        if (restClient != null) {
            restClient.close();
        }
    }
}
