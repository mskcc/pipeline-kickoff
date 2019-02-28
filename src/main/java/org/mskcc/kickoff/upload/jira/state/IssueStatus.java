package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.JiraFileUploader;

public interface IssueStatus {
    void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String key, String summary);

    void validateInputs(String key, String summary, JiraFileUploader jiraFileUploader);

    String getName();
}
