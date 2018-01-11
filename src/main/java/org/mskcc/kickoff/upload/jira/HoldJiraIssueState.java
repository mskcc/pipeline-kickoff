package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HoldJiraIssueState implements JiraIssueState {
    @Value("${jira.roslin.hold.transition}")
    private String name;

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public String getName() {
        return name;
    }
}
