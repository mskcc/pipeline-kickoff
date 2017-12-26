package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.domain.KickoffRequest;

public class FilesGeneratedState implements JiraIssueState {
    private final String name;

    public FilesGeneratedState(String name) {
        this.name = name;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public String getName() {
        return name;
    }
}
