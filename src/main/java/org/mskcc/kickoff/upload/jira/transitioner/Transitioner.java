package org.mskcc.kickoff.upload.jira.transitioner;

import org.mskcc.kickoff.upload.FileUploader;

public interface Transitioner {
    void transition(FileUploader jiraFileUploader, String key);
}
