package org.mskcc.kickoff.roslin.upload.jira.transitioner;

import org.mskcc.kickoff.roslin.upload.FileUploader;

public interface Transitioner {
    void transition(FileUploader jiraFileUploader, String key);
}
