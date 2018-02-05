package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;

public interface JiraIssueState {
    void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader);

    String getName();
}
