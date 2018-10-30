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
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.NotificationFormatter;
import org.mskcc.kickoff.upload.FileDeletionException;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.upload.jira.domain.JiraUser;
import org.mskcc.kickoff.upload.jira.state.IssueStatus;
import org.mskcc.kickoff.upload.jira.state.StatusFactory;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

@Component
public class JiraFileUploader implements FileUploader {
    public static final String ERRORS_HEADER_KEY = "errors";
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    @Value("${jira.url}")
    private String jiraUrl;
    @Value("${jira.username}")
    private String username;
    @Value("${jira.password}")
    private String password;
    @Value("${jira.roslin.project.name}")
    private String projectName;
    @Value("${jira.rest.path}")
    private String jiraRestPath;
    @Autowired
    private StatusFactory statusFactory;
    @Autowired
    private PmJiraUserRetriever pmJiraUserRetriever;

    @Autowired
    private ErrorRepository errorRepository;

    @Autowired
    @Qualifier("jiraRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    @Qualifier("doubleSlash")
    private NotificationFormatter notificationFormatter;

    private IssueStatus issueStatus;
    private JiraRestClient restClient;

    @Override
    public void deleteExistingFiles(KickoffRequest request) throws FileDeletionException {
        String summary = request.getId();
        Issue issue = getIssue(summary);

        LOGGER.info(String.format("Starting to delete existing manifest files for request: %s from issue: %s in " +
                "project: %s", request.getId(), summary, projectName));

        try {
            deleteExistingManifestAttachments(request, issue, summary);
            validateNoAttachmentExist(request, summary);
        } catch (Exception e) {
            String error = String.format("Error while trying to delete existing manifest attachments from jira " +
                    "instance: %s for issue: %s", jiraUrl, summary);
            throw new FileDeletionException(error, e);
        }
    }

    private void validateNoAttachmentExist(KickoffRequest request, String requestId) throws FileDeletionException {
        int numberOfManifestAttachments = getExistingManifestAttachments(request, requestId).size();

        if (numberOfManifestAttachments > 0) {
            throw new FileDeletionException(String.format("%d attached manifest file (s) was/were not deleted from " +
                    "jira " +
                    "instance: %s for issue: %s", numberOfManifestAttachments, jiraUrl, requestId));
        }
    }

    @Override
    public void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile, String requestId) {
        Issue issue = getIssue(requestId);

        LOGGER.info(String.format("Starting to attach file: %s for request: %s to issue: %s in project: %s",
                manifestFile
                        .getFilePath(request), request.getId(), requestId, projectName));

        try {
            addAttachment(request, manifestFile, restClient, issue);
        } catch (Exception e) {
            LOGGER.error(String.format("Error while trying to attach file: %s to jira instance: %s for issue: %s",
                    manifestFile.getFilePath(request), jiraUrl, requestId), e);
        }
    }

    @Override
    public void upload(KickoffRequest kickoffRequest) {
        String summary = kickoffRequest.getId();

        try {
            restClient = getJiraRestClient();
            issueStatus = retrieveJiraIssueState(summary);
            issueStatus.uploadFiles(kickoffRequest, this, summary);
            issueStatus.validateInputs(summary, this);
            addInfoComment(summary);
        } catch (Exception e) {
            LOGGER.error(String.format("Error while trying to attach files: to jira instance: %s for issue: %s",
                    jiraUrl, summary), e);
        } finally {
            closeJiraConnection(restClient);
        }
    }

    private void addInfoComment(String summary) {
        String fileCreationTimeComment = String.format("Manifest files time creation: %s",
                LocalDateTime.now());

        String errorComment = notificationFormatter.format();

        String comment = getFormattedComment(String.format("%s\\n\\n%s", fileCreationTimeComment, errorComment));

        addComment(getIssue(summary), comment);
    }

    @Override
    public void assignUser(KickoffRequest kickoffRequest, String requestId) {
        JiraUser pmJiraUser = getPmJiraUser(kickoffRequest.getProjectInfo().get(Constants.ProjectInfo.PROJECT_MANAGER));
        Issue issue = getIssue(requestId);

        addAssignee(pmJiraUser, issue);
        addWatcher(pmJiraUser, issue);
    }

    private void addAssignee(JiraUser pmJiraUser, Issue issue) {
        LOGGER.info(String.format("Assigning jira user %s to issue %s", pmJiraUser.getKey(), issue.getSummary()));

        HttpEntity<JiraUser> jiraIssueHttpEntity = new HttpEntity<>(pmJiraUser);

        try {
            ResponseEntity<JiraIssue> jiraIssueResponseEntity = restTemplate.exchange(String.format
                            ("%s/%s/issue/%s/assignee", jiraUrl, jiraRestPath, issue.getKey()), HttpMethod.PUT,
                    jiraIssueHttpEntity, JiraIssue.class);

            logAddAssigneeStatus(pmJiraUser, issue, jiraIssueResponseEntity);
        } catch (Exception e) {
            throw new JiraIssueAssignmentException(String.format("Unable to assign jira user %s to issue %s. This " +
                            "user may not exist.",
                    pmJiraUser.getKey(), issue.getSummary()), e);
        }
    }

    private void logAddAssigneeStatus(JiraUser pmJiraUser, Issue issue, ResponseEntity<JiraIssue>
            jiraIssueResponseEntity) {
        if (jiraIssueResponseEntity.getStatusCode().is2xxSuccessful())
            LOGGER.info(String.format(String.format("Successfully assigned jira user %s to issue %s", pmJiraUser
                    .getKey(), issue.getSummary())));
        else {
            throw new JiraIssueAssignmentException(String.format("Unable to assign jira user %s to issue %s. Reason: " +
                    "%s", pmJiraUser.getKey(), issue.getSummary(), jiraIssueResponseEntity.getHeaders().get
                    (ERRORS_HEADER_KEY)));
        }
    }

    private void addWatcher(JiraUser pmJiraUser, Issue issue) {
        LOGGER.info(String.format("Adding user %s as watcher to issue %s", pmJiraUser, issue.getSummary()));
        String watchersUrl = String.format("%s/%s/issue/%s/watchers", jiraUrl, jiraRestPath, issue.getKey());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        HttpEntity<String> entity = new HttpEntity<>(String.format("\"%s\"", "whateva"), headers);

        try {
            ResponseEntity<String> watchersResponse = restTemplate.postForEntity(watchersUrl, entity, String.class);
            processAddWatcherStatus(pmJiraUser, issue, watchersResponse);
        } catch (HttpClientErrorException e) {
            throw new JiraIssueAssignmentException(String.format("Unable to add user '%s' as a watcher to issue %s. " +
                    "This user may not exist.", pmJiraUser, issue.getSummary()), e);
        }
    }

    private void processAddWatcherStatus(JiraUser pmJiraUser, Issue issue, ResponseEntity<String> watchersResponse) {
        if (watchersResponse.getStatusCode().is2xxSuccessful())
            LOGGER.info(String.format(String.format("Successfully added user %s as watcher to issue %s", pmJiraUser,
                    issue.getSummary())));
        else {
            throw new JiraIssueAssignmentException(String.format("Unable to add user user %s as a watcher to issue %s",
                    pmJiraUser, issue.getSummary()));
        }
    }

    private JiraUser getPmJiraUser(String projectManagerIgoName) {
        return pmJiraUserRetriever.retrieve(projectManagerIgoName);
    }

    private IssueStatus retrieveJiraIssueState(String requestId) {
        Issue issue = getIssue(requestId);

        String currentState = issue.getStatus().getName();
        return statusFactory.getStatus(currentState);
    }

    public void uploadFiles(KickoffRequest kickoffRequest, String requestId) {
        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            if (manifestFile.isFileGenerated())
                uploadSingleFile(kickoffRequest, manifestFile, requestId);
        }
    }

    public void changeStatus(String transitionName, String issueId) {
        Issue issue = getIssue(issueId);
        String previousStatus = issue.getStatus().getName();

        Promise<Iterable<Transition>> transitionsPromise = restClient.getIssueClient().getTransitions(issue);
        Iterable<Transition> transitions = transitionsPromise.claim();

        for (Transition transition : transitions) {
            String name = transition.getName();
            if (transitionName.equalsIgnoreCase(name)) {
                Promise<Void> setGeneratedStatus = restClient.getIssueClient().transition(issue, new TransitionInput
                        (transition.getId()));
                setGeneratedStatus.claim();

                issue = getIssue(issueId);
                String newStatus = issue.getStatus().getName();

                LOGGER.info(String.format("Status for issue: %s changed from: \"%s\" to: \"%s\" using transition: " +
                        "%s", issue
                        .getSummary(), previousStatus, newStatus, name));

                return;
            }
        }

        throw new NoTransitionFoundException(String.format("No transition found with name %s for issue %s for current" +
                        " status: %s",
                transitionName, issue.getSummary(), previousStatus));
    }

    private void addComment(Issue issue, String comment) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        HttpEntity<String> entity = new HttpEntity<>(comment, headers);

        try {
            ResponseEntity<String> addCommentResponse = restTemplate.postForEntity(issue.getCommentsUri(), entity,
                    String.class);

            logIfNotSucceeded(issue, addCommentResponse);
        } catch (HttpClientErrorException e) {
            LOGGER.warn(String.format("Comment not added to issue %s", issue.getSummary()), e);
        }
    }

    private void logIfNotSucceeded(Issue issue, ResponseEntity<String> addCommentResponse) {
        if (addCommentResponse.getStatusCode().is2xxSuccessful())
            LOGGER.warn(String.format("Comment not added to issue %s. Cause: %s %s", issue.getSummary(),
                    addCommentResponse.getStatusCode(),
                    addCommentResponse.getHeaders().getOrDefault(ERRORS_HEADER_KEY, emptyList())));
    }

    private String getFormattedComment(String comment) {
        return String.format("{\"body\": \"%s\"}", comment);
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

    private void deleteExistingManifestAttachments(KickoffRequest request, Issue issue, String requestId) {
        List<JiraIssue.Fields.Attachment> existingManifestAttachments = getExistingManifestAttachments(request,
                requestId);
        for (JiraIssue.Fields.Attachment existingAttachment : existingManifestAttachments)
            deleteAttachment(issue, existingAttachment);
    }

    private void deleteAttachment(Issue issue, JiraIssue.Fields.Attachment existingAttachment) {
        LOGGER.info(String.format("Deleting attachment: %s from issue: %s", existingAttachment.getFileName(),
                issue.getSummary()));

        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(existingAttachment.getUri(), HttpMethod
                    .DELETE, null, String.class);

            logDeletionStatus(issue, existingAttachment, responseEntity);
        } catch (HttpClientErrorException e) {
            rethrowExceptionWithDescription(issue, existingAttachment, e);
        }
    }

    private void rethrowExceptionWithDescription(Issue issue, JiraIssue.Fields.Attachment existingAttachment,
                                                 HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.NOT_FOUND)
            throw new AttachmentNotFound(String.format("Attachment with file name: '%s' for issue '%s' doesn't exist",
                    existingAttachment.getFileName(), issue.getSummary()), e);
        else
            throw new CannotDeleteJiraAttachment(String.format("Attachment with file name: '%s' for issue '%s' cannot" +
                            " be deleted",
                    existingAttachment.getFileName(), issue.getSummary()), e);
    }

    private void logDeletionStatus(Issue issue, JiraIssue.Fields.Attachment existingAttachment, ResponseEntity<String
            > responseEntity) {
        if (responseEntity.getStatusCode().is2xxSuccessful())
            LOGGER.info(String.format("Deleted attachment: %s from issue: %s", existingAttachment.getFileName(),
                    issue.getSummary()));
        else
            LOGGER.warn(String.format("Attachment with file name: '%s' for issue '%s' cannot be deleted",
                    existingAttachment.getFileName(), issue.getSummary()));
    }

    public List<JiraIssue.Fields.Attachment> getExistingManifestAttachments(KickoffRequest request, String requestId) {
        Issue issue = getIssue(requestId);
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
        String getAttachmentsUrl = String.format("%s/%s/issue/%s?fields", jiraUrl, jiraRestPath, issue
                .getKey());

        try {
            ResponseEntity<JiraIssue> response = restTemplate.exchange(getAttachmentsUrl, HttpMethod.GET, null,
                    JiraIssue.class);

            if (response.getStatusCode().is2xxSuccessful())
                return response.getBody();

            throw new CannotRetrieveJiraIssue(String.format("Jira issue '%s' cannot be retrieved. Cause: %s %s", issue
                    .getSummary(), response.getStatusCode(), response.getHeaders().getOrDefault(ERRORS_HEADER_KEY,
                    emptyList())));
        } catch (HttpClientErrorException e) {
            throw new CannotRetrieveJiraIssue(String.format("Jira issue '%s' cannot be retrieved", issue.getSummary()
            ), e);
        }
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

    public IssueStatus getIssueStatus() {
        return issueStatus;
    }

    public void setIssueStatus(IssueStatus issueStatus) {
        this.issueStatus = issueStatus;
    }

    private class JiraIssueAssignmentException extends RuntimeException {
        public JiraIssueAssignmentException(String message, Exception e) {
            super(message, e);
        }

        public JiraIssueAssignmentException(String message) {
            super(message);
        }
    }

    private class NoTransitionFoundException extends RuntimeException {
        public NoTransitionFoundException(String message) {
            super(message);
        }
    }

    private class AttachmentNotFound extends RuntimeException {
        public AttachmentNotFound(String message, Exception e) {
            super(message, e);
        }
    }

    private class CannotDeleteJiraAttachment extends RuntimeException {
        public CannotDeleteJiraAttachment(String message, Exception e) {
            super(message, e);
        }
    }

    private class CannotRetrieveJiraIssue extends RuntimeException {
        public CannotRetrieveJiraIssue(String message) {
            super(message);
        }

        public CannotRetrieveJiraIssue(String message, Exception e) {
            super(message, e);
        }
    }
}
