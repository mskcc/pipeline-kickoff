package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;

public class FilesGeneratedStatus implements IssueStatus {
    private final String name;

    public FilesGeneratedStatus(String name) {
        this.name = name;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public void validateInputs(String issueId) {

    }

    @Override
    public String getName() {
        return name;
    }
}
