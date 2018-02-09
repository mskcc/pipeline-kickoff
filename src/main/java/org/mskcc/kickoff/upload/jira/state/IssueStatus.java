package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;

public interface IssueStatus {
    void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String requestId);

    void validateInputs(String issueId, JiraFileUploader jiraFileUploader);

    String getName();
}
