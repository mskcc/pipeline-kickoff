package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.domain.KickoffRequest;

public interface JiraIssueState {
    void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader);

    String getName();
}
