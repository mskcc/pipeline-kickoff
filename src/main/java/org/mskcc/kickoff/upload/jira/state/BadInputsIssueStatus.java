package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
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
    public boolean validateBefore(KickoffRequest kickoffRequest, FileUploader fileUploader, String key) {
        return true;
    }

    @Override
    public void validateAfter(String key, String summary, FileUploader fileUploader) {
        throw new IllegalStateException(String.format("Files in state: %s are invalid. There is no need to validate " +
                "them.", getName()));
    }

    @Override
    public String getName() {
        return badInputsStateName;
    }
}
