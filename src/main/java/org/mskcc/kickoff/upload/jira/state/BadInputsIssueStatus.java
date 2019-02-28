package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BadInputsIssueStatus implements IssueStatus {
    @Value("${jira.roslin.bad.inputs.status}")
    private String badInputsStateName;

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader, String key, String summary) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", getName()));
    }

    @Override
    public void validateInputs(String key, String summary, JiraFileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files in state: %s are invalid. There is no need to validate " +
                "them.", getName()));
    }

    @Override
    public String getName() {
        return badInputsStateName;
    }
}
