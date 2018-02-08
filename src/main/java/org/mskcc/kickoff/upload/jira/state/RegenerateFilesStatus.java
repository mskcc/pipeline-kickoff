package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;

public class RegenerateFilesStatus implements IssueStatus {
    private final String name;
    private final String transitionName;
    private final IssueStatus nextState;

    public RegenerateFilesStatus(String name, String transitionName, IssueStatus nextState) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String requestId) {
        fileUploader.deleteExistingFiles(kickoffRequest);
        fileUploader.uploadFiles(kickoffRequest, requestId);
        fileUploader.setIssueStatus(nextState);
        fileUploader.assignUser(kickoffRequest, requestId);
        fileUploader.changeStatus(transitionName, requestId);
    }

    @Override
    public void validateInputs(String issueId, JiraFileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be validated in state: %s. They haven't been " +
                "generated yet.", getName()));
    }

    @Override
    public String getName() {
        return name;
    }
}
