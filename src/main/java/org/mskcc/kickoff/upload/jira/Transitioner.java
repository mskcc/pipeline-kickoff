package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.upload.FileUploader;

public interface Transitioner {
    void transition(FileUploader jiraFileUploader, String issueId);
}
