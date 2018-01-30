package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BadInputsJiraIssueState implements JiraIssueState {
    @Value("${jira.roslin.bad.inputs.status}")
    private String badInputsStateName;

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", getName()));
    }

    @Override
    public String getName() {
        return badInputsStateName;
    }
}
