package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.FilesValidator;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;
import org.mskcc.kickoff.upload.jira.ToBadInputsTransitioner;
import org.springframework.beans.factory.annotation.Autowired;

public class FilesRegeneratedStatus implements IssueStatus {
    private final String name;

    @Autowired
    private FilesValidator filesValidator;

    @Autowired
    private ToBadInputsTransitioner toBadInputsJiraTransitioner;

    public FilesRegeneratedStatus(String name) {
        this.name = name;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader, String requestId) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public void validateInputs(String issueId, JiraFileUploader jiraFileUploader) {
        if (!filesValidator.isValid(issueId))
            toBadInputsJiraTransitioner.transition(jiraFileUploader, issueId);
    }

    @Override
    public String getName() {
        return name;
    }
}
