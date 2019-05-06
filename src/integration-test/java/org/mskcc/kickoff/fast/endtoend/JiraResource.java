package org.mskcc.kickoff.fast.endtoend;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import com.google.common.collect.Iterables;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.junit.rules.ExternalResource;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.util.Constants;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

/**
 * @author liuf
 * @project pipeline-kickoff
 */
public class JiraResource extends ExternalResource {

    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private String jiraUrl;
    private String jiraUsername;
    private String jiraPassword;
    private String jiraRoslinProjectName;
    private JiraRestClient restClient;

    public JiraResource() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream("src/main/resources/application-dev.properties"));
            jiraUrl = prop.getProperty("jira.url");
            jiraUsername = prop.getProperty("jira.username");
            jiraPassword = prop.getProperty("jira.password");
            jiraRoslinProjectName = prop.getProperty("jira.roslin.project.name");
        } catch (IOException e) {
            throw new RuntimeException("File not found: " + e.getMessage());
        }
    }

    @Override
    protected void before() throws Throwable {
        try {
            URI jiraRerverUri = new URI(jiraUrl);
            restClient = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
                    jiraRerverUri, jiraUsername, jiraPassword
            );
        }catch (URISyntaxException e) {
            throw new RuntimeException("Invalid uri: " + jiraUrl);
        }
    }

    @Override
    protected void after() {
        if (restClient != null) {
            try {
                restClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public JiraRestClient getJiraRestClient() {
        return restClient;
    }

    public JiraIssue getJiraIssue(Issue issue) {
        String getAttachmentsUrl = String.format("%s/rest/api/2/issue/%s?fields=attachment", jiraUrl, issue
                .getKey());

        HttpEntity<String> request = getHttpHeaders();
        ResponseEntity<JiraIssue> response = new RestTemplate().exchange(getAttachmentsUrl, HttpMethod.GET, request,
                JiraIssue.class);
        return response.getBody();
    }

    public List<JiraIssue.Fields.Attachment> getAllAttachments(String projectId) {
        Issue issue = getIssue(projectId);
        JiraIssue jiraIssue = getJiraIssue(issue);
        return jiraIssue.getFields().getAttachments();
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

    public void setJiraStatus(String projectId, String status) {
        Issue issue = getIssue(projectId);
        Promise<Iterable<Transition>> transitionsPromise = restClient.getIssueClient().getTransitions(issue);

        Iterable<Transition> transitions = transitionsPromise.claim();

        for (Transition transition : transitions) {
            String name = transition.getName();
            if (status.equalsIgnoreCase(name)) {
                Promise<Void> setGeneratedStatus = restClient.getIssueClient().transition(issue, new TransitionInput(transition.getId()));
                setGeneratedStatus.claim();
                LOGGER.info(String.format("Status for issue: %s changed to: %s", issue.getSummary(), name));
                break;
            }
        }
    }

    public Issue getIssue(String summary) {
        Iterable<Issue> issues = getIssues(summary);
        return Iterables.getFirst(issues, null);
    }

    private Iterable<Issue> getIssues(String summary) {
        SearchRestClient searchClient = restClient.getSearchClient();
        String jql = String.format("project" +
                " = \"%s\" AND summary ~ \"%s\"", jiraRoslinProjectName, summary);

        LOGGER.info(String.format("Getting jira issues using url %s", jql));
        Promise<SearchResult> searchResultPromise = searchClient.searchJql(jql);

        SearchResult searchResult = searchResultPromise.claim();

        Iterable<Issue> issues = searchResult.getIssues();

        return issues;
    }


    public void deleteAttachment(String projectId, JiraIssue.Fields.Attachment existingAttachment) {
        LOGGER.info(String.format("Deleting attachment: %s", existingAttachment.getUri()));
        HttpEntity<String> request = getHttpHeaders();
        new RestTemplate().exchange(existingAttachment.getUri(), HttpMethod.DELETE, request, String.class);
    }
}
