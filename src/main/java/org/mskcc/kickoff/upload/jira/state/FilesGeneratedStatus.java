package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.FilesValidator;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.mskcc.kickoff.upload.jira.transitioner.ToBadInputsTransitioner;
import org.mskcc.kickoff.upload.jira.transitioner.ToHoldTransitioner;
import org.springframework.beans.factory.annotation.Autowired;

public class FilesGeneratedStatus implements IssueStatus {
    private final String name;

    @Autowired
    private ToHoldTransitioner toHoldJiraTransitioner;

    @Autowired
    private FilesValidator filesValidator;

    @Autowired
    private ToBadInputsTransitioner toBadInputsJiraTransitioner;

    public FilesGeneratedStatus(String name) {
        this.name = name;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader, String key, String summary) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public void validateInputs(String key, String summary, JiraFileUploader jiraFileUploader) {
        if (!filesValidator.isValid(summary))
            toBadInputsJiraTransitioner.transition(jiraFileUploader, key);
        else
            toHoldJiraTransitioner.transition(jiraFileUploader, key);

    }

    @Override
    public String getName() {
        return name;
    }
}
