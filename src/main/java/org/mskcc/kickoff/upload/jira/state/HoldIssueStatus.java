package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HoldIssueStatus implements IssueStatus {
    @Value("${jira.roslin.hold.status}")
    private String name;

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String key, String summary) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public void validateInputs(String key, String summary, JiraFileUploader jiraFileUploader) {

    }

    @Override
    public String getName() {
        return name;
    }
}
