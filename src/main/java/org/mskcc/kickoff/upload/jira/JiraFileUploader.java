package org.mskcc.kickoff.upload.jira;

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
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.upload.FileDeletionException;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class JiraFileUploader implements FileUploader {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final String jiraUrl;
    private final String username;
    private final String password;
    private final String projectName;
    private final JiraStateFactory jiraStateFactory;
    private JiraIssueState jiraIssueState;
    private JiraRestClient restClient;
    private String summary = "";

    @Autowired
    public JiraFileUploader(String jiraUrl, String username, String password, String projectName, JiraStateFactory
            jiraStateFactory) {
        this.jiraUrl = jiraUrl;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.jiraStateFactory = jiraStateFactory;
    }

    @Override
    public void deleteExistingFiles(KickoffRequest request) throws FileDeletionException {
        String summary = request.getId();
        Issue issue = getIssue(request.getId());

        LOGGER.info(String.format("Starting to delete existing manifest files for request: %s from issue: %s in " +
                "project: %s", request.getId(), summary, projectName));

        try {
            deleteExistingManifestAttachments(request, issue);
            validateNoAttachmentExist(request, issue);
        } catch (Exception e) {
            String error = String.format("Error while trying to delete existing manifest attachments from jira " +
                    "instance: %s for issue: %s", jiraUrl, summary);
            throw new FileDeletionException(error, e);
        }
    }

    private void validateNoAttachmentExist(KickoffRequest request, Issue issue) throws FileDeletionException {
        int numberOfManifestAttachments = getExistingManifestAttachments(request).size();

        if (numberOfManifestAttachments > 0) {
            String summary = request.getId();
            String error = String.format("%d attached manifest file (s) was/were not deleted from jira " +
                    "instance: %s for issue: %s", numberOfManifestAttachments, jiraUrl, summary);
            throw new FileDeletionException(error);
        }
    }

    @Override
    public void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile) {
        String summary = request.getId();
        Issue issue = getIssue(request.getId());

        LOGGER.info(String.format("Starting to attach file: %s for request: %s to issue: %s in project: %s",
                manifestFile
                        .getFilePath(request), request.getId(), summary, projectName));

        try {
            addAttachment(request, manifestFile, restClient, issue);
        } catch (Exception e) {
            LOGGER.error(String.format("Error while trying to attach file: %s to jira instance: %s for issue: %s",
                    manifestFile.getFilePath(request), jiraUrl, summary), e);
        }
    }

    @Override
    public void upload(KickoffRequest kickoffRequest) {
        try {
            restClient = getJiraRestClient();
            summary = kickoffRequest.getId();

            jiraIssueState = retrieveJiraIssueState(kickoffRequest);
            jiraIssueState.uploadFiles(kickoffRequest, this);
        } catch (Exception e) {
            LOGGER.error(String.format("Error while trying to attach files: to jira instance: %s for issue: %s",
                    jiraUrl, summary), e);
        } finally {
            closeJiraConnection(restClient);
        }
    }

    private JiraIssueState retrieveJiraIssueState(KickoffRequest kickoffRequest) {
        Issue issue = getIssue(kickoffRequest.getId());

        String currentState = issue.getStatus().getName();
        return jiraStateFactory.getJiraState(currentState);
    }

    void uploadFiles(KickoffRequest kickoffRequest) {
        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            if (manifestFile.isFileGenerated())
                uploadSingleFile(kickoffRequest, manifestFile);
        }
    }

    void changeStatus(String transitionName, KickoffRequest kickoffRequest) {
        Issue issue = getIssue(kickoffRequest.getId());
        String previousStatus = issue.getStatus().getName();

        Promise<Iterable<Transition>> transitionsPromise = restClient.getIssueClient().getTransitions(issue);

        Iterable<Transition> transitions = transitionsPromise.claim();

        for (Transition transition : transitions) {
            String name = transition.getName();
            if (transitionName.equalsIgnoreCase(name)) {
                Promise<Void> setGeneratedStatus = restClient.getIssueClient().transition(issue, new TransitionInput
                        (transition.getId()));
                setGeneratedStatus.claim();

                issue = getIssue(kickoffRequest.getId());
                String newStatus = issue.getStatus().getName();

                LOGGER.info(String.format("Status for issue: %s changed from: %s to: %s using transition: %s", issue
                        .getSummary(), previousStatus, newStatus, name));

                break;
            }
        }
    }

    private void closeJiraConnection(JiraRestClient restClient) {
        if (restClient != null) {
            try {
                restClient.close();
            } catch (IOException e) {
                LOGGER.warn(String.format("Unable to close connection to jira instance: %s", jiraUrl));
            }
        }
    }

    private void addAttachment(KickoffRequest request, ManifestFile manifestFile, JiraRestClient restClient, Issue
            issue) {
        File file = new File(manifestFile.getFilePath(request));
        LOGGER.info(String.format("Adding attachment: %s to issue: %s", file.getName(), issue.getSummary()));

        Promise issuePromise = restClient.getIssueClient().addAttachments(issue.getAttachmentsUri(), file);

        issuePromise.claim();

        LOGGER.info(String.format("Added attachment: %s to issue: %s", file.getName(), issue.getSummary()));
    }

    private void deleteExistingManifestAttachments(KickoffRequest request, Issue issue) {
        List<JiraIssue.Fields.Attachment> existingManifestAttachments = getExistingManifestAttachments(request);
        for (JiraIssue.Fields.Attachment existingAttachment : existingManifestAttachments)
            deleteAttachment(issue, existingAttachment);
    }

    private void deleteAttachment(Issue issue, JiraIssue.Fields.Attachment existingAttachment) {
        LOGGER.info(String.format("Deleting attachment: %s from issue: %s", existingAttachment.getFileName(),
                issue.getSummary()));
        HttpEntity<String> request = getHttpHeaders();

        new RestTemplate().exchange(existingAttachment.getUri(), HttpMethod.DELETE, request, String.class);
        LOGGER.info(String.format("Deleted attachment: %s from issue: %s", existingAttachment.getFileName(),
                issue.getSummary()));
    }

    List<JiraIssue.Fields.Attachment> getExistingManifestAttachments(KickoffRequest request) {
        Issue issue = getIssue(request.getId());
        JiraIssue jiraIssue = getJiraIssue(issue);
        List<JiraIssue.Fields.Attachment> attachments = jiraIssue.getFields().getAttachments();
        List<String> requiredFiles = getRequiredFilesNames(request);

        return attachments.stream()
                .filter(isRequiredManifestFile(requiredFiles))
                .collect(Collectors.toList());
    }

    private Predicate<JiraIssue.Fields.Attachment> isRequiredManifestFile(List<String> requiredFiles) {
        return f -> requiredFiles.contains(f.getFileName());
    }

    private List<String> getRequiredFilesNames(KickoffRequest request) {
        return ManifestFile.getRequiredFiles()
                .stream()
                .map(r -> new File(r.getFilePath(request)).getName())
                .collect(Collectors.toList());
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
        String plainCreds = String.format("%s:%s", username, password);
        byte[] plainCredsBytes = plainCreds.getBytes();
        byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        String base64Creds = new String(base64CredsBytes);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);

        return new HttpEntity<>(headers);
    }

    private JiraRestClient getJiraRestClient() throws URISyntaxException {
        LOGGER.info(String.format("Using jira instance: %s", jiraUrl));

        URI jiraServerUri = new URI(jiraUrl);

        return new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication
                (jiraServerUri, username, password);
    }

    private Issue getIssue(String summary) {
        Iterable<Issue> issues = getIssues(summary, restClient);
        validateIssueExists(summary, issues);

        Issue foundIssue = Iterables.getFirst(issues, null);
        Promise<Issue> issuePromise = restClient.getIssueClient().getIssue(foundIssue.getKey());
        Issue issue = issuePromise.claim();

        return issue;
    }

    private Iterable<Issue> getIssues(String summary, JiraRestClient restClient) {
        SearchRestClient searchClient = restClient.getSearchClient();
        String jql = String.format("project" +
                " = \"%s\" AND summary ~ \"%s\"", projectName, summary);
        Promise<SearchResult> searchResultPromise = searchClient.searchJql(jql);

        SearchResult searchResult = searchResultPromise.claim();

        Iterable<Issue> issues = searchResult.getIssues();

        return issues;
    }

    private void validateIssueExists(String summary, Iterable<Issue> issues) {
        if (Iterables.size(issues) == 0)
            throw new RuntimeException(String.format("No jira issues found for project: %s and summary: %s. No " +
                    "files will be uploaded.", projectName, summary));
        if (Iterables.size(issues) > 1)
            throw new RuntimeException(String.format("Multiple jira issues found for project: %s and summary: %s." +
                    " No files will be uploaded.", projectName, summary));

        LOGGER.info(String.format("Found issue with summary: %s in project: %s", summary, projectName));
    }

    public JiraIssueState getJiraIssueState() {
        return jiraIssueState;
    }

    public void setJiraIssueState(JiraIssueState jiraIssueState) {
        this.jiraIssueState = jiraIssueState;
    }
}