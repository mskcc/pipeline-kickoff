package org.mskcc.kickoff.upload;

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
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.notify.NotificationFormatter;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.upload.jira.PmJiraUserRetriever;
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
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    @Value("${jira.roslin.generated.transition}")
    private String generatedTransition;

    @Value("${jira.roslin.files.insufficient.transition}")
    private String badInputsTransition;

    @Autowired
    @Qualifier("jiraRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    @Qualifier("doubleSlash")
    private NotificationFormatter notificationFormatter;

    private IssueStatus issueStatus;
    private JiraRestClient restClient;

    @Override
    public void upload(String summary, KickoffRequest kickoffRequest) {

        try {
            restClient = getJiraRestClient();

            Optional<String> optionalKey = getIssueKeyFromSummary(summary);
            if (!optionalKey.isPresent()) {
                return;
            }

            String key = optionalKey.get();
            issueStatus = retrieveJiraIssueState(key);
            issueStatus.uploadFiles(kickoffRequest, this, key, summary);
            issueStatus.validateAfter(key, summary, this);
            addInfoComment(key);
        } catch (Exception e) {
            LOGGER.error(String.format("Error while trying to attach files: to jira instance: %s for issue: %s",
                    jiraUrl, summary), e);
        } finally {
            closeJiraConnection(restClient);
        }
    }

    private void addInfoComment(String key) {
        String fileCreationTimeComment = String.format("Manifest files time creation: %s",
                LocalDateTime.now());
        String errorComment = notificationFormatter.format();
        String comment = getFormattedComment(String.format("%s\\n\\n%s", fileCreationTimeComment, errorComment));
        addComment(getIssueByKey(key), comment);
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

    @Override
    public void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile, String key) {
        Issue issue = getIssueByKey(key);

        LOGGER.info(String.format("Starting to attach file: %s for request: %s to issue: %s in project: %s",
                manifestFile.getFilePath(request), request.getId(), key, projectName));

        try {
            addAttachment(request, manifestFile, restClient, issue);
        } catch (Exception e) {
            LOGGER.error(String.format("Error while trying to attach file: %s to jira instance: %s for issue: %s",
                    manifestFile.getFilePath(request), jiraUrl, key), e);
        }
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, String key) {
        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            if (manifestFile.isFileGenerated())
                uploadSingleFile(kickoffRequest, manifestFile, key);
        }
    }

    @Override
    public void assignUser(KickoffRequest kickoffRequest, String key) {
        JiraUser pmJiraUser = getPmJiraUser(kickoffRequest.getProjectInfo().get(Constants.ProjectInfo.PROJECT_MANAGER));
        Issue issue = getIssueByKey(key);

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

        HttpEntity<String> entity = new HttpEntity<>(String.format("\"%s\"", pmJiraUser.getUserName()), headers);

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


    private IssueStatus retrieveJiraIssueState(String key) {
        Issue issue = getIssueByKey(key);
        String currentState = issue.getStatus().getName();
        return statusFactory.getStatus(currentState);
    }

    @Override
    public void changeStatus(String transitionName, String key) {
        Issue issue = getIssueByKey(key);
        Promise<Iterable<Transition>> transitionsPromise = restClient.getIssueClient().getTransitions(issue);

        Iterable<Transition> transitions = transitionsPromise.claim();

        for (Transition transition : transitions) {
            String name = transition.getName();
            if (transitionName.equalsIgnoreCase(name)) {
                Promise<Void> setGeneratedStatus = restClient.getIssueClient().transition(issue, new TransitionInput(transition.getId()));
                setGeneratedStatus.claim();
                LOGGER.info(String.format("Status for issue: %s changed to: %s", issue.getSummary(), name));
                return;
            }
        }

        throw new NoTransitionFoundException(String.format("No transition found with name %s for issue %s for current" +
                        " status: %s",
                transitionName, issue.getSummary(), issue.getStatus().getName()));
    }

    private String getFormattedComment(String comment) {
        return String.format("{\"body\": \"%s\"}", comment);
    }

    private void addAttachment(KickoffRequest request, ManifestFile manifestFile, JiraRestClient restClient, Issue
            issue) {
        File file = new File(manifestFile.getFilePath(request));
        LOGGER.info(String.format("Adding attachment: %s to issue: %s", file.getName(), issue.getSummary()));

        Promise issuePromise = restClient.getIssueClient().addAttachments(issue.getAttachmentsUri(), file);

        issuePromise.claim();

        LOGGER.info(String.format("Added attachment: %s to issue: %s", file.getName(), issue.getSummary()));
    }

    @Override
    public void deleteExistingFiles(KickoffRequest request, String key) throws FileDeletionException {
        String summary = request.getId();
        Issue issue = getIssueByKey(key);

        LOGGER.info(String.format("Starting to delete existing manifest files for request: %s from issue: %s in " +
                "project: %s", request.getId(), summary, projectName));

        try {
            deleteExistingManifestAttachments(request, issue, key);
            validateNoAttachmentExist(request, key);
        } catch (Exception e) {
            String error = String.format("Error while trying to delete existing manifest attachments from jira " +
                    "instance: %s for issue: %s", jiraUrl, summary);
            throw new FileDeletionException(error, e);
        }
    }

    private void validateNoAttachmentExist(KickoffRequest request, String key) throws FileDeletionException {
        int numberOfManifestAttachments = getExistingManifestAttachments(request, key).size();

        if (numberOfManifestAttachments > 0) {
            throw new FileDeletionException(String.format("%d attached manifest file (s) was/were not deleted from " +
                    "jira " +
                    "instance: %s for issue: %s", numberOfManifestAttachments, jiraUrl, key));
        }
    }

    private void deleteExistingManifestAttachments(KickoffRequest request, Issue issue, String key) {
        List<JiraIssue.Fields.Attachment> existingManifestAttachments = getExistingManifestAttachments(request,
                key);
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

    @Override
    public List<JiraIssue.Fields.Attachment> getExistingManifestAttachments(KickoffRequest request, String key) {
        Issue issue = getIssueByKey(key);
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

    private Issue getIssueBySummary(String summary) {
        Iterable<Issue> issues = getIssues(summary, restClient);
        Issue foundIssue = Iterables.getFirst(issues, null);
        return getIssueByKey(foundIssue.getKey());
    }

    private Issue getIssueByKey(String key) {
        Promise<Issue> issuePromise = restClient.getIssueClient().getIssue(key);
        return issuePromise.claim();
    }

    private Iterable<Issue> getIssues(String summary, JiraRestClient restClient) {
        SearchRestClient searchClient = restClient.getSearchClient();
        String jql = formatJqlQuery(projectName, summary);
        Promise<SearchResult> searchResultPromise = searchClient.searchJql(jql);
        SearchResult searchResult = searchResultPromise.claim();
        Iterable<Issue> issues = searchResult.getIssues();

        Set<Issue> exactSummaryIssues = StreamSupport.stream(issues.spliterator(), false)
                .filter(i -> Objects.equals(i.getSummary(), summary))
                .collect(Collectors.toSet());

        return exactSummaryIssues;
    }

    private Optional<String> getIssueKeyFromSummary(String summary) {
        Iterable<Issue> issues = getIssues(summary, restClient);
        int size = Iterables.size(issues);
        String msg = "";
        if (size == 0) {
            msg = String.format("No jira issues found for project: %s and summary: %s. No files will be uploaded.", projectName, summary);
            LOGGER.error(msg);
            errorRepository.add(new GenerationError(msg, ErrorCode.JIRA_UPLOAD_ERROR));
            return Optional.empty();
        } else if (size > 1) {
            msg = String.format("Multiple jira issues found for project: %s and summary: %s. No files will be uploaded.", projectName, summary);
            LOGGER.error(msg);
            errorRepository.add(new GenerationError(msg, ErrorCode.JIRA_UPLOAD_ERROR));
            return Optional.empty();
        }

        msg = String.format("Found %d issues with summary: %s in project: %s",
                size, summary, projectName);
        LOGGER.info(msg);
        return Optional.of(Iterables.getFirst(issues, null).getKey());
    }

    private String formatJqlQuery(String project, String summary) {
        StringJoiner sj = new StringJoiner(" ", "", "");
        sj.add("project").add("=").add("\"" + project + "\"").add("AND");
        sj.add("summary").add("~").add("\\\"" + summary + "\\\"");
        return sj.toString();
    }

    private JiraRestClient getJiraRestClient() throws URISyntaxException {
        LOGGER.info(String.format("Using jira instance: %s", jiraUrl));

        URI jiraServerUri = new URI(jiraUrl);

        return new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication
                (jiraServerUri, username, password);
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

    public IssueStatus getIssueStatus() {
        return issueStatus;
    }

    public void setIssueStatus(IssueStatus issueStatus) {
        this.issueStatus = issueStatus;
    }


    private void logIfNotSucceeded(Issue issue, ResponseEntity<String> addCommentResponse) {
        if (!addCommentResponse.getStatusCode().is2xxSuccessful())
            LOGGER.warn(String.format("Comment not added to issue %s. Cause: %s %s", issue.getSummary(),
                    addCommentResponse.getStatusCode(),
                    addCommentResponse.getHeaders().getOrDefault(ERRORS_HEADER_KEY, emptyList())));
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
